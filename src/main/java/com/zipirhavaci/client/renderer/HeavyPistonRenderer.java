package com.zipirhavaci.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zipirhavaci.block.HeavyPistonBlock;
import com.zipirhavaci.block.entity.HeavyPistonBlockEntity;
import com.zipirhavaci.core.ZipirHavaci;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class HeavyPistonRenderer extends GeoBlockRenderer<HeavyPistonBlockEntity> {


    private static final ResourceLocation EMPTY_TEXTURE = new ResourceLocation("minecraft", "textures/colormap/foliage.png");
    private static final ResourceLocation GLOW_TEXTURE = new ResourceLocation(ZipirHavaci.MOD_ID, "textures/block/heavy_piston_glow.png");

    public HeavyPistonRenderer(BlockEntityRendererProvider.Context context) {
        super(new DefaultedBlockGeoModel<>(new ResourceLocation(ZipirHavaci.MOD_ID, "heavy_piston")));

        this.addRenderLayer(new AutoGlowingGeoLayer<HeavyPistonBlockEntity>(this) {
            @Override
            public ResourceLocation getTextureResource(HeavyPistonBlockEntity animatable) {
                return GLOW_TEXTURE;
            }

            @Override
            public RenderType getRenderType(HeavyPistonBlockEntity animatable) {

                if (!animatable.isStickyMode()) {

                    return RenderType.armorEntityGlint(); // Bu mod boş dokuda hiçbir şey çizmez
                }

                return RenderType.eyes(getTextureResource(animatable));
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(HeavyPistonBlockEntity animatable) {
        return new ResourceLocation(ZipirHavaci.MOD_ID, "textures/block/heavy_piston_texture.png");
    }




    @Override
    protected Direction getFacing(HeavyPistonBlockEntity block) {
        return block.getBlockState().getValue(HeavyPistonBlock.FACING);
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case NORTH -> {}
            case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180));
            case EAST  -> poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            case WEST  -> poseStack.mulPose(Axis.YP.rotationDegrees(90));
            case UP -> {
                poseStack.translate(0, 0.0625, 0.4375);
                poseStack.mulPose(Axis.XP.rotationDegrees(90));
                poseStack.translate(0, -0.9375, -0.4375);
            }
            case DOWN -> {
                poseStack.translate(0, 0, 1.4375 - 0.9375);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));
                poseStack.translate(0, 0, -0.4375 + 0.9375);
            }
        }
    }
}