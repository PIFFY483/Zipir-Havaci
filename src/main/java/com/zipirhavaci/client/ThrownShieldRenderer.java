package com.zipirhavaci.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zipirhavaci.entity.ThrownShieldEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.util.Mth;

public class ThrownShieldRenderer extends ThrownItemRenderer<ThrownShieldEntity> {
    public ThrownShieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ThrownShieldEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        poseStack.scale(0.7f, 0.7f, 0.7f);

        int jitter = entity.getJitterTicks();
        if (jitter > 0) {
            // Şiddeti sürenin bitişine doğru azalt (16 tick'ten aşağı doğru)
            float intensity = (jitter / 16.0f) * 0.05f;

            // MatrixStack yerine doğrudan senin poseStack
            poseStack.translate(
                    (entity.level().random.nextFloat() - 0.5f) * intensity,
                    (entity.level().random.nextFloat() - 0.5f) * intensity,
                    (entity.level().random.nextFloat() - 0.5f) * intensity
            );
        }

        // 1. PÜRÜZSÜZ HAREKET (Interpolation)
        // Kalkanın dünya üzerindeki titremesini engellemek için offset.
        double xOffset = Mth.lerp(partialTicks, entity.xOld, entity.getX()) - entity.getX();
        double yOffset = Mth.lerp(partialTicks, entity.yOld, entity.getY()) - entity.getY();
        double zOffset = Mth.lerp(partialTicks, entity.zOld, entity.getZ()) - entity.getZ();
        poseStack.translate(xOffset, yOffset, zOffset);

        poseStack.scale(2.5F, 2.5F, 2.5F);

        // Dünyadaki temel yönü sabitle
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));

        float time = entity.tickCount + partialTicks; //
        float speedFactor = (float) entity.getDeltaMovement().length(); //

        if (!entity.isStuck()) {
            float transition = Math.max(0, 1.0f - (time / 20.0f));
            float stability = 1.0f - transition;

            // 1. KAOTİK DÖNÜŞ (Açılış)
            if (transition > 0) {
                poseStack.mulPose(Axis.YP.rotationDegrees(time * 40.0F * transition));
                poseStack.mulPose(Axis.XP.rotationDegrees(time * 30.0F * transition));
            }

            // 2. TESTERE MODU
            poseStack.mulPose(Axis.XP.rotationDegrees(90 * stability));

            // 3. MOMENTUM BAZLI TESTERE DÖNÜŞÜ (Z ekseni)
            float spinSpeed = 50.0F + speedFactor * 120.0F; // Hız çarpanını biraz artırdım
            poseStack.mulPose(Axis.ZP.rotationDegrees(time * spinSpeed));

            // 4. YALPALAMA (Orbit)

            float orbitRadius = 12.0F * (speedFactor + 0.1f);
            poseStack.mulPose(Axis.XP.rotationDegrees((float) Math.cos(time * 0.15F) * orbitRadius));
            poseStack.mulPose(Axis.YP.rotationDegrees((float) Math.sin(time * 0.15F) * orbitRadius));
        }else {
            // Saplandığında: Yatay duruşu koru ve 45 derece eğik çakıl
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.mulPose(Axis.ZP.rotationDegrees(45.0F));
        }

        // Render işlemi
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                entity.getItem(),
                net.minecraft.world.item.ItemDisplayContext.FIXED,
                packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();


    }
}