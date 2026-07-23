package io.github.andrewwwwwwwwwwwwwww.fallen.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One row in the death-history screen. {@code corpseGone} is false while the
 * body still exists in the world (green check) and true once it's been looted
 * empty or despawned (red X).
 */
public record HistoryEntry(long time, String dimension, BlockPos pos, int itemCount, boolean corpseGone) {
    public static final StreamCodec<RegistryFriendlyByteBuf, HistoryEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, HistoryEntry::time,
            ByteBufCodecs.STRING_UTF8, HistoryEntry::dimension,
            BlockPos.STREAM_CODEC, HistoryEntry::pos,
            ByteBufCodecs.VAR_INT, HistoryEntry::itemCount,
            ByteBufCodecs.BOOL, HistoryEntry::corpseGone,
            HistoryEntry::new);
}
