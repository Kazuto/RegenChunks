package com.kazuto.regenchunks.util;

import com.kazuto.regenchunks.RegenChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Handles surface rule application and fallback terrain generation.
 */
public class SurfaceBuilder {

    /**
     * Applies basic surface rules: grass/dirt on top, deepslate at low Y levels.
     * Used as fallback when NoiseBasedChunkGenerator.buildSurface fails.
     */
    public static void applyBasicSurfaceRules(LevelChunk chunk, ServerLevel level) {
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

                        if (!state.isAir() && (state.is(net.minecraft.world.level.block.Blocks.STONE) ||
                                               state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE))) {
                            // Found top solid block - replace with grass
                            chunk.setBlockState(pos, grass, 0);

                            // Replace a few blocks below with dirt
                            for (int d = 1; d <= 3; d++) {
                                BlockPos below = pos.below(d);
                                var belowState = chunk.getBlockState(below);
                                if (belowState.is(net.minecraft.world.level.block.Blocks.STONE) ||
                                    belowState.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)) {
                                    chunk.setBlockState(below, dirt, 0);
                                }
                            }
                            break; // Found surface, move to next column
                        }
                    }
                }
            }

            RegenChunks.LOGGER.debug("Applied basic surface rules to chunk {}", chunk.getPos());
        } catch (Exception e) {
            RegenChunks.LOGGER.error("Failed to apply surface rules: {}", e.getMessage());
        }
    }

    /**
     * Fills a chunk with simple flat terrain (stone + grass).
     * Emergency fallback if generation completely fails.
     */
    public static void fillWithFlatTerrain(LevelChunk chunk, ServerLevel level) {
        try {
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
            var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

            int baseX = chunk.getPos().x() << 4;
            int baseZ = chunk.getPos().z() << 4;

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);

                        if (y < 60) {
                            chunk.setBlockState(pos, stone, 0);
                        } else if (y < 63) {
                            chunk.setBlockState(pos, dirt, 0);
                        } else if (y == 63) {
                            chunk.setBlockState(pos, grass, 0);
                        } else {
                            chunk.setBlockState(pos, air, 0);
                        }
                    }
                }
            }

            RegenChunks.LOGGER.debug("Filled chunk {} with flat terrain", chunk.getPos());
        } catch (Exception e) {
            RegenChunks.LOGGER.error("Failed to fill chunk with terrain: {}", e.getMessage());
        }
    }
}
