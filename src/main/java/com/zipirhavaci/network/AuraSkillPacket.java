package com.zipirhavaci.network;



import com.zipirhavaci.core.capability.StaticProgressionProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class AuraSkillPacket {
    public AuraSkillPacket() {}

    public static void encode(AuraSkillPacket msg, FriendlyByteBuf buffer) {}
    public static AuraSkillPacket decode(FriendlyByteBuf buffer) { return new AuraSkillPacket(); }

    public static void handle(AuraSkillPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {

                    // ─── DARK AURA YÖNLENDİRME ───────────────────────────────
                    if (data.isCursed()) {
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
                                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);

                        com.zipirhavaci.core.physics.DarkAuraHandler.handleDarkAuraSkill(player);
                        return;
                    }
                    // ──────────────────────────────────────────────────────────

                    float level = data.getAuraLevel();

                    if (level <= 0.0f) {
                        player.sendSystemMessage(Component.literal(
                                "§7Power not yet manifested. Initiate Lightning Calibration."));
                        return;
                    }

                    long currentTime = System.currentTimeMillis();
                    long cooldownMillis = getCooldownForLevel(level);

                    if (data.isAuraActive()) {
                        executeShockwave(player, level, data.getAuraTicksLeft());

                        data.setAuraActive(false);
                        data.setAuraTicksLeft(0);
                        data.setLastAuraUseTime(currentTime);

                        player.removeEffect(MobEffects.MOVEMENT_SPEED);
                        player.removeEffect(MobEffects.JUMP);
                    } else {
                        long timeLeft = (data.getLastAuraUseTime() + cooldownMillis) - currentTime;
                        if (timeLeft > 0) {
                            player.sendSystemMessage(Component.literal("§c§lSPIRIT FATIGUE! §e" + (timeLeft / 1000) + "s §6until soul mended."));
                            return;
                        }
                        data.setAuraActive(true);
                        data.setAuraTicksLeft(getDurationForLevel(level));
                        executeInitialBurst(player, level);
                        player.sendSystemMessage(Component.literal("§b§lAURA ASCENDANT! §fThe radiance awakens..."));
                        com.zipirhavaci.core.physics.LightningProgressionHandler.summonLightningOnPlayer(player);
                    }
                    PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
                });
            }
        });
        context.setPacketHandled(true);
    }

    // ------------------------------------------------

    private static void executeShockwave(ServerPlayer player, final float level, int ticksLeft) {
        final int maxTicks = getDurationForLevel(level);
        final float finalEnergyRatio = (float) ticksLeft / (float) maxTicks;
        final boolean isCrit = finalEnergyRatio >= 0.95f;
        final boolean isDesp = finalEnergyRatio <= 0.1f;
        final double scanRadius = (level >= 3.0f) ? 6.5 : 4.8;
        final List<LivingEntity> caughtTargets = new ArrayList<>();

        Vector3f auraColor;
        Vector3f beamColor;

        if (level >= 3.0f) {
            float time = System.currentTimeMillis() / 400f;
            auraColor = new Vector3f(0.4f + (float)Math.sin(time) * 0.2f, 0.1f, 0.8f);
            beamColor = new Vector3f(0.5f + (float)Math.cos(time) * 0.2f, 0.2f, 0.9f);
        } else if (level >= 2.0f) {
            auraColor = new Vector3f(0.2f, 0.9f, 0.9f);
            beamColor = new Vector3f(0.4f, 1.0f, 1.0f);
        } else if (level >= 1.0f) {
            auraColor = new Vector3f(1.0f, 0.7f, 0.0f);
            beamColor = new Vector3f(1.0f, 0.85f, 0.2f);
        } else {
            auraColor = new Vector3f(0.2f, 0.4f, 1.0f);
            beamColor = new Vector3f(0.4f, 0.6f, 1.0f);
        }

        final DustParticleOptions spiralParticle = new DustParticleOptions(auraColor, 1.4f);

        player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - 3));
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 35, (level >= 3.0f ? 1 : 0)));
        player.setInvulnerable(true);

        for (int t = 0; t < 18; t++) {
            final int step = t;
            CompletableFuture.runAsync(() -> {
                player.getServer().execute(() -> {
                    if (player.isAlive()) {
                        double rotation = step * 1.3;
                        int points = 14;
                        for (int i = 0; i < points; i++) {
                            double angle = (i * (Math.PI * 2) / points) + rotation;
                            double currentDist = scanRadius * (1.0 - (step * 0.015));
                            double x = player.getX() + Math.cos(angle) * currentDist;
                            double z = player.getZ() + Math.sin(angle) * currentDist;
                            player.serverLevel().sendParticles(spiralParticle, x, player.getY() + 0.5, z, 1, 0, 0, 0, 0);
                            if (isCrit) {
                                player.serverLevel().sendParticles(ParticleTypes.END_ROD, x, player.getY() + 0.5, z, 1, 0, 0.1, 0, 0.05);
                            }
                        }
                    }
                });
            }, CompletableFuture.delayedExecutor(t * 25, TimeUnit.MILLISECONDS));
        }

        for (int t = 0; t < 10; t++) {
            CompletableFuture.runAsync(() -> {
                player.getServer().execute(() -> {
                    if (player.isAlive() && player.level() != null) {
                        for (int branch = 0; branch < 6; branch++) {
                            double angle = (double) branch / 6.0 * Math.PI * 2;
                            Vec3 start = player.position().add(0, 2.5, 0);
                            Vec3 end = player.position().add(Math.cos(angle) * scanRadius, -1.0, Math.sin(angle) * scanRadius);
                            for (int i = 0; i < 10; i++) {
                                Vec3 p = start.lerp(end, (double) i / 10.0);
                                player.serverLevel().sendParticles(new DustParticleOptions(beamColor, 1.1f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                            }
                        }
                    }
                });
            }, CompletableFuture.delayedExecutor(t * 100, TimeUnit.MILLISECONDS));
        }

        for (int t = 0; t < 15; t++) {
            CompletableFuture.runAsync(() -> {
                player.getServer().execute(() -> {
                    if (player.isAlive() && player.level() != null) {
                        player.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class, player.getBoundingBox().inflate(scanRadius)).forEach(proj -> {
                            Vec3 push = proj.position().subtract(player.position()).normalize().scale(0.6);
                            proj.setDeltaMovement(push);
                        });
                    }
                });
            }, CompletableFuture.delayedExecutor(t * 100, TimeUnit.MILLISECONDS));
        }

        float vacuumPower = (level >= 3.0f ? 1.55f : 0.95f);
        int totalGainedRaw = 0;
        final DustParticleOptions drainParticle = new DustParticleOptions(new Vector3f(0f, 0f, 0f), 0.7f);

        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(scanRadius))) {
            if (target != player && target.distanceTo(player) <= scanRadius) {
                caughtTargets.add(target);

                Vec3 pullDir = player.position().subtract(target.position()).normalize();
                target.setDeltaMovement(pullDir.x * vacuumPower, 0.26, pullDir.z * vacuumPower);

                if (target instanceof ServerPlayer targetPlayer) {
                    targetPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(targetPlayer));
                    targetPlayer.getFoodData().setFoodLevel(Math.max(0, targetPlayer.getFoodData().getFoodLevel() - 1));
                }
                target.hurtMarked = true;

                for (int i = 0; i < 5; i++) {
                    final int delay = i;
                    CompletableFuture.runAsync(() -> {
                        player.getServer().execute(() -> {
                            if (player.isAlive() && target.isAlive()) {
                                Vec3 origin = target.position().add(0, 1.2, 0);
                                Vec3 toPlayer = player.position().add(0, 1.2, 0).subtract(origin);
                                player.serverLevel().sendParticles(drainParticle, origin.x, origin.y, origin.z, 0, toPlayer.x, toPlayer.y, toPlayer.z, 0.25);
                            }
                        });
                    }, CompletableFuture.delayedExecutor(delay * 150, TimeUnit.MILLISECONDS));
                }
                totalGainedRaw++;
            }
        }

        if (totalGainedRaw > 0) {
            float gain = totalGainedRaw * 0.25f;
            player.getFoodData().setFoodLevel((int) Math.min(20, player.getFoodData().getFoodLevel() + gain));
            player.sendSystemMessage(Component.literal("§d⚡ " + String.format("%.1f", gain) + "§4STATIC HARVEST! §fEnergy redirected."), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        }

        if (!caughtTargets.isEmpty()) spawnGuardianBeams(player, caughtTargets, level);

        CompletableFuture.runAsync(() -> {
            player.getServer().execute(() -> {
                if (player.isAlive()) {
                    player.setInvulnerable(false);
                    if (caughtTargets.isEmpty()) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 1.0f, 0.8f);
                    } else {
                        triggerFinalBlast(player, level, finalEnergyRatio, scanRadius, isCrit, isDesp, caughtTargets);
                        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
                    }
                }
            });
        }, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    private static void triggerFinalBlast(ServerPlayer player, float level, float energyRatio, double radius, boolean isCrit, boolean isDesp, List<LivingEntity> hitTargets) {
        final float basePower = (level >= 3.0f ? 2.1f : 1.4f);
        final float finalPower = basePower * energyRatio * (isCrit ? 1.4f : 1.0f);

        for (LivingEntity target : hitTargets) {
            if (target.isAlive()) {
                target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 1));
                Vec3 pushDir = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(pushDir.x * finalPower, 0.55, pushDir.z * finalPower);
                target.hurtMarked = true;

                applyAuraEffects(target, level, energyRatio, isCrit);

                CompletableFuture.runAsync(() -> {
                    player.getServer().execute(() -> {
                        if (target.isAlive()) {
                            float damage = (level >= 3.0f) ? 6.5f : 3.5f;
                            target.hurt(player.damageSources().magic(), damage);
                            player.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);

                            ParticleOptions extra = (level >= 3.0f) ? ParticleTypes.DRAGON_BREATH : ParticleTypes.SOUL_FIRE_FLAME;
                            player.serverLevel().sendParticles(extra, target.getX(), target.getY() + 1, target.getZ(), 15, 0.2, 0.2, 0.2, 0.05);
                            player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.3f);

                            if (target instanceof ServerPlayer targetPlayer) {
                                targetPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(tData -> {
                                    if (tData.isAuraActive()) {
                                        float myEffectiveWeight = level;
                                        if (energyRatio < 0.5f) {
                                            myEffectiveWeight *= 0.5f;
                                        } else {
                                            myEffectiveWeight *= (0.5f + energyRatio);
                                        }

                                        if (myEffectiveWeight >= tData.getAuraLevel() && level >= tData.getAuraLevel()) {
                                            tData.setAuraActive(false);
                                            tData.setAuraTicksLeft(0);
                                            targetPlayer.sendSystemMessage(Component.literal("§c§lAURA FRAGMENTED! §fHigh-frequency shockwave breached the soul."));
                                            player.sendSystemMessage(Component.literal("§a§lADVERSARY BREACHED! §fOpponent's aura has collapsed."));
                                            player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
                                            PacketHandler.sendToTracking(targetPlayer, new SyncStaticProgressionPacket(tData, targetPlayer.getId()));
                                        } else {
                                            int energyDrain = (int)(20 * myEffectiveWeight);
                                            tData.setAuraTicksLeft(Math.max(0, tData.getAuraTicksLeft() - energyDrain));
                                            targetPlayer.sendSystemMessage(Component.literal("§6AURA IMPACTED! §fHeavy kinetic shock absorbed."));
                                        }
                                    }
                                });
                            }
                        }
                    });
                }, CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
            }
        }

        playDynamicSound(player, level, energyRatio, isCrit, isDesp);
        com.zipirhavaci.client.visuals.AuraVisualEffects.startScreenShake(1.5f * energyRatio, 13);
    }

    private static void spawnGuardianBeams(ServerPlayer player, List<LivingEntity> targets, float level) {
        int maxTargets = (level >= 3.0f) ? 5 : (level >= 2.0f ? 3 : 1);
        if (targets.isEmpty()) return;
        List<LivingEntity> targetsToHit = targets.stream().limit(maxTargets).toList();

        ParticleOptions mainPart;
        ParticleOptions orbitPart;

        if (level >= 3.0f) {
            mainPart  = ParticleTypes.END_ROD;
            orbitPart = ParticleTypes.DRAGON_BREATH;
        } else if (level >= 2.0f) {
            mainPart  = ParticleTypes.SOUL_FIRE_FLAME;
            orbitPart = ParticleTypes.SOUL_FIRE_FLAME;
        } else if (level >= 1.0f) {
            mainPart  = ParticleTypes.SOUL_FIRE_FLAME;
            orbitPart = ParticleTypes.FLAME;
        } else {
            mainPart  = ParticleTypes.FLAME;
            orbitPart = ParticleTypes.SMOKE;
        }

        for (LivingEntity target : targetsToHit) {
            float dmg = (level >= 3.0f) ? 6.0f : (level >= 2.0f ? 4.5f : 3.0f);
            target.hurt(player.damageSources().indirectMagic(player, player), dmg);

            for (int wave = 0; wave < 5; wave++) {
                final int currentWave = wave;
                final ParticleOptions fMainPart = mainPart;
                final ParticleOptions fOrbitPart = orbitPart;
                CompletableFuture.runAsync(() -> {
                    player.getServer().execute(() -> {
                        if (target.isAlive() && player.isAlive()) {
                            Vec3 start = target.position().add(0, 1.0, 0);
                            Vec3 end   = player.position().add(0, 1.2, 0);
                            Vec3 path  = end.subtract(start);

                            for (int i = 0; i < 15; i++) {
                                double t = (double) i / 15.0;
                                double angle = (t * Math.PI * 6) + (currentWave * 0.7);
                                double r = (1.0 - t) * 1.3;
                                double ox = Math.cos(angle) * r;
                                double oz = Math.sin(angle) * r;

                                Vec3 p = start.add(path.scale(t)).add(ox, 0, oz);
                                player.serverLevel().sendParticles(fMainPart, p.x, p.y, p.z, 1, 0, 0, 0, 0);

                                if (i == 0) {
                                    for (int h = 0; h < 2; h++) {
                                        double ha = (currentWave * 1.2) + (h * Math.PI);
                                        player.serverLevel().sendParticles(fOrbitPart, start.x + Math.cos(ha)*0.7, start.y, start.z + Math.sin(ha)*0.7, 1, 0, 0, 0, 0);
                                    }
                                }
                            }
                        }
                    });
                }, CompletableFuture.delayedExecutor(wave * 100, TimeUnit.MILLISECONDS));
            }
            player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }

    public static void applyAuraEffects(LivingEntity target, float level, float energyRatio, boolean isCritical) {
        int duration = isCritical ? 120 : 80;
        if (level >= 3.0f) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, (int)(15 * energyRatio)));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, duration, 250));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration / 2, 0));
            if (isCritical) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 3));
            if (energyRatio <= 0.1f) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
        } else if (level >= 2.0f) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1));
        }
    }

    private static void executeInitialBurst(ServerPlayer player, float level) {
        double range = (level >= 3.0f) ? 7.0 : (level >= 2.0f ? 5.5 : 4.0);
        float knockbackPower = (level >= 3.0f) ? 2.2f : (level >= 2.0f ? 1.6f : 1.1f);

        Vector3f vCol;
        if (level >= 3.0f) vCol = new Vector3f(0.65f, 0.1f, 0.95f);
        else if (level >= 2.0f) vCol = new Vector3f(0.0f, 0.9f, 0.9f);
        else if (level >= 1.0f) vCol = new Vector3f(1.0f, 0.85f, 0.1f);
        else vCol = new Vector3f(0.2f, 0.45f, 1.0f);

        final DustParticleOptions ringPart = new DustParticleOptions(vCol, 1.5f);

        player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range)).forEach(target -> {
            if (target != player && target.distanceTo(player) <= range) {
                Vec3 dir = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(dir.x * knockbackPower, 0.4, dir.z * knockbackPower);

                if (level >= 3.0f) {
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                } else if (level >= 2.0f) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0));
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                } else if (level >= 1.0f) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                }

                if (target instanceof ServerPlayer tp) {
                    tp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(tp));
                }
                target.hurtMarked = true;
            }
        });

        for (int step = 1; step <= 5; step++) {
            final int currentStep = step;
            final double currentRadius = (range / 5.0) * currentStep;

            CompletableFuture.runAsync(() -> {
                player.getServer().execute(() -> {
                    if (player.isAlive()) {
                        int points = (int)(currentRadius * 18);
                        for (int i = 0; i < points; i++) {
                            double angle = (i * Math.PI * 2) / points;
                            double px = player.getX() + Math.cos(angle) * currentRadius;
                            double pz = player.getZ() + Math.sin(angle) * currentRadius;
                            player.serverLevel().sendParticles(ringPart, px, player.getY() + 0.2, pz, 1, 0, 0, 0, 0);
                            if (level >= 3.0f && i % 3 == 0) {
                                player.serverLevel().sendParticles(ParticleTypes.END_ROD, px, player.getY() + 0.2, pz, 1, 0, 0.01, 0, 0.01);
                            }
                        }
                    }
                });
            }, CompletableFuture.delayedExecutor(step * 50, TimeUnit.MILLISECONDS));
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.2f);
    }

    private static void playDynamicSound(ServerPlayer player, float level, float energyRatio, boolean isCrit, boolean isDesp) {
        float p = (level >= 3.0f ? 0.5f : 1.1f) + (1.0f - energyRatio);
        float v = 1.0f * energyRatio + 0.5f;
        if (isCrit) { v = 1.8f; p = 0.4f; }
        if (isDesp) { v = 2.2f; p = 1.8f; }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, v, p);
        if (level >= 3.0f) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    private static long getCooldownForLevel(float level) {
        if (level >= 3.0f) return 130000;
        if (level >= 2.0f) return 130000;
        if (level >= 1.0f) return 115000;
        return 90000;
    }

    private static int getDurationForLevel(float level) {
        if (level >= 3.0f) return 600;
        if (level >= 2.0f) return 520;
        if (level >= 1.0f) return 480;
        return 360;
    }
}