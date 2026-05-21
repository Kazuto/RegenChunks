package com.kazuto.resetchunks.commands;

import com.kazuto.resetchunks.ResetChunks;
import com.kazuto.resetchunks.util.ChunkRegeneration;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public class ResetChunksCommand {
    private static final int MIN_DIAMETER = 0;
    private static final int MAX_DIAMETER = 20;
    private static final int REQUIRED_PERMISSION_LEVEL = 2;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("resetchunks")
                .then(Commands.argument("diameter", IntegerArgumentType.integer(MIN_DIAMETER, MAX_DIAMETER))
                    .executes(ResetChunksCommand::execute)
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        int diameter = IntegerArgumentType.getInteger(context, "diameter");

        // Get player's current chunk position
        ChunkPos playerChunk = player.chunkPosition();

        // Calculate total chunks to regenerate
        int totalChunks = (2 * diameter + 1) * (2 * diameter + 1);

        // Send confirmation message
        source.sendSuccess(() -> Component.literal(
            String.format("Preparing to regenerate %d chunks in a %dx%d area...",
                totalChunks,
                2 * diameter + 1,
                2 * diameter + 1)
        ), true);

        // TODO: Add confirmation prompt for large operations (diameter > 10)

        try {
            // Execute chunk regeneration
            ChunkRegeneration.regenerateChunks(level, playerChunk, diameter);

            source.sendSuccess(() -> Component.literal(
                String.format("Successfully regenerated %d chunks!", totalChunks)
            ), true);

            ResetChunks.LOGGER.info("Player {} regenerated {} chunks at position {}",
                player.getName().getString(), totalChunks, playerChunk);

            return totalChunks;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to regenerate chunks: " + e.getMessage()));
            ResetChunks.LOGGER.error("Error regenerating chunks", e);
            return 0;
        }
    }
}
