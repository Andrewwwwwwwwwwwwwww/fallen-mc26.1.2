package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server -> client: a player's death history, ready to display. */
public record HistoryPayload(String ownerName, String targetUuid, List<HistoryEntry> entries) implements CustomPacketPayload {
    public static final Type<HistoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "history"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HistoryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HistoryPayload::ownerName,
            ByteBufCodecs.STRING_UTF8, HistoryPayload::targetUuid,
            HistoryEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), HistoryPayload::entries,
            HistoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
