package com.zipirhavaci.core;

import com.zipirhavaci.client.ThrownShieldRenderer;
import com.zipirhavaci.client.renderer.AnomalousArrowRenderer;
import com.zipirhavaci.client.renderer.FakeMovingBlockRenderer;
import com.zipirhavaci.client.renderer.HeavyPistonRenderer;
import com.zipirhavaci.client.renderer.layer.AuraLayer;
import com.zipirhavaci.entity.BlazeCoreEntity;
import com.zipirhavaci.entity.ScorchedImpellerArrowEntity;
import com.zipirhavaci.entity.SingularityRemnantArrowEntity;
import com.zipirhavaci.network.PacketHandler;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;                          // ← doğru paket
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(ZipirHavaci.MOD_ID)
public class ZipirHavaci {
    public static final String MOD_ID = "zipirhavaci";
    public static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public ZipirHavaci() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ItemRegistry.BLOCKS.register(modEventBus);
        ItemRegistry.BLOCK_ENTITIES.register(modEventBus);
        EntityRegistry.ENTITIES.register(modEventBus);
        ItemRegistry.ITEMS.register(modEventBus);
        ItemRegistry.CREATIVE_MODE_TABS.register(modEventBus);
        LootModifierRegistry.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        SoundRegistry.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        PacketHandler.register();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> {
                event.registerEntityRenderer(EntityRegistry.THROWN_SHIELD.get(), ThrownShieldRenderer::new);
            });
            modEventBus.register(ClientModEvents.class);
            MinecraftForge.EVENT_BUS.register(com.zipirhavaci.client.visuals.AuraHudOverlay.class);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(ItemRegistry.BLAZE_CORE.get(), new DefaultDispenseItemBehavior() {

                private static final float POWER       = 1.8F;
                private static final float UNCERTAINTY = 1.1F;

                @Override
                protected ItemStack execute(BlockSource source, ItemStack stack) {
                    ServerLevel level = source.getLevel();   // net.minecraft.core.BlockSource
                    BlockPos pos      = source.getPos();
                    Direction facing  = source.getBlockState().getValue(DispenserBlock.FACING);

                    double x = pos.getX() + 0.5 + facing.getStepX() * 0.6;
                    double y = pos.getY() + 0.5 + facing.getStepY() * 0.6 - 0.15;
                    double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.6;

                    double vx = facing.getStepX() + level.random.nextGaussian() * 0.0075 * UNCERTAINTY;
                    double vy = facing.getStepY() + level.random.nextGaussian() * 0.0075 * UNCERTAINTY;
                    double vz = facing.getStepZ() + level.random.nextGaussian() * 0.0075 * UNCERTAINTY;

                    Vec3 velocity = new Vec3(vx, vy, vz).normalize().scale(POWER * 0.5);

                    BlazeCoreEntity projectile = new BlazeCoreEntity(level, x, y, z, velocity);
                    level.addFreshEntity(projectile);

                    stack.shrink(1);
                    return stack;
                }
            });
        });
    }

    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ItemRegistry.HEAVY_PISTON_BE.get(), HeavyPistonRenderer::new);
            event.registerBlockEntityRenderer(ItemRegistry.FAKE_MOVING_BLOCK_BE.get(), FakeMovingBlockRenderer::new);
            event.registerEntityRenderer(EntityRegistry.BLAZE_CORE_EFFECT.get(), NoopRenderer::new);
            event.<ScorchedImpellerArrowEntity>registerEntityRenderer(EntityRegistry.SCORCHED_IMPELLER_ARROW.get(),
                    context -> new AnomalousArrowRenderer<>(context, "scorched_impeller_arrow"));

            event.<SingularityRemnantArrowEntity>registerEntityRenderer(EntityRegistry.SINGULARITY_REMNANT_ARROW.get(),
                    context -> new AnomalousArrowRenderer<>(context, "singularity_remnant_arrow"));
        }

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skinType : new String[]{"default", "slim"}) {
                PlayerRenderer renderer = event.getSkin(skinType);
                if (renderer != null) {
                    renderer.addLayer(new AuraLayer<>(renderer, event.getEntityModels()));
                }
            }
        }
    }
}