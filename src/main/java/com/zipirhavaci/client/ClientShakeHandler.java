package com.zipirhavaci.client;

import net.minecraft.world.entity.player.Player;
import java.util.Random;

public class ClientShakeHandler {
    private static int remainingTicks = 0;
    private static float currentIntensity = 0;
    private static final Random RANDOM = new Random();

    public static void startShake(int ticks, float intensity) {
        remainingTicks = ticks;
        currentIntensity = intensity;
    }

    // Sarsıntı değerleri dışa
    public static float getShakeAmount() {
        return (RANDOM.nextFloat() - 0.5f) * currentIntensity;
    }

    public static boolean isShaking() {
        return remainingTicks > 0;
    }

    public static void clientTick(Player player) {
        if (remainingTicks > 0) {
            currentIntensity *= 0.9f;
            remainingTicks--;
        } else {
            currentIntensity = 0;
        }
    }
}