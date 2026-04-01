package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zipirhavaci.entity.FlyingDoorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.joml.Quaternionf;

public class FlyingDoorRenderer extends EntityRenderer<FlyingDoorEntity> {

    private final BlockRenderDispatcher blockRenderer;

    public FlyingDoorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = Minecraft.getInstance().getBlockRenderer();
        this.shadowRadius  = 0.5f;
    }

    @Override
    public void render(FlyingDoorEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        BlockState state = entity.getDoorBlockState();
        if (state == null || state.isAir()) return;

        float interpYaw   = entity.prevVisualYaw
                + (entity.visualYaw   - entity.prevVisualYaw)   * partialTick;
        float interpPitch = entity.prevVisualPitch
                + (entity.visualPitch - entity.prevVisualPitch) * partialTick;
        float interpRoll  = entity.prevVisualRoll
                + (entity.visualRoll  - entity.prevVisualRoll)  * partialTick;

        boolean isUpper = entity.isUpperHalf();

        if (state.getBlock() instanceof TrapDoorBlock) {
            renderTrapDoor(state, interpYaw, interpPitch, interpRoll,
                    poseStack, buffer, packedLight);
        } else if (state.getBlock() instanceof FenceGateBlock) {
            renderFenceGate(state, interpYaw, interpPitch, interpRoll,
                    poseStack, buffer, packedLight);
        } else {
            renderDoorHalf(state, isUpper, interpYaw, interpPitch, interpRoll,
                    poseStack, buffer, packedLight);
        }

        // Shadow (EntityRenderer base)
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    // =========================================================================
    // NORMAL KAPI (DoorBlock) — ALT VEYA ÜST YARI
    // =========================================================================

    private void renderDoorHalf(BlockState state, boolean isUpper,
                                float yaw, float pitch, float roll,
                                PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        net.minecraft.core.Direction facing = state.hasProperty(DoorBlock.FACING)
                ? state.getValue(DoorBlock.FACING)
                : net.minecraft.core.Direction.NORTH;

        boolean isRight = state.hasProperty(DoorBlock.HINGE)
                && state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;

        float hingeX, hingeZ;
        switch (facing) {
            case NORTH -> { hingeX = isRight ? 1.0f : 0.0f; hingeZ = 1.0f; }
            case SOUTH -> { hingeX = isRight ? 0.0f : 1.0f; hingeZ = 0.0f; }
            case WEST  -> { hingeX = 1.0f; hingeZ = isRight ? 0.0f : 1.0f; }
            case EAST  -> { hingeX = 0.0f; hingeZ = isRight ? 1.0f : 0.0f; }
            default    -> { hingeX = 0.0f; hingeZ = 1.0f; }
        }

        // Üst yarı için Y pivotu bloğun üst köşesinde (ters uçtan döner)
        float hingeY = isUpper ? 1.0f : 0.0f;

        float yawRad   = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch * 0.45f); // pitch küçük tutulur (aşırı görünmesin)
        float rollRad  = (float) Math.toRadians(roll  * 0.25f); // roll minimal

        float axisSign = switch (facing) {
            case NORTH, WEST -> -1f;
            default          ->  1f;
        };

        poseStack.pushPose();

        // Pivot noktasına git
        poseStack.translate(hingeX, hingeY, hingeZ);

        poseStack.mulPose(new Quaternionf().rotateY(yawRad * axisSign));
        poseStack.mulPose(new Quaternionf().rotateX(pitchRad));
        poseStack.mulPose(new Quaternionf().rotateZ(rollRad));

        // Pivot'tan geri dön
        poseStack.translate(-hingeX, -hingeY, -hingeZ);

        BlockState renderState;
        if (state.hasProperty(DoorBlock.HALF)) {
            renderState = state.setValue(DoorBlock.HALF,
                    isUpper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        } else {
            renderState = state;
        }

        blockRenderer.renderSingleBlock(renderState, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    // =========================================================================
    // TUZAK KAPISI (TrapDoorBlock)
    // =========================================================================

    private void renderTrapDoor(BlockState state, float yaw, float pitch, float roll,
                                PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        net.minecraft.core.Direction facing = state.hasProperty(TrapDoorBlock.FACING)
                ? state.getValue(TrapDoorBlock.FACING)
                : net.minecraft.core.Direction.NORTH;

        float pivotX = 0.5f;
        float pivotZ = 0.5f;

        switch (facing) {
            case NORTH -> pivotZ = 1.0f;
            case SOUTH -> pivotZ = 0.0f;
            case WEST  -> pivotX = 1.0f;
            case EAST  -> pivotX = 0.0f;
        }

        boolean isTop = state.hasProperty(TrapDoorBlock.HALF)
                && state.getValue(TrapDoorBlock.HALF)
                == net.minecraft.world.level.block.state.properties.Half.TOP;
        float pivotY = isTop ? 1.0f : 0.0f;

        float yawRad   = (float) Math.toRadians(yaw   * 0.8f);
        float pitchRad = (float) Math.toRadians(pitch  * 0.5f);
        float rollRad  = (float) Math.toRadians(roll   * 0.25f);

        boolean rotateOnX = (facing == net.minecraft.core.Direction.NORTH
                || facing == net.minecraft.core.Direction.SOUTH);
        float sign = (facing == net.minecraft.core.Direction.NORTH
                || facing == net.minecraft.core.Direction.WEST) ? -1f : 1f;

        poseStack.pushPose();
        poseStack.translate(pivotX, pivotY, pivotZ);

        if (rotateOnX) {
            poseStack.mulPose(new Quaternionf().rotateX(yawRad * sign));
        } else {
            poseStack.mulPose(new Quaternionf().rotateZ(yawRad * sign));
        }
        poseStack.mulPose(new Quaternionf().rotateY(pitchRad)); // ikincil dönüş
        poseStack.mulPose(new Quaternionf().rotateX(rollRad));  // minimal meyil

        poseStack.translate(-pivotX, -pivotY, -pivotZ);

        blockRenderer.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    // =========================================================================
    // ÇİT KAPISI (FenceGateBlock)
    // =========================================================================

    private void renderFenceGate(BlockState state, float yaw, float pitch, float roll,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        float yawRad   = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch * 0.5f);
        float rollRad  = (float) Math.toRadians(roll  * 0.3f);

        poseStack.pushPose();

        // Merkez pivot
        poseStack.translate(0.5f, 0.5f, 0.5f);

        poseStack.mulPose(new Quaternionf().rotateY(yawRad));
        poseStack.mulPose(new Quaternionf().rotateX(pitchRad));
        poseStack.mulPose(new Quaternionf().rotateZ(rollRad));

        poseStack.translate(-0.5f, -0.5f, -0.5f);

        blockRenderer.renderSingleBlock(state, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }


    @Override
    public ResourceLocation getTextureLocation(FlyingDoorEntity entity) {
        return new ResourceLocation("minecraft", "textures/block/oak_door_bottom.png");
    }

    @Override
    public boolean shouldRender(FlyingDoorEntity entity,
                                net.minecraft.client.renderer.culling.Frustum frustum,
                                double camX, double camY, double camZ) {
        return true;
    }
}