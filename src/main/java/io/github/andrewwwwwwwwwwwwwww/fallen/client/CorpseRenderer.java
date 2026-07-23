package io.github.andrewwwwwwwwwwwwwww.fallen.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.andrewwwwwwwwwwwwwww.fallen.entity.CorpseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Renders a corpse as the owner's body (wide or slim to match their skin), or
 * as a skeleton once it has aged. The body is laid flat by the vanilla
 * renderer because the entity reports {@link net.minecraft.world.entity.Pose#SLEEPING}.
 *
 * <p>Uses plain {@link HumanoidModel}s rather than {@code PlayerModel}, because
 * {@code PlayerModel} requires an {@code AvatarRenderState} — and any state of
 * that type gets routed to vanilla's player renderer by the dispatcher, which
 * would bypass this class completely. See {@link CorpseRenderState}.
 */
public class CorpseRenderer extends LivingEntityRenderer<CorpseEntity, CorpseRenderState, HumanoidModel<CorpseRenderState>> {

    private static final java.util.Map<UUID, Supplier<PlayerSkin>> SKIN_LOOKUPS = new ConcurrentHashMap<>();
    private static final Identifier SKELETON_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/skeleton/skeleton.png");

    private final HumanoidModel<CorpseRenderState> wideModel;
    private final HumanoidModel<CorpseRenderState> slimModel;
    private final HumanoidModel<CorpseRenderState> skeletonModel;

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        this.skeletonModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON));
    }

    @Override
    public CorpseRenderState createRenderState() {
        return new CorpseRenderState();
    }

    @Override
    public Identifier getTextureLocation(CorpseRenderState state) {
        if (state.skeleton) {
            return SKELETON_TEXTURE;
        }
        PlayerSkin skin = state.skin;
        return skin != null ? skin.body().texturePath() : DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    public void extractRenderState(CorpseEntity entity, CorpseRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.nameTag = null; // the owner's name lives in the GUI title, not over the body
        state.facing = entity.getYRot();
        state.skeleton = entity.isSkeleton();

        PlayerSkin skin = resolveSkin(entity.getOwnerProfileId(), entity.getOwnerName());
        state.skin = skin;
        state.slim = skin.model() == PlayerModelType.SLIM;
        state.isCrouching = false;
    }

    @Override
    public void submit(CorpseRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        // Choose the model for THIS state, immediately before it is drawn.
        this.model = state.skeleton ? skeletonModel : (state.slim ? slimModel : wideModel);
        super.submit(state, poseStack, collector, camera);
    }

    private static PlayerSkin resolveSkin(UUID ownerId, String ownerName) {
        Minecraft mc = Minecraft.getInstance();
        if (ownerId != null) {
            ClientPacketListener connection = mc.getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(ownerId);
                if (info != null) {
                    return info.getSkin();
                }
            }
            if (ownerName != null && !ownerName.isEmpty()) {
                GameProfile profile = new GameProfile(ownerId, ownerName);
                PlayerSkin looked = SKIN_LOOKUPS
                        .computeIfAbsent(ownerId, id -> mc.getSkinManager().createLookup(profile, false))
                        .get();
                if (looked != null) {
                    return looked;
                }
            }
            return DefaultPlayerSkin.get(ownerId);
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }
}
