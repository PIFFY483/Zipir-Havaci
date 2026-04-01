package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;


public class BlazeCoreEffectEntity extends Entity {

    // Kırmızı toz partikülü
    private static final DustParticleOptions DUST_RED =
            new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.0f, 0.0f), 1.2f);

    private int timer = 0;

    public BlazeCoreEffectEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public BlazeCoreEffectEntity(Level level, double x, double y, double z) {
        this(EntityRegistry.BLAZE_CORE_EFFECT.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) return;

        timer++;
        ServerLevel serverLevel = (ServerLevel) this.level();
        Vec3 center = this.position();

        // 120 tick (6 saniye) sonunda yok ol + fake soul fire temizle
        if (timer > 120) {
            cleanupFakeSoulFire(serverLevel);
            this.discard();
            return;
        }

        // ── ALAN TANIMLARI ──
        // 10x10 → radius 5 → çekim + itki
        List<LivingEntity> pullEntities = serverLevel.getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(5.0));
        // 8x8 → radius 4 → ısı hasarı
        List<LivingEntity> damageEntities = serverLevel.getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(4.0));


        if (timer <= 50) {

            // KONİ PARTİKÜLLERİ: Sabit açılarla eşit dağılım (görsel tutarlılık için)
            int spokeCount = 16;
            for (int s = 0; s < spokeCount; s++) {
                double spokeAngle = s * Math.PI * 2.0 / spokeCount
                        + (timer * 0.04); // Hafif rotasyon — dönen etki

                // Her spoke üzerinde merkezden dışa doğru birkaç nokta
                int pointsPerSpoke = 6;
                for (int p = 0; p < pointsPerSpoke; p++) {
                    // dist: 0 (merkez) → 5.0 (dış çember)
                    double dist = (p + 1) * (5.0 / pointsPerSpoke);

                    double px = center.x + Math.cos(spokeAngle) * dist;
                    double pz = center.z + Math.sin(spokeAngle) * dist;

                    // KONİ FORMÜLÜ: Merkezde 3 blok yukarı, dışta yerde (0)
                    double coneY = center.y + (1.0 - dist / 5.0) * 3.0;

                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            px, coneY, pz,
                            1, 0, 0.02, 0, 0.0);

                    if (p % 2 == 0) {
                        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                                px, coneY + 0.15, pz,
                                1, 0, 0.01, 0, 0.0);
                    }

                    if (dist < 2.5) {
                        serverLevel.sendParticles(ParticleTypes.FLAME,
                                px, coneY + 0.1, pz,
                                1, 0, 0.03, 0, 0.0);
                    }
                }
            }

            // DIŞ KIRIMIZI ÇEMBER: Koni tabanının tam dışında, yerle aynı seviyede
            int ringCount = 32;
            for (int i = 0; i < ringCount; i++) {
                double angle = i * Math.PI * 2.0 / ringCount;
                double rx = center.x + Math.cos(angle) * 5.0;
                double rz = center.z + Math.sin(angle) * 5.0;
                // Kırmızı toz — tam yerde, hafif yukarı hareketle
                serverLevel.sendParticles(DUST_RED,
                        rx, center.y + 0.05, rz,
                        1, 0, 0.03, 0, 0.0);
            }
        }


        if (timer == 1) {

            serverLevel.sendParticles(ParticleTypes.FLASH,
                    center.x, center.y, center.z,
                    1, 0, 0, 0, 0.0);

            // 1. PATLAMA RAW DAMAGE: 8x8 alandaki herkese 0.5 kalp (1.0F)
            List<LivingEntity> blastTargets = serverLevel.getEntitiesOfClass(
                    LivingEntity.class, this.getBoundingBox().inflate(4.0));
            for (LivingEntity e : blastTargets) {
                e.hurt(serverLevel.damageSources().explosion(null, null), 1.0F);
            }
        }

        // ──  TİK 51: ÇEKİM BİTİŞİ — DIŞA SAÇILMA + İKİNCİ FLASH ──
        if (timer == 51) {

            int scatterCount = 32;
            for (int i = 0; i < scatterCount; i++) {
                double angle = i * Math.PI * 2.0 / scatterCount;
                double rx = center.x + Math.cos(angle) * 5.0;
                double rz = center.z + Math.sin(angle) * 5.0;
                double vx = Math.cos(angle) * 0.4;
                double vz = Math.sin(angle) * 0.4;
                serverLevel.sendParticles(DUST_RED,                      rx, center.y + 0.1, rz, 3, vx, 0.15, vz, 0.0);
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, rx, center.y + 0.3, rz, 3, vx * 0.8, 0.2, vz * 0.8, 0.0);
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,   rx, center.y + 0.5, rz, 2, vx * 0.6, 0.25, vz * 0.6, 0.0);
            }

            // İKİNCİ FLASH
            serverLevel.sendParticles(ParticleTypes.FLASH,
                    center.x, center.y, center.z,
                    1, 0, 0, 0, 0.0);
            serverLevel.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.1F);

            // 2. PATLAMA RAW DAMAGE: 10x10 alandaki herkese 0.5 kalp (1.0F)
            List<LivingEntity> blastTargets2 = serverLevel.getEntitiesOfClass(
                    LivingEntity.class, this.getBoundingBox().inflate(5.0));
            for (LivingEntity e : blastTargets2) {
                e.hurt(serverLevel.damageSources().explosion(null, null), 1.0F);
            }
        }

        // ── E. HASAR: Isı Dalgası (magic) + Soul Fire (inFire) ──
        if (timer % 20 == 0) {
            // Magic hasar: 8x8 alanın tamamına
            for (LivingEntity e : damageEntities) {
                e.hurt(serverLevel.damageSources().magic(), 1.0F);
            }
            // Soul fire hasarı: fake ateş üzerinde duran entity'lere
            applyFakeSoulFireDamage(serverLevel);
        }

        // ── F. ÇEKİM VE İTKİ ──
        for (LivingEntity e : pullEntities) {
            double distance = e.distanceTo(this);

            if (timer <= 50) {
                // Merkeze doğru çekim (kaçılabilir güç)
                double strength = Math.max(0, (1.0 - (distance / 6.0)) * 0.06);
                Vec3 pull = center.subtract(e.position()).normalize().scale(strength);
                Vec3 newMotion = e.getDeltaMovement().add(pull.x, 0, pull.z);
                e.setDeltaMovement(newMotion);
                e.hurtMarked = true;
                // ServerPlayer için motion paketi göndermek ŞART

                if (e instanceof ServerPlayer sp) {
                    sp.hasImpulse = true;
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }

            } else if (timer == 51) {
                // Anlık itki: ~2.5 blok yukarı + dışa
                Vec3 push = e.position().subtract(center).normalize()
                        .scale(1.4)
                        .add(0, 0.65, 0);
                e.setDeltaMovement(push);
                e.hurtMarked = true;
                if (e instanceof ServerPlayer sp) {
                    sp.hasImpulse = true;
                    sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
                }
            }
        }
    }

    // ── FAKE SOUL FIRE SİSTEMİ ──
    // Server'da HİÇBİR BLOK DEĞİŞMİYOR.
    // HashSet kullan O(1) lookup, BlockPos.equals() koordinat bazlı karşılaştırır.
    private final java.util.Set<BlockPos> fakeSoulFirePositions = new java.util.HashSet<>();

    public void spawnFakeSoulFire(ServerLevel level, Vec3 origin) {
        BlockPos centerPos = BlockPos.containing(origin);

        // X/Z ekseninde 8x8 alanı kolon kolon tara
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {

                if (dx * dx + dz * dz > 4.0 * 4.0) continue;
                int worldX = centerPos.getX() + dx;
                int worldZ = centerPos.getZ() + dz;

                // Patlama noktasından aşağı tara — ilk "hava + altı katı" pozisyonu bul
                BlockPos firePos = null;
                for (int dy = 4; dy >= -8; dy--) {
                    BlockPos check = new BlockPos(worldX, centerPos.getY() + dy, worldZ);
                    BlockPos below  = check.below();
                    if (level.getBlockState(check).isAir()
                            && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                        firePos = check;
                        break;
                    }
                }

                if (firePos == null) continue;

                // Client'lara yalan söyle
                ClientboundBlockUpdatePacket fakePacket = new ClientboundBlockUpdatePacket(
                        firePos, Blocks.SOUL_FIRE.defaultBlockState());
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(firePos.getX(), firePos.getY(), firePos.getZ()) < 128 * 128) {
                        player.connection.send(fakePacket);
                    }
                }

                fakeSoulFirePositions.add(firePos);
            }
        }
    }

    // 6 saniye sonra fake blokları temizle — gerçek state'i (air) geri gönder
    private void cleanupFakeSoulFire(ServerLevel level) {
        for (BlockPos pos : fakeSoulFirePositions) {
            ClientboundBlockUpdatePacket restore = new ClientboundBlockUpdatePacket(
                    pos, level.getBlockState(pos));
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 128 * 128) {
                    player.connection.send(restore);
                }
            }
        }
        fakeSoulFirePositions.clear();
    }

    // Soul fire hasarı — HashSet.contains() BlockPos.equals() ile koordinat karşılaştırır, doğru çalışır
    private void applyFakeSoulFireDamage(ServerLevel level) {
        if (fakeSoulFirePositions.isEmpty()) return;
        AABB fireZone = this.getBoundingBox().inflate(4.0);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, fireZone)) {
            BlockPos feetPos = BlockPos.containing(e.getX(), e.getY() - 0.1, e.getZ());
            if (fakeSoulFirePositions.contains(feetPos)) {
                e.hurt(level.damageSources().inFire(), 1.0F);
            }
        }
    }

    // ── ZORUNLU OVERRIDE'LAR ──

    @Override
    protected void defineSynchedData() {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.timer = tag.getInt("EffectTimer");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("EffectTimer", this.timer);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    // Entity tamamen görünmez — render edilmesi gerekmiyor
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false;
    }
}