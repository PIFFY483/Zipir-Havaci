package com.zipirhavaci.client;

import com.zipirhavaci.core.AviatorConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnimationManager {

    public static boolean isSquashed = false;
    private static float animationTime = 0;
    private static int squashTimer = 0;
    private static float recoveryTimer = 0;

    // RENDER GÜVENLİĞİ: Pre ve Post arasında köprü kurar
    private static boolean wasPosePushed = false;

    private static final int SQUASH_DURATION = 80;
    //  interpolasyon sayesinde kare atlaması yapmaz
    private static final float RECOVERY_SPEED = 0.06f;

    public static void triggerSquash() {
        isSquashed = true;
        squashTimer = 0;
        recoveryTimer = 0;
    }

    public static void resetSquash() {
        isSquashed = false;
        squashTimer = 0;
        recoveryTimer = 1.0f;
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        float pt = event.getPartialTick();
        float displayY = 1.0f;
        float displayXZ = 1.0f;

        if (isSquashed) {
            float wobble = (float) Math.sin((animationTime + pt) * 0.6f) * 0.06f;
            displayY = AviatorConstants.SQUASH_Y + wobble;
            displayXZ = AviatorConstants.STRETCH_XZ - wobble;
        }
        else if (recoveryTimer > 0) {

            float smoothedTimer = recoveryTimer - (RECOVERY_SPEED * pt);
            if (smoothedTimer < 0) smoothedTimer = 0;

            float spring = (float) Math.sin((1.0f - smoothedTimer) * 12.0f) * smoothedTimer * 0.45f;
            displayY = 1.0f + spring;
            displayXZ = 1.0f - (smoothedTimer * 0.4f);
        }

        // --- GÜVENLİ RENDER BAŞLANGICI ---
        if (displayY != 1.0f) {
            event.getPoseStack().pushPose();
            event.getPoseStack().scale(displayXZ, displayY, displayXZ);
            wasPosePushed = true;
        } else {
            wasPosePushed = false;
        }
    }

    @SubscribeEvent
    public static void onPlayerRenderPost(RenderPlayerEvent.Post event) {
        if (wasPosePushed) {
            event.getPoseStack().popPose();
            wasPosePushed = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            animationTime += 1.0f;

            if (isSquashed) {
                squashTimer++;
                if (squashTimer >= SQUASH_DURATION) {
                    resetSquash();
                }
            }
            else if (recoveryTimer > 0) {
                recoveryTimer -= RECOVERY_SPEED;
                if (recoveryTimer < 0) recoveryTimer = 0;
            }
        }
    }
}