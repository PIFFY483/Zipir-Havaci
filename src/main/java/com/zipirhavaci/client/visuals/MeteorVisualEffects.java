package com.zipirhavaci.client.visuals;

import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.TimeUnit;


public class MeteorVisualEffects {

    public static boolean IMPACT_THIS_TICK = false;
    public static double LAST_RADIUS = 0;
    public static Vec3 LAST_NORMAL = Vec3.ZERO;

    public static void sendImpact(ServerPlayer sp, double radius, Vec3 normal) {
        sp.connection.send(new ClientboundLevelEventPacket(2001, sp.blockPosition(), 0, false));
        LAST_RADIUS = radius;
        LAST_NORMAL = normal;
        IMPACT_THIS_TICK = true;
    }

    public static void updateAmbience(Player p, int charge) {
        if (charge > 5 && !p.onGround()) {
            spawnTrailingSoulFlame(p, (float) (charge / 40.0));
            for (int i = 0; i < 2; i++) {
                double t = (p.tickCount % 20) / 20.0 * Math.PI * 2 + ((Math.PI * 2 / 3) * i);
                p.level().addParticle(ParticleTypes.SMOKE, p.getX() + Math.cos(t) * 0.4, p.getY() + 0.8, p.getZ() + Math.sin(t) * 0.4, 0, 0.02, 0);
                p.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX(), p.getY() + 0.5, p.getZ(), 0, 0.03, 0);


                if (charge >= 35) {
                    // Şarj arttıkça daha yoğun lav damlacıkları
                    if (p.getRandom().nextFloat() < (charge / 100.0f)) {
                        p.level().addParticle(ParticleTypes.LAVA,
                                p.getX() + (p.getRandom().nextDouble() - 0.5),
                                p.getY() + p.getRandom().nextDouble() * p.getBbHeight(),
                                p.getZ() + (p.getRandom().nextDouble() - 0.5),
                                0, 0, 0);
                    }
                }
            }
        }

