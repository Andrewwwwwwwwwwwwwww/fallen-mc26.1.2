package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -&gt; server: reclaim one stored item (backpack/curio) from the open
 * corpse's display slot. The slot itself is inert on the client, so a click
 * sends this instead of moving the item, and the server does the whole transfer
 * — no click-prediction desync, no duplication.
 */
public record ReclaimExtraPayload(int slot) implements CustomPacketPayload {
    public static final Type<ReclaimExtraPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "reclaim_extra"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimExtraPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReclaimExtraPayload::slot,
            ReclaimExtraPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
