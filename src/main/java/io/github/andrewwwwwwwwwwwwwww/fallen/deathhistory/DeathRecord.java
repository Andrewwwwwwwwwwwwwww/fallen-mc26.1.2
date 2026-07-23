package io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * A permanent record of one death. {@code items} is an index-aligned snapshot
 * of the inventory <em>as it was at the moment of death</em> and is never
 * mutated afterwards, so the history screen can always show what the player
 * was carrying even after the corpse has been partly or fully looted.
 * {@code extras} is the same for addon items (backpacks, curios) that live
 * outside the vanilla inventory. {@code corpseGone} tracks whether the body
 * still exists in the world.
 */
public record DeathRecord(UUID corpseId, long time, Identifier dimension, BlockPos pos,
                          List<ItemStack> items, int xp, boolean corpseGone, List<ItemStack> extras) {

    public static final Codec<DeathRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("corpseId", new UUID(0L, 0L)).forGetter(DeathRecord::corpseId),
            Codec.LONG.fieldOf("time").forGetter(DeathRecord::time),
            Identifier.CODEC.fieldOf("dimension").forGetter(DeathRecord::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(DeathRecord::pos),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(DeathRecord::items),
            Codec.INT.fieldOf("xp").forGetter(DeathRecord::xp),
            Codec.BOOL.optionalFieldOf("gone", false).forGetter(DeathRecord::corpseGone),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("extras", List.of()).forGetter(DeathRecord::extras)
    ).apply(instance, DeathRecord::new));

    /** A copy of this record marked as no longer having a body in the world. */
    public DeathRecord asGone() {
        return new DeathRecord(corpseId, time, dimension, pos, items, xp, true, extras);
    }

    /** Non-empty item count, for display — counts stored backpacks/curios too. */
    public int nonEmptyCount() {
        int count = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        for (ItemStack stack : extras) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
