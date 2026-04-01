package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zipirhavaci.block.entity.DoorShakeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;

public class DoorShakeRenderer implements BlockEntityRenderer<DoorShakeBlockEntity> {

    public DoorShakeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(DoorShakeBlockEntity be, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {

        BlockState state = be.getDoorStateBottom();
        if (state == null || state.isAir()) return;

        float angleDeg = be.getAngle(partialTick);


        if (state.getBlock() instanceof TrapDoorBlock) {
            renderTrapDoor(state, angleDeg, poseStack, bufferSource, packedLight, packedOverlay);
        } else if (state.getBlock() instanceof FenceGateBlock) {
            renderFenceGate(state, angleDeg, poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            renderDoor(be, state, angleDeg, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void renderDoor(DoorShakeBlockEntity be, BlockState bottomState, float angleDeg,
                            PoseStack poseStack, MultiBufferSource bufferSource,
                            int packedLight, int packedOverlay) {

        BlockState topState = getDoorTopState(bottomState);

        Direction facing = bottomState.hasProperty(DoorBlock.FACING)
                ? bottomState.getValue(DoorBlock.FACING)
                : Direction.NORTH;

        boolean isRight = bottomState.hasProperty(DoorBlock.HINGE)
                && bottomState.getValue(DoorBlock.HINGE)
                == net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;


        float hingeX = 0.0f;
        float hingeZ = 0.0f;

        switch (facing) {
            case NORTH -> { hingeX = isRight ? 1.0f : 0.0f; hingeZ = 1.0f; }
            case SOUTH -> { hingeX = isRight ? 0.0f : 1.0f; hingeZ = 0.0f; }
            case WEST  -> { hingeX = 1.0f; hingeZ = isRight ? 0.0f : 1.0f; }
            case EAST  -> { hingeX = 0.0f; hingeZ = isRight ? 1.0f : 0.0f; }
        }

        float angleRad = (float) Math.toRadians(angleDeg * 0.75f);
        float axisSign = switch (facing) {
            case NORTH, WEST -> -1f;
            default          ->  1f;
        };

        poseStack.pushPose();
        // Pivot noktasına git, dönüştür, geri dön
        poseStack.translate(hingeX, 0, hingeZ);
        poseStack.mulPose(new Quaternionf().rotateY(angleRad * axisSign));
        poseStack.translate(-hingeX, 0, -hingeZ);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                bottomState, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.translate(0, 1, 0);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                topState, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }

    private void renderTrapDoor(BlockState state, float angleDeg,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                int packedLight, int packedOverlay) {

        Direction facing = state.hasProperty(TrapDoorBlock.FACING)
                ? state.getValue(TrapDoorBlock.FACING)
                : Direction.NORTH;

        float pivotX = 0.5f;
        float pivotY = 0.5f;
        float pivotZ = 0.5f;

        switch (facing) {
            case NORTH -> pivotZ = 1.0f;
            case SOUTH -> pivotZ = 0.0f;
            case WEST  -> pivotX = 1.0f;
            case EAST  -> pivotX = 0.0f;
            default    -> pivotZ = 1.0f;
        }

        boolean isTop = state.hasProperty(TrapDoorBlock.HALF)
                && state.getValue(TrapDoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.Half.TOP;
        pivotY = isTop ? 1.0f : 0.0f;

        float angleRad = (float) Math.toRadians(angleDeg * 0.45f);
        boolean rotateOnX = (facing == Direction.NORTH || facing == Direction.SOUTH);

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);

        if (rotateOnX) {
            float sign = (facing == Direction.NORTH) ? -1f : 1f;
            poseStack.mulPose(new Quaternionf().rotateX(angleRad * sign));
        } else {
            float sign = (facing == Direction.EAST) ? -1f : 1f;
            poseStack.mulPose(new Quaternionf().rotateZ(angleRad * sign));
        }

        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }

    private void renderFenceGate(BlockState state, float angleDeg,
                                 PoseStack poseStack, MultiBufferSource bufferSource,
                                 int packedLight, int packedOverlay) {

        float t = Math.max(-1.0f, Math.min(1.0f, angleDeg / 12.0f));

        float offsetY = t * 0.02f;
        float offsetX = t * 0.005f;
        float offsetZ = t * 0.03f;
        float zAngleRad = (float) Math.toRadians(t * 1.0f);

        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.mulPose(new Quaternionf().rotateX(zAngleRad));
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state, poseStack, bufferSource, packedLight, packedOverlay);

        poseStack.popPose();
    }


    private BlockState getDoorTopState(BlockState bottomState) {
        if (bottomState.hasProperty(DoorBlock.HALF)) {
            return bottomState.setValue(DoorBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        }
        return bottomState;
    }

    @Override
    public boolean shouldRenderOffScreen(DoorShakeBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}