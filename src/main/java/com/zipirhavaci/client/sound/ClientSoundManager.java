package com.zipirhavaci.client.sound;

import com.zipirhavaci.core.SoundRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class ClientSoundManager {

    private static AviatorLoopSound current;
    private static boolean restartQueued = false;
    private static LocalPlayer queuedPlayer;

    public static void startIdle(LocalPlayer player, float phase) {
        if (player == null) return;

        stopIdle();

        int delayTicks = (int)((1f - phase) * 80f);
        current = new AviatorLoopSound(player, delayTicks);

        Minecraft.getInstance().getSoundManager().play(current);
    }

    public static void stopIdle() {
        if (current != null) {
            current.kill();
            current = null;
        }
    }

    // Bu her client tick çağrılmalı (ClientTickEvent)
    public static void tick() {
        if (restartQueued && queuedPlayer != null) {
            restartQueued = false;
            startIdle(queuedPlayer, 0f);
        }
    }

    // ================= SAFE LOOP =================

    private static class AviatorLoopSound extends AbstractTickableSoundInstance {

        private final LocalPlayer player;
        private int life = 0;

        protected AviatorLoopSound(LocalPlayer player, int delayTicks) {
            super(SoundRegistry.AVIATOR_IDLE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = false;
            this.delay = Math.max(delayTicks, 0);
            this.volume = 0.6f;
            this.pitch = 1.0f;
        }

        @Override
        public void tick() {
            if (net.minecraft.client.Minecraft.getInstance().isPaused()) {
                this.volume = 0.0f;
                return;
            } else {
                this.volume = 0.6f;
            }

            if (player == null || player.isRemoved() || !player.isAlive()) {
                kill();
                return;
            }

            this.x = (float) player.getX();
            this.y = (float) player.getY();
            this.z = (float) player.getZ();

            life++; // Sadece oyun akarken ilerler

            if (life >= 80) {
                life = 0;
                ClientSoundManager.restartQueued = true;
                ClientSoundManager.queuedPlayer = player;
                kill();
            }
        }

        public void kill() {
            this.stop();
        }
    }
}
