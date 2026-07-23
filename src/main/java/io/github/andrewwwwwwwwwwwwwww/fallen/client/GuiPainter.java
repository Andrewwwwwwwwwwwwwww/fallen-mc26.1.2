package io.github.andrewwwwwwwwwwwwwww.fallen.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws Minecraft-style GUI chrome with plain fills using vanilla's exact
 * container colours and geometry.
 *
 * <p>Vanilla's panels are not square: the outer 2x2 of each corner is cut away
 * diagonally (those pixels are transparent in the texture) with a single black
 * pixel on the diagonal, which is what gives them their rounded look. We
 * reproduce that by simply never drawing those pixels, so the background shows
 * through exactly as it does in vanilla.
 */
public final class GuiPainter {
    private GuiPainter() {}

    private static final int BODY = 0xFFC6C6C6;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    /** A raised window panel with vanilla's cut (rounded) corners. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        // Body drawn as two overlapping bands, leaving each 2x2 corner untouched.
        g.fill(x + 2, y, x + w - 2, y + h, BODY);
        g.fill(x, y + 2, x + w, y + h - 2, BODY);

        // Bevels: light top/left, dark bottom/right (stepped in at the corners).
        g.fill(x + 2, y + 1, x + w - 2, y + 3, WHITE);
        g.fill(x + 1, y + 2, x + 3, y + h - 2, WHITE);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, DARK);
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 2, DARK);

        // Black outer frame, inset by 2 at each end so corners stay cut.
        g.fill(x + 2, y, x + w - 2, y + 1, BLACK);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, BLACK);
        g.fill(x, y + 2, x + 1, y + h - 2, BLACK);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, BLACK);

        // The single black pixel on each corner diagonal.
        g.fill(x + 1, y + 1, x + 2, y + 2, BLACK);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, BLACK);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, BLACK);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, BLACK);
    }

    /** A sunken 16x16 inventory slot (item area top-left at x, y). */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADOW);
        g.fill(x, y, x + 17, y + 17, WHITE);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    /** Vanilla's green tick, used to mark a corpse that still exists. */
    public static final Identifier CHECK = Identifier.withDefaultNamespace("icon/checkmark");
    /** Vanilla's red cross, used to mark a corpse that's gone. */
    public static final Identifier CROSS = Identifier.withDefaultNamespace("pending_invite/reject");

    /** Blit one of Minecraft's own GUI sprites. */
    public static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    /** A sunken inset strip for list rows. */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_SHADOW);
        g.fill(x + 1, y + 1, x + w, y + h, WHITE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FILL);
    }
}
