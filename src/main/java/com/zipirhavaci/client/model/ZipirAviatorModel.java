package com.zipirhavaci.client.model;

import com.zipirhavaci.item.ZipirAviatorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class ZipirAviatorModel extends GeoModel<ZipirAviatorItem> {
    @Override
    public ResourceLocation getModelResource(ZipirAviatorItem animatable) {
        return new ResourceLocation("zipirhavaci", "geo/zipir_aviator_gauntlet.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ZipirAviatorItem animatable) {
        return new ResourceLocation("zipirhavaci", "textures/item/zipir.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ZipirAviatorItem animatable) {
        return new ResourceLocation("zipirhavaci", "animations/zipir_aviator_gauntlet.animation.json");
    }


    @Override
    public void setCustomAnimations(ZipirAviatorItem animatable, long instanceId, AnimationState<ZipirAviatorItem> animationState) {
        GeoBone needle = (GeoBone) getAnimationProcessor().getBone("needle");

        if (needle != null) {
            ItemStack stack = animationState.getData(DataTickets.ITEMSTACK);
            if (stack != null && stack.hasTag()) {
                int uses = stack.getTag().getInt("Uses");
                float targetAngle = (float) Math.toRadians(uses * 72f);

                needle.setRotX(needle.getRotX() + targetAngle);
            }
        }
    }
}