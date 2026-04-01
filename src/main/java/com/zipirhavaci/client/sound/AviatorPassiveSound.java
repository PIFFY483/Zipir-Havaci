package com.zipirhavaci.client.sound;

import com.zipirhavaci.core.SoundRegistry;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

public class AviatorPassiveSound extends AbstractTickableSoundInstance {

    private final Player player;

    public AviatorPassiveSound(Player player) {
        super(SoundRegistry.AVIATOR_IDLE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.6f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        if (player == null || !player.isAlive() || player.isRemoved()) {
            stop();
            return;
        }

        this.x = (float) player.getX();
        this.y = (float) player.getY();
        this.z = (float) player.getZ();
    }

    public void stopSound() {
        this.stop();
    }
}

