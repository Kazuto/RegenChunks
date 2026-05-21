package com.kazuto.resetchunks.util;

import com.kazuto.resetchunks.ResetChunks;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Helper class for manipulating Minecraft region files (.mca).
 * Region files store chunks in a 32x32 grid using the Anvil format.
 */
public class RegionFileHelper {

    private static final int SECTOR_SIZE = 4096;
    private static final int CHUNK_HEADER_SIZE = 5;

    /**
     * Deletes a chunk from a region file by zeroing out its data.
     *
     * @param regionFilePath Path to the .mca region file
     * @param chunkPos The chunk position to delete
     * @return true if the chunk was successfully deleted, false otherwise
     */
    public static boolean deleteChunkFromRegion(Path regionFilePath, ChunkPos chunkPos) {
        try (RandomAccessFile regionFile = new RandomAccessFile(regionFilePath.toFile(), "rw")) {
            // Calculate the chunk's position within the region (0-31 for both x and z)
            int localX = chunkPos.getRegionLocalX();
            int localZ = chunkPos.getRegionLocalZ();

            // Each chunk's location is stored in the first 8KB of the file
            // Location offset = 4 bytes at position: 4 * ((x % 32) + (z % 32) * 32)
            int locationOffset = 4 * (localX + localZ * 32);

            // Read the chunk location data (3 bytes offset + 1 byte sector count)
            regionFile.seek(locationOffset);
            int locationData = regionFile.readInt();

            if (locationData == 0) {
                // Chunk doesn't exist in this region file
                ResetChunks.LOGGER.debug("Chunk {} not found in region file {}", chunkPos, regionFilePath.getFileName());
                return false;
            }

            // Extract offset (in sectors) and size (in sectors)
            int offset = locationData >> 8;  // First 3 bytes
            int sectorCount = locationData & 0xFF;  // Last byte

            if (offset == 0 || sectorCount == 0) {
                ResetChunks.LOGGER.debug("Chunk {} has invalid data in region file", chunkPos);
                return false;
            }

            // Zero out the location entry (marks chunk as deleted)
            regionFile.seek(locationOffset);
            regionFile.writeInt(0);

            // Also zero out the timestamp entry (in the second 4KB section)
            int timestampOffset = 4096 + (localX + localZ * 32) * 4;
            regionFile.seek(timestampOffset);
            regionFile.writeInt(0);

            // Optionally: zero out the actual chunk data (recommended for clean deletion)
            long chunkDataOffset = offset * SECTOR_SIZE;
            regionFile.seek(chunkDataOffset);
            byte[] zeros = new byte[sectorCount * SECTOR_SIZE];
            regionFile.write(zeros);

            ResetChunks.LOGGER.info("Successfully deleted chunk {} from region file {}", chunkPos, regionFilePath.getFileName());
            return true;

        } catch (IOException e) {
            ResetChunks.LOGGER.error("Failed to delete chunk {} from region file {}: {}",
                chunkPos, regionFilePath.getFileName(), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a chunk exists in a region file.
     *
     * @param regionFilePath Path to the .mca region file
     * @param chunkPos The chunk position to check
     * @return true if the chunk exists in the region file
     */
    public static boolean chunkExistsInRegion(Path regionFilePath, ChunkPos chunkPos) {
        try (RandomAccessFile regionFile = new RandomAccessFile(regionFilePath.toFile(), "r")) {
            int localX = chunkPos.getRegionLocalX();
            int localZ = chunkPos.getRegionLocalZ();
            int locationOffset = 4 * (localX + localZ * 32);

            regionFile.seek(locationOffset);
            int locationData = regionFile.readInt();

            return locationData != 0;

        } catch (IOException e) {
            ResetChunks.LOGGER.error("Failed to check chunk {} in region file {}: {}",
                chunkPos, regionFilePath.getFileName(), e.getMessage());
            return false;
        }
    }
}
