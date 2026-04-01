package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zipirhavaci.entity.SilentCaptiveEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class SilentCaptiveRenderer extends HumanoidMobRenderer<SilentCaptiveEntity, PlayerModel<SilentCaptiveEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("zipirhavaci", "textures/entity/silent_captive.png");

    private static final ResourceLocation EYES_TEXTURE = new ResourceLocation("zipirhavaci", "textures/entity/silent_captive_eyes.png");
    private static final int CYCLE_TICKS = 80;

    private static final String[] NORMAL_MESSAGES = {
            "§dI beseech thy mercy...",
            "§7I implore thee...",
            "§4Grant me thy salvation!",
            "§5Forsake me not...",
            "§dI beg of thee, deliver me!"
    };

    private static final String[] HURT_MESSAGES = {
            "§4I beseech thee, cease!",
            "§cThe agony... it rends me!",
            "§4I implore thy mercy!",
            "§8Stay thy hand, I pray thee!"
    };

    public SilentCaptiveRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);

        this.addLayer(new SilentCaptiveEyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SilentCaptiveEntity entity) {
        return TEXTURE;
    }


    private class SilentCaptiveEyesLayer extends RenderLayer<SilentCaptiveEntity, PlayerModel<SilentCaptiveEntity>> {
        public SilentCaptiveEyesLayer(RenderLayerParent<SilentCaptiveEntity, PlayerModel<SilentCaptiveEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SilentCaptiveEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.eyes(EYES_TEXTURE));
            this.getParentModel().renderToBuffer(poseStack, vertexconsumer, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public void render(SilentCaptiveEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        if (!entity.wantsHelp()) return;

        double distSq = this.entityRenderDispatcher.camera.getPosition().distanceToSqr(entity.position());
        if (distSq > 256.0) return;

        this.renderSpeechBubble(entity, poseStack, buffer, packedLight, partialTicks, distSq);
    }

    private void renderSpeechBubble(SilentCaptiveEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTicks, double distSq) {
        Font font = this.getFont();
        long tick = entity.level().getGameTime();

        String[] activeMessages = entity.isFleeing() ? HURT_MESSAGES : NORMAL_MESSAGES;
        String msg = activeMessages[(int) ((tick / CYCLE_TICKS) % activeMessages.length)];

        float distance = (float) Math.sqrt(distSq);
        float alphaFactor = Mth.clamp((16.0F - distance) / 8.0F, 0.0F, 1.0F);
        int alpha = (int) (alphaFactor * 255.0F);

        if (alpha <= 5) return;

        int textColor = (alpha << 24) | 0xFF0000;
        int bgColor = ((int)(alphaFactor * 0xCC) << 24) | 0x333333;

        float bob = Mth.sin((tick + partialTicks) * 0.08f) * 0.05f;

        poseStack.pushPose();

        float height = entity.getBbHeight() + 1.2F + bob;
        poseStack.translate(0.0D, height, 0.0D);

        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        Matrix4f matrix = poseStack.last().pose();

        font.drawInBatch(
                msg,
                (float)(-font.width(msg) / 2),
                0,
                textColor,
                false,
                matrix,
                buffer,
                Font.DisplayMode.NORMAL,
                bgColor,
                packedLight
        );

        poseStack.popPose();
    }
}