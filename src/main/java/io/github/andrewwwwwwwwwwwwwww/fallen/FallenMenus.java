package io.github.andrewwwwwwwwwwwwwww.fallen;

import io.github.andrewwwwwwwwwwwwwww.fallen.menu.CorpseMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class FallenMenus {
    private FallenMenus() {}

    /** The normal, lootable corpse screen. */
    public static final MenuType<CorpseMenu> CORPSE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "corpse"),
            new MenuType<>(CorpseMenu::new, FeatureFlags.VANILLA_SET));

    /** Read-only "as it was at death" snapshot, used by the death history. */
    public static final MenuType<CorpseMenu> CORPSE_VIEW = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "corpse_view"),
            new MenuType<>(CorpseMenu::view, FeatureFlags.VANILLA_SET));

    public static void init() {
        // class-load triggers registration
    }
}
