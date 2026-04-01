package com.zipirhavaci.client.visuals;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class GauntletVisuals {


    public static void spawnFireRing(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        Vec3 lookVec = player.getLookAngle();
        Vec3 origin = player.getEyePosition().add(lookVec.scale(0.8));
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = lookVec.cross(up).normalize();
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
        Vec3 top = right.cross(lookVec).normalize();

        for (int i = 0; i < 16; i++) {
            double angle = 2 * Math.PI * i / 16;
            Vec3 offset = right.scale(Math.cos(angle) * 1.2).add(top.scale(Math.sin(angle) * 1.2));
            serverLevel.sendParticles(ParticleTypes.CLOUD, origin.x + offset.x, origin.y + offset.y, origin.z + offset.z, 1, 0, 0, 0, 0.01);
        }
        serverLevel.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
    }


    public static void spawnOverloadEffect(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        Vec3 look = player.getLookAngle();
        Vec3 velocity = player.getDeltaMovement();

        double forward = 1.8;
        double side = 0.5;
        double vertical = -0.4;

        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);

        Vec3 spawnPos = player.getEyePosition()
                .add(velocity.scale(1.15))
                .add(look.scale(forward))
                .add(right.scale(side))
                .add(new Vec3(0, vertical, 0));

        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                spawnPos.x, spawnPos.y, spawnPos.z,
                2, 0.05, 0.05, 0.05, 0.01);

        serverLevel.sendParticles(ParticleTypes.ASH,
                spawnPos.x, spawnPos.y, spawnPos.z,
                4, 0.1, 0.1, 0.1, 0.01);
    }
}