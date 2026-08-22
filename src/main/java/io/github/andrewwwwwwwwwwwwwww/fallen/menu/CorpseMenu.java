package io.github.andrewwwwwwwwwwwwwww.fallen.menu;

import io.github.andrewwwwwwwwwwwwwww.fallen.FallenMenus;
import io.github.andrewwwwwwwwwwwwwww.fallen.api.FallenApi;
import io.github.andrewwwwwwwwwwwwwww.fallen.entity.CorpseEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for a corpse. The backing container mirrors the player inventory's slot
 * indices exactly (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand), so items
 * can be handed back to their original slots.
 *
 * <p>A small run of read-only "stored equipment" slots (backpacks, curios from
 * compat addons) sits in the empty right of the armor row when an addon reserves
 * them; base Fallen reserves none, so the layout is unchanged without addons.
 *
 * <p>In read-only mode the same layout is used to display an untouchable
 * "as it was at death" snapshot for the death-history screen.
 */
public class CorpseMenu extends AbstractContainerMenu {
    /** Player inventory container size in 26.2 (0-35 main, 36-39 armor, 40 offhand, 41-42 body/saddle). */
    public static final int CONTAINER_SIZE = 43;
    public static final int TRANSFER_BUTTON = 0;

    /** Number of vanilla-aligned corpse slots at the front of {@link #slots} (armor 5 + main 27 + hotbar 9). */
    private static final int CORPSE_SLOTS = 4 + 1 + 27 + 9;

    private static final int ARMOR_Y = 18;
    private static final int MAIN_Y = 40;
    private static final int HOTBAR_Y = 98;
    private static final int PLAYER_Y = 153;
    private static final int PLAYER_HOTBAR_Y = 213;

    /**
     * Columns (left edge x) for the read-only stored-equipment slots. They sit
     * at the far right of the armor row with an empty column (6) between them
     * and the offhand (5), so a stored backpack reads as its own thing.
     */
    private static final int[] EXTRA_X = {8 + 7 * 18, 8 + 8 * 18};

    private final Container contents;
    private final CorpseEntity corpse; // server-side only; null on the client and for snapshots
    private final boolean readOnly;

    /** Menu-slot index where the stored-equipment slots begin, and where the player's own inventory begins. */
    private final int extraStart;
    private final int playerStart;

    /** Total stored addon items, synced so the client tooltip can note ones not shown in a slot. */
    private int clientExtrasCount;

    /** Client constructor for the lootable corpse. */
    public CorpseMenu(int containerId, Inventory playerInventory) {
        this(FallenMenus.CORPSE, containerId, playerInventory,
                new SimpleContainer(CONTAINER_SIZE), new SimpleContainer(FallenApi.totalDisplaySlots()), null, false);
    }

    /** Client constructor for the read-only snapshot. */
    public static CorpseMenu view(int containerId, Inventory playerInventory) {
        return new CorpseMenu(FallenMenus.CORPSE_VIEW, containerId, playerInventory,
                new SimpleContainer(CONTAINER_SIZE), new SimpleContainer(FallenApi.totalDisplaySlots()), null, true);
    }

    /** Server constructor for a live corpse. */
    public CorpseMenu(int containerId, Inventory playerInventory, Container contents, Container extras, CorpseEntity corpse) {
        this(FallenMenus.CORPSE, containerId, playerInventory, contents, extras, corpse, false);
    }

    /** Server constructor for a read-only snapshot (no stored equipment). */
    public static CorpseMenu view(int containerId, Inventory playerInventory, Container contents) {
        return view(containerId, playerInventory, contents, new SimpleContainer(FallenApi.totalDisplaySlots()));
    }

    /** Server constructor for a read-only snapshot that also pictures stored equipment (backpacks/curios). */
    public static CorpseMenu view(int containerId, Inventory playerInventory, Container contents, Container extras) {
        return new CorpseMenu(FallenMenus.CORPSE_VIEW, containerId, playerInventory, contents, extras, null, true);
    }

