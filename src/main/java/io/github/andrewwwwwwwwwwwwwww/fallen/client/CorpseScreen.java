package io.github.andrewwwwwwwwwwwwwww.fallen.client;

import io.github.andrewwwwwwwwwwwwwww.fallen.menu.CorpseMenu;
import io.github.andrewwwwwwwwwwwwwww.fallen.net.ReclaimExtraPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The corpse GUI: a flat panel showing the dead player's armor + offhand,
 * inventory grid and hotbar, with a "Transfer Items" button that hands
 * everything back to its original slots. Drawn with plain fills (no texture)
 * to match the clean, slot-outline look.
 */
public class CorpseScreen extends AbstractContainerScreen<CorpseMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 237;
    private static final int BUTTON_Y = 116;

    private static final int LABEL_COLOR = 0xFF404040;

    public CorpseScreen(CorpseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventoryLabelY = 143;
    }

    @Override
    protected void init() {
        super.init();
        if (this.menu.isReadOnly()) {
            return; // snapshots are look-only: no transfer button
        }
        int buttonWidth = 120;
        addRenderableWidget(Button.builder(
                        Component.literal("Transfer Items"),
                        b -> {
                            if (this.minecraft != null && this.minecraft.gameMode != null) {
                                this.minecraft.gameMode.handleInventoryButtonClick(
                                        this.menu.containerId, CorpseMenu.TRANSFER_BUTTON);
                            }
                        })
                .bounds(this.leftPos + (WIDTH - buttonWidth) / 2, this.topPos + BUTTON_Y, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        GuiPainter.panel(graphics, leftPos, topPos, WIDTH, HEIGHT);
        for (Slot slot : this.menu.slots) {
            // A stored-equipment slot only exists when it holds something (a
            // backpack, etc.); when empty it isn't pictured at all.
            if (this.menu.isExtraSlot(slot.index) && !slot.hasItem()) {
                continue;
            }
            GuiPainter.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        // Stored-equipment slots aren't dragged out (that desyncs and dupes).
        // A click on a filled one asks the server to hand over exactly one.
        if (!this.menu.isReadOnly() && slot != null && this.menu.isExtraSlot(slot.index) && slot.hasItem()) {
            ClientPlayNetworking.send(new ReclaimExtraPayload(slot.getContainerSlot()));
            return;
        }
        super.slotClicked(slot, slotId, button, input);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        Slot slot = this.hoveredSlot;
        // When the body holds more stored items than the slot can show, say so on
        // the one that's visible, and point at the Transfer button for the rest.
        if (slot != null && this.menu.isExtraSlot(slot.index)) {
            int stored = this.menu.storedExtrasCount();
            if (stored > 1) {
                lines = new ArrayList<>(lines);
                lines.add(Component.literal(stored + " stored in this body").withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Take one at a time, or Transfer Items for all")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_COLOR, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
        if (this.menu.isReadOnly()) {
            String note = "Inventory at death (view only)";
            graphics.text(this.font, note, (WIDTH - this.font.width(note)) / 2, BUTTON_Y + 6, 0xFF707070, false);
        }
    }
}
