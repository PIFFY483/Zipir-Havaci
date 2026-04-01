package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zipirhavaci.block.CoreRodBlock;
import com.zipirhavaci.block.entity.CoreRodBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CoreRodRenderer implements BlockEntityRenderer<CoreRodBlockEntity> {

    private static final ResourceLocation EMISSIVE_TEXTURE =
            new ResourceLocation("zipirhavaci", "textures/block/core_rod_e.png");

    public CoreRodRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CoreRodBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (entity.getLevel() == null) return;
        BlockState state = entity.getLevel().getBlockState(entity.getBlockPos());
        if (!(state.getBlock() instanceof CoreRodBlock)) return;
        if (!state.getValue(CoreRodBlock.POWERED)) return;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE));

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();
        Matrix3f norm = poseStack.last().normal();

        float e = 0.001f;

        // NORTH (z = -e)
        vertex(consumer, mat, norm,  0, 0, -e,  0, 1,  0, 0, -1);
        vertex(consumer, mat, norm,  0, 1, -e,  0, 0,  0, 0, -1);
        vertex(consumer, mat, norm,  1, 1, -e,  1, 0,  0, 0, -1);
        vertex(consumer, mat, norm,  1, 0, -e,  1, 1,  0, 0, -1);

        // SOUTH (z = 1+e)
        vertex(consumer, mat, norm,  1, 0, 1+e,  0, 1,  0, 0, 1);
        vertex(consumer, mat, norm,  1, 1, 1+e,  0, 0,  0, 0, 1);
        vertex(consumer, mat, norm,  0, 1, 1+e,  1, 0,  0, 0, 1);
        vertex(consumer, mat, norm,  0, 0, 1+e,  1, 1,  0, 0, 1);

        // WEST (x = -e)
        vertex(consumer, mat, norm, -e, 0, 1,  0, 1, -1, 0, 0);
        vertex(consumer, mat, norm, -e, 1, 1,  0, 0, -1, 0, 0);
        vertex(consumer, mat, norm, -e, 1, 0,  1, 0, -1, 0, 0);
        vertex(consumer, mat, norm, -e, 0, 0,  1, 1, -1, 0, 0);

        // EAST (x = 1+e)
        vertex(consumer, mat, norm, 1+e, 0, 0,  0, 1,  1, 0, 0);
        vertex(consumer, mat, norm, 1+e, 1, 0,  0, 0,  1, 0, 0);
        vertex(consumer, mat, norm, 1+e, 1, 1,  1, 0,  1, 0, 0);
        vertex(consumer, mat, norm, 1+e, 0, 1,  1, 1,  1, 0, 0);

        // DOWN (y = -e)
        vertex(consumer, mat, norm,  0, -e, 1,  0, 1,  0, -1, 0);
        vertex(consumer, mat, norm,  0, -e, 0,  0, 0,  0, -1, 0);
        vertex(consumer, mat, norm,  1, -e, 0,  1, 0,  0, -1, 0);
        vertex(consumer, mat, norm,  1, -e, 1,  1, 1,  0, -1, 0);

        // UP (y = 1+e)
        vertex(consumer, mat, norm,  0, 1+e, 0,  0, 1,  0, 1, 0);
        vertex(consumer, mat, norm,  0, 1+e, 1,  0, 0,  0, 1, 0);
        vertex(consumer, mat, norm,  1, 1+e, 1,  1, 0,  0, 1, 0);
        vertex(consumer, mat, norm,  1, 1+e, 0,  1, 1,  0, 1, 0);

        poseStack.popPose();
    }

    private void vertex(VertexConsumer c, Matrix4f mat, Matrix3f norm,
                        float x, float y, float z, float u, float v,
                        float nx, float ny, float nz) {
        c.vertex(mat, x, y, z)
                .color(1f, 1f, 1f, 1f)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal(norm, nx, ny, nz)
                .endVertex();
    }

    @Override public boolean shouldRenderOffScreen(CoreRodBlockEntity e) { return true; }
    @Override public int getViewDistance() { return 64; }
}