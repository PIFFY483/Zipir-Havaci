package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;


public class BlastItemEntity extends ItemEntity {

    private static final double GRAVITY          = 0.04;
    private static final double DRAG             = 0.98;
    private static final double BOUNCE_DECAY     = 0.58;
    private static final double BOUNCE_MIN       = 0.4;
    private static final double BOUNCE_MIN_SQ    = BOUNCE_MIN * BOUNCE_MIN;   // OPT: karekök'ten kaçın
    private static final double DAMAGE_MIN       = 1.4;
    private static final double DAMAGE_MIN_SQ    = DAMAGE_MIN * DAMAGE_MIN;   // OPT: karekök'ten kaçın
    private static final float  HIT_DAMAGE       = 1.0f;
    private static final double MAX_SPEED        = 2.2;
    private static final double MAX_SPEED_SQ     = MAX_SPEED * MAX_SPEED;     // OPT: karekök'ten kaçın
    private static final double STOP_SPEED       = 0.05;
    private static final double STOP_SPEED_SQ    = STOP_SPEED * STOP_SPEED;   // OPT: karekök'ten kaçın
    private static final double STEP_SIZE        = 0.3;                        // Sub-step eşiği

    private boolean handledCollision = false;

    public BlastItemEntity(EntityType<? extends ItemEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public BlastItemEntity(Level level, double x, double y, double z,
                           ItemStack stack, Vec3 velocity) {
        super(EntityRegistry.BLAST_ITEM.get(), level);
        this.noPhysics = true;
        this.setPos(x, y, z);
        this.setItem(stack);
        this.setDeltaMovement(velocity);
        this.setPickUpDelay(30);
        this.lifespan = 6000; // 5 dakika
    }

    @Override
    public void tick() {
        // xo/yo/zo — client interpolasyon için şart
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        super.tick();
        if (this.isRemoved()) return;

        Vec3 vel = this.getDeltaMovement();

        // OPT: length() yerine lengthSqr() — karekök çağrısından kaçın
        double spdSq = vel.lengthSqr();

        // Hız çok düştüyse vanilla ItemEntity'ye dönüştür
        if (spdSq < STOP_SPEED_SQ) {
            convertToVanilla();
            return;
        }

        // Yerçekimi + hava sürtünmesi
        vel = new Vec3(vel.x * DRAG, vel.y * DRAG - GRAVITY, vel.z * DRAG);

        // OPT: Max hız sınırı — lengthSqr ile karesel karşılaştırma
        double newSpdSq = vel.lengthSqr();
        if (newSpdSq > MAX_SPEED_SQ) vel = vel.scale(MAX_SPEED / Math.sqrt(newSpdSq));

        this.setDeltaMovement(vel);
        handledCollision = false;

        // OPT: Sub-step sweep — adım sayısını karesel hız ile hesapla
        // Math.sqrt(newSpdSq) zaten hesaplandı, tekrar çağırmaya gerek yok
        double spd = Math.sqrt(newSpdSq);
        int steps = Math.max(1, (int) Math.ceil(spd / STEP_SIZE));
        // OPT: stepVec sabittir — döngü içinde yeniden scale() çağrısı yok
        Vec3 stepVec = vel.scale(1.0 / steps);

        for (int i = 0; i < steps; i++) {
            if (this.isRemoved()) return;

            Vec3 from = this.position();
            Vec3 to   = from.add(stepVec);

            // Blok collision
            BlockHitResult blockHit = this.level().clip(
                    new net.minecraft.world.level.ClipContext(
                            from, to,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                            this));

            if (blockHit.getType() == HitResult.Type.BLOCK && !handledCollision) {
                handledCollision = true;
                if (handleBounce(vel, blockHit)) {
                    break;
                }
                break;
            }

            // Entity collision — sadece server
            if (!this.level().isClientSide) {
                handleEntityHit(from, to, vel, spdSq);
            }

            this.setPos(to.x, to.y, to.z);
        }
    }

    private boolean handleBounce(Vec3 vel, BlockHitResult hit) {
        // OPT: lengthSqr ile karşılaştır — karekök yok
        double spdSq = vel.lengthSqr();

        Direction dir = hit.getDirection();
        Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());

        // Sekme eşiği altındaysa dur
        if (spdSq < BOUNCE_MIN_SQ) {
            Vec3 surfacePos = hit.getLocation().add(normal.scale(0.01));
            this.setPos(surfacePos.x, surfacePos.y, surfacePos.z);
            this.setDeltaMovement(Vec3.ZERO);
            convertToVanilla();
            return true;
        }

        // Yansıma: v' = v - 2(v·n)n
        double dot = vel.dot(normal);
        Vec3 reflected = vel.subtract(normal.scale(2.0 * dot)).scale(BOUNCE_DECAY);

        // OPT: reflected uzunluk kontrolü — karesel
        if (reflected.lengthSqr() > MAX_SPEED_SQ)
            reflected = reflected.normalize().scale(MAX_SPEED);

        Vec3 surfacePos = hit.getLocation().add(normal.scale(0.02));
        this.setPos(surfacePos.x, surfacePos.y, surfacePos.z);
        this.setDeltaMovement(reflected);

        // Ses
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL,
                0.2f, 1.3f + (float)(this.level().random.nextDouble() * 0.4));

        return false;
    }

    // OPT: spdSq parametresi dışarıdan alınıyor — içeride tekrar vel.length() çağrısı yok
    private void handleEntityHit(Vec3 from, Vec3 to, Vec3 vel, double spdSq) {
        // OPT: DAMAGE_MIN_SQ ile karesel karşılaştırma
        if (spdSq < DAMAGE_MIN_SQ) return;

        // OPT: expandTowards + inflate birleştirildi — tek AABB çağrısı
        AABB sweepBox = this.getBoundingBox().expandTowards(vel.x, vel.y, vel.z).inflate(0.1);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(
                LivingEntity.class, sweepBox,
                t -> t.isAlive() && !t.isSpectator() && t.invulnerableTime <= 0);

        for (LivingEntity target : targets) {
            if (target.getBoundingBox().inflate(0.1).clip(from, to).isPresent()) {
                target.hurt(this.level().damageSources().generic(), HIT_DAMAGE);
                // OPT: normalize() + scale() — vel.length() çağrısından kaçınmak için
                // Math.sqrt(spdSq) zaten tick()'te hesaplandı ama burada spdSq'dan türetiyoruz
                double invSpd = 1.0 / Math.sqrt(spdSq);
                Vec3 push = vel.scale(invSpd * 0.5);
                target.setDeltaMovement(
                        target.getDeltaMovement().add(push.x, 0.2, push.z));
                target.hurtMarked = true;
                break; // tek hedef
            }
        }
    }

    /**
     * Hız sıfırlanınca vanilla ItemEntity spawn edip kendini siler.
     * Böylece pickup, despawn vb. vanilla davranışları korunur.
     */
    private void convertToVanilla() {
        if (this.level().isClientSide) return;
        ItemEntity vanilla = new ItemEntity(
                this.level(),
                this.getX(), this.getY(), this.getZ(),
                this.getItem().copy());
        vanilla.setPickUpDelay(10);
        vanilla.setDeltaMovement(Vec3.ZERO);
        this.level().addFreshEntity(vanilla);
        this.discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}