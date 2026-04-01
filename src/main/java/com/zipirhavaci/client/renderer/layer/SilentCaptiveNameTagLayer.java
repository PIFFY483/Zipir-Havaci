package com.zipirhavaci.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zipirhavaci.entity.SilentCaptiveEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;


@OnlyIn(Dist.CLIENT)
public class SilentCaptiveNameTagLayer extends RenderLayer<SilentCaptiveEntity,
        net.minecraft.client.model.PlayerModel<SilentCaptiveEntity>> {

    private static final String[] MESSAGES = {
            "§5I beseech thee... I beseech thee...",
            "§4Grant me thy aid!",
            "§5Leave me not in this void...",
            "§dI beg of thee, help me...",
    };

    private static final int CYCLE_TICKS = 60;

    public SilentCaptiveNameTagLayer(RenderLayerParent<SilentCaptiveEntity,
            net.minecraft.client.model.PlayerModel<SilentCaptiveEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, SilentCaptiveEntity entity,
                       float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {

        long tick = entity.level().getGameTime();
        int msgIndex = (int) ((tick / CYCLE_TICKS) % MESSAGES.length);
        String msg = MESSAGES[msgIndex];

        float bobOffset = Mth.sin((tick + partialTick) * 0.1f) * 0.04f;

        poseStack.pushPose();

        poseStack.translate(0.0, 2.1 + bobOffset, 0.0);

        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        float scale = 0.025f;
        poseStack.scale(-scale, -scale, scale);

        Font font = Minecraft.getInstance().font;
        Component text = Component.literal(msg);
        int textWidth = font.width(text);

        Matrix4f matrix = poseStack.last().pose();

        font.drawInBatch(text, -textWidth / 2.0f, 0, 0xFFFFFFFF,
                false, matrix, bufferSource, Font.DisplayMode.NORMAL,
                0x50000000, packedLight);

        poseStack.popPose();
    }
}