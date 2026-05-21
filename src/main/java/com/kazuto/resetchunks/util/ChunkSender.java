package com.kazuto.resetchunks.util;

import com.kazuto.resetchunks.ResetChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Handles sending chunk updates to clients and recalculating lighting.
 */
public class ChunkSender {

    /**
     * Recalculates lighting and sends chunk to all nearby players.
     */
    public static void sendChunkToPlayers(ServerLevel level, LevelChunk chunk) {
        try {
            var lightEngine = level.getChunkSource().getLightEngine();
            var chunkPos = chunk.getPos();

            ResetChunks.LOGGER.debug("Recalculating lighting for chunk {}", chunkPos);

            // Force lighting recalculation by checking every block
            int baseX = chunkPos.getMinBlockX();
            int baseZ = chunkPos.getMinBlockZ();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = level.getMaxY() - 1; y >= level.getMinY(); y--) {
                        BlockPos pos = new BlockPos(baseX + x, y, baseZ + z);
                        lightEngine.checkBlock(pos);
                    }
                }
            }

            // Update neighboring chunk borders to prevent light seams
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    var neighborPos = new net.minecraft.world.level.ChunkPos(chunkPos.x() + dx, chunkPos.z() + dz);
                    if (level.getChunkSource().hasChunk(neighborPos.x(), neighborPos.z())) {
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
                ResetChunks.LOGGER.debug("Sent regenerated chunk {} to player {}",
                    chunkPos, player.getName().getString());
            }

        } catch (Exception e) {
            ResetChunks.LOGGER.error("Failed to send chunk update: {}", e.getMessage());
        }
    }
}
