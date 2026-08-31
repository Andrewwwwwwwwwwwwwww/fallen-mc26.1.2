package io.github.andrewwwwwwwwwwwwwww.fallen;

import io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi;
import io.github.andrewwwwwwwwwwwwwww.fallen.compat.TravelersBackpackCorpseProvider;
import io.github.andrewwwwwwwwwwwwwww.fallen.compat.TrinketsCorpseProvider;
import io.github.andrewwwwwwwwwwwwwww.fallen.net.FallenNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fallen implements ModInitializer {
    public static final String MOD_ID = "fallen";
    public static final Logger LOGGER = LoggerFactory.getLogger("Fallen");

    /** The entry a claim mod needs so bodies stay lootable inside claims. */
    private static final String OPAC_EXCEPTION = "\"anything$#fallen:corpses\"";

    /**
     * Diagnostic logging, silent unless {@code debugLogging} is set. Every
     * reason the death handler declines to make a body goes through here: a
     * missing corpse is otherwise indistinguishable from an ordinary death, so
     * without it a server owner has nothing to go on.
     */
    public static void debug(String format, Object... args) {
        if (CorpseConfig.get().debugLogging) {
            LOGGER.info("[Fallen] " + format, args);
        }
    }

    @Override
    public void onInitialize() {
        FallenEntities.init();
        FallenMenus.init();
        FallenNetworking.init();
        DeathHistoryCommand.init();
        FallenCommand.init();
        // Soft compat: when Patbox's Trinkets is installed, sweep a dying
        // player's equipped accessories into the body too (they'd otherwise
        // vanish, since Fallen cancels the death drop that Trinkets rides on).
        // Reflection-only, so this is a no-op — and never loads Trinkets classes
        // — when the mod is absent.
        if (FabricLoader.getInstance().isModLoaded("trinkets")) {
            FallenApi.register(
                    Identifier.fromNamespaceAndPath(MOD_ID, "trinkets"),
                    new TrinketsCorpseProvider());
            LOGGER.info("[Fallen] Trinkets detected — equipped accessories will be stored in corpses");
        }
        // Soft compat: sweep a worn Traveler's Backpack into the body too. TB's
        // own death handling places it as a block or drops it at the death spot,
        // where a lava death burns it. Same reflection-only pattern as above.
        if (FabricLoader.getInstance().isModLoaded("travelersbackpack")) {
            FallenApi.register(
                    Identifier.fromNamespaceAndPath(MOD_ID, "travelersbackpack"),
                    new TravelersBackpackCorpseProvider());
            LOGGER.info("[Fallen] Traveler's Backpack detected — worn backpacks will be stored in corpses");
        }
        // Claim mods protect entity interaction, and a corpse is an entity to
        // them — so looting a body inside a claim is refused until the server
        // owner allows it. Say so at startup rather than leaving it to be
        // discovered as an inventory nobody could reach.
        if (FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            LOGGER.info("[Fallen] Open Parties and Claims detected. To let players loot bodies inside "
                    + "claims, add " + OPAC_EXCEPTION + " to forcedEntityProtectionExceptionList in "
                    + "<world>/serverconfig/openpartiesandclaims-server.toml (server must be stopped to edit).");
        }

        // Config is per-installation; (re)load it as each server starts so
        // dedicated servers and singleplayer worlds both pick up edits.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CorpseConfig.load());
    }
}
