package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import com.mojang.authlib.GameProfile;
import io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathHistoryData;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathRecord;
import io.github.andrewwwwwwwwwwwwwww.fallen.entity.CorpseEntity;
import io.github.andrewwwwwwwwwwwwwww.fallen.menu.CorpseMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Registers the death-history packets and handles the server side of them. */
public final class FallenNetworking {
    private FallenNetworking() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(HistoryPayload.TYPE, HistoryPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestHistoryPayload.TYPE, RequestHistoryPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RecoverPayload.TYPE, RecoverPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ReclaimExtraPayload.TYPE, ReclaimExtraPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RestoreBodyPayload.TYPE, RestoreBodyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MoveBodyPayload.TYPE, MoveBodyPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestHistoryPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            if (server != null) {
                server.execute(() -> handleRequest(player, payload.targetUuid()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(RecoverPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            if (server != null) {
                server.execute(() -> handleRecover(player, payload.targetUuid(), payload.index()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ReclaimExtraPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            if (server != null) {
                server.execute(() -> {
                    if (player.containerMenu instanceof CorpseMenu menu) {
                        menu.reclaimExtra(player, payload.slot());
                    }
                });
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(RestoreBodyPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            if (server != null) {
                server.execute(() -> handleRestore(player, payload.targetUuid(), payload.index()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(MoveBodyPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = context.server();
            if (server != null) {
                server.execute(() -> handleMove(player, payload.targetUuid(), payload.index()));
            }
        });
    }

    /**
     * Operator body rescue: bring an existing body to the operator so a stuck or
     * invisible one can still be looted. If the record says the body exists but
     * it can't be found in the world, the record is marked lost — turning the
     * row's button into Respawn — so the items are recoverable either way.
     */
    private static void handleMove(ServerPlayer requester, String targetStr, int index) {
        if (!isOp(requester)) {
            return;
        }
        UUID target = parseUuid(targetStr);
        if (target == null) {
            return;
        }
        MinecraftServer server = serverOf(requester);
        DeathHistoryData data = DeathHistoryData.get(server);
        DeathRecord record = data.getRecord(target, index);
        if (record == null) {
            return;
        }
        String name = resolveName(server, target);
        if (record.corpseGone()) {
            requester.sendSystemMessage(Component.literal("That body is already gone — use Respawn."));
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
        CorpseEntity corpse = null;
        if (level != null) {
            if (!(level.getEntity(record.corpseId()) instanceof CorpseEntity)) {
                level.getChunk(record.pos()); // force-load where it should be, then look again
            }
            if (level.getEntity(record.corpseId()) instanceof CorpseEntity found) {
                corpse = found;
            }
        }
        if (corpse == null) {
            // The record claims a body but the world doesn't have it. Reconcile:
            // mark it lost so the operator can Respawn it instead.
            data.replace(target, index, record.asGone());
            requester.sendSystemMessage(Component.literal(
                    "Couldn't find " + name + "'s body in the world — marked it lost. Use Respawn to bring it back."));
            sendHistory(requester, target, name);
            return;
        }
        CorpseEntity.relocate(corpse, (ServerLevel) requester.level(), requester.position());
        requester.sendSystemMessage(Component.literal("Moved " + name + "'s body to you."));
        sendHistory(requester, target, name);
    }

    /**
     * Operator body restore: re-create a lost body from a death record. Guarded
     * hard — operators only, and only for records whose body is actually gone,
     * so it can never duplicate a body that still exists in the world.
     */
    private static void handleRestore(ServerPlayer requester, String targetStr, int index) {
        if (!isOp(requester)) {
            return;
        }
        UUID target = parseUuid(targetStr);
        if (target == null) {
            return;
        }
        MinecraftServer server = serverOf(requester);
        DeathHistoryData data = DeathHistoryData.get(server);
        DeathRecord record = data.getRecord(target, index);
        if (record == null) {
            return;
        }
        if (!record.corpseGone()) {
            requester.sendSystemMessage(Component.literal("That body still exists — nothing to restore."));
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.dimension()));
        if (level == null) {
            requester.sendSystemMessage(Component.literal("That death's dimension isn't loaded."));
            return;
        }
        String name = resolveName(server, target);
        CorpseEntity corpse = CorpseEntity.restoreFromRecord(level, new GameProfile(target, name), record);
        data.replace(target, index, record.asRestored(corpse.getUUID()));
        BlockPos at = corpse.blockPosition();
        requester.sendSystemMessage(Component.literal(
                "Restored " + name + "'s body at " + at.getX() + ", " + at.getY() + ", " + at.getZ() + "."));
        sendHistory(requester, target, name); // refresh the screen's data
    }

    private static void handleRequest(ServerPlayer requester, String targetStr) {
        UUID target = targetStr.isEmpty() ? requester.getUUID() : parseUuid(targetStr);
        if (target == null) {
            return;
        }
        if (!target.equals(requester.getUUID()) && !isOp(requester)) {
            return; // only operators may view another player's history
        }
        String name = target.equals(requester.getUUID())
                ? requester.getGameProfile().name()
                : resolveName(serverOf(requester), target);
        sendHistory(requester, target, name);
    }

    /** Build and send a player's history to the given viewer. */
    public static void sendHistory(ServerPlayer viewer, UUID target, String name) {
        MinecraftServer server = serverOf(viewer);
        if (server == null) {
            return;
        }
        DeathHistoryData data = DeathHistoryData.get(server);
        List<HistoryEntry> entries = new ArrayList<>();
        for (DeathRecord record : data.getFor(target)) {
            entries.add(new HistoryEntry(record.time(), record.dimension().toString(), livePos(server, record),
                    record.nonEmptyCount(), record.corpseGone()));
        }
        ServerPlayNetworking.send(viewer, new HistoryPayload(
                name == null ? "?" : name, target.toString(), entries, isOp(viewer)));
    }

    /**
     * Where the body actually is now. Bodies settle under gravity (and hazard
     * deaths were relocated), so the recorded spot can be stale — if the corpse
     * still exists and is loaded, use its current position; otherwise fall back
     * to the recorded spawn spot.
     */
    private static BlockPos livePos(MinecraftServer server, DeathRecord record) {
        if (record.corpseGone()) {
            return record.pos();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, record.dimension());
        ServerLevel level = server.getLevel(key);
        if (level != null) {
            Entity entity = level.getEntity(record.corpseId());
            if (entity instanceof CorpseEntity) {
                return entity.blockPosition();
            }
        }
        return record.pos();
    }

    private static void handleRecover(ServerPlayer requester, String targetStr, int index) {
        UUID target = parseUuid(targetStr);
        if (target == null) {
            return;
        }
        if (!target.equals(requester.getUUID()) && !isOp(requester)) {
            return;
        }
        MinecraftServer server = serverOf(requester);
        DeathRecord record = DeathHistoryData.get(server).getRecord(target, index);
        if (record == null) {
            return;
        }
        // Always show the untouched at-death snapshot, never the live corpse:
        // it stays accurate after partial looting, and being read-only makes
        // duplication impossible.
        SimpleContainer snapshot = new SimpleContainer(CorpseMenu.CONTAINER_SIZE);
        List<ItemStack> items = record.items();
        for (int i = 0; i < items.size() && i < snapshot.getContainerSize(); i++) {
            snapshot.setItem(i, items.get(i).copy());
        }
        // Picture any stored backpacks/curios too, so a looted body's snapshot
        // still shows what was on the player outside the vanilla inventory.
        SimpleContainer extras = new SimpleContainer(FallenApi.totalDisplaySlots());
        List<ItemStack> extraItems = record.extras();
        for (int i = 0; i < extraItems.size() && i < extras.getContainerSize(); i++) {
            extras.setItem(i, extraItems.get(i).copy());
        }
        String ownerName = resolveName(server, target);
        requester.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> CorpseMenu.view(id, inv, snapshot, extras),
                Component.literal("Corpse of " + ownerName)));
    }

    private static boolean isOp(ServerPlayer player) {
        return serverOf(player).getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        return player.level().getServer();
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        return online != null ? online.getGameProfile().name() : shortId(uuid);
    }

    private static String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
