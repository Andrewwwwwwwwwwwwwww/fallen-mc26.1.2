package io.github.andrewwwwwwwwwwwwwww.fallen;

import io.github.andrewwwwwwwwwwwwwww.fallen.entity.CorpseEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class FallenEntities {
    private FallenEntities() {}

    public static final ResourceKey<EntityType<?>> CORPSE_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Fallen.MOD_ID, "corpse"));

    public static final EntityType<CorpseEntity> CORPSE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, CORPSE_KEY,
            EntityType.Builder.of(CorpseEntity::new, MobCategory.MISC)
                    // Fallback dimensions only — the real hitbox is built in
                    // CorpseEntity.makeBoundingBox to hug the lying body.
                    .sized(0.9f, 0.5f)
                    .eyeHeight(0.2f)
                    .clientTrackingRange(8)
                    .build(CORPSE_KEY));

    public static void init() {
        FabricDefaultAttributeRegistry.register(CORPSE, CorpseEntity.createAttributes());
    }
}
