package io.github.andrewwwwwwwwwwwwwww.fallen.mixin;

import io.github.andrewwwwwwwwwwwwwww.fallen.CorpseConfig;
import io.github.andrewwwwwwwwwwwwwww.fallen.FallenEntities;
import io.github.andrewwwwwwwwwwwwwww.fallen.entity.CorpseEntity;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathHistoryData;
import io.github.andrewwwwwwwwwwwwwww.fallen.deathhistory.DeathRecord;
import io.github.andrewwwwwwwwwwwwwww.fallen.menu.CorpseMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the death-drop step: instead of scattering the player's items as
 * loose item entities, sweep them (and their experience) into a
 * {@link CorpseEntity} at the death location. Runs at HEAD of
 * {@code Player.dropEquipment} and cancels the vanilla scatter entirely.
 */
@Mixin(Player.class)
public abstract class PlayerDropMixin {

    @Shadow protected abstract void destroyVanishingCursedItems();

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void fallen$captureCorpse(ServerLevel level, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!CorpseConfig.get().enabled || self.isSpectator()) {
            return;
        }
        // keepInventory keeps everything already — nothing to bury.
        if (level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            return;
        }

        // Honour Curse of Vanishing exactly as vanilla would before we sweep.
        this.destroyVanishingCursedItems();

        Inventory inventory = self.getInventory();
        // Snapshot the whole inventory keeping each item at its own slot index,
        // so the corpse can later return items to their original slots.
        NonNullList<ItemStack> byIndex = NonNullList.withSize(CorpseMenu.CONTAINER_SIZE, ItemStack.EMPTY);
        boolean anyItems = false;
        int size = Math.min(inventory.getContainerSize(), CorpseMenu.CONTAINER_SIZE);
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                byIndex.set(i, stack);
                anyItems = true;
            }
        }

        int experience = CorpseConfig.get().keepExperience ? self.totalExperience : 0;

        // Respect where players want corpses to spawn. Check hazards BEFORE
        // taking anything off the player: at a disallowed spot we bail and let
        // everything drop normally (inventory via vanilla, backpacks via their
        // own mod) instead of stranding items we'd already captured.
        CorpseConfig cfg = CorpseConfig.get();
        if (!cfg.spawnInLava && self.isInLava()) {
            return;
        }
        if (!cfg.spawnOverVoid && isOverVoid(level, self.blockPosition(), cfg.voidScanDepth)) {
            return;
        }

        // Let compat addons (backpacks, curios) hand over items that live
        // outside the vanilla inventory. Captured before the empty-handed check
        // so a player wearing only a backpack still gets a body instead of
        // dropping it on the floor.
        java.util.List<io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.Group> extraGroups =
                io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.captureAll(self);
        boolean anyExtras = !extraGroups.isEmpty();

        if (!anyItems && experience <= 0 && !anyExtras) {
            return; // truly nothing to bury — let vanilla proceed (it drops nothing)
        }

        CorpseEntity corpse = new CorpseEntity(FallenEntities.CORPSE, level);
        // Decide where the body rests: a death over a still pool floats it on the
        // surface, a death in flowing fluid rests it on the nearest surface (not
        // the bottom of the fall), a death over the void holds it just inside the
        // world, and a normal death lets it fall to the ground. Floated/held
        // bodies are pinned so they can't sink into something unreachable.
        CorpseEntity.RestSpot rest = CorpseEntity.computeRestSpot(level, self.position());
        corpse.setPos(rest.x(), rest.y(), rest.z());
        if (rest.pin()) {
            corpse.pin();
        }
        corpse.setYRot(self.getYRot());
        corpse.setOwner(self.getGameProfile());
        corpse.fill(byIndex, experience);
        corpse.setExtras(extraGroups);

        // Log this death, tied to the corpse, so the "your corpses" screen shows
        // only bodies that still exist. The entry is removed when the corpse is
        // grabbed or despawns.
        if (level.getServer() != null) {
            // Keep items index-aligned (copies) so recovery re-opens the corpse
            // screen with everything in its original slot.
            java.util.List<ItemStack> historyItems = new java.util.ArrayList<>(CorpseMenu.CONTAINER_SIZE);
            for (int i = 0; i < CorpseMenu.CONTAINER_SIZE; i++) {
                historyItems.add(byIndex.get(i).copy());
            }
            // Flatten the addon items to a plain list so the read-only snapshot
            // can picture the backpacks/curios even after the body is looted.
            java.util.List<ItemStack> historyExtras =
                    io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi.flatten(extraGroups);
            DeathHistoryData.get(level.getServer()).record(
                    self.getUUID(),
                    // Record where the BODY actually is (relocated out of lava/void),
                    // not where the player died, so the history points at the corpse.
                    new DeathRecord(corpse.getUUID(), System.currentTimeMillis(), level.dimension().identifier(),
                            corpse.blockPosition(), historyItems, experience, false, historyExtras),
                    CorpseConfig.get().deathHistorySize);
        }

        inventory.clearContent();
        self.skipDropExperience(); // we're carrying the XP in the corpse; don't also drop orbs

        level.addFreshEntity(corpse);
        ci.cancel();
    }

    /** True when there's no solid ground within {@code depth} blocks below the death spot. */
    private static boolean isOverVoid(ServerLevel level, BlockPos pos, int depth) {
        int bottom = Math.max(level.getMinY(), pos.getY() - Math.max(1, depth));
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int y = pos.getY() - 1; y >= bottom; y--) {
            cursor.setY(y);
            if (!level.getBlockState(cursor).isAir()) {
                return false;
            }
        }
        return true;
    }
}
