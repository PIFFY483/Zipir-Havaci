package com.zipirhavaci.physics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class VehiclePushHandler {

    private static final double SCAN_RADIUS = 5.0;
    private static final double MAX_SPEED   = 2.5;

    private VehiclePushHandler() {}

    public static void push(ServerLevel level, Player player, Vec3 look, double power) {
        Vec3 dir    = look.normalize();
        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
        AABB box    = player.getBoundingBox().inflate(SCAN_RADIUS);

        List<Entity> entities = level.getEntities(player, box);
        for (Entity entity : entities) {
            if (entity == player) continue;
            if (!isVehicleType(entity)) continue;

            Vec3   toEntity = entity.position().subtract(origin);
            double dist     = toEntity.length();
            if (dist < 0.1) continue;

            double dot = toEntity.normalize().dot(dir);
            if (dot < 0.15) continue;

            double distFactor = Math.max(0.3, 1.0 - (dist / SCAN_RADIUS) * 0.6);
            applyPush(entity, dir, power * distFactor);
        }
    }

    public static boolean tryPushVehicle(Entity entity, Vec3 direction, double power) {
        if (!isVehicleType(entity)) return false;
        applyPush(entity, direction, power);
        return true;
    }

    private static void applyPush(Entity entity, Vec3 dir, double power) {
        double mass  = getMass(entity);
        double accel = Math.min(power / mass, MAX_SPEED);

        Vec3 current = entity.getDeltaMovement();
        Vec3 impulse = new Vec3(
                dir.x * accel,
                Math.abs(dir.y) * accel * 0.5 + 0.08,
                dir.z * accel
        );

        Vec3   newVel = current.add(impulse);
        double speed  = newVel.length();
        if (speed > MAX_SPEED) newVel = newVel.scale(MAX_SPEED / speed);

        entity.setDeltaMovement(newVel);
        entity.hasImpulse = true;
    }

    private static boolean isVehicleType(Entity entity) {
        return entity instanceof AbstractMinecart
                || entity instanceof Boat
                || entity instanceof ArmorStand;
    }

    private static double getMass(Entity entity) {
        if (entity instanceof AbstractMinecart minecart) {
            return minecart.getPassengers().isEmpty() ? 1.0 : 2.2;
        }
        if (entity instanceof Boat boat) {
            return boat.getPassengers().isEmpty() ? 0.8 : 1.8;
        }
        if (entity instanceof ArmorStand) {
            return 0.6;
        }
        return 1.0;
    }
}