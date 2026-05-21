package com.kazuto.regenchunks;

import com.kazuto.regenchunks.commands.RegenChunksCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(RegenChunks.MOD_ID)
public class RegenChunks {
    public static final String MOD_ID = "regenchunks";
    public static final Logger LOGGER = LoggerFactory.getLogger(RegenChunks.class);

    public RegenChunks(IEventBus modEventBus) {
        LOGGER.info("RegenChunks mod initializing...");

        // Register command event listener
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering RegenChunks commands");
        RegenChunksCommand.register(event.getDispatcher());
    }
}
