package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zipirhavaci.entity.FallingBlockProjectileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class FallingBlockProjectileRenderer extends EntityRenderer<FallingBlockProjectileEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public FallingBlockProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer  = Minecraft.getInstance().getBlockRenderer();
        this.shadowRadius   = 0.5f;
    }

    @Override
    public void render(FallingBlockProjectileEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        poseStack.pushPose();

        float interpYaw   = entity.prevVisualYaw
                + (entity.visualYaw   - entity.prevVisualYaw)   * partialTick;
        float interpPitch = entity.prevVisualPitch
                + (entity.visualPitch - entity.prevVisualPitch) * partialTick;

        // ── Blok merkezi etrafında döndürme ───────────────────────────────
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(interpYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(interpPitch));
        poseStack.translate(-0.5, -0.5, -0.5);

        // ── BlockState çöz ───────────────────────────────────────────────────────
        BlockState state = entity.getProjectileBlockState();

        // ── Bloğu çiz ────────────────────────────────────────────────────────────
        blockRenderer.renderSingleBlock(
                state,
                poseStack,
                buffer,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();

        // Shadow ve label
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(FallingBlockProjectileEntity entity) {
        return new ResourceLocation("minecraft", "textures/block/sand.png");
    }

    @Override
    public boolean shouldRender(FallingBlockProjectileEntity entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        // Frustum culling'i devre dışı bırak
        return true;
    }
}