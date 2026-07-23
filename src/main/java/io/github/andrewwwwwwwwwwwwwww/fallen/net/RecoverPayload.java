package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: recover the items from a past death (creative only). */
public record RecoverPayload(String targetUuid, int index) implements CustomPacketPayload {
    public static final Type<RecoverPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "recover_items"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecoverPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RecoverPayload::targetUuid,
            ByteBufCodecs.VAR_INT, RecoverPayload::index,
            RecoverPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
