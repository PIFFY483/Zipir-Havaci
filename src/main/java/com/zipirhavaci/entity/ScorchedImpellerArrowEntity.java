package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ScorchedImpellerArrowEntity extends AbstractArrow {

    private boolean hasImpacted = false;
    // Okun bir önceki tick'teki uçuş yönü — baskı alanı için kullanılır
    private Vec3 lastFlightDir = Vec3.ZERO;

    public ScorchedImpellerArrowEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public ScorchedImpellerArrowEntity(Level level, LivingEntity shooter) {
        super(EntityRegistry.SCORCHED_IMPELLER_ARROW.get(), shooter, level);
        this.setBaseDamage(5.0D);
    }

    @Override
    public void tick() {


        // super.tick() ÖNCE uçuş yönünü kaydet (blok çarpması öncesi geçerli yön)
        if (!this.inGround) {
            Vec3 motion = this.getDeltaMovement();
            if (motion.lengthSqr() > 0.0001) {
                lastFlightDir = motion.normalize();
            }
        }

        super.tick(); // Minecraft'ın kendi fizik/çarpma sistemini BOZMA

        // Sadece server, sadece uçuşta, sadece kaldırılmamışsa
        if (!this.level().isClientSide() && !this.inGround && !this.isRemoved()) {
            applyPressureField();
            spawnFlightTrail();
        }
    }


    private void applyPressureField() {
        final double MAX_RANGE = 3.0D;

        AABB searchArea = this.getBoundingBox().inflate(MAX_RANGE);

        List<Entity> nearby = this.level().getEntities(this, searchArea, e -> {
            if (!(e instanceof LivingEntity)) return false;
            // Atan kişiyi etkileme
            if (e == this.getOwner()) return false;
            // AABB köşe false-positive'ini gerçek mesafe ile filtrele
            return e.position().distanceTo(this.position()) <= MAX_RANGE;
        });

        for (Entity entity : nearby) {
            LivingEntity living = (LivingEntity) entity;

            double dist = living.position().distanceTo(this.position());
            if (dist < 0.01D) continue; // tam üst üste gelme koruması

            // t: 0.0 (sınır) → 1.0 (merkez)
            double t = 1.0D - (dist / MAX_RANGE);
            // Kare eğri: merkeze yakın çok daha şiddetli
            double power = t * t;

            Vec3 vel = living.getDeltaMovement();

            // ── 1. BASKISI ─────
            // Minecraft her tick 0.91 drag uygular bu yüzden sadece
            // Math.min ile kesmek yetmez — direkt sabit bir değere kilitle.
            double downForce = -0.5D - (0.55D * power); // -0.5 (sınır) → -1.05 (merkez)
            living.setDeltaMovement(vel.x, downForce, vel.z);
            living.hurtMarked = true;

            // ── 2 - 3.  SAVURMA + ÇEKİM ─────────
            // Drag (0.91/tick) kuvvetleri yutar — bu yüzden değerler agresif olmalı.
            Vec3 toTarget = living.position().subtract(this.position());
            double hDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            if (hDist > 0.05D) {
                Vec3 outward = new Vec3(toTarget.x / hDist, 0, toTarget.z / hDist);

                // İTME: (0.95 peak)
                double pushForce = 0.95D * power;
                // ÇEKME:  (0.35 peak)
                double pullForce = 0.35D * power;
                // Net yatay etki: ~0.60 peak
                double netForce = pushForce - pullForce;

                Vec3 v2 = living.getDeltaMovement();
                living.setDeltaMovement(
                        v2.x + outward.x * netForce,
                        v2.y,
                        v2.z + outward.z * netForce
                );
                living.hurtMarked = true;
            }

            // ── 4. OK YÖNÜNDE HAFIF İTME + ANLICK YUKARI KALDIRIŞ ────────
            if (lastFlightDir.lengthSqr() > 0.0001D) {

                Vec3 windPush = new Vec3(lastFlightDir.x, 0, lastFlightDir.z)
                        .scale(0.08D * power); // çok hafif: peak ~0.08, uzakta neredeyse 0
                Vec3 v3 = living.getDeltaMovement();
                living.setDeltaMovement(
                        v3.x + windPush.x,
                        v3.y + 0.12D * power, // anlık hafif kalkış — 0.1 block hissi
                        v3.z + windPush.z
                );
                living.hurtMarked = true;
            }

            int slownessAmp = (int) Math.round(5.0D * power); // 0–5
            if (slownessAmp >= 0) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        8,
                        slownessAmp,
                        false, false
                ));
            }

            // ── 6. MINING FATIGUE — merkeze çok yakınsa şok/uyuşma hissi ─
            if (power > 0.4D) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.DIG_SLOWDOWN,
                        10, 2, false, false
                ));
            }

            // ── 7. GÖRSELLEŞTİRME ───────────────────────────────────────
            if (this.level() instanceof ServerLevel sl) {
                // Hedefin etrafında dönen kül halkası — baskı alanının görünümü
                float chance = (float) power * 0.85f;
                if (this.random.nextFloat() < chance) {
                    double angle = (this.tickCount * 0.3D) + (living.getId() * 2.1D);
                    double radius = 0.5D + (1.0D - power) * 0.8D; // güçlüyken dar halka
                    double rx = Math.cos(angle) * radius;
                    double rz = Math.sin(angle) * radius;
                    sl.sendParticles(ParticleTypes.WHITE_ASH,
                            living.getX() + rx,
                            living.getY() + 0.6D + this.random.nextDouble() * 1.4D,
                            living.getZ() + rz,
                            1, 0.03, 0.06, 0.03, 0.002);
                }

                // Merkeze yakınsa ateş partikülleri — tehlike göstergesi
                if (power > 0.45D && this.random.nextFloat() < power * 0.7f) {
                    sl.sendParticles(ParticleTypes.FLAME,
                            living.getX() + (this.random.nextDouble() - 0.5D) * 0.5D,
                            living.getY() + this.random.nextDouble() * 2.0D,
                            living.getZ() + (this.random.nextDouble() - 0.5D) * 0.5D,
                            1, 0.04, 0.04, 0.04, 0.01);
                }

                // Çok yakınsa yoğun baskı efekti — "içindesin" hissi
                if (power > 0.7D && this.random.nextFloat() < 0.5f) {
                    sl.sendParticles(ParticleTypes.SMOKE,
                            living.getX(), living.getY() + 1.0D, living.getZ(),
                            2, 0.2, 0.3, 0.2, 0.01);
                }
            }
        }
    }

    private void spawnFlightTrail() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        // Her tick ok arkasında dönen küçük ateş sarmalı
        double angle = this.tickCount * 0.5D;
        double spiralR = 0.3D;
        double bx = this.getX() + Math.cos(angle) * spiralR;
        double bz = this.getZ() + Math.sin(angle) * spiralR;

        sl.sendParticles(ParticleTypes.FLAME,
                bx, this.getY() + 0.1D, bz,
                1, 0.02, 0.02, 0.02, 0.005);

        // Her 2 tick'te bir kül izi
        if (this.tickCount % 2 == 0) {
            sl.sendParticles(ParticleTypes.WHITE_ASH,
                    this.getX(), this.getY() + 0.1D, this.getZ(),
                    1, 0.05, 0.05, 0.05, 0.005);
        }
    }

    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult result) {
        super.onHitEntity(result);
        triggerImpactExplosion();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        triggerImpactExplosion();
    }

    private void triggerImpactExplosion() {
        if (hasImpacted) return;
        hasImpacted = true;

        if (!(this.level() instanceof ServerLevel sl)) return;

        double ix = this.getX();
        double iy = this.getY();
        double iz = this.getZ();

        // ── KATMAN 1: İÇ FLAME HALKASI — iki açıda ────────────────────
        for (int deg = 0; deg < 360; deg += 7) {
            double rad = Math.toRadians(deg);
            double c = Math.cos(rad), s = Math.sin(rad);
            // Yatay yayılım
            sl.sendParticles(ParticleTypes.FLAME,
                    ix, iy, iz, 1, c * 0.9D, 0.04D, s * 0.9D, 0.03D);
            // 35 derece yukarı açılı — "kubbe" şekli
            sl.sendParticles(ParticleTypes.FLAME,
                    ix, iy, iz, 1, c * 0.6D, 0.4D, s * 0.6D, 0.03D);
        }

        // ── KATMAN 2: SOUL FIRE — turuncu/mavi ton, daha geniş ────────
        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    ix, iy, iz, 1,
                    Math.cos(rad) * 1.5D, 0.07D, Math.sin(rad) * 1.5D, 0.05D);
        }

        // ── KATMAN 3: LAVA PARTICLES — sıcaklık hissi ─────────────────
        for (int deg = 0; deg < 360; deg += 18) {
            double rad = Math.toRadians(deg);
            sl.sendParticles(ParticleTypes.LAVA,
                    ix, iy + 0.1D, iz, 1,
                    Math.cos(rad) * 0.8D, 0.15D, Math.sin(rad) * 0.8D, 0.1D);
        }

        // ── KATMAN 4: DIŞ DUMAN — en geniş halka ──────────────────────
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    ix, iy, iz, 1,
                    Math.cos(rad) * 2.1D, 0.2D, Math.sin(rad) * 2.1D, 0.03D);
        }

        // ── KATMAN 5: MERKEZ PARLAMA ───────────────────────────────────
        sl.sendParticles(ParticleTypes.EXPLOSION, ix, iy, iz, 3, 0.3D, 0.3D, 0.3D, 0.1D);
        sl.sendParticles(ParticleTypes.FLASH,     ix, iy + 0.5D, iz, 1, 0, 0, 0, 0);

        // ── PATLAMA SESLERİ ────────────────────────────────────────────
        // Ana patlama — derin, ağır vuruş
        sl.playSound(null, ix, iy, iz,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.9F, 0.75F);
        // Kor vızıltısı — ateşin içinden gelen tiz katman
        sl.playSound(null, ix, iy, iz,
                net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 0.55F);
        // Yankı/rezonans — uzaktan duyulan titreşim
        sl.playSound(null, ix, iy, iz,
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.3F, 1.6F);

        // ── FİZİK PATLAMA ──────────────────────────────────────────────

        applyExplosionPhysics(3.0D);

        this.discard();
    }

    private void applyExplosionPhysics(double radius) {
        List<Entity> targets = this.level().getEntities(this,
                this.getBoundingBox().inflate(radius));

        for (Entity entity : targets) {
            if (!(entity instanceof LivingEntity living)) continue;

            Vec3 diff = living.position().subtract(this.position());
            double dist = diff.length();
            if (dist < 0.05D) continue;


            double force = Math.max(0.4D, 2.0D * (1.0D - (dist / radius)));

            // ── PATLAMA VEKTÖRÜ ──────────────────────────────────────────
            // Y bileşenini güçlü tut ki entity havaya kalksın
            Vec3 dir = diff.normalize();
            double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
            // Yatay çarpan: uzaktakiler daha fazla yana savrulur
            double hScale = 1.6D + (hDist / radius) * 0.6D; // 1.6 – 2.2
            // Y: her zaman güçlü bir kalkış — zemin sürtünmesini kes
            double yLaunch = 0.42D + force * 0.22D;
            living.setDeltaMovement(
                    dir.x * force * hScale,
                    yLaunch,
                    dir.z * force * hScale
            );
            living.hasImpulse = true;
            living.hurtMarked = true;


            living.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));

            living.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS, 20, 0, false, false));
        }
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ItemRegistry.SCORCHED_IMPELLER_ARROW.get());
    }
}