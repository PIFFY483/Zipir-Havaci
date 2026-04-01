package com.zipirhavaci.client.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.item.ZipirAviatorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, value = Dist.CLIENT)
public class GauntletShakeHandler {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ZipirAviatorItem)) return;

        CompoundTag pData = player.getPersistentData();

        int superCharge = pData.getInt("SuperChargeShake");
        int dashTicks = pData.getInt("AviatorDashTicks");
        int smokeTicks = pData.getInt("ShotSmokeTicks");

        if (superCharge <= 0 && dashTicks <= 0 && smokeTicks <= 0) return;

        float partialTicks = event.getPartialTick();
        float totalTime = (player.tickCount + partialTicks);
        PoseStack poseStack = event.getPoseStack();

        if (superCharge > 0) {

            float chargeFactor = Math.min(superCharge / 100.0f, 1.0f);

            // Titreme Büyüklüğü (Intensity):0.002f
            //  eldivenin ekrandaki milimetrik oynamasını sağlar.
            float intensity = 0.002f + (chargeFactor * 0.006f);

            // Titreme Hızı (Frekans)
            float shakeFreq = 45.0f;

            float sX = (float) Math.sin(totalTime * shakeFreq) * intensity;
            float sY = (float) Math.cos(totalTime * (shakeFreq + 2.0f)) * intensity;
            float sZ = (float) Math.sin(totalTime * (shakeFreq * 1.2f)) * (intensity * 0.3f);

            poseStack.translate(sX, sY, sZ);

            // Dönüş Açısı (Rotation)
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(sX * 40f));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(sY * 20f));

        } else {
            // --- ATEŞLEME SONRASI (DAHA SAKİN RECOIL) ---
            float normalIntensity = (dashTicks > 0) ? 0.01f + (dashTicks / 60.0f) * 0.015f : 0.003f;
            float sX = (float) Math.sin(totalTime * 2.5f) * normalIntensity;
            float sY = (float) Math.cos(totalTime * 2.0f) * (normalIntensity * 0.6f);
            poseStack.translate(sX, sY, -0.005f);
        }
    }
}