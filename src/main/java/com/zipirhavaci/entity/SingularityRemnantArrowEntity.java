package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SingularityRemnantArrowEntity extends AbstractArrow {

    private boolean hasImpacted = false;
    private int suctionTicks = 0;
    private static final int MAX_SUCTION_TICKS = 40;
    private final Set<Integer> hitEntities = new HashSet<>();

    public SingularityRemnantArrowEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
    }

    public SingularityRemnantArrowEntity(Level level, LivingEntity shooter) {
        super(EntityRegistry.SINGULARITY_REMNANT_ARROW.get(), shooter, level);
        this.setBaseDamage(2.0D);


    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            // Her tick değil, 2 tick'te bir ağır mantığı çalıştır
            if (this.tickCount % 2 == 0) {
                if (!this.inGround) {
                    applyHeavyMagnetFlight();
                } else if (hasImpacted) {

                    applySingularitySuction();
                }
            }
        }
    }

    private void applyHeavyMagnetFlight() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        double range = 3.5D; // Etki alanı
        AABB area = this.getBoundingBox().inflate(range);
        List<Entity> entities = sl.getEntities(this, area, e -> e instanceof LivingEntity && e != this.getOwner() && !e.isSpectator());

        Vec3 flightDir = this.getDeltaMovement().normalize();

        for (Entity target : entities) {
            if (target instanceof LivingEntity living) {
                Vec3 diff = this.position().subtract(living.position());
                double dist = diff.length();
                double force = Math.max(0.1, 1.0 - (dist / range));

                // ── MEKANİK: AGRESİF SÜRÜKLEME ──

                // Merkeze Vakum: Mobu okun tam geçtiği hatta sertçe çeker (0.65D)
                Vec3 pull = diff.normalize().scale(0.65D * force);

                Vec3 drift = flightDir.scale(0.85D * force);

                living.setDeltaMovement(living.getDeltaMovement().add(pull).add(drift).add(0, 0.15D, 0));
                living.hasImpulse = true;
                living.hurtMarked = true;

                // Görsel: Çekim efektini belli et
                if (sl.random.nextFloat() < 0.2f) {
                    sl.sendParticles(ParticleTypes.PORTAL, living.getX(), living.getY() + 1, living.getZ(), 2, 0.1, 0.1, 0.1, -0.4);
                }

                // Tek seferlik hasar kontrolü
                if (dist < 1.2D && !hitEntities.contains(living.getId())) {
                    living.hurt(sl.damageSources().arrow(this, this.getOwner()), 2.0F);
                    hitEntities.add(living.getId());
                }
            }
        }

        // Okun arkasındaki vakum izi
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
    }

    private void applySingularitySuction() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        // ── SES EFEKTİ (BAŞLANGIÇ) ──────────────────────────────
        if (this.suctionTicks == 0) {
            sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.2F, 1.8F);

            sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 2.0F);
        }

        if (this.suctionTicks % 10 == 0) {
            sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.CONDUIT_AMBIENT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 1.4F);
        }
        // ─────────────────────────────────────────────────────────

        if (this.suctionTicks < 40) {
            double radius = 3.5D;
            List<Entity> targets = sl.getEntities(this, this.getBoundingBox().inflate(radius),
                    e -> e instanceof LivingEntity && e != this.getOwner() && !e.isSpectator());

            for (Entity target : targets) {
                if (target instanceof LivingEntity living) {
                    Vec3 diff = this.position().subtract(living.position());
                    double dist = diff.length();
                    double force = Math.max(0, 1.0 - (dist / radius));

                    living.setDeltaMovement(living.getDeltaMovement().add(diff.normalize().scale(0.45D * force)).add(0, 0.08D, 0));
                    living.hurtMarked = true;

                    if (this.suctionTicks % 6 == 0 && dist < 1.5D) {
                        living.hurt(sl.damageSources().generic(), 0.5F);
                    }
                }
            }

            if (this.suctionTicks % 2 == 0) {
                spawnImpactParticles(sl);
            }

            this.suctionTicks += 2;

        } else {
            // ── VAKUM BİTTİ: IŞINLANMA VE GÖRSEL ŞÖLEN ──

            // 1. Ses: Boyutlar arası geçiş sesi
            sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.2F);

            // 2. Partikül Patlaması (Portal + Camgöbeği Dust)
            double effectY = this.getY() + 0.1D; // İstediğin 0.1 block yukarı kaydırma

            for (int i = 0; i < 25; i++) {
                double rx = (sl.random.nextDouble() - 0.5) * 0.6;
                double ry = (sl.random.nextDouble() - 0.5) * 0.6;
                double rz = (sl.random.nextDouble() - 0.5) * 0.6;

                // Mor Portal Küresi
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        this.getX() + rx, effectY + ry, this.getZ() + rz,
                        1, 0, 0, 0, 0.1);

                // Nadir Camgöbeği Dust (Arttırılmış Oran - Her patlamada %60 ihtimalle eşlik eder)
                if (sl.random.nextFloat() < 0.6f) {
                    sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                                    new org.joml.Vector3f(0.0F, 0.45F, 0.45F), 0.6f), // Koyu camgöbeği tonu, biraz daha belirgin boyut
                            this.getX() + rx, effectY + ry, this.getZ() + rz,
                            1, 0, 0, 0, 0.05);
                }
            }

            // 3. Geri Dönüşüm Mantığı
            if (this.getOwner() instanceof net.minecraft.world.entity.player.Player player) {
                handleEnderChestRecycle(player, sl);
            }

            this.discard(); // Ok veda eder
        }
    }

    private void handleEnderChestRecycle(net.minecraft.world.entity.player.Player player, ServerLevel sl) {
        net.minecraft.world.inventory.PlayerEnderChestContainer enderChest = player.getEnderChestInventory();

        double roll = sl.random.nextDouble();

        // %10 Şansla hiçbir şey düşmez
        if (roll < 0.10) return;

        ItemStack reward;
        // %32.5 Remnant İhtimali (0.10 + 0.325 = 0.425)
        if (roll < 0.425) {
            reward = new ItemStack(ItemRegistry.SCORCHED_REMNANT.get());
            // %8 Ekstra bonus Remnant
            if (sl.random.nextFloat() < 0.08f) {
                reward.setCount(2);
            }
        } else { // Geri kalan %57.5 Ender Pearl
            int count = 1;
            // %25 İhtimalle 6'ya kadar ekleme
            if (sl.random.nextFloat() < 0.25f) {
                count += sl.random.nextInt(6) + 1; // 1-6 arası ek
            }
            reward = new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, count);
        }

        // GÜVENLİK KONTROLÜ: Ender Sandığında yer var mı?
        // canAddItem metodu sandığın dolu olup olmadığını ve stack lenebilirliği kontrol et.
        if (enderChest.canAddItem(reward)) {
            enderChest.addItem(reward);
            // Başarılı aktarım sesi
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.ENDER_CHEST_CLOSE, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.4F);
        }
        // Eğer yer yoksa (bug önleyici), eşya sessizce yok ol.
    }

    private void spawnImpactParticles(ServerLevel sl) {
        for (int i = 0; i < 5; i++) {
            double angle = sl.random.nextDouble() * Math.PI * 2;
            double px = this.getX() + Math.cos(angle) * 3.0D;
            double pz = this.getZ() + Math.sin(angle) * 3.0D;
            Vec3 toCenter = new Vec3(this.getX() - px, 0, this.getZ() - pz).normalize().scale(0.5);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, px, this.getY() + 0.5, pz, 0, toCenter.x, 0, toCenter.z, 1);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!hasImpacted) {
            this.hasImpacted = true;
        }
        super.onHitBlock(result);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // Delici özellik: Entity'ye çarpsa da durma, içinden geç.
    }

    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(ItemRegistry.SINGULARITY_REMNANT_ARROW.get());
    }
}