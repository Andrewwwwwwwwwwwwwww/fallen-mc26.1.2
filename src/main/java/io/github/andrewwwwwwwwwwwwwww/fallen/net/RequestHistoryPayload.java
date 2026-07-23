package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: request a death history. Empty target = the requester's own. */
public record RequestHistoryPayload(String targetUuid) implements CustomPacketPayload {
    public static final Type<RequestHistoryPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "request_history"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHistoryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestHistoryPayload::targetUuid,
            RequestHistoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
