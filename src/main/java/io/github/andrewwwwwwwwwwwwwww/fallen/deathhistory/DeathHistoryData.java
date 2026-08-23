package io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory;

import com.mojang.serialization.Codec;
import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-world persistent record of every player's recent deaths, stored on the
 * overworld's data storage. Powers the death-history screen and creative item
 * recovery.
 */
public class DeathHistoryData extends SavedData {

    public static final Codec<DeathHistoryData> CODEC = Codec
            .unboundedMap(UUIDUtil.STRING_CODEC, DeathRecord.CODEC.listOf())
            .xmap(DeathHistoryData::new, data -> data.byPlayer);

    public static final SavedDataType<DeathHistoryData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "death_history"),
            DeathHistoryData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, List<DeathRecord>> byPlayer;

    public DeathHistoryData() {
        this.byPlayer = new HashMap<>();
    }

    private DeathHistoryData(Map<UUID, List<DeathRecord>> map) {
        this.byPlayer = new HashMap<>();
        map.forEach((uuid, list) -> this.byPlayer.put(uuid, new ArrayList<>(list)));
    }

    public static DeathHistoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Add a death, newest first, trimming to {@code maxSize}. */
    public void record(UUID player, DeathRecord record, int maxSize) {
        List<DeathRecord> list = byPlayer.computeIfAbsent(player, k -> new ArrayList<>());
        list.add(0, record);
        int cap = Math.max(1, maxSize);
        while (list.size() > cap) {
            list.remove(list.size() - 1);
        }
        setDirty();
    }

    /**
     * Flag a corpse as no longer in the world (looted empty or despawned). The
     * record itself is kept forever so the death-history snapshot survives.
     */
    public void markCorpseGone(UUID player, UUID corpseId) {
        List<DeathRecord> list = byPlayer.get(player);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            DeathRecord record = list.get(i);
            if (record.corpseId().equals(corpseId) && !record.corpseGone()) {
                list.set(i, record.asGone());
                setDirty();
                return;
            }
        }
    }

    public List<DeathRecord> getFor(UUID player) {
        return byPlayer.getOrDefault(player, List.of());
    }

    public DeathRecord getRecord(UUID player, int index) {
        List<DeathRecord> list = byPlayer.get(player);
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /** Replace a record in place (operator body restore re-ties it to the new corpse). */
    public void replace(UUID player, int index, DeathRecord record) {
        List<DeathRecord> list = byPlayer.get(player);
        if (list != null && index >= 0 && index < list.size()) {
            list.set(index, record);
            setDirty();
        }
    }
}
