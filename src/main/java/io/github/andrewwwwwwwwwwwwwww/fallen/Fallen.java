package io.github.andrewwwwwwwwwwwwwww.fallen;

import io.github.andrewwwwwwwwwwwwwww.fallen.net.FallenNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fallen implements ModInitializer {
    public static final String MOD_ID = "fallen";
    public static final Logger LOGGER = LoggerFactory.getLogger("Fallen");

    @Override
    public void onInitialize() {
        FallenEntities.init();
        FallenMenus.init();
        FallenNetworking.init();
        DeathHistoryCommand.init();
        // Config is per-installation; (re)load it as each server starts so
        // dedicated servers and singleplayer worlds both pick up edits.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CorpseConfig.load());
    }
}
