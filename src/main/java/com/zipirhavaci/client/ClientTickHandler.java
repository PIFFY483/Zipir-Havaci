package com.zipirhavaci.client;

import com.zipirhavaci.client.sound.ClientSoundManager;
import com.zipirhavaci.client.visuals.AuraVisualEffects;
import com.zipirhavaci.client.visuals.FOVHandler;
import com.zipirhavaci.client.visuals.MeteorVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (Minecraft.getInstance().player == null) return;

        // 1. SARSINTI KISMI
        if (e.phase == TickEvent.Phase.START) {
            ClientShakeHandler.clientTick(Minecraft.getInstance().player);
        }

        if (e.phase != TickEvent.Phase.END) return;

        Level level = Minecraft.getInstance().player.level();
        if (level.dimension() == Level.NETHER && Minecraft.getInstance().player.getY() > 25 && Minecraft.getInstance().player.getY() < 45) {
            BlockPos pPos = Minecraft.getInstance().player.blockPosition();
            // Yapının Bedrock mühürlü olduğunu doğrulamak için basit bir tavan/taban kontrolü
            if (level.getBlockState(pPos.below(2)).is(Blocks.BEDROCK) || level.getBlockState(pPos.above(3)).is(Blocks.BEDROCK)) {
                if (level.random.nextFloat() < 0.2F) { // %20 şansla her tick'te mavi kare süzülür
                    double px = Minecraft.getInstance().player.getX() + (level.random.nextDouble() - 0.5D) * 10;
                    double py = Minecraft.getInstance().player.getY() + level.random.nextDouble() * 3;
                    double pz = Minecraft.getInstance().player.getZ() + (level.random.nextDouble() - 0.5D) * 10;
                    level.addParticle(ParticleTypes.SOUL, px, py, pz, 0.0D, 0.01D, 0.0D);
                }
            }
        }
        // -------------------------------------------------------

        AuraVisualEffects.tickPlayerAura();

        // Aura ambient ses
        Minecraft.getInstance().player.getCapability(
                com.zipirhavaci.core.capability.StaticProgressionProvider.STATIC_PROGRESSION
        ).ifPresent(data -> {
            if (data.isAuraActive() && Minecraft.getInstance().player.tickCount % 60 == 0) {
                float pitch = (data.getAuraLevel() >= 3.0f) ? 1.5f : 1.8f;
                net.minecraft.client.resources.sounds.SimpleSoundInstance sound =
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forLocalAmbience(
                                (data.getAuraLevel() >= 3.0f)
                                        ? net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_AMBIENT
                                        : net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,
                                pitch, 0.18f
                        );
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        });

        com.zipirhavaci.client.visuals.AuraHudOverlay.tick();

        // Meteor efektleri
        MeteorVisualEffects.handleClientImpact(
                Minecraft.getInstance().player
        );

        ClientSoundManager.tick();

        RegularSmoke.clientTick();
    }
}