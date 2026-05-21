package com.kazuto.resetchunks.util;

import com.kazuto.resetchunks.ResetChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles cleaning operations on chunks (entities, blocks, vegetation).
 */
public class ChunkCleaner {

    /**
     * Removes all entities (except players) from a chunk.
     */
    public static void removeEntities(ServerLevel level, ChunkPos chunkPos) {
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
            entity.remove(Entity.RemovalReason.DISCARDED);
            ResetChunks.LOGGER.debug("Removing entity: {} at {}", entity.getType(), entity.blockPosition());
        }

        if (!entitiesToRemove.isEmpty()) {
            ResetChunks.LOGGER.info("Removed {} entities from chunk {}", entitiesToRemove.size(), chunkPos);
        }
    }

    /**
     * Clears all blocks in a chunk to air.
     */
    public static void clearAllBlocks(LevelChunk chunk) {
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

    /**
     * Removes all tree blocks (logs and leaves) from a chunk.
     */
    public static void removeTreesFromChunk(ServerLevel level, LevelChunk chunk) {
        try {
            int removed = 0;
            int baseX = chunk.getPos().x() << 4;
            int baseZ = chunk.getPos().z() << 4;
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                        var state = level.getBlockState(pos);

                        if (state.is(net.minecraft.tags.BlockTags.LOGS) ||
                            state.is(net.minecraft.tags.BlockTags.LEAVES)) {
                            level.setBlock(pos, air, 3);
                            removed++;
                        }
                    }
                }
            }

            if (removed > 0) {
                ResetChunks.LOGGER.info("Removed {} tree blocks from chunk {}", removed, chunk.getPos());
            }
        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to remove trees: {}", e.getMessage());
        }
    }

    /**
     * Removes tree blocks ONLY at the boundaries of neighboring chunks.
     */
    public static void removeTreesAtBoundaries(ServerLevel level, ChunkPos centerChunk) {
        try {
            int removed = 0;
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    ChunkPos neighborPos = new ChunkPos(centerChunk.x() + dx, centerChunk.z() + dz);
                    if (!level.getChunkSource().hasChunk(neighborPos.x(), neighborPos.z())) continue;

                    int scanX = dx == -1 ? 15 : (dx == 1 ? 0 : -1);
                    int scanZ = dz == -1 ? 15 : (dz == 1 ? 0 : -1);
                    int neighborBaseX = neighborPos.getMinBlockX();
                    int neighborBaseZ = neighborPos.getMinBlockZ();

                    if (dx != 0) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                                BlockPos pos = new BlockPos(neighborBaseX + scanX, y, neighborBaseZ + z);
                                var state = level.getBlockState(pos);

                                if (state.is(net.minecraft.tags.BlockTags.LOGS) ||
                                    state.is(net.minecraft.tags.BlockTags.LEAVES)) {
                                    level.setBlock(pos, air, 3);
                                    removed++;
                                }
                            }
                        }
                    }

                    if (dz != 0) {
                        for (int x = 0; x < 16; x++) {
                            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                                BlockPos pos = new BlockPos(neighborBaseX + x, y, neighborBaseZ + scanZ);
                                var state = level.getBlockState(pos);

                                if (state.is(net.minecraft.tags.BlockTags.LOGS) ||
                                    state.is(net.minecraft.tags.BlockTags.LEAVES)) {
                                    level.setBlock(pos, air, 3);
                                    removed++;
                                }
                            }
                        }
                    }
                }
            }

            if (removed > 0) {
                ResetChunks.LOGGER.info("Removed {} boundary tree blocks", removed);
            }
        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to remove boundary trees: {}", e.getMessage());
        }
    }
}
