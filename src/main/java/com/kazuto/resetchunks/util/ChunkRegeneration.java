package com.kazuto.resetchunks.util;

import com.kazuto.resetchunks.ResetChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkRegeneration {

    public static void regenerateChunks(ServerLevel level, ChunkPos centerChunk, int diameter) {
        List<ChunkPos> chunksToRegenerate = calculateChunkArea(centerChunk, diameter);

        ResetChunks.LOGGER.info("Starting regeneration of {} chunks", chunksToRegenerate.size());

        for (ChunkPos chunkPos : chunksToRegenerate) {
            regenerateChunk(level, chunkPos);
        }

        ResetChunks.LOGGER.info("Completed regeneration of {} chunks", chunksToRegenerate.size());
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

    private static void regenerateChunk(ServerLevel level, ChunkPos chunkPos) {
        try {
            ResetChunks.LOGGER.debug("Regenerating chunk at {}", chunkPos);

            ServerChunkCache chunkSource = level.getChunkSource();

            // Remove entities first
            if (chunkSource.hasChunk(chunkPos.x(), chunkPos.z())) {
                removeEntitiesFromChunk(level, chunkPos);
            }

            // Get the existing chunk
            LevelChunk existingChunk = chunkSource.getChunk(chunkPos.x(), chunkPos.z(), false);
            if (existingChunk == null) {
                ResetChunks.LOGGER.warn("Chunk {} not loaded, skipping", chunkPos);
                return;
            }

            // Fill chunk with simple terrain (stone + grass)
            // This is a simplified reset - not true world generation
            fillChunkWithTerrain(existingChunk, level);

            ResetChunks.LOGGER.info("Reset chunk {} with basic terrain", chunkPos);

            // Send updated chunk to nearby players
            sendChunkToPlayers(level, existingChunk);

        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to regenerate chunk at {}", chunkPos, e);
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
            // First, send unload packet to clear client cache
            ClientboundForgetLevelChunkPacket unloadPacket = new ClientboundForgetLevelChunkPacket(chunk.getPos());

            for (ServerPlayer player : level.players()) {
                player.connection.send(unloadPacket);
            }

            // Small delay to ensure unload is processed
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Mark the chunk as needing light updates
            level.getChunkSource().getLightEngine().checkBlock(chunk.getPos().getWorldPosition());

            // Now send the updated chunk
            ClientboundLevelChunkWithLightPacket reloadPacket =
                new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);

            for (ServerPlayer player : level.players()) {
                player.connection.send(reloadPacket);
                ResetChunks.LOGGER.debug("Sent regenerated chunk {} to player {}", chunk.getPos(), player.getName().getString());
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
            BlockPos pos = entity.blockPosition();
            if (pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                entitiesToRemove.add(entity);
            }
        }

        for (Entity entity : entitiesToRemove) {
            entity.discard();
        }

        if (!entitiesToRemove.isEmpty()) {
            ResetChunks.LOGGER.debug("Removed {} entities from chunk {}", entitiesToRemove.size(), chunkPos);
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
