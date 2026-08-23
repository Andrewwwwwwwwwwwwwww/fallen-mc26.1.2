package io.github.andrewwwwwwwwwwwwwww.fallen.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.andrewwwwwwwwwwwwwww.fallen.FallenEntities;
import io.github.andrewwwwwwwwwwwwwww.fallen.FallenMenus;
import io.github.andrewwwwwwwwwwwwwww.fallen.net.HistoryPayload;
import io.github.andrewwwwwwwwwwwwwww.fallen.net.RequestHistoryPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.lwjgl.glfw.GLFW;

public class FallenClient implements ClientModInitializer {

    private static KeyMapping openHistoryKey;

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(FallenEntities.CORPSE, CorpseRenderer::new);
        MenuScreens.register(FallenMenus.CORPSE, CorpseScreen::new);
        MenuScreens.register(FallenMenus.CORPSE_VIEW, CorpseScreen::new);

        openHistoryKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.fallen.death_history",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                KeyMapping.Category.GAMEPLAY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openHistoryKey.consumeClick()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new RequestHistoryPayload(""));
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(HistoryPayload.TYPE, (payload, context) ->
                context.client().execute(() ->
                        context.client().setScreenAndShow(new DeathHistoryScreen(
                                payload.ownerName(), payload.targetUuid(), payload.entries(),
                                payload.viewerIsOp()))));
    }
}
