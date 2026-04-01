package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zipirhavaci.client.model.ZipirAviatorModel;
import com.zipirhavaci.item.ZipirAviatorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.Arrays;
import java.util.List;

public class ZipirAviatorRenderer extends GeoItemRenderer<ZipirAviatorItem> {

    private static final List<String> LED_NAMES = Arrays.asList("led_1", "led_4", "led_2", "led_5", "led_3", "led_6");

    // Supercharge için önbellek değişkenleri(Performans Optimizasyonu)
    private int cachedSuperCharge = 0;
    private float cachedColorWave = 0.0f;

    public ZipirAviatorRenderer() {
        super(new ZipirAviatorModel());
    }

    @Override
    public void actuallyRender(PoseStack poseStack, ZipirAviatorItem animatable, BakedGeoModel model,
                               RenderType renderType, MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer,
                               boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {

        if (Minecraft.getInstance().player != null) {
            this.cachedSuperCharge = Minecraft.getInstance().player.getPersistentData().getInt("SuperChargeShake");
            this.cachedColorWave = (float) (Math.sin(Minecraft.getInstance().player.tickCount * 0.5f) * 0.5f + 0.5f);
        } else {
            this.cachedSuperCharge = 0;
        }

        super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ZipirAviatorItem animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {

        if (LED_NAMES.contains(bone.getName())) {


            // --- SÜPERSKİLL ŞARJ LED KATMANI ---
            if (this.cachedSuperCharge > 0) {
                int ledIndex = LED_NAMES.indexOf(bone.getName());

                // 100 şarj limitine göre her LED ~16 birimde yanar
                int requiredCharge = (ledIndex + 1) * 16;

                if (this.cachedSuperCharge >= requiredCharge) {
                    float redOverride = 0.0f;
                    float greenOverride = this.cachedColorWave;               // 0'dan 1'e dalgalanır
                    float blueOverride = 0.5f + (this.cachedColorWave * 0.5f);  // 0.5'ten 1.0'a dalgalanır

                    super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                            isReRender, partialTick, 15728880, packedOverlay,
                            redOverride, greenOverride, blueOverride, alpha);
                    return;
                }

            }

            // --- CEPHANE/RELOAD SİSTEMİ MANTIĞI ---
            ItemStack stack = this.getCurrentItemStack();
            if (stack != null && stack.hasTag()) {
                int uses = stack.getTag().getInt("Uses");
                int index = LED_NAMES.indexOf(bone.getName());

                // --- SIRALI DOLMA (RELOAD) SİSTEMİ ---
                boolean isReloading = stack.getTag().contains("LastUseTime");
                boolean shouldBeLit = false;
                int visualLevel = uses;

                if (isReloading) {
                    long elapsed = System.currentTimeMillis() - stack.getTag().getLong("LastUseTime");

                    int filledCount = (int) (elapsed / 1000);
                    filledCount = Math.min(filledCount, 6);

                    // sağdan sola dolum
                    if (index >= (5 - filledCount)) {
                        shouldBeLit = true;
                    }

                    visualLevel = Math.max(0, 5 - filledCount);
                } else {
                    shouldBeLit = (uses <= (5 - index));
                    visualLevel = uses;
                }

                // --- ÇİZİM ---
                if (!shouldBeLit) {
                    // SÖNÜK LED
                    super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                            isReRender, partialTick, packedLight, packedOverlay,
                            0.15f, 0.15f, 0.15f, alpha);
                    return;
                }

                // CANLI LED: Bireysel
                float flicker = calculateIndividualFlicker(visualLevel, bone.getName());
                float[] rgb = getGroupRGB(visualLevel / 2);

                super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
                        isReRender, partialTick, 15728880, packedOverlay,
                        rgb[0] * flicker, rgb[1] * flicker, rgb[2] * flicker, alpha);
                return;
            }
        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    private float calculateIndividualFlicker(int uses, String boneName) {
        int boneSeed = boneName.hashCode();
        long time = System.currentTimeMillis();
        double individualTime = (time * 0.001) + (boneSeed * 0.1337);
        double speed = 1.0 + (uses * 1.5);
        double wave = Math.sin(individualTime * 15.0 * speed) * Math.cos(individualTime * 7.2 * speed) * Math.sin(individualTime * 22.4);

        if (uses <= 1) return (wave > 0.95) ? 0.9f : 1.0f; // Yeşil: Stabil
        if (uses <= 3) { // Turuncu: Titrek
            float depth = (uses == 3) ? 0.4f : 0.6f;
            if (wave < -0.4) return depth + (float)(Math.abs(wave) * 0.2);
            return 0.9f + (float)(wave * 0.1);
        }
        // Kırmızı
        if (Math.sin(individualTime * 50.0 + boneSeed) > 0.92) return 0.2f;
        return 0.5f + (float)(Math.abs(wave) * 0.5);
    }

    private float[] getGroupRGB(int groupIdx) {
        if (groupIdx == 0) return new float[]{0.0f, 1.0f, 0.2f};
        if (groupIdx == 1) return new float[]{1.0f, 0.5f, 0.0f};
        return new float[]{1.0f, 0.0f, 0.0f};
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        poseStack.pushPose();
        float finalScale;
        float tx, ty, tz;

        if (ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            // --- BİRİNCİ ŞAHIS ---
            poseStack.mulPose(Axis.XP.rotationDegrees(-66f));
            poseStack.mulPose(Axis.YP.rotationDegrees(-240f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(158f));
            finalScale = 1.5f; tx = -0.4f; ty = -1.12f; tz = -0.53f;

        } else if (ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
            // --- ÜÇÜNCÜ ŞAHIS ---
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            poseStack.mulPose(Axis.YP.rotationDegrees(-180f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            boolean isSlim = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getModelName().equals("slim");
            if (isSlim) { finalScale = 0.9f; tx = -0.28f; ty = -0.7f; tz = -0.915f; }
            else { finalScale = 1.0f; tx = -0.31f; ty = -0.72f; tz = -0.875f; }

        } else if (ctx == ItemDisplayContext.GUI) {
            // --- ENVANTER / SLOT GÖRÜNÜMÜ (BURASI) ---
            // Rotasyon: Modeli slota göre çevir
            poseStack.mulPose(Axis.XP.rotationDegrees(30f));  // Öne eğim
            poseStack.mulPose(Axis.YP.rotationDegrees(135f)); // Yan duruş
            poseStack.mulPose(Axis.ZP.rotationDegrees(0f));

            // Ölçek ve Pozisyon
            finalScale = 1.6f; // Slotun içine sığacak büyüklük
            tx = -1.2f;          // Sağa-Sola (Pozitif sağ)
            ty = -0.9f;          // Yukarı-Aşağı (Pozitif yukarı)
            tz = -0.4f;          // Ön-Arka

        } else {
            // --- YERDEKİ (GROUND) VE DİĞER DURUMLAR ---
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
            poseStack.mulPose(Axis.YP.rotationDegrees(-180f));
            poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
            finalScale = 0.9f; tx = -0.3f; ty = -1.0f; tz = -1.0f;
        }

        poseStack.scale(finalScale, finalScale, finalScale);
        poseStack.translate(tx, ty, tz);
        super.renderByItem(stack, ctx, poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}