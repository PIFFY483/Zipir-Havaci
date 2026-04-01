package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;


public class BlazeCoreEntity extends Entity {

    // ── Açı eşiği ─────────────────────────────────────────────────────────────
    // |cos(çarpma açısı)| >= bu değer → patlama
    // cos(60°) = 0.50
    private static final double EXPLODE_ANGLE_DOT       = 0.45;
    private static final double EXPLODE_SPEED_THRESHOLD = 1.4;

    // ── Fizik sabitleri ───────────────────────────────────────────────────────
    private static final double GRAVITY      = 0.03;
    private static final double DRAG_AIR     = 0.99;
    private static final double DRAG_WATER   = 0.80;
    private static final double BOUNCE_DECAY = 0.60;  // sekmede hız katsayısı

    // ── Entity veri senkronizasyonu ───────────────────────────────────────────
    // Client'a owner UUID'ini senkronize ediyoruz (isteğe bağlı, ileride lazım olabilir)
    private static final EntityDataAccessor<Integer> DATA_BOUNCE_COUNT =
            SynchedEntityData.defineId(BlazeCoreEntity.class, EntityDataSerializers.INT);

    // ── Server-only state ────────────────────────────────────────────────────
    @Nullable
    private UUID ownerUUID;
    @Nullable
    private LivingEntity cachedOwner;

    private int bounceCount = 0;

    // Aynı tick içinde çift collision işlemini önlemek için
    private boolean handledCollisionThisTick = false;

    // ─── CONSTRUCTOR'LAR ──────────────────────────────────────────────────────

    public BlazeCoreEntity(EntityType<? extends BlazeCoreEntity> type, Level level) {
        super(type, level);
        // Projectile'lar yerle çarpışmamalı (fizik manuel) —  Entity extend
        //  zaten moveProjectile yok. noClip=false bırak
        // getBoundingBox düzgün çalışsın.
        this.noPhysics = true; // Entity.move() çağrılmayacak, fizik tamamen manuel
    }

    /**
     * Dispenser için: pozisyon + hız vektörü, owner yok.
     */
    public BlazeCoreEntity(Level level, double x, double y, double z, Vec3 velocity) {
        this(EntityRegistry.BLAZE_CORE_PROJECTILE.get(), level);
        this.setPos(x, y, z);
        this.setDeltaMovement(velocity);
        updateRotationFromMotion();
    }

    public BlazeCoreEntity(Level level, LivingEntity shooter, Vec3 velocity) {
        this(EntityRegistry.BLAZE_CORE_PROJECTILE.get(), level);

        this.ownerUUID   = shooter.getUUID();
        this.cachedOwner = shooter;

        // Spawn pozisyonu: gözün önüne bak yönünde 0.8 blok
        Vec3 look = shooter.getLookAngle();
        this.setPos(
                shooter.getX() + look.x * 0.8,
                shooter.getEyeY() - 0.15,
                shooter.getZ() + look.z * 0.8
        );

        this.setDeltaMovement(velocity);
        updateRotationFromMotion();
    }

