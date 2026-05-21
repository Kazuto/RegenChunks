package com.kazuto.resetchunks;

import com.kazuto.resetchunks.commands.ResetChunksCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ResetChunks.MOD_ID)
public class ResetChunks {
    public static final String MOD_ID = "resetchunks";
    public static final Logger LOGGER = LoggerFactory.getLogger(ResetChunks.class);

    public ResetChunks(IEventBus modEventBus) {
        LOGGER.info("ResetChunks mod initializing...");

        // Register command event listener
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering ResetChunks commands");
        ResetChunksCommand.register(event.getDispatcher());
    }
}
