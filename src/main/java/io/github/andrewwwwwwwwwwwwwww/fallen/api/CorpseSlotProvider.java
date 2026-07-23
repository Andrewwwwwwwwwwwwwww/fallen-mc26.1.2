package io.github.andrewwwwwwwwwwwwwww.fallen.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Lets other mods put items that live <em>outside</em> the vanilla inventory
 * into a corpse — backpack slots, curio/trinket slots, and so on.
 *
 * <p>Fallen only knows about the 43 vanilla inventory slots. Anything stored
 * elsewhere would otherwise be missed on death: it would drop on the floor (or
 * be lost) while everything else went into the body. Implement this and
 * register it with {@link FallenApi} to close that gap.
 *
 * <p>Implementations are called on the server thread during death handling.
 */
public interface CorpseSlotProvider {

    /**
     * Take the items this provider is responsible for off the dying player.
     *
     * <p>Implementations <strong>must clear</strong> the slots they read from,
     * otherwise the items will be duplicated — the player keeps them and the
     * corpse holds a copy.
     *
     * @param player the player who just died
     * @return stacks to store in the corpse; empty if there's nothing to take
     */
    List<ItemStack> capture(Player player);

    /**
     * Put previously captured items back where they belong.
     *
     * @param player the player reclaiming the corpse
     * @param stacks the stacks this provider originally captured, in order
     * @return anything that could not be placed back; Fallen will put it in the
     *         player's inventory or drop it, so nothing is ever lost
     */
    List<ItemStack> restore(Player player, List<ItemStack> stacks);

    /**
     * How many slots this provider's items should occupy in the corpse screen,
     * so a looter can see what's stored (a backpack, say) before reclaiming.
     *
     * <p>Best-effort and purely cosmetic: the first {@code displaySlots()}
     * captured stacks are pictured in the body's screen; anything beyond that is
     * still stored and handed back on reclaim, just not shown. These display
     * slots are read-only — items are reclaimed with the Transfer button (or by
     * crouch-clicking the body), which returns them to your own slots, never by
     * dragging them out of the picture.
     *
     * <p>The count must be constant for the life of the game and identical on
     * client and server (it decides the screen layout), so return a literal.
     * Return 0 to show nothing.
     */
    default int displaySlots() {
        return 0;
    }
}
