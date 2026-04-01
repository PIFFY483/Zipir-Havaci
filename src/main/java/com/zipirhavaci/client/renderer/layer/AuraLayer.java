package com.zipirhavaci.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zipirhavaci.core.capability.StaticProgressionProvider;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Render layer:
 *  - Normal aura  → body parlaması + 14 katmanlı jiroskopik kafes (orijinal)
 *  - Karanlık aura → sadece body parlaması (mor-siyah ↔ koyu gri nabız), yüksek opacity
 */
public class AuraLayer<T extends Player, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation ARMOR_LOCATION =
            new ResourceLocation("textures/entity/creeper/creeper_armor.png");
    private final M model;

    public AuraLayer(RenderLayerParent<T, M> parent, EntityModelSet modelSet) {
        super(parent);
        this.model = parent.getModel();
    }

    @Override
    public void render(PoseStack matrixStack, MultiBufferSource buffer, int packedLight,
                       T player, float limbSwing, float limbSwingAmount, float partialTicks,
                       float ageInTicks, float netHeadYaw, float headPitch) {

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            float f = (float) player.tickCount + partialTicks;

            // ─── NORMAL AURA (body + orbital ring) ──────────────────────────
            if (data.isAuraActive() && data.getAuraLevel() > 0 && !data.isCursed()) {
                float level    = data.getAuraLevel();
                int ticksLeft  = data.getAuraTicksLeft();

                renderBodyAura(matrixStack, buffer, packedLight, player, level, f,
                        limbSwing, limbSwingAmount, partialTicks,
                        ageInTicks, netHeadYaw, headPitch, false);

                renderOrbitalRing(matrixStack, buffer, packedLight, level, ticksLeft, f);
            }

            // ─── KARANLIK AURA (sadece body, yüksek opacity) ────────────────
            if (data.isDarkAuraActive() && data.getDarkAuraLevel() > 0) {
                renderBodyAura(matrixStack, buffer, packedLight, player,
                        data.getDarkAuraLevel(), f,
                        limbSwing, limbSwingAmount, partialTicks,
                        ageInTicks, netHeadYaw, headPitch, true);
            }
        });
    }

    // ─── BODY PARLAMA ────────────────────────────────────────────────────────
    private void renderBodyAura(PoseStack matrixStack, MultiBufferSource buffer,
                                int packedLight, T player, float level, float f,
                                float limbSwing, float limbSwingAmount, float partialTicks,
                                float ageInTicks, float netHeadYaw, float headPitch,
                                boolean dark) {
        matrixStack.pushPose();
        matrixStack.scale(1.02F, 1.02F, 1.02F);

        float speedMultiplier = (level >= 3.0f) ? 0.03F : 0.015F;
        VertexConsumer vc = buffer.getBuffer(
                RenderType.energySwirl(ARMOR_LOCATION, f * speedMultiplier, f * speedMultiplier));

        this.getParentModel().copyPropertiesTo(this.model);
        this.model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTicks);
        this.model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        float[] colors = dark ? calculateDarkAuraColor(level, f)
                : calculateAuraColor(level, f);

        this.model.renderToBuffer(matrixStack, vc, packedLight,
                OverlayTexture.NO_OVERLAY,
                colors[0], colors[1], colors[2], colors[3]);

        matrixStack.popPose();
    }

    // ─── 14 KATMANLI JİROSKOPİK KAFES (NORMAL AURA) ─────────────────────────
    private void renderOrbitalRing(PoseStack matrixStack, MultiBufferSource buffer,
                                   int packedLight, float level, int ticksLeft, float f) {
        int maxTicks;
        if (level >= 3.0f)      maxTicks = 600;
        else if (level >= 2.0f) maxTicks = 520;
        else if (level >= 1.0f) maxTicks = 480;
        else                    maxTicks = 360;

        float ratio = Math.max(0.05f, (float) ticksLeft / maxTicks);

        float baseScale;
        if (level >= 3.0f)      baseScale = 5.5f;
        else if (level >= 2.0f) baseScale = 4.8f;
        else                    baseScale = 3.8f;

        float scale = baseScale * ratio;

        VertexConsumer vc = buffer.getBuffer(
                RenderType.energySwirl(ARMOR_LOCATION, f * 0.02F, f * 0.02F));
        float[] colors = calculateAuraColor(level, f);

        if (this.getParentModel() instanceof HumanoidModel<?> humanoidModel) {
            matrixStack.pushPose();
            humanoidModel.body.translateAndRotate(matrixStack);

            for (int i = 0; i < 14; i++) {
                matrixStack.pushPose();
                float angleShift = i * (360f / 14f);
                matrixStack.mulPose(Axis.YP.rotationDegrees(angleShift));
                matrixStack.mulPose(Axis.XP.rotationDegrees(45.0f));

                float selfRotation = f * (3.0F + (i % 4) * 2.0F);
                if (i % 2 == 0) matrixStack.mulPose(Axis.ZP.rotationDegrees(selfRotation));
                else             matrixStack.mulPose(Axis.ZP.rotationDegrees(-selfRotation));

                matrixStack.scale(scale, scale, 0.005f);

                float baseAlpha = (level >= 3.0f) ? 0.35f : (level >= 2.0f ? 0.30f : 0.25f);
                float alpha = Math.max(0.15f, baseAlpha * ratio);

                humanoidModel.body.render(matrixStack, vc, packedLight,
                        OverlayTexture.NO_OVERLAY,
                        colors[0], colors[1], colors[2], alpha);
                matrixStack.popPose();
            }
            matrixStack.popPose();
        }
    }

    // ─── KARANLIK AURA RENKLERİ ──────────────────────────────────────────────
    private float[] calculateDarkAuraColor(float level, float f) {
        float time  = f * 0.03F;
        float pulse = (float) Math.sin(time) * 0.5F + 0.5F;

        float r = 0.10F + pulse * 0.15F;
        float g = 0.00F + pulse * 0.22F;
        float b = 0.12F + pulse * 0.13F;

        float brightness = (level >= 3.0f) ? 1.3f
                : (level >= 2.0f) ? 1.15f
                : (level >= 1.0f) ? 1.05f : 1.0f;
        r = Math.min(1.0f, r * brightness);
        g = Math.min(1.0f, g * brightness);
        b = Math.min(1.0f, b * brightness);

        float alpha = 0.75F
                + (level >= 3.0f ? 0.20f : level >= 2.0f ? 0.12f : 0.06f)
                + (float) Math.sin(time * 1.5F) * 0.08F;
        alpha = Math.min(1.0f, alpha);

        return new float[]{r, g, b, alpha};
    }

    // ─── NORMAL AURA RENKLERİ (DOKUNULMADI) ──────────────────────────────────
    private float[] calculateAuraColor(float level, float f) {
        float r, g, b, alpha;
        float time = f * 0.05F;

        if (level >= 3.0f) {
            r = 0.7F + (float) Math.sin(time) * 0.3F;
            g = 0.4F + (float) Math.sin(time * 1.3F + 1.0F) * 0.4F;
            b = 0.6F + (float) Math.cos(time * 0.9F) * 0.4F;
            float whitePulse = (float)(Math.sin(time * 0.4F) * 0.5F + 0.5F);
            r = r + (1.0F - r) * whitePulse * 0.45F;
            g = g + (1.0F - g) * whitePulse * 0.45F;
            b = b + (1.0F - b) * whitePulse * 0.45F;
            alpha = 0.75F + (float) Math.sin(time * 1.7F) * 0.15F;
        } else if (level >= 2.0f) {
            float mix = (float) Math.sin(time * 1.4F) * 0.5F + 0.5F;
            r = 0.05F + (mix * 0.4F);
            g = 0.95F - (mix * 0.8F);
            b = 0.95F - (mix * 0.3F);
            alpha = 0.55F + (float) Math.sin(time) * 0.05F;
        } else if (level >= 1.0f) {
            float mix = (float) Math.sin(time * 0.8F) * 0.5F + 0.5F;
            r = 1.0F - (mix * 0.15F);
            g = 0.82F + (mix * 0.18F);
            b = 0.1F;
            alpha = 0.5F;
        } else {
            float wave = (float) Math.sin(time * 1.5F);
            r = 0.2F; g = 0.4F; b = 1.0F;
            if (wave > 0) {
                r += wave * 0.45F;
                g -= wave * 0.2F;
            } else {
                float w = -wave;
                r += w * 0.6F; g += w * 0.5F;
            }
            alpha = 0.5F;
        }
        return new float[]{r, g, b, alpha};
    }
}