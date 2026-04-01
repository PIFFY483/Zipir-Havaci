package com.zipirhavaci.physics;

import com.zipirhavaci.entity.BlastItemEntity;
import com.zipirhavaci.entity.BlastTntEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;


public class ItemBlastHandler {

    private static final double MAX_SPEED       = 2.2;
    private static final double SCAN_RADIUS     = 4.5;
    private static final double CYLINDER_DOT    = 0.70;
    private static final double CYLINDER_RADIUS = 1.8;
    private static final double SCAN_RADIUS_SQR     = SCAN_RADIUS * SCAN_RADIUS;
    private static final double CYLINDER_RADIUS_SQR = CYLINDER_RADIUS * CYLINDER_RADIUS;
    private static final double INV_CYLINDER_RADIUS = 1.0 / CYLINDER_RADIUS;
    private static final Long2ObjectMap<Vec3> BLAST_FLAG_CACHE = new Long2ObjectOpenHashMap<>();
    private static final float[] DIST_LOOKUP = new float[46];

    static {
        for (int i = 0; i <= 45; i++) {
            double d = i / 10.0;
            // Karekök hassasiyetini tabloya statik olarak göm
            DIST_LOOKUP[i] = (float) Math.max(0.25, 1.0 - (d / SCAN_RADIUS) * 0.6);
        }
    }

    public static long getFlagId(float yaw, float pitch) {
        long y = Math.round(yaw / 2.0f);
        long p = Math.round(pitch / 2.0f);
        return (y << 32) | (p & 0xFFFFFFFFL);
    }


    public static void blast(ServerPlayer player, ServerLevel level, Vec3 look, double power) {
        //  Flag ID ve Odak Kontrolü
        float playerYaw = player.getYRot();
        float playerPitch = player.getXRot();
        long flagId = getFlagId(playerYaw, playerPitch);

        // Bakış yönünü bir kez hazırla veya Cacheden çek (120 derece odaklı)
        Vec3 dir = BLAST_FLAG_CACHE.computeIfAbsent(flagId, id ->
                new Vec3(look.x, Math.max(look.y, -0.2), look.z).normalize()
        );

        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);
        AABB scanBox = player.getBoundingBox().inflate(SCAN_RADIUS);

        blastTnt(player, level, look, power, scanBox);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, scanBox);
        if (items.isEmpty()) return;

        for (ItemEntity item : items) {
            if (item instanceof BlastItemEntity) continue;

            Vec3 toItem = item.position().subtract(origin);
            double distSq = toItem.lengthSqr();

            // Karesel Mesafe Kontrolü (Hızlı Eleme)
            if (distSq > SCAN_RADIUS_SQR || distSq < 0.01) continue;

            double dist = Math.sqrt(distSq);
            double invDist = 1.0 / dist;
            Vec3 toNorm = toItem.scale(invDist);

            double dot = toNorm.dot(dir);
            if (dot < 0.05) continue;

            // Silindirik projeksiyon
            double dotToItem = toItem.dot(dir);
            Vec3 axial = dir.scale(dotToItem);
            Vec3 lateral = toItem.subtract(axial);
            double latDistSq = lateral.lengthSqr();


            if (latDistSq > CYLINDER_RADIUS_SQR * 2.25) continue;

            Vec3 blastDir;
            double factor;
            double latDist = Math.sqrt(latDistSq);

            // Hibrit Açı Mantığı (Dinamik/Önbellek Geçişi)
            if (latDist <= CYLINDER_RADIUS && dot >= CYLINDER_DOT) {
                blastDir = dir;
                factor = 1.0;
            } else {
                // Kenar 60 derecelik alan
                double outStr = Math.min(1.0, latDist * INV_CYLINDER_RADIUS) * 0.6;
                Vec3 outward = latDist > 0.01 ? lateral.scale(1.0 / latDist).scale(outStr) : Vec3.ZERO;
                blastDir = dir.scale(1.0 - outStr * 0.5).add(outward).normalize();
                factor = Math.max(0.2, dot) * (1.0 - latDist * (INV_CYLINDER_RADIUS / 1.5));
            }

            // Birim Mesafe Tablosu (Karekök yerine )
            int distIndex = Math.min(45, (int)(dist * 10));
            float distFactor = DIST_LOOKUP[distIndex];

            double spd = power * factor * distFactor * MAX_SPEED;

            // Hız vektörü oluşturma
            Vec3 vel = new Vec3(
                    blastDir.x * spd,
                    Math.abs(blastDir.y) * spd + 0.25 * power * factor + 0.15,
                    blastDir.z * spd
            );

            // Hız limiti (VMAX karesel kontrol)
            double vLenSq = vel.lengthSqr();
            if (vLenSq > MAX_SPEED * MAX_SPEED) {
                vel = vel.scale(MAX_SPEED / Math.sqrt(vLenSq));
            }

            // Nesne Dönüşümü
            ItemStack stack = item.getItem().copy();
            item.discard();

            BlastItemEntity blast = new BlastItemEntity(
                    level,
                    item.getX(), item.getY(), item.getZ(),
                    stack, vel);
            level.addFreshEntity(blast);
        }
    }



    // ===== TNT FIRLATMA =====

    private static void blastTnt(ServerPlayer player, ServerLevel level, Vec3 look, double power, AABB scanBox) {
        Vec3 dir = new Vec3(look.x, Math.max(look.y, -0.2), look.z).normalize();
        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);

        List<PrimedTnt> tnts = level.getEntitiesOfClass(PrimedTnt.class, scanBox);
        if (tnts.isEmpty()) return;

        for (PrimedTnt tnt : tnts) {

            Vec3 toTnt = tnt.position().subtract(origin);
            double dist = toTnt.length();
            if (dist < 0.1) continue;

            Vec3 toNorm = toTnt.normalize();
            double dot = toNorm.dot(dir);
            if (dot < 0.05) continue;

            Vec3 axial     = dir.scale(toTnt.dot(dir));
            Vec3 lateral   = toTnt.subtract(axial);
            double latDist = lateral.length();
            if (latDist > CYLINDER_RADIUS * 1.5) continue;

            Vec3 blastDir;
            double factor;
            if (latDist <= CYLINDER_RADIUS && dot >= CYLINDER_DOT) {
                blastDir = dir;
                factor   = 1.0;
            } else {
                double outStr = Math.min(1.0, latDist / CYLINDER_RADIUS) * 0.6;
                Vec3 outward  = latDist > 0.01 ? lateral.normalize().scale(outStr) : Vec3.ZERO;
                blastDir = dir.scale(1.0 - outStr * 0.5).add(outward).normalize();
                factor   = Math.max(0.2, dot) * (1.0 - latDist / (CYLINDER_RADIUS * 1.5));
            }

            double distFactor = Math.max(0.25, 1.0 - (dist / SCAN_RADIUS) * 0.6);
            double spd = power * factor * distFactor * MAX_SPEED;

            Vec3 vel = new Vec3(
                    blastDir.x * spd,
                    blastDir.y * spd + 0.08 * power * factor,
                    blastDir.z * spd
            );
            double vLen = vel.length();
            if (vLen > MAX_SPEED) vel = vel.scale(MAX_SPEED / vLen);

            // ── Vanilla PrimedTnt → BlastTntEntity ile değiştir ──
            BlastTntEntity blastTnt = BlastTntEntity.fromPrimedTnt(tnt, vel);
            tnt.discard();
            level.addFreshEntity(blastTnt);
        }
    }

}