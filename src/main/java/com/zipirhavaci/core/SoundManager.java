package com.zipirhavaci.core;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SoundManager {
    public static void playLaunchSound(Level level, Vec3 pos) {

        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.6F);


        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.8F, 1.2F);


        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    public static void playImpactSound(Level level, Vec3 pos) {
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 2.0F);
    }
}