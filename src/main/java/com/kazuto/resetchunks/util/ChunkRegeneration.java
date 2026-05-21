package com.kazuto.resetchunks.util;

import com.kazuto.resetchunks.ResetChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkRegeneration {

    public static void regenerateChunks(ServerLevel level, ChunkPos centerChunk, int diameter) {
        List<ChunkPos> chunksToRegenerate = calculateChunkArea(centerChunk, diameter);

        ResetChunks.LOGGER.info("Starting regeneration of {} chunks", chunksToRegenerate.size());

        // Process chunks asynchronously one at a time
        regenerateChunksAsync(level, chunksToRegenerate, 0);
    }

    private static void regenerateChunksAsync(ServerLevel level, List<ChunkPos> chunks, int index) {
        if (index >= chunks.size()) {
            ResetChunks.LOGGER.info("Completed regeneration of {} chunks", chunks.size());
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
        ResetChunks.LOGGER.info("Starting regeneration for chunk {}", chunkPos);

        ServerChunkCache chunkSource = level.getChunkSource();

        // Remove entities first
        if (chunkSource.hasChunk(chunkPos.x(), chunkPos.z())) {
            removeEntitiesFromChunk(level, chunkPos);
        }

        // Get the chunk generator
        var generator = chunkSource.getGenerator();
        var randomState = chunkSource.randomState();

        // Get existing chunk to replace
        LevelChunk existingChunk = chunkSource.getChunk(chunkPos.x(), chunkPos.z(), false);
        if (existingChunk == null) {
            ResetChunks.LOGGER.warn("Chunk {} not loaded", chunkPos);
            return CompletableFuture.completedFuture(null);
        }

        // Clear existing blocks
        clearChunkBlocks(existingChunk);

        // Generate fresh terrain using the generator (async)
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

                // Try to apply decoration (ores, features)
                // This requires WorldGenLevel, so we try ServerLevel and catch if it fails
                try {
                    generator.applyBiomeDecoration(level, generatedChunk, level.structureManager());
                    ResetChunks.LOGGER.debug("Applied biome decoration (ores, features) to chunk {}", chunkPos);
                } catch (Exception e) {
                    ResetChunks.LOGGER.warn("Could not apply biome decoration - ores and features will be missing: {}", e.getMessage());
                }

                // Apply surface rules (grass/dirt on top)
                applySurfaceRules(existingChunk, level);

                // Update clients
                sendChunkToPlayers(level, existingChunk);

                ResetChunks.LOGGER.info("Successfully regenerated chunk {}", chunkPos);

            } catch (Exception e) {
                ResetChunks.LOGGER.error("Failed to finalize chunk {}: {}", chunkPos, e.getMessage(), e);
            }
        }, level.getServer()).exceptionally(throwable -> {
            ResetChunks.LOGGER.error("fillFromNoise failed for chunk {}, using fallback: {}", chunkPos, throwable.getMessage());
            try {
                fillChunkWithTerrain(existingChunk, level);
                sendChunkToPlayers(level, existingChunk);
            } catch (Exception e) {
                ResetChunks.LOGGER.error("Fallback failed for chunk {}: {}", chunkPos, e.getMessage());
            }
            return null;
        });
    }

    private static void applySurfaceRules(LevelChunk chunk, ServerLevel level) {
        try {
            var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();

            int baseX = chunk.getPos().x() << 4;
            int baseZ = chunk.getPos().z() << 4;

            // Apply basic surface rules (replace top stone with grass/dirt)
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // Find the top solid block
                    for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                        var state = chunk.getBlockState(pos);

                        if (!state.isAir() && state.is(net.minecraft.world.level.block.Blocks.STONE)) {
                            // Found top stone block - replace with grass
                            chunk.setBlockState(pos, grass, 0);

                            // Replace a few blocks below with dirt
                            for (int d = 1; d <= 3; d++) {
                                BlockPos below = pos.below(d);
                                var belowState = chunk.getBlockState(below);
                                if (belowState.is(net.minecraft.world.level.block.Blocks.STONE)) {
                                    chunk.setBlockState(below, dirt, 0);
                                }
                            }
                            break; // Found surface, move to next column
                        }
                    }
                }
            }

            ResetChunks.LOGGER.debug("Applied surface rules to chunk {}", chunk.getPos());

        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to apply surface rules: {}", e.getMessage());
        }
    }

    private static void fillChunkWithTerrain(LevelChunk chunk, ServerLevel level) {
        try {
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

            int baseX = chunk.getPos().x() << 4;
            int baseZ = chunk.getPos().z() << 4;

            // Fill chunk with simple layered terrain
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);

                        if (y < 60) {
                            // Below y=60: stone
                            chunk.setBlockState(pos, stone, 0);
                        } else if (y < 63) {
                            // y=60-62: dirt
                            chunk.setBlockState(pos, dirt, 0);
                        } else if (y == 63) {
                            // y=63: grass
                            chunk.setBlockState(pos, grass, 0);
                        } else {
                            // Above y=63: air
                            chunk.setBlockState(pos, air, 0);
                        }
                    }
                }
            }

            ResetChunks.LOGGER.debug("Filled chunk {} with basic terrain", chunk.getPos());

        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to fill chunk with terrain: {}", e.getMessage());
        }
    }

    private static void sendChunkToPlayers(ServerLevel level, LevelChunk chunk) {
        try {
            var lightEngine = level.getChunkSource().getLightEngine();
            var chunkPos = chunk.getPos();

            // Force lighting recalculation by checking every block in the chunk
            int baseX = chunkPos.getMinBlockX();
            int baseZ = chunkPos.getMinBlockZ();

            ResetChunks.LOGGER.debug("Recalculating lighting for chunk {}", chunkPos);

            // Queue light updates for every column in the chunk
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    // Check from top to bottom
                    for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                        lightEngine.checkBlock(pos);
                    }
                }
            }

            // Also update neighboring chunk borders to prevent light bleeding
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip center chunk, already done

                    ChunkPos neighborPos = new ChunkPos(chunkPos.x() + dx, chunkPos.z() + dz);
                    if (level.getChunkSource().hasChunk(neighborPos.x(), neighborPos.z())) {
                        // Update border blocks of neighbors
                        int nx = neighborPos.getMinBlockX() + (dx < 0 ? 15 : 0);
                        int nz = neighborPos.getMinBlockZ() + (dz < 0 ? 15 : 0);

                        for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                            lightEngine.checkBlock(new BlockPos(nx, y, nz));
                        }
                    }
                }
            }

            // Send the updated chunk with recalculated lighting
            ClientboundLevelChunkWithLightPacket packet =
                new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null);

            for (ServerPlayer player : level.players()) {
                player.connection.send(packet);
                ResetChunks.LOGGER.debug("Sent regenerated chunk {} to player {}", chunkPos, player.getName().getString());
            }

        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to send chunk update: {}", e.getMessage());
        }
    }

    private static void removeEntitiesFromChunk(ServerLevel level, ChunkPos chunkPos) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        List<Entity> entitiesToRemove = new ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            // Skip players - we don't want to remove them!
            if (entity instanceof ServerPlayer) {
                continue;
            }

            BlockPos pos = entity.blockPosition();
            if (pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                entitiesToRemove.add(entity);
            }
        }

        for (Entity entity : entitiesToRemove) {
            // Use remove() with RemovalReason for more forceful removal
            entity.remove(Entity.RemovalReason.DISCARDED);
            ResetChunks.LOGGER.debug("Removing entity: {} at {}", entity.getType(), entity.blockPosition());
        }

        if (!entitiesToRemove.isEmpty()) {
            ResetChunks.LOGGER.info("Removed {} entities from chunk {}", entitiesToRemove.size(), chunkPos);
        }
    }

    private static void clearChunkBlocks(LevelChunk chunk) {
        try {
            var airState = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

            for (var section : chunk.getSections()) {
                if (section != null && !section.hasOnlyAir()) {
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                section.setBlockState(x, y, z, airState, false);
                            }
                        }
                    }
                }
            }

            ResetChunks.LOGGER.debug("Cleared all blocks in chunk {}", chunk.getPos());

        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to clear chunk blocks: {}", e.getMessage());
        }
    }
}