        if (charge > 15 && !p.onGround() && p.tickCount % 5 == 0) {
            p.level().playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.9f, 0.5f);
            p.level().playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7f, 0.35f);
            p.level().playSound(p, p.getX(), p.getY(), p.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 0.65f, 0.6f);
        } else if (p.onGround()) {
            stopAllFallingSounds(p);
        }
    }

    public static void playSonicBoom(final Player p) {
        final double speed = p.getDeltaMovement().length();
        final Vec3 lookVec = p.getLookAngle().normalize();

        // Yön hesaplamaları
        Vec3 tempRight = new Vec3(-lookVec.z, 0, lookVec.x).normalize();
        if (tempRight.lengthSqr() < 0.01) tempRight = new Vec3(1, 0, 0);
        final Vec3 right = tempRight;
        final Vec3 up = lookVec.cross(right).normalize();

        for (int i = 0; i < 4; i++) {
            final int stage = i;
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                Minecraft.getInstance().execute(() -> {
                    if (p == null || p.level() == null) return;

                    // 1. SES
                    float thunderPitch = (float) (0.4f + (stage * 0.35f) + (speed * 0.2f));
                    p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5f, thunderPitch, false);

                    // 2. TERMAL AKKOR (Re-entry Heat)
                    for(int t=0; t<5; t++) {
                        p.level().addParticle(ParticleTypes.LAVA,
                                p.getX() + (p.getRandom().nextDouble()-0.5), p.getY() + p.getBbHeight()*0.5, p.getZ() + (p.getRandom().nextDouble()-0.5), 0, 0, 0);
                    }

                    // 3. VAKUM İZİ (Distortion Wake)
                    for(int v=0; v<3; v++) {
                        p.level().addParticle(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0);
                    }

                    // 4. HIZ ÇİZGİLERİ VE HALKALAR
                    spawnUltraRing(p, lookVec, right, up, (stage * 4.0f) + 2.0f, true);

                    // 5. FİNAL İNFERNO
                    if (stage == 3) {
                        // Tok sesler karması
                        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.5f, 0.5f, false);
                        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.4f, false);

                        p.level().addParticle(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0);
                        p.level().addParticle(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 0, 0, 0);

                        for (int f = 0; f < 120; f++) {
                            double angle = Math.random() * 2 * Math.PI;
                            double randR = 1.0 + Math.random() * 5.0;
                            Vec3 pPos = right.scale(Math.cos(angle) * randR).add(up.scale(Math.sin(angle) * randR));

                            // Koyu, yoğun dumanlar
                            p.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.getX() + pPos.x, p.getY() + 1 + pPos.y, p.getZ() + pPos.z, 0, 0.07, 0);

                            // Sürtünme alevleri
                            if (f % 2 == 0) {
                                p.level().addParticle(ParticleTypes.FLAME, p.getX() + pPos.x, p.getY() + 1 + pPos.y, p.getZ() + pPos.z, lookVec.x * -0.3, lookVec.y * -0.3, lookVec.z * -0.3);
                            }
                        }
                    }
                });
            }, i * 320, TimeUnit.MILLISECONDS);
        }
    }

    private static void spawnUltraRing(Player p, Vec3 look, Vec3 right, Vec3 up, float radius, boolean thick) {
        int points = thick ? 80 : 40; // Kalın halka için daha çok nokta
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Vec3 ringPos = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            double px = p.getX() + ringPos.x;
            double py = p.getY() + p.getBbHeight()/2 + ringPos.y;
            double pz = p.getZ() + ringPos.z;

            p.level().addParticle(ParticleTypes.CLOUD, px, py, pz, 0, 0.1, 0);
            if (thick) {
                p.level().addParticle(ParticleTypes.FIREWORK, px, py, pz, 0, 0, 0);
                p.level().addParticle(ParticleTypes.END_ROD, px, py, pz, 0, 0, 0); // Parlama efekti
            }
        }
    }

    public static void stopAllFallingSounds(Player p) {
        if (p.level().isClientSide) {
            Minecraft.getInstance().getSoundManager().stop(SoundEvents.GHAST_SHOOT.getLocation(), SoundSource.PLAYERS);
            Minecraft.getInstance().getSoundManager().stop(SoundEvents.ENDER_DRAGON_GROWL.getLocation(), SoundSource.PLAYERS);
            Minecraft.getInstance().getSoundManager().stop(SoundEvents.LAVA_AMBIENT.getLocation(), SoundSource.PLAYERS);
        }
    }

    private static void spawnTrailingSoulFlame(Player p, float size) {
        Vec3 rev = p.getDeltaMovement().reverse().scale(0.15);
        for (int i = 0; i < 360; i += 30) {
            double r = Math.toRadians(i);
            p.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.getX() + Math.cos(r) * size, p.getY() + 0.4, p.getZ() + Math.sin(r) * size, rev.x, 0.02, rev.z);
        }
    }


    public static ImpactTemplate calculateImpact(double speed, Vec3 normal) {
        float power = (float) Mth.clamp((speed - 0.5) / 2.5, 0.0, 1.0);
        return new ImpactTemplate(2.0 + (power * 4.0), Direction.getNearest(normal.x, normal.y, normal.z), power);
    }

    public record ImpactTemplate(double radius, Direction direction, float power) {}

    public static void handleClientImpact(Player p) {
        if (!IMPACT_THIS_TICK) return; //

        // 1. Hesaplamalar ve Hazırlık
        ImpactTemplate impact = calculateImpact(LAST_RADIUS > 0 ? (LAST_RADIUS - 2.0) * 2.5 / 4.0 + 0.5 : 1.0, LAST_NORMAL);
        Vec3 normal = LAST_NORMAL.lengthSqr() < 0.01 ? new Vec3(0, 1, 0) : LAST_NORMAL;
        Vec3 impactPoint = p.position().add(normal.scale(p.getBbWidth() / 2 + 0.15));
        Vec3 helper = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 tangent = normal.cross(helper).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();
        BlockState floor = p.level().getBlockState(p.blockPosition().relative(impact.direction().getOpposite()));

        // 2. Standart Efektler
        for (int i = 0; i < (int)(impact.radius() * 25); i++) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.random() * impact.radius();
            Vec3 spread = tangent.scale(Math.cos(angle) * dist).add(bitangent.scale(Math.sin(angle) * dist));
            Vec3 spawnPos = impactPoint.add(spread).add(normal.scale(0.05));

            if (Math.random() < 0.7) p.level().addParticle(ParticleTypes.FLAME, spawnPos.x, spawnPos.y, spawnPos.z, spread.x * 0.15, normal.y * 0.1, spread.z * 0.15);
            if (i % 3 == 0) p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, floor), spawnPos.x, spawnPos.y, spawnPos.z, normal.x * -0.5, normal.y * -0.45, normal.z * -0.5);
            p.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, spawnPos.x, spawnPos.y, spawnPos.z, spread.x * 0.1, normal.y * 0.05, spread.z * 0.1);
            if (i % 8 == 0) p.level().addParticle(ParticleTypes.LAVA, spawnPos.x, spawnPos.y, spawnPos.z, (Math.random()-0.5)*0.25, 0.15, (Math.random()-0.5)*0.25);
        }

        // --- YENİ: YERYÜZÜ DAĞILMA EFEKTİ (DEBRIS) ---
        BlockPos impactPos = p.blockPosition().below();
        double r = impact.radius();

        for (int x = (int)-r; x <= r; x++) {
            for (int z = (int)-r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > r) continue;

                BlockPos targetPos = impactPos.offset(x, 0, z);
                BlockState floorState = p.level().getBlockState(targetPos);

                if (floorState.isAir()) continue;

                for (int i = 0; i < 2; i++) {
                    p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, floorState),
                            targetPos.getX() + 0.5 + (x * 0.1),
                            targetPos.getY() + 1.1,
                            targetPos.getZ() + 0.5 + (z * 0.1),
                            0, 0, 0);
                }
            }
        }

        // 3. Ses ve Kilit Sıfırlama
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, (float)(1.5f + impact.radius()/2f), 0.5f, false);


        IMPACT_THIS_TICK = false;
    }
}