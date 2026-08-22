package io.github.andrewwwwwwwwwwwwwww.fallen.compat;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import io.github.andrewwwwwwwwwwwwwww.fallen.api.CorpseSlotProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fallen compatibility for Patbox's Trinkets ({@code eu.pb4.trinkets}).
 *
 * <p>Trinkets drops a dying player's equipped accessories from inside
 * {@code LivingEntity.dropEquipment} — the very method Fallen cancels to bury
 * the inventory instead. With that method cancelled, the accessories are neither
 * dropped nor kept: they simply vanish. This provider sweeps them into the body
 * during Fallen's capture step (which runs before the cancel) so they're stored
 * with the rest of the loot and handed back on recovery.
 *
 * <p>Everything here is reflection: Fallen carries no compile- or run-time
 * dependency on Trinkets, and this class is only ever loaded when the
 * {@code trinkets} mod is present (see the guarded registration in
 * {@link io.github.andrewwwwwwwwwwwwwww.fallen.Fallen}). A Trinkets inventory is
 * a vanilla {@link Container}, so once reflection hands one over the item moves
 * happen through the plain container interface.
 */
public final class TrinketsCorpseProvider implements CorpseSlotProvider {
    private static final String TRINKETS_API = "eu.pb4.trinkets.api.TrinketsApi";

    @Override
    public List<ItemStack> capture(Player player) {
        List<ItemStack> taken = new ArrayList<>();
        try {
            for (Container inventory : inventoriesOf(player)) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        taken.add(stack.copy());
                        inventory.setItem(i, ItemStack.EMPTY); // must clear, or the item duplicates
                    }
                }
            }
        } catch (Throwable t) {
            // Never let a Trinkets/API mismatch destroy someone's inventory — bail
            // and leave the accessories for Trinkets' own handling.
            Fallen.LOGGER.error("[Fallen] Trinkets capture failed; leaving accessories to the mod", t);
        }
        return taken;
    }

    @Override
    public List<ItemStack> restore(Player player, List<ItemStack> stacks) {
        // Hand them straight back — Fallen puts each into the player's inventory
        // (or drops it if there's no room), so nothing is ever lost. The player
        // re-equips the accessory into its slot themselves.
        return new ArrayList<>(stacks);
    }

    /**
     * The player's Trinkets inventories, reached by reflection. Each is a vanilla
     * {@link Container}, so the caller can read and clear it directly.
     */
    private static List<Container> inventoriesOf(Player player) throws Exception {
        List<Container> out = new ArrayList<>();
        Class<?> api = Class.forName(TRINKETS_API);
        Method getAttachment = api.getMethod("getAttachment", LivingEntity.class);
        Object attachment = getAttachment.invoke(null, player);
        if (attachment == null) {
            return out;
        }
        Object inventories = attachment.getClass().getMethod("getInventories").invoke(attachment);
        if (inventories instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value instanceof Container container) {
                    out.add(container);
                }
            }
        }
        return out;
    }
}
