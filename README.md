# RegenChunks

A NeoForge mod that allows server operators to regenerate chunks in Minecraft, perfect for fixing corrupted terrain, resetting resource areas, or refreshing landscapes.

## Features

- **Regenerate Chunks** - Reset chunks to their original generated state based on world seed
- **Precise Control** - Specify exact diameter for chunk regeneration area
- **Permission-Based** - Only server operators (level 2+) can execute the command
- **Multi-Dimension** - Works in Overworld, Nether, and End dimensions
- **Simple Command** - Easy-to-use single command interface
- **Safety Limits** - Built-in maximum diameter to prevent accidental mass regeneration

## Requirements

- **Minecraft Version:** 26.1.2+
- **Modloader:** NeoForge 26.1.3.60-beta+
- **Side:** Server-side (works on dedicated servers and single-player)

## Installation

1. Download the latest release from the [Releases](../../releases) page
2. Place the `.jar` file in your `mods` folder
3. Restart your Minecraft server or game
4. Verify installation with `/regenchunks` command

## Usage

### Command

```
/regenchunks <diameter>
```

### Parameters

- **`diameter`** - Number of chunks from the center in each direction (radius)
  - Range: 1-20
  - Example: `5` regenerates an 11×11 chunk area (5 + 1 + 5 = 11 chunks wide)

### Examples

```bash
# Regenerate current chunk only
/regenchunks 0

# Regenerate a 3×3 chunk area (1 chunk in each direction + center)
/regenchunks 1

# Regenerate a 11×11 chunk area (5 chunks in each direction + center)
/regenchunks 5

# Regenerate a 21×21 chunk area (10 chunks in each direction + center)
/regenchunks 10
```

### Calculating Area Size

The total area regenerated is **(2 × diameter + 1)²** chunks:

| Diameter | Total Area | Chunk Count | Block Area |
|----------|------------|-------------|------------|
| 0        | 1×1        | 1 chunk     | 16×16 blocks |
| 1        | 3×3        | 9 chunks    | 48×48 blocks |
| 5        | 11×11      | 121 chunks  | 176×176 blocks |
| 10       | 21×21      | 441 chunks  | 336×336 blocks |
| 20       | 41×41      | 1,681 chunks| 656×656 blocks |

## Permissions

Only server operators with **permission level 2 or higher** can use this command.

### Setting Operator Level

```bash
# Default operator (level 4)
/op <username>

# Specific operator level
/op <username> <level>
```

## Important Warnings

**DATA LOSS**: Regenerating chunks will **permanently delete** all blocks, entities, and tile entities in the affected area, including:
- Player-built structures
- Chests and their contents
- Item frames, paintings, armor stands
- Animals, mobs, and villagers
- Redstone contraptions
- Any other player modifications

**BACKUP FIRST**: Always create a world backup before using this command on important areas!

**PLAYER SAFETY**: Players standing in regenerating chunks may experience issues. Teleport them away first.

**SERVER PERFORMANCE**: Large regeneration operations may cause temporary lag spikes.

## How It Works

1. **Calculate Area**: Determines all chunks within the specified diameter from your current position
2. **Unload Chunks**: Safely unloads all chunks in the regeneration area
3. **Delete Data**: Removes chunk data from region files
4. **Regenerate**: Chunks are regenerated using the world seed when reloaded
5. **Reload**: Chunks are loaded back with fresh terrain

## Use Cases

- **Reset Resource Areas**: Regenerate mining areas or farms
- **Fix Corruption**: Repair chunks with visual glitches or errors
- **Restore Griefed Areas**: Quickly restore terrain damaged by griefers
- **Landscape Refresh**: Remove unwanted terrain modifications
- **World Updates**: Get new terrain features from Minecraft updates
- **Creative Mode**: Quickly reset build areas for testing

## Frequently Asked Questions

### Q: Can I undo a chunk regeneration?
**A:** No, regeneration is permanent. Always backup your world first!

### Q: Will this work in modded dimensions?
**A:** The mod is designed for vanilla dimensions (Overworld, Nether, End). Modded dimensions may have unpredictable results.

### Q: What happens to my items/builds?
**A:** Everything in the regenerated chunks is permanently deleted and replaced with fresh terrain.

### Q: Can I use this on a multiplayer server?
**A:** Yes! This mod is designed primarily for server use. Ensure you're an operator to use the command.

### Q: Does this change the world seed?
**A:** No, chunks regenerate using the same world seed, so terrain will match the original generation.

### Q: What's the maximum diameter I can use?
**A:** The hard limit is 20 chunks (41×41 area) to prevent accidental large-scale regeneration.

## Troubleshooting

### Command doesn't appear
- Verify mod is in `mods` folder
- Check NeoForge version compatibility
- Restart server/game

### "Permission denied" error
- Ensure you're a server operator: `/op <username>`
- Verify operator level is 2 or higher

### Chunks not regenerating
- Players/entities may be preventing unload - clear the area first
- Check server logs for errors
- Try smaller diameter values

### Server lag during regeneration
- Large diameters (>10) can cause temporary lag
- Use smaller diameters or wait for server idle time
- Consider warning players before large operations

## Development

### Building from Source

```bash
git clone https://github.com/Kazuto/regenchunks.git
cd regenchunks
./gradlew build
```

The compiled mod will be in `build/libs/`.

### Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Roadmap

- [ ] Confirmation prompt for large regenerations (diameter > 10)
- [ ] Progress bar for multi-chunk operations
- [ ] Entity preservation option
- [ ] Async processing to reduce lag
- [ ] Chunk backup before regeneration
- [ ] Configuration file for customization

## Support

- **Issues**: [GitHub Issues](../../issues)
- **Discussions**: [GitHub Discussions](../../discussions)
- **Wiki**: [Project Wiki](../../wiki)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- **Author**: Kazuto
- **Built with**: [NeoForge](https://neoforged.net/)
- **Inspired by**: WorldEdit and similar terrain management tools

## Disclaimer

This mod modifies world data. Always backup your worlds before use. The author is not responsible for data loss or corruption resulting from the use of this mod.

---

**Made for the Minecraft modding community**
