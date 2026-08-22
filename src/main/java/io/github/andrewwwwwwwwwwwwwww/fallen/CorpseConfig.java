package io.github.andrewwwwwwwwwwwwwww.fallen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global configuration, stored at {@code config/fallen.json}. Loaded once on
 * server start. All durations are expressed in whole minutes in the file and
 * converted to ticks on load.
 */
public final class CorpseConfig {
    /** Master switch — when false, death behaves like vanilla. */
    public boolean enabled = true;
    /** Minutes before the corpse despawns and drops its contents. 0 = never. Default 2 days (1 as a body, then 1 as a skeleton). */
    public double despawnMinutes = 2880.0;
    /** Store the player's experience in the corpse and give it back on retrieval. */
    public boolean keepExperience = true;
    /** Operators may always loot any corpse, ignoring the owner lock. */
    public boolean opsBypassProtection = true;
    /** Spawn a corpse when the player dies over the void. If false, items drop as vanilla (and may be lost). */
    public boolean spawnOverVoid = true;
    /** Spawn a corpse when the player dies in lava. If false, items drop as vanilla (and may burn). */
    public boolean spawnInLava = true;
    /** How far down to scan for solid ground before treating a death spot as "over the void". */
    public int voidScanDepth = 12;
    /** Minutes before a corpse ages into a skeleton and unlocks (0 = never). Default 1 day. */
    public double skeletonMinutes = 1440.0;
    /** Once a corpse has aged into a skeleton, anyone may loot it. */
    public boolean skeletonStageIsPublic = true;
    /** How many past deaths to keep per player for the death-history screen. */
    public int deathHistorySize = 20;

    private static CorpseConfig instance = new CorpseConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static CorpseConfig get() {
        return instance;
    }

    public long despawnTicks() {
        return Math.max(0L, (long) (despawnMinutes * 60.0 * 20.0));
    }

    public long skeletonTicks() {
        return Math.max(0L, (long) (skeletonMinutes * 60.0 * 20.0));
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("fallen.json");
        try {
            if (Files.exists(path)) {
                instance = GSON.fromJson(Files.readString(path), CorpseConfig.class);
                if (instance == null) {
                    instance = new CorpseConfig();
                }
            } else {
                instance = new CorpseConfig();
            }
        } catch (Exception e) {
            Fallen.LOGGER.warn("[Fallen] Failed to read config, using defaults", e);
            instance = new CorpseConfig();
        }
        save(); // rewrite so new/missing keys get their defaults on disk
    }

    public static void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("fallen.json");
        try {
            Files.writeString(path, GSON.toJson(instance));
        } catch (IOException e) {
            Fallen.LOGGER.warn("[Fallen] Failed to write config", e);
        }
    }
}
