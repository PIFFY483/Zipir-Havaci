package com.zipirhavaci.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;

public class AviatorFollowSound extends AbstractTickableSoundInstance {
    private final Player player;
    private final boolean limitDuration;
    private int ticksExisted = 0;

    // --- [BAĞLANTI 1]: (3 Argüman) ---
    public AviatorFollowSound(Player player, SoundEvent sound, float pitch) {
        this(player, sound, pitch, false); // Varsayılan olarak süre sınırı yok
    }

    // --- [BAĞLANTI 2]: Overload/Arıza sesi için (4 Argüman) ---
    public AviatorFollowSound(Player player, SoundEvent sound, float pitch, boolean limitDuration) {
        super(sound, SoundSource.PLAYERS, RandomSource.create());
        this.player = player;
        this.pitch = pitch;
        this.limitDuration = limitDuration;
        this.volume = 1.0F;
        this.looping = false;
        this.delay = 0;
        this.x = (float) player.getX();
        this.y = (float) player.getY();
        this.z = (float) player.getZ();
    }

    @Override
    public void tick() {
        if (this.player.isRemoved()) {
            this.stop();
            return;
        }

        this.x = (float) player.getX();
        this.y = (float) player.getY();
        this.z = (float) player.getZ();

        if (limitDuration) {
            ticksExisted++;

            // --- FADE-OUT SİSTEMİ ---
            if (ticksExisted > 30) {

                float fadeProgress = (float) (40 - ticksExisted) / 10.0F;
                this.volume = Math.max(0.0F, fadeProgress);
            }

            // 40. tick'te (2.0 sn) tamamen durdur
            if (ticksExisted >= 40) {
                this.stop();
            }
        }
    }
}