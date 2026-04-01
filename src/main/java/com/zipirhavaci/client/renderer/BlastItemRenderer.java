package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zipirhavaci.entity.BlastItemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

public class BlastItemRenderer extends EntityRenderer<BlastItemEntity> {

    private final ItemRenderer itemRenderer;

    public BlastItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BlastItemEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        itemRenderer.renderStatic(
                entity.getItem(),
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BlastItemEntity entity) {
        return new ResourceLocation("minecraft", "textures/item/stone.png");
    }

    @Override
    protected int getBlockLightLevel(BlastItemEntity entity,
                                     net.minecraft.core.BlockPos pos) {
        return 15;
    }

    @Override
    public boolean shouldRender(BlastItemEntity entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        // 256 blok — 4 chunk görünürlük
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        return distSq < 256 * 256 && frustum.isVisible(entity.getBoundingBox());
    }
}