    // ─── ENTITY DATA (SynchedEntityData) ─────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_BOUNCE_COUNT, 0);
    }

    // ─── NBT KAYIT / YÜKLEME ─────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        bounceCount = tag.getInt("BounceCount");
        this.entityData.set(DATA_BOUNCE_COUNT, bounceCount);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putInt("BounceCount", bounceCount);
    }

    // ─── NETWORK PACKET ───────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    // ─── OWNER ────────────────────────────────────────────────────────────────

    @Nullable
    public LivingEntity getOwner() {
        if (cachedOwner != null && !cachedOwner.isRemoved()) {
            return cachedOwner;
        }
        if (ownerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(ownerUUID);
            if (entity instanceof LivingEntity living) {
                cachedOwner = living;
                return cachedOwner;
            }
        }
        return null;
    }

    // ─── ANA TICK ─────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Önceki pozisyonu MUTLAKA kaydet — client interpolasyonu için şart.
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        super.tick();

        if (this.isRemoved()) return;

        handledCollisionThisTick = false;


        physicsTick();

        updateRotationFromMotion();
    }

    // ─── FİZİK TICK (SERVER + CLIENT) ────────────────────────────────────────

    private void physicsTick() {
        Vec3 motion = this.getDeltaMovement();

        double drag = this.isInWater() ? DRAG_WATER : DRAG_AIR;
        motion = new Vec3(
                motion.x * drag,
                motion.y * drag - GRAVITY,
                motion.z * drag
        );

        double speed = motion.length();

        if (speed < 0.001) {
            this.setDeltaMovement(Vec3.ZERO);
            if (!this.level().isClientSide) {
                triggerCoreExplosion();
                this.discard();
            }
            return;
        }

        int  steps   = Math.max(1, (int) Math.ceil(speed / 0.1));
        Vec3 stepVec = motion.scale(1.0 / steps);

        for (int i = 0; i < steps; i++) {
            if (this.isRemoved()) return;

            Vec3 from = this.position();
            Vec3 to   = from.add(stepVec);

            BlockHitResult blockHit = sweepBlock(from, to);
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                // Client sadece görsel bounce uygular, patlama kararı vermez
                if (this.level().isClientSide) {
                    handleClientBounce(motion, blockHit);
                } else {
                    onBlockCollision(motion, blockHit);
                }
                return;
            }

            // Entity collision sadece server da işlenir
            if (!this.level().isClientSide) {
                LivingEntity hitEntity = sweepEntities(from, to);
                if (hitEntity != null) {
                    onEntityCollision();
                    return;
                }
            }

            this.setPos(to.x, to.y, to.z);
        }

        this.setDeltaMovement(motion);
    }

    /** Client tarafında görsel bounce — sadece pozisyon ve motion günceller, patlama yok */
    private void handleClientBounce(Vec3 motion, BlockHitResult hit) {
        Direction dir  = hit.getDirection();
        Vec3 normal    = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        double speed   = motion.length();
        double dot     = motion.dot(normal) / speed;


        boolean wouldExplode = Math.abs(dot) >= EXPLODE_ANGLE_DOT
                || speed > EXPLODE_SPEED_THRESHOLD
                || bounceCount >= 1;

        if (wouldExplode) {
            // Server patlama paketi gelene kadar yüzeyde beklet
            Vec3 surfacePos = hit.getLocation().add(normal.scale(0.05));
            this.setPos(surfacePos.x, surfacePos.y, surfacePos.z);
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 surfacePos = hit.getLocation().add(normal.scale(0.05));
        this.setPos(surfacePos.x, surfacePos.y, surfacePos.z);

        double fullDot = motion.dot(normal);
        Vec3 reflected = motion.subtract(normal.scale(2.0 * fullDot)).scale(BOUNCE_DECAY);
        this.setDeltaMovement(reflected);
    }


    // ─── BLOCK SWEEP ──────────────────────────────────────────────────────────

    private BlockHitResult sweepBlock(Vec3 from, Vec3 to) {
        return this.level().clip(
                new net.minecraft.world.level.ClipContext(
                        from, to,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                        this
                )
        );
    }

    // ─── ENTITY SWEEP ─────────────────────────────────────────────────────────

    @Nullable
    private LivingEntity sweepEntities(Vec3 from, Vec3 to) {
        // Hareket boyunca geçilen AABB'yi oluştur
        AABB sweepBox = this.getBoundingBox().expandTowards(to.subtract(from)).inflate(0.1);

        List<Entity> candidates = this.level().getEntities(this, sweepBox,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isAlive());

        LivingEntity owner = getOwner();

        for (Entity candidate : candidates) {
            // İlk 10 tick sahibine çarpma
            if (candidate == owner && this.tickCount < 10) continue;

            // AABB ile daha hassas kontrol
            AABB entityBox = candidate.getBoundingBox().inflate(0.15);
            if (entityBox.clip(from, to).isPresent()) {
                return (LivingEntity) candidate;
            }
        }
        return null;
    }

    // ─── BLOCK COLLISION HANDLER ──────────────────────────────────────────────

    private void onBlockCollision(Vec3 motion, BlockHitResult hit) {
        if (handledCollisionThisTick) return;
        handledCollisionThisTick = true;

        Direction dir  = hit.getDirection();
        Vec3 normal    = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());

        // Normalize edilmiş motion ile açıyı hesapla
        double speed   = motion.length();
        double dot = motion.dot(normal) / speed;


        boolean tooSteep       = Math.abs(dot) >= EXPLODE_ANGLE_DOT;
        boolean tooFast        = speed > EXPLODE_SPEED_THRESHOLD;
        boolean alreadyBounced = bounceCount >= 1;

        if (tooSteep || tooFast || alreadyBounced) {
            // Patlama konumunu yüzeyde sabitle (bloğun içinde değil)
            Vec3 explodePos = hit.getLocation().add(normal.scale(0.05));
            this.setPos(explodePos.x, explodePos.y, explodePos.z);
            triggerCoreExplosion();
            this.discard();
            return;
        }

        // ── SEKME ─────────────────────────────────────────────────────────────
        // hit.getLocation() bloğun tam yüzeyi — raycast projetili içeri sokmadan
        // yüzeyde kesti. Normal yönünde küçük bir epsilon ile dışarı it.
        Vec3 surfacePos = hit.getLocation().add(normal.scale(0.05));
        this.setPos(surfacePos.x, surfacePos.y, surfacePos.z);

        // Yansıma: v' = v - 2(v·n)n
        double fullDot = motion.dot(normal);
        Vec3 reflected = motion.subtract(normal.scale(2.0 * fullDot)).scale(BOUNCE_DECAY);
        this.setDeltaMovement(reflected);

        bounceCount++;
        this.entityData.set(DATA_BOUNCE_COUNT, bounceCount);

        this.level().playSound(null,
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    // ─── ENTITY COLLISION HANDLER ─────────────────────────────────────────────

    private void onEntityCollision() {
        if (handledCollisionThisTick) return;
        handledCollisionThisTick = true;

        triggerCoreExplosion();
        this.discard();
    }

    // ─── ROTASYON ─────────────────────────────────────────────────────────────

    private void updateRotationFromMotion() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() > 0.001) {
            double h = motion.horizontalDistance();
            this.setXRot((float) (Math.atan2(motion.y, h) * (180.0 / Math.PI)));
            this.setYRot((float) (Math.atan2(motion.x, motion.z) * (180.0 / Math.PI)));
        }
    }

    // ─── DİĞER OVERRIDE'LAR ───────────────────────────────────────────────────

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.15F, 0.15F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false; // Görsel ateş efekti istemiyoruz
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    // ─── PATLAMA ──────────────────────────────────────────────────────────────

    private void triggerCoreExplosion() {
        Level level  = this.level();
        Vec3  origin = this.position();

        if (level instanceof ServerLevel serverLvl) {
            serverLvl.sendParticles(
                    ParticleTypes.FLASH,
                    origin.x, origin.y, origin.z,
                    1, 0, 0, 0, 0.0
            );
        }

        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5F, 0.8F);

        for (int i = 0; i < 3; i++) {
            ItemEntity remnant = new ItemEntity(
                    level,
                    origin.x, origin.y + 0.5, origin.z,
                    new ItemStack(ItemRegistry.SCORCHED_REMNANT.get())
            );
            remnant.setDeltaMovement(
                    level.random.nextGaussian() * 0.25,
                    0.45 + level.random.nextDouble() * 0.15,
                    level.random.nextGaussian() * 0.25
            );
            remnant.lifespan = 120;
            level.addFreshEntity(remnant);
        }

        BlazeCoreEffectEntity effect = new BlazeCoreEffectEntity(
                level, origin.x, origin.y, origin.z);
        level.addFreshEntity(effect);

        if (level instanceof ServerLevel serverLvl) {
            effect.spawnFakeSoulFire(serverLvl, origin);
        }
    }
}