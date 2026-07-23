package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import com.mojang.authlib.GameProfile;
import io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathHistoryData;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathRecord;
import io.github.andrewwwwwwwwwwwwwww.fallen.menu.CorpseMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

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
            entries.add(new HistoryEntry(record.time(), record.dimension().toString(), record.pos(),
                    record.nonEmptyCount(), record.corpseGone()));
        }
        ServerPlayNetworking.send(viewer, new HistoryPayload(name == null ? "?" : name, target.toString(), entries));
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
