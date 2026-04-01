package com.zipirhavaci.core;

import com.zipirhavaci.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "zipirhavaci");

    public static final RegistryObject<EntityType<ThrownShieldEntity>> THROWN_SHIELD = ENTITIES.register("thrown_shield",
            () -> EntityType.Builder.<ThrownShieldEntity>of(ThrownShieldEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(64)
                    .updateInterval(2)
                    .build("thrown_shield"));




    public static final RegistryObject<EntityType<BlazeCoreEntity>> BLAZE_CORE_PROJECTILE =
            ENTITIES.register("blaze_core_projectile",
                    () -> EntityType.Builder.<BlazeCoreEntity>of(BlazeCoreEntity::new, MobCategory.MISC)
                            .sized(0.45F, 0.45F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("blaze_core_projectile"));

    public static final RegistryObject<EntityType<BlazeCoreEffectEntity>> BLAZE_CORE_EFFECT =
            ENTITIES.register("blaze_core_effect",
                    () -> EntityType.Builder.<BlazeCoreEffectEntity>of(BlazeCoreEffectEntity::new, MobCategory.MISC)
                            .sized(0.0F, 0.0F) // Sıfır boyut — tamamen görünmez
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("blaze_core_effect"));



    // Kinetik İtici Ok Varlığı
    public static final RegistryObject<EntityType<ScorchedImpellerArrowEntity>> SCORCHED_IMPELLER_ARROW =
            ENTITIES.register("scorched_impeller_arrow",
                    () -> EntityType.Builder.<ScorchedImpellerArrowEntity>of(ScorchedImpellerArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("scorched_impeller_arrow"));

    // Kuantum Çekim Oku Varlığı
    public static final RegistryObject<EntityType<SingularityRemnantArrowEntity>> SINGULARITY_REMNANT_ARROW =
            ENTITIES.register("singularity_remnant_arrow",
                    () -> EntityType.Builder.<SingularityRemnantArrowEntity>of(SingularityRemnantArrowEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("singularity_remnant_arrow"));
    public static final RegistryObject<EntityType<com.zipirhavaci.entity.BlastItemEntity>> BLAST_ITEM =
            ENTITIES.register("blast_item",
                    () -> EntityType.Builder.<com.zipirhavaci.entity.BlastItemEntity>of(
                                    com.zipirhavaci.entity.BlastItemEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("blast_item"));

    public static final RegistryObject<EntityType<com.zipirhavaci.entity.BlastTntEntity>> BLAST_TNT =
            ENTITIES.register("blast_tnt",
                    () -> EntityType.Builder.<com.zipirhavaci.entity.BlastTntEntity>of(
                                    com.zipirhavaci.entity.BlastTntEntity::new, MobCategory.MISC)
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("blast_tnt"));

    public static final RegistryObject<EntityType<FallingBlockProjectileEntity>> FALLING_BLOCK_PROJECTILE =
            ENTITIES.register("falling_block_projectile",
                    () -> EntityType.Builder.<FallingBlockProjectileEntity>of(
                                    FallingBlockProjectileEntity::new, MobCategory.MISC)
                            .sized(0.98f, 0.98f)
                            .clientTrackingRange(8)
                            .build("falling_block_projectile"));


    public static final RegistryObject<EntityType<com.zipirhavaci.entity.FlyingDoorEntity>> FLYING_DOOR =
            ENTITIES.register("flying_door",
                    () -> EntityType.Builder.<com.zipirhavaci.entity.FlyingDoorEntity>of(
                                    com.zipirhavaci.entity.FlyingDoorEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("flying_door"));

    public static final RegistryObject<EntityType<SilentCaptiveEntity>> SILENT_CAPTIVE =
            ENTITIES.register("silent_captive", () ->
                    EntityType.Builder.<SilentCaptiveEntity>of(
                                    SilentCaptiveEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .updateInterval(3)
                            .build("silent_captive")
            );

    public static final RegistryObject<EntityType<LibratedSoulEntity>> LIBRATED_SOUL =
            ENTITIES.register("librated_soul", () ->
                    EntityType.Builder.<LibratedSoulEntity>of(
                                    LibratedSoulEntity::new, MobCategory.MISC)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("librated_soul")
            );

}