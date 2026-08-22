package io.github.andrewwwwwwwwwwwwwww.fallen.compat;

import io.github.andrewwwwwwwwwwwwwww.fallen.Fallen;
import io.github.andrewwwwwwwwwwwwwww.fallen.api.CorpseSlotProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fallen compatibility for Traveler's Backpack (attachment mode).
 *
 * <p>TB handles the worn backpack itself on AFTER_DEATH: it tries to place it
 * as a block near the death spot, and otherwise drops it as a plain item at the
 * player's feet — where a lava death burns it. This provider sweeps the worn
 * backpack into the body during Fallen's capture step, which runs earlier
 * (inside {@code dropEquipment}); TB's own handler then sees no backpack and
 * does nothing. The backpack's contents live in the item's data components, so
 * the whole thing travels with the stack.
 *
 * <p>When TB runs in Trinkets-integration mode the backpack lives in a Trinkets
 * slot instead and {@link TrinketsCorpseProvider} covers it, so this provider
 * steps aside (mirroring TB's own {@code enableIntegration()} death guard) to
 * avoid double-capturing.
 *
 * <p>Reflection-only: Fallen carries no dependency on TB, and this class is
 * only loaded when the {@code travelersbackpack} mod is present.
 */
public final class TravelersBackpackCorpseProvider implements CorpseSlotProvider {
    private static final String TB_MAIN = "com.tiviacz.travelersbackpack.TravelersBackpack";
    private static final String TB_UTILS = "com.tiviacz.travelersbackpack.attachment.AttachmentUtils";

    @Override
    public List<ItemStack> capture(Player player) {
        List<ItemStack> taken = new ArrayList<>();
        try {
            Class<?> main = Class.forName(TB_MAIN);
            if ((Boolean) main.getMethod("enableIntegration").invoke(null)) {
                return taken; // Trinkets mode — the Trinkets provider owns the backpack
            }
            Class<?> utils = Class.forName(TB_UTILS);
            if (!(Boolean) utils.getMethod("isWearingBackpack", Player.class).invoke(null, player)) {
                return taken;
            }
            ItemStack backpack = (ItemStack) utils.getMethod("getWearingBackpack", Player.class).invoke(null, player);
            if (backpack == null || backpack.isEmpty()) {
                return taken;
            }
            taken.add(backpack.copy());
            // Clear the wearable exactly the way TB's own death handler does, so
            // its AFTER_DEATH pass finds nothing to place or drop.
            Optional<?> attachment = (Optional<?>) utils.getMethod("getAttachment", Player.class).invoke(null, player);
            if (attachment != null && attachment.isPresent()) {
                Object a = attachment.get();
                Method remove = a.getClass().getMethod("remove", Player.class);
                remove.invoke(a, player);
                Method sync = a.getClass().getMethod("synchronise", Player.class);
                sync.invoke(a, player);
            }
        } catch (Throwable t) {
            // Never let an API mismatch destroy the backpack — bail with nothing
            // captured and leave it to TB's own death handling.
            Fallen.LOGGER.error("[Fallen] Traveler's Backpack capture failed; leaving it to the mod", t);
            return new ArrayList<>();
        }
        return taken;
    }

    @Override
    public List<ItemStack> restore(Player player, List<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!tryEquip(player, stack)) {
                leftovers.add(stack); // back already taken (or API failed) — goes to the inventory
            }
        }
        return leftovers;
    }

    /** Re-equip the backpack onto the player's back if it's free; false = give it back some other way. */
    private static boolean tryEquip(Player player, ItemStack backpack) {
        try {
            Class<?> utils = Class.forName(TB_UTILS);
            if ((Boolean) utils.getMethod("isWearingBackpack", Player.class).invoke(null, player)) {
                return false; // something's already on their back — don't overwrite it
            }
            utils.getMethod("equipBackpack", Player.class, ItemStack.class).invoke(null, player, backpack);
            // Confirm it actually took; otherwise hand the stack back another way.
            return (Boolean) utils.getMethod("isWearingBackpack", Player.class).invoke(null, player);
        } catch (Throwable t) {
            Fallen.LOGGER.error("[Fallen] Traveler's Backpack re-equip failed; returning it to the inventory", t);
            return false;
        }
    }
}
