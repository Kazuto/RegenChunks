# Changelog

All notable changes to RegenChunks will be documented in this file.

## [1.0.0] - 2025-05-21

### Added
- Initial release of RegenChunks for Minecraft 26.1.2 (NeoForge)
- `/regenchunks <diameter>` command for server operators
- True seed-based chunk regeneration using world generator
- Regenerates terrain (stone, deepslate, grass, dirt, water)
- Regenerates all ores (vanilla and modded)
- Removes entities (except players) from regenerated chunks
- Automatic tree removal to prevent broken trees at chunk boundaries
- Lighting recalculation for regenerated chunks
- Async chunk processing to prevent server freezing

### Features
- Diameter parameter: 0 = 1 chunk, 1 = 9 chunks (3×3), 2 = 25 chunks (5×5), etc.
- Compatible with modded ore generation (tested with ATM11)
- Regenerates modded ores like Stellar Arcanum, Prosperity ore, etc.
- Processes chunks one at a time to maintain server performance

### Known Limitations
- Trees are removed from regenerated areas to prevent broken trees
- Some fluid post-processing warnings in logs (harmless)
- Current chunk may require relog to see changes
- Chunks must be loaded to regenerate

### Technical Details
- Uses NoiseBasedChunkGenerator.fillFromNoise() for base terrain
- Uses NoiseBasedChunkGenerator.buildSurface() for deepslate/surface rules
- Uses generator.applyBiomeDecoration() for ore/feature placement
- Boundary-aware tree removal prevents broken stumps in neighboring chunks
