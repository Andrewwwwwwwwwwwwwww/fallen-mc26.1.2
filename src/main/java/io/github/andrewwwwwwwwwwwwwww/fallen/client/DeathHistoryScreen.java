package io.github.andrewwwwwwwwwwwwwww.fallen.client;

import io.github.andrewwwwwwwwwwwwwww.fallen.net.HistoryEntry;
import io.github.andrewwwwwwwwwwwwwww.fallen.net.RecoverPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Lists the player's corpses that still exist (not yet grabbed). Each row can
 * open chat with a teleport command or recover that corpse's items (the server
 * enforces creative-only recovery). Drawn with vanilla GUI chrome.
 */
public class DeathHistoryScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int HEADER = 26;
    private static final int ROW_H = 26;
    private static final int PER_PAGE = 6;
    private static final int NAV_H = 30;
    private static final int PANEL_H = HEADER + PER_PAGE * ROW_H + NAV_H;
    private static final int LOC_W = 58;
    private static final int ITEMS_W = 52;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("MMM d, HH:mm");

    private final String ownerName;
    private final String targetUuid;
    private final List<HistoryEntry> entries;
    private int page;

    private int panelLeft;
    private int panelTop;
    private int itemsX;
    private int locX;
    private int stripX;
    private int stripW;

    public DeathHistoryScreen(String ownerName, String targetUuid, List<HistoryEntry> entries) {
        super(Component.literal("Corpses"));
        this.ownerName = ownerName;
        this.targetUuid = targetUuid;
        this.entries = entries;
    }

    @Override
    protected void init() {
        panelLeft = (this.width - PANEL_W) / 2;
        panelTop = (this.height - PANEL_H) / 2;
        itemsX = panelLeft + PANEL_W - 12 - ITEMS_W;
        locX = itemsX - 6 - LOC_W;
        stripX = panelLeft + 8;
        stripW = locX - stripX - 8;

        int rowsTop = panelTop + HEADER;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            int index = start + i;
            int bY = rowsTop + i * ROW_H + (ROW_H - 20) / 2;
            HistoryEntry entry = entries.get(index);
            addRenderableWidget(Button.builder(Component.literal("Location"), b -> openLocation(entry))
                    .bounds(locX, bY, LOC_W, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Items"), b -> recover(index))
                    .bounds(itemsX, bY, ITEMS_W, 20).build());
        }

        int navY = panelTop + PANEL_H - 26;
        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
                page--;
                rebuildWidgets();
            }).bounds(panelLeft + 10, navY, 64, 20).build());
        }
        if (start + PER_PAGE < entries.size()) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
                page++;
                rebuildWidgets();
            }).bounds(panelLeft + PANEL_W - 74, navY, 64, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(panelLeft + PANEL_W / 2 - 32, navY, 64, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        GuiPainter.panel(graphics, panelLeft, panelTop, PANEL_W, PANEL_H);
        int rowsTop = panelTop + HEADER;
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            GuiPainter.inset(graphics, stripX, rowsTop + i * ROW_H + 1, stripW, ROW_H - 4);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, "Corpses of " + ownerName, panelLeft + 10, panelTop + 9, 0xFF404040, false);

        int rowsTop = panelTop + HEADER;
        if (entries.isEmpty()) {
            String none = "No current deaths.";
            graphics.text(this.font, none, panelLeft + (PANEL_W - this.font.width(none)) / 2, rowsTop + 30, 0xFF606060, false);
            return;
        }

        int start = page * PER_PAGE;
        int textX = stripX + 20;
        int maxTextW = stripW - 24;
        for (int i = 0; i < PER_PAGE && start + i < entries.size(); i++) {
            HistoryEntry entry = entries.get(start + i);
            int rowY = rowsTop + i * ROW_H;
            BlockPos pos = entry.pos();

            // Status: green tick while the body is still out there, red cross once it's gone.
            if (entry.corpseGone()) {
                GuiPainter.sprite(graphics, GuiPainter.CROSS, stripX + 5, rowY + 7, 9, 9);
            } else {
                GuiPainter.sprite(graphics, GuiPainter.CHECK, stripX + 5, rowY + 8, 9, 8);
            }

            String coords = shortDim(entry.dimension()) + "  " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            String meta = DATE.format(new Date(entry.time())) + "  -  " + entry.itemCount() + " items";
            graphics.text(this.font, clip(coords, maxTextW), textX, rowY + 3, 0xFF1E1E1E, false);
            graphics.text(this.font, clip(meta, maxTextW), textX, rowY + 13, 0xFF474747, false);
        }
    }

    private String clip(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
    }

    private void openLocation(HistoryEntry entry) {
        BlockPos pos = entry.pos();
        String command = "/execute in " + entry.dimension() + " run tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        Minecraft.getInstance().setScreenAndShow(new ChatScreen(command, false));
    }

    private void recover(int index) {
        ClientPlayNetworking.send(new RecoverPayload(targetUuid, index));
        onClose();
    }

    private static String shortDim(String dimension) {
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }
}
