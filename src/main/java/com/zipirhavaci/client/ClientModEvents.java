package com.zipirhavaci.client;

import com.zipirhavaci.client.renderer.BlazeCoreRenderer;
import com.zipirhavaci.client.renderer.DoorShakeRenderer;
import com.zipirhavaci.client.renderer.CoreRodRenderer;
import com.zipirhavaci.client.renderer.FallingBlockProjectileRenderer;
import com.zipirhavaci.client.renderer.LibratedSoulRenderer;
import com.zipirhavaci.client.renderer.SilentCaptiveRenderer;
import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.core.ItemRegistry;
import com.zipirhavaci.core.ZipirHavaci;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(
                    ItemRegistry.CORE_ROD_BE.get(),
                    CoreRodRenderer::new
            );
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityRegistry.BLAZE_CORE_PROJECTILE.get(), BlazeCoreRenderer::new);
        event.registerEntityRenderer(EntityRegistry.BLAST_ITEM.get(),
                com.zipirhavaci.client.renderer.BlastItemRenderer::new);
        event.registerEntityRenderer(EntityRegistry.BLAST_TNT.get(),
                com.zipirhavaci.client.renderer.BlastTntRenderer::new);
        event.registerBlockEntityRenderer(ItemRegistry.DOOR_SHAKE_BE.get(), DoorShakeRenderer::new);
        event.registerEntityRenderer(EntityRegistry.FALLING_BLOCK_PROJECTILE.get(),
                FallingBlockProjectileRenderer::new);
        event.registerEntityRenderer(EntityRegistry.FLYING_DOOR.get(),
                com.zipirhavaci.client.renderer.FlyingDoorRenderer::new);
        event.registerEntityRenderer(EntityRegistry.SILENT_CAPTIVE.get(),
                SilentCaptiveRenderer::new);
        // ── YENİ ──────────────────────────────────────────────────────────────
        event.registerEntityRenderer(EntityRegistry.LIBRATED_SOUL.get(),
                LibratedSoulRenderer::new);

    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {

        event.registerAboveAll("death_deny_bar", com.zipirhavaci.client.hud.DarkAuraOverlay.HUD_DEATH_DENY);
    }

}