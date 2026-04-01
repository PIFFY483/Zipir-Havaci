package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zipirhavaci.block.HeavyPistonBlock;
import com.zipirhavaci.block.entity.FakeMovingBlockEntity;
import com.zipirhavaci.block.entity.HeavyPistonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class FakeMovingBlockRenderer implements BlockEntityRenderer<FakeMovingBlockEntity> {
    private final BlockEntityRendererProvider.Context context;

    public FakeMovingBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(FakeMovingBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        BlockState movedState = be.getMovedState();
        if (movedState == null || movedState.isAir()) return;

        // 1. Hareket Ofsetlerini Hesapla
        float progress = be.getProgressFraction(partialTick);
        Direction dir = be.getDirection();

        float offsetX = dir.getStepX() * progress;
        float offsetY = dir.getStepY() * progress;
        float offsetZ = dir.getStepZ() * progress;

        poseStack.pushPose();
        poseStack.translate(offsetX, offsetY, offsetZ);

        // 2. Çizim Mantığı: Piston mu yoksa Normal Blok mu?
        if (movedState.getBlock() instanceof HeavyPistonBlock) {
            renderPistonSnapshot(be, poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            // Standart Blok Render
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    movedState,
                    poseStack,
                    bufferSource,
                    packedLight,
                    packedOverlay
            );
        }

        poseStack.popPose();
    }

    private void renderPistonSnapshot(FakeMovingBlockEntity be, PoseStack poseStack,
                                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        float progress = be.getProgressFraction(Minecraft.getInstance().getFrameTime());

        // Gerçek HeavyPistonBlockEntity'yi dünyadan bul — GeckoLib cache'i onun üzerinde
        net.minecraft.world.level.Level level = be.getLevel();
        BlockPos pistonTargetPos = be.getTargetPos();


        HeavyPistonBlockEntity cached = getCachedPiston(be);
        cached.setLevel(level);

        float animTime = be.getAnimationTick() + Minecraft.getInstance().getFrameTime();

        BlockEntityRenderer<? super HeavyPistonBlockEntity> pistonRenderer =
                context.getBlockEntityRenderDispatcher().getRenderer(cached);

        if (pistonRenderer != null) {
            pistonRenderer.render(cached, animTime, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    // Her FakeMovingBlockEntity için sabit bir HeavyPistonBlockEntity tut
    // Böylece GeckoLib animasyon cache'i sıfırlanmaz
    private final java.util.WeakHashMap<FakeMovingBlockEntity, HeavyPistonBlockEntity> pistonCache = new java.util.WeakHashMap<>();

    private HeavyPistonBlockEntity getCachedPiston(FakeMovingBlockEntity be) {
        return pistonCache.computeIfAbsent(be, key -> {
            net.minecraft.world.level.block.state.BlockState targetState = key.getMovedState();
            if (targetState.hasProperty(HeavyPistonBlock.EXTENDED)) {
                targetState = targetState.setValue(HeavyPistonBlock.EXTENDED, key.isForceExtendAnimation());
            }
            HeavyPistonBlockEntity temp = new HeavyPistonBlockEntity(key.getBlockPos(), targetState);
            if (key.getMovedNbt() != null) temp.load(key.getMovedNbt());
            temp.markNotJustPlaced();
            return temp;
        });
    }

    @Override
    public boolean shouldRenderOffScreen(FakeMovingBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}