    private CorpseMenu(MenuType<CorpseMenu> type, int containerId, Inventory playerInventory,
                       Container contents, Container extras, CorpseEntity corpse, boolean readOnly) {
        super(type, containerId);
        this.contents = contents;
        this.corpse = corpse;
        this.readOnly = readOnly;

        // Corpse contents. Armor reads head-to-toe left-to-right with the same
        // ghost icons vanilla shows, so an empty slot clearly reads as "helmet",
        // "boots", etc. Inventory indices: 39 head, 38 chest, 37 legs, 36 feet.
        addContentsSlot(39, 8, ARMOR_Y, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET);
        addContentsSlot(38, 8 + 18, ARMOR_Y, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE);
        addContentsSlot(37, 8 + 2 * 18, ARMOR_Y, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS);
        addContentsSlot(36, 8 + 3 * 18, ARMOR_Y, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
        addContentsSlot(40, 8 + 5 * 18, ARMOR_Y, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addContentsSlot(9 + row * 9 + col, 8 + col * 18, MAIN_Y + row * 18, null);
            }
        }
        for (int col = 0; col < 9; col++) {
            addContentsSlot(col, 8 + col * 18, HOTBAR_Y, null);
        }

        // Stored-equipment slots (backpacks/curios), tucked into the empty right
        // of the armor row. None of these exist without an addon.
        this.extraStart = this.slots.size();
        int extraCount = Math.min(extras.getContainerSize(), EXTRA_X.length);
        for (int k = 0; k < extraCount; k++) {
            addExtraSlot(extras, k, EXTRA_X[k], ARMOR_Y);
        }
        this.playerStart = this.slots.size();

        // The viewing player's own inventory.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + col, 8 + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, PLAYER_HOTBAR_Y));
        }

        // Sync the total stored count (which can exceed the shown slots) so the
        // client tooltip can tell a looter there are more than they can see.
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return corpse != null ? corpse.totalStoredExtras() : clientExtrasCount;
            }

            @Override
            public void set(int value) {
                clientExtrasCount = value;
            }
        });
    }

    private void addContentsSlot(int index, int x, int y, Identifier emptyIcon) {
        addSlot(new Slot(contents, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return !readOnly; // snapshots are look-only
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // take-only: you can only take items OUT of a corpse, never put them in
            }

            @Override
            public Identifier getNoItemIcon() {
                return emptyIcon != null ? emptyIcon : super.getNoItemIcon();
            }
        });
    }

    /**
     * A stored-equipment slot (a stored backpack, etc.). The vanilla click
     * machinery never moves these — pickup and placement are both disabled — so
     * it can't desync and duplicate the item. Reclaiming is done instead by the
     * screen sending a packet (see {@link #reclaimExtra}), one item per click.
     */
    private void addExtraSlot(Container extras, int index, int x, int y) {
        addSlot(new Slot(extras, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
    }

    /**
     * Reclaim one stored item from the given display slot to the player. Called
     * server-side from the reclaim packet; ignored on snapshots and off a corpse.
     */
    public void reclaimExtra(Player player, int displaySlot) {
        if (!readOnly && corpse != null) {
            corpse.reclaimOneExtra(player, displaySlot);
        }
    }

    /** True when this is a look-but-don't-touch death snapshot. */
    public boolean isReadOnly() {
        return readOnly;
    }

    /** True when {@code menuIndex} is one of the stored-equipment slots. */
    public boolean isExtraSlot(int menuIndex) {
        return menuIndex >= extraStart && menuIndex < playerStart;
    }

    /** Total addon items stored in the body, including any not shown in a slot. */
    public int storedExtrasCount() {
        return corpse != null ? corpse.totalStoredExtras() : clientExtrasCount;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == TRANSFER_BUTTON && !readOnly) {
            transferAll(player);
            return true;
        }
        return false;
    }

    /**
     * Hand every stored item back to its original inventory slot; overflow
     * drops. The corpse then empties and despawns on its own.
     */
    private void transferAll(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < contents.getContainerSize(); i++) {
            ItemStack stack = contents.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (i < inventory.getContainerSize() && inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack);
                contents.setItem(i, ItemStack.EMPTY);
            } else {
                inventory.add(stack); // mutates to whatever didn't fit
                // Keep the remainder in the body rather than dropping it, so a
                // full inventory never spills loot (into lava/void, say).
                contents.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
        }
        if (corpse != null) {
            corpse.returnExtrasTo(player); // backpacks/curios from addon slots go to their own slots
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (readOnly || isExtraSlot(index)) {
            return ItemStack.EMPTY; // nothing leaves a snapshot, or a read-only stored slot
        }
        // Take-only: only corpse items shift-move (to the player). Shift-clicking
        // one of your own items does nothing — you can never push items into a corpse.
        if (index >= CORPSE_SLOTS) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        // Corpse item -> straight to the player's own inventory.
        if (!moveItemStackTo(stack, playerStart, this.slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return corpse == null || (corpse.isAlive() && corpse.distanceToSqr(player) < 64.0);
    }
}
