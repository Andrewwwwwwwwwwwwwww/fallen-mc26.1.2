package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: re-create a lost body from a death-history record
 * (operator only). Spawns a new corpse at the recorded death spot holding the
 * record's full at-death snapshot — the insurance policy for a body that was
 * lost to a bug or an unlucky spot.
 */
public record RestoreBodyPayload(String targetUuid, int index) implements CustomPacketPayload {
    public static final Type<RestoreBodyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "restore_body"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RestoreBodyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RestoreBodyPayload::targetUuid,
            ByteBufCodecs.VAR_INT, RestoreBodyPayload::index,
            RestoreBodyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
