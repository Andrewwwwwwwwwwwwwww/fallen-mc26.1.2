package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: bring an existing body to the requesting operator
 * (operator only) — the rescue for a body that exists but is stuck somewhere
 * unreachable or invisible. If the body can't be found in the world, the
 * record is marked lost so the Respawn path opens up instead.
 */
public record MoveBodyPayload(String targetUuid, int index) implements CustomPacketPayload {
    public static final Type<MoveBodyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "move_body"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveBodyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MoveBodyPayload::targetUuid,
            ByteBufCodecs.VAR_INT, MoveBodyPayload::index,
            MoveBodyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
