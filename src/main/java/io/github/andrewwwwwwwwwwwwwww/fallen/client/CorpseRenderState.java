package io.github.andrewwwwwwwwwwwwwww.fallen.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Render state for a corpse.
 *
 * <p>This deliberately extends {@link HumanoidRenderState} and <em>not</em>
 * {@code AvatarRenderState}: {@code EntityRenderDispatcher.getRenderer(state)}
 * routes any {@code AvatarRenderState} to vanilla's player renderer, which
 * would bypass this mod's renderer entirely (skin and pose would still work,
 * because those are state fields vanilla reads, but every override here —
 * including the skeleton swap — would never run).
 */
public class CorpseRenderState extends HumanoidRenderState {
    public float facing;
    public boolean skeleton;
    public boolean slim;
    public PlayerSkin skin;
}
