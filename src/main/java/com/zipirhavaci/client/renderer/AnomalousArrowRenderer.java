package com.zipirhavaci.client.renderer;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;

public class AnomalousArrowRenderer<T extends AbstractArrow> extends ArrowRenderer<T> {
    private final ResourceLocation texture;

    public AnomalousArrowRenderer(EntityRendererProvider.Context context, String textureName) {
        super(context);
        this.texture = new ResourceLocation("zipirhavaci", "textures/entity/projectiles/" + textureName + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return this.texture;
    }
}