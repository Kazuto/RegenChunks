package com.kazuto.regenchunks.util;

import com.kazuto.regenchunks.RegenChunks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main coordinator for chunk regeneration operations.
 * Delegates specific tasks to helper classes: ChunkCleaner, ChunkSender, SurfaceBuilder.
 */
public class ChunkRegeneration {

    public static void regenerateChunks(ServerLevel level, ChunkPos centerChunk, int diameter) {
        List<ChunkPos> chunksToRegenerate = calculateChunkArea(centerChunk, diameter);

        RegenChunks.LOGGER.info("Starting regeneration of {} chunks", chunksToRegenerate.size());

        // Process chunks asynchronously one at a time
        regenerateChunksAsync(level, chunksToRegenerate, 0);
    }

    private static void regenerateChunksAsync(ServerLevel level, List<ChunkPos> chunks, int index) {
        if (index >= chunks.size()) {
            RegenChunks.LOGGER.info("Completed regeneration of {} chunks", chunks.size());
            return;
        }

        ChunkPos chunkPos = chunks.get(index);
        regenerateChunkAsync(level, chunkPos).thenRun(() -> {
            // Schedule next chunk on the server thread
            level.getServer().execute(() -> {
                regenerateChunksAsync(level, chunks, index + 1);
            });
        });
    }

    private static List<ChunkPos> calculateChunkArea(ChunkPos center, int diameter) {
        List<ChunkPos> chunks = new ArrayList<>();

        int startX = center.x() - diameter;
        int endX = center.x() + diameter;
        int startZ = center.z() - diameter;
        int endZ = center.z() + diameter;

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }

        return chunks;
    }

    private static CompletableFuture<Void> regenerateChunkAsync(ServerLevel level, ChunkPos chunkPos) {
        RegenChunks.LOGGER.info("Starting regeneration for chunk {}", chunkPos);

        ServerChunkCache chunkSource = level.getChunkSource();

        // Remove entities first
        if (chunkSource.hasChunk(chunkPos.x(), chunkPos.z())) {
            ChunkCleaner.removeEntities(level, chunkPos);
        }

        // Get existing chunk
        LevelChunk existingChunk = chunkSource.getChunk(chunkPos.x(), chunkPos.z(), false);
        if (existingChunk == null) {
            RegenChunks.LOGGER.warn("Chunk {} not loaded", chunkPos);
            return CompletableFuture.completedFuture(null);
        }

        // Get generator and state
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();

        // Clear existing chunk data
        ChunkCleaner.clearAllBlocks(existingChunk);

        // Regenerate using the full chunk generation pipeline
        return generator.fillFromNoise(
            net.minecraft.world.level.levelgen.blending.Blender.empty(),
            randomState,
            level.structureManager(),
            existingChunk
        ).thenAcceptAsync(generatedChunk -> {
            try {
                // Apply biomes
                generator.createBiomes(
                    randomState,
                    net.minecraft.world.level.levelgen.blending.Blender.empty(),
                    level.structureManager(),
                    generatedChunk
                );

                // Apply surface rules
                if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGenerator) {
                    try {
                        var context = new net.minecraft.world.level.levelgen.WorldGenerationContext(noiseGenerator, level);
                        var biomeRegistry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);

                        noiseGenerator.buildSurface(
                            generatedChunk,
                            context,
                            randomState,
                            level.structureManager(),
                            level.getBiomeManager(),
                            biomeRegistry,
                            net.minecraft.world.level.levelgen.blending.Blender.empty()
                        );
                        RegenChunks.LOGGER.debug("Applied buildSurface (with deepslate)");
                    } catch (Exception e) {
                        RegenChunks.LOGGER.warn("buildSurface failed: {}, using fallback", e.getMessage());
                        SurfaceBuilder.applyBasicSurfaceRules(existingChunk, level);
                    }
                } else {
                    SurfaceBuilder.applyBasicSurfaceRules(existingChunk, level);
                }

                // Apply biome decoration (ores + trees)
                try {
                    generator.applyBiomeDecoration(level, generatedChunk, level.structureManager());
                    RegenChunks.LOGGER.debug("Applied biome decoration");

                    // Remove trees to prevent broken trees at boundaries
                    ChunkCleaner.removeTreesFromChunk(level, existingChunk);
                    ChunkCleaner.removeTreesAtBoundaries(level, chunkPos);
                } catch (Exception e) {
                    RegenChunks.LOGGER.warn("Biome decoration failed: {}", e.getMessage());
                }

                // Update clients
                ChunkSender.sendChunkToPlayers(level, existingChunk);

                RegenChunks.LOGGER.info("Successfully regenerated chunk {}", chunkPos);

            } catch (Exception e) {
                RegenChunks.LOGGER.error("Failed to finalize chunk {}: {}", chunkPos, e.getMessage(), e);
            }
        }, level.getServer()).exceptionally(throwable -> {
            RegenChunks.LOGGER.error("Chunk generation failed: {}", throwable.getMessage());
            return null;
        });
    }
}
