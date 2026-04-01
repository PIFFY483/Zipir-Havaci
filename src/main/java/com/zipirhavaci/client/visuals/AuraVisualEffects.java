package com.zipirhavaci.client.visuals;

import com.zipirhavaci.core.capability.StaticProgressionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Random;

public class AuraVisualEffects {
    private static final Random RANDOM = new Random();
    private static int coreTickCounter = 0;
    private static int colorTick = 0;

    public static float screenShakeX = 0f;
    public static float screenShakeY = 0f;
    private static int shakeTicksLeft = 0;
    private static float shakeIntensity = 0f;

    // PATLAMA EFEKTLERİ
    private static boolean burstActive = false;
    private static int burstLayer = 0;
    private static float burstLevel = 0f;
    private static double burstX, burstY, burstZ;
    private static final int BURST_LAYERS = 8;

    private static boolean shieldBreakActive = false;
    private static int shieldBreakLayer = 0;
    private static double shieldBreakX, shieldBreakY, shieldBreakZ;
    private static float shieldBreakLevel = 0f;
    private static final int SHIELD_BREAK_LAYERS = 5;

    public static void tickPlayerAura() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        colorTick++;
        tickScreenShake();

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (data.isAuraActive()) {
                spawnAuraParticles(player, level, data.getAuraLevel());

                coreTickCounter++;
                if (coreTickCounter >= 2) {
                    coreTickCounter = 0;
                    spawnCoreGlow(player, level, data.getAuraLevel(), data.getAuraTicksLeft());
                }
            }
        });

        // KARANLIK AURA: Phase 2
        tickDarkAuraRing(player, level);

        // KARANLIK AURA: Gündüz duman efekti
        com.zipirhavaci.core.physics.DarkAuraHandler.tickCursedSmoke(player);

        if (burstActive)       tickHemisphereBurst(level);
        if (shieldBreakActive) tickShieldBreakBurst(level);
    }

    // --- YENİ: KARAKTERE YAPIŞIK VE DARALAN IŞIMA ---
    private static void spawnCoreGlow(Player player, Level level, float lvl, int ticksLeft) {
        Vec3 motion = player.getDeltaMovement();
        double baseRadius = (lvl >= 3.0f) ? 1.2 : 0.8;

        // Duration değerleri: 0.5=360, 1.0=480, 2.0=520, 3.0=600
        float maxTicks;
        if (lvl >= 3.0f) maxTicks = 600f;
        else if (lvl >= 2.0f) maxTicks = 520f;
        else if (lvl >= 1.0f) maxTicks = 480f;
        else maxTicks = 360f;

        // AGRESİF DARALMA: %100'den %5'e - Diğer sistemlerle TAM SENKRON
        float ratio = Math.max(0.05f, (float) ticksLeft / maxTicks);
        double dynamicRadius = baseRadius * ratio;

        Vector3f color = getAreaRingColor(lvl, ratio);
        DustParticleOptions dust = new DustParticleOptions(color, 0.7f * ratio);

        for (int i = 0; i < 2; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double ox = Math.cos(angle) * dynamicRadius;
            double oz = Math.sin(angle) * dynamicRadius;
            double oy = RANDOM.nextDouble() * 2.0;

            level.addParticle(dust, player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                    motion.x, motion.y, motion.z);
        }
    }

    public static void spawnShockwaveBurst(Player player, float lvl) {
        burstActive = true;
        burstLayer  = 0;
        burstLevel  = lvl;
        burstX = player.getX(); burstY = player.getY(); burstZ = player.getZ();
        startScreenShake(lvl >= 3.0f ? 0.9f : 0.5f, lvl >= 3.0f ? 10 : 6);
    }

    public static void spawnShieldBreakBurst(Player target, float lvl) {
        shieldBreakActive = true;
        shieldBreakLayer  = 0;
        shieldBreakLevel  = lvl;
        shieldBreakX = target.getX(); shieldBreakY = target.getY(); shieldBreakZ = target.getZ();
        startScreenShake(0.35f, 4);
    }

    public static void startScreenShake(float intensity, int durationTicks) {
        shakeIntensity = intensity;
        shakeTicksLeft = durationTicks;
    }

    private static void tickScreenShake() {
        if (shakeTicksLeft > 0) {
            shakeTicksLeft--;
            float decay = (float) shakeTicksLeft / 10f;
            screenShakeX = (RANDOM.nextFloat() - 0.5f) * shakeIntensity * decay;
            screenShakeY = (RANDOM.nextFloat() - 0.5f) * shakeIntensity * decay;
        } else {
            screenShakeX = 0f; screenShakeY = 0f;
        }
    }

    private static void tickHemisphereBurst(Level level) {
        float t      = (float) burstLayer / (float)(BURST_LAYERS - 1);
        double lat   = t * (Math.PI / 2.0);
        double maxR  = (burstLevel >= 3.0f) ? 8.0 : 4.0;
        double ringR = maxR * Math.cos(lat);
        double ringY = burstY + maxR * Math.sin(lat);
        int points   = Math.max(8, (int)(24 * Math.cos(lat)));

        for (int i = 0; i < points; i++) {
            double angle = (2.0 * Math.PI / points) * i;
            double px = burstX + Math.cos(angle) * ringR;
            double pz = burstZ + Math.sin(angle) * ringR;
            spawnBurstParticle(level, burstLevel, px, ringY, pz, 0, 0.05, 0, burstLayer, t);
        }

        burstLayer++;
        if (burstLayer >= BURST_LAYERS) { burstActive = false; burstLayer = 0; }
    }

    private static void tickShieldBreakBurst(Level level) {
        float t      = (float) shieldBreakLayer / (float)(SHIELD_BREAK_LAYERS - 1);
        double lat   = t * (Math.PI / 2.0);
        double maxR  = (shieldBreakLevel >= 3.0f) ? 4.5 : 2.0;
        double ringR = maxR * Math.cos(lat);
        double ringY = shieldBreakY + maxR * Math.sin(lat);
        int points   = Math.max(5, (int)(14 * Math.cos(lat)));

        for (int i = 0; i < points; i++) {
            double angle = (2.0 * Math.PI / points) * i;
            double px = shieldBreakX + Math.cos(angle) * ringR;
            double pz = shieldBreakZ + Math.sin(angle) * ringR;
            level.addParticle(ParticleTypes.ENCHANTED_HIT, px, ringY, pz, 0, 0.02, 0);
        }

        shieldBreakLayer++;
        if (shieldBreakLayer >= SHIELD_BREAK_LAYERS) { shieldBreakActive = false; shieldBreakLayer = 0; }
    }

    private static void spawnBurstParticle(Level level, float lvl, double x, double y, double z, double vx, double vy, double vz, int layer, float t) {
        if (lvl >= 3.0f) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
            if (layer % 2 == 0) level.addParticle(ParticleTypes.DRAGON_BREATH, x, y, z, vx, vy, vz);
        } else if (lvl >= 2.0f) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, vx, vy, vz);
        } else {
            level.addParticle(ParticleTypes.FLAME, x, y, z, vx, vy, vz);
        }
    }

    private static void spawnAuraParticles(Player player, Level level, float lvl) {
        double x = player.getX(), y = player.getY() + 0.1, z = player.getZ();
        Vec3 motion = player.getDeltaMovement();
        int count = (lvl >= 3.0f) ? 2 : 1;
        for (int i = 0; i < count; i++) {
            double ox = (RANDOM.nextDouble() - 0.5) * 0.5;
            double oz = (RANDOM.nextDouble() - 0.5) * 0.5;
            double oy = RANDOM.nextDouble() * 2.0;
            if (lvl >= 3.0f) level.addParticle(ParticleTypes.END_ROD, x+ox, y+oy, z+oz, motion.x, motion.y + 0.05, motion.z);
            else level.addParticle(ParticleTypes.ELECTRIC_SPARK, x+ox, y+oy, z+oz, motion.x, motion.y, motion.z);
        }
    }


    private static Vector3f getAreaRingColor(float lvl, float ratio) {
        float fade = 0.4f + ratio * 0.6f;
        if (lvl >= 3.0f) return new Vector3f(0.7f * fade, 0.3f * fade, 1.0f);
        if (lvl >= 2.0f) return new Vector3f(1.0f, 0.5f * ratio, 0f);
        return new Vector3f(0.2f, 0.7f * ratio, 1.0f);
    }
    // ─── KARANLIK AURA PHASE 2 ÇEMBERİ (CLIENT) ─────────────────────────────

    private static float phase2RingAngle = 0f;

    private static void tickDarkAuraRing(Player player, Level level) {
        player.getCapability(com.zipirhavaci.core.capability.StaticProgressionProvider.STATIC_PROGRESSION)
                .ifPresent(data -> {
                    if (!data.isDarkAuraPhase2()) return;

                    float lvl    = data.getDarkAuraLevel();
                    double radius = com.zipirhavaci.core.physics.DarkAuraHandler.getPhase2Radius(lvl);

                    // Her tick açıyı ilerlet
                    phase2RingAngle = (phase2RingAngle + 4.0f) % 360f;

                    int points = (int)(radius * 6);
                    float angleStep = 360f / points;

                    // Çift halka
                    for (int ring = 0; ring < 2; ring++) {
                        float dir = (ring == 0) ? 1f : -1f;
                        for (int i = 0; i < points; i++) {
                            double angle = Math.toRadians(phase2RingAngle * dir + i * angleStep);
                            double px = player.getX() + Math.cos(angle) * radius;
                            double pz = player.getZ() + Math.sin(angle) * radius;
                            double py = player.getY() + 0.8 + Math.sin(Math.toRadians(phase2RingAngle * 2 + i * 30)) * 0.4;

                            // --- RENK HESABI ---
                            float darkLvl = data.getDarkAuraLevel();
                            float colorRatio = net.minecraft.util.Mth.clamp((darkLvl - 0.5f) / 2.5f, 0f, 1f);

                            float r = net.minecraft.util.Mth.lerp(colorRatio, 0.25f, 0.01f);
                            float b = net.minecraft.util.Mth.lerp(colorRatio, 0.35f, 0.02f);

                            net.minecraft.core.particles.DustParticleOptions dust =
                                    new net.minecraft.core.particles.DustParticleOptions(
                                            new org.joml.Vector3f(r, 0.0f, b), 1.2f);

                            level.addParticle(dust, px, py, pz, 0, 0, 0);

                            if (i % 4 == 0) {
                                level.addParticle(ParticleTypes.SOUL, px, py, pz, 0, 0.01, 0);
                            }
                        }
                    }
                });
    }
}