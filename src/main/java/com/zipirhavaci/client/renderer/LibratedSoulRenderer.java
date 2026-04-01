package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zipirhavaci.entity.LibratedSoulEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public class LibratedSoulRenderer extends HumanoidMobRenderer<LibratedSoulEntity, PlayerModel<LibratedSoulEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("zipirhavaci", "textures/entity/librated_soul.png");


    public LibratedSoulRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.0F);

    }

    @Override
    public ResourceLocation getTextureLocation(LibratedSoulEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(LibratedSoulEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, 15728880);

        if (!entity.isTalking()) return;

        double distSq = this.entityRenderDispatcher.camera.getPosition()
                .distanceToSqr(entity.position());
        if (distSq > 400.0) return; // 20 blok mesafe

        renderGoldenBubble(entity, poseStack, buffer, packedLight, partialTicks, distSq);
    }

    private void renderGoldenBubble(LibratedSoulEntity entity, PoseStack poseStack,
                                    MultiBufferSource buffer, int packedLight,
                                    float partialTicks, double distSq) {
        Font font = this.getFont();
        long tick = entity.level().getGameTime();

        String msg = entity.getCurrentMessage();

        float distance = (float) Math.sqrt(distSq);
        float alphaFactor = Mth.clamp((20.0F - distance) / 10.0F, 0.0F, 1.0F);
        int alpha = (int) (alphaFactor * 255.0F);
        if (alpha <= 5) return;

        int textColor = (alpha << 24) | 0xFFD700;
        int bgColor   = ((int)(alphaFactor * 0xAA) << 24) | 0x2A2000;

        float bob = Mth.sin((tick + partialTicks) * 0.08f) * 0.06f;

        poseStack.pushPose();

        float height = entity.getBbHeight() + 1.3F + bob;
        poseStack.translate(0.0, height, 0.0);
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