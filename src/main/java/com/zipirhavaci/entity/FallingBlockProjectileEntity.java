package com.zipirhavaci.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class FallingBlockProjectileEntity extends Entity {

    // ── Synced data ──────────────────────────────────────────────────────────
    /** Fırlatılan bloğun global palette ID'si — renderer BlockState'e çevirir. */
    private static final EntityDataAccessor<Integer> BLOCK_STATE_ID =
            SynchedEntityData.defineId(FallingBlockProjectileEntity.class, EntityDataSerializers.INT);

    /** Fırlatan oyuncunun seviyesi (1-5) — knockback skalası için. */
    private static final EntityDataAccessor<Integer> SKILL_LEVEL =
            SynchedEntityData.defineId(FallingBlockProjectileEntity.class, EntityDataSerializers.INT);

    // ── Fizik sabitleri (BlastTntEntity ile aynı) ─────────────────────────────
    private static final double GRAVITY      = 0.04;
    private static final double AIR_DRAG     = 0.98;

    // ── Hasar sabitleri ───────────────────────────────────────────────────────
    private static final float  MAX_DAMAGE    = 5.0f;   // 2.5 kalp
    private static final float  MIN_DAMAGE    = 1.5f;   // 0.75 kalp
    private static final double MAX_SPEED_REF = 2.2;    // ItemBlastHandler.MAX_SPEED

    // ── Rotation takibi (BlastTntEntity ile birebir aynı alan adları) ─────────
    /** Bu tick'teki yaw — renderer partialTick ile interpole eder. */
    public float visualYaw      = 0f;
    /** Önceki tick'teki yaw — smooth interpolasyon için. */
    public float prevVisualYaw  = 0f;
    /** Bu tick'teki pitch. */
    public float visualPitch    = 0f;
    /** Önceki tick'teki pitch. */
    public float prevVisualPitch = 0f;

    // ── Durum ─────────────────────────────────────────────────────────────────
    /** Fırlatan oyuncu UUID — hasar kaynağı + kendine vurma koruması. */
    private UUID shooterUUID = null;

    /** Aynı entity'ye iki kez hasar vermeyi önler. */
    private final Set<UUID> hitEntities = new HashSet<>();

    /** Otomatik yok-olma sayacı (100 tick = 5 saniye). */
    private int lifetime = 100;

    /** Önceki velocity — rotation hesabında kullanılır  */
    private Vec3 prevVelocity = Vec3.ZERO;

    // ── Constructor ──────────────────────────────────────────────────────────

    public FallingBlockProjectileEntity(EntityType<? extends FallingBlockProjectileEntity> type,
                                        Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }


    public static FallingBlockProjectileEntity create(ServerLevel level,
                                                      double x, double y, double z,
                                                      BlockState state,
                                                      Vec3 velocity,
                                                      Player shooter,
                                                      int skillLevel) {
        FallingBlockProjectileEntity entity = new FallingBlockProjectileEntity(
                com.zipirhavaci.core.EntityRegistry.FALLING_BLOCK_PROJECTILE.get(), level);

        entity.setPos(x, y, z);
        entity.setDeltaMovement(velocity);
        entity.prevVelocity = velocity;
        entity.shooterUUID  = shooter.getUUID();

        entity.setBlockStateId(Block.getId(state));
        entity.setSkillLevel(skillLevel);

        return entity;
    }

    // ── SynchedData ──────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK_STATE_ID, Block.getId(
                net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()));
        this.entityData.define(SKILL_LEVEL, 1);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // Yaşam süresi
        if (--lifetime <= 0) {
            if (!this.level().isClientSide) breakAndDrop();
            this.discard();
            return;
        }

        // ── Yerçekimi (BlastTntEntity ile aynı) ──────────────────────────────
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -GRAVITY, 0));
        }

        // ── Hareket ──────────────────────────────────────────────────────────
        this.move(MoverType.SELF, this.getDeltaMovement());

        // ── Hava direnci ─────────────────────────────────────────────────────
        this.setDeltaMovement(this.getDeltaMovement().scale(AIR_DRAG));

        // ── Yere çarpınca kırıl ───────────────────────────────────────────────
        if (this.onGround()) {
            if (!this.level().isClientSide) breakAndDrop();
            this.discard();
            return;
        }

        // ── Rotation (BlastTntEntity.updateVisualRotation() ile birebir) ─────
        updateVisualRotation();
        prevVelocity = this.getDeltaMovement();

        // ── Çarpışma kontrolü (sadece server) ────────────────────────────────
        if (!this.level().isClientSide) {
            checkEntityCollisions();
        }
    }

    /**
     * Velocity'den yaw ve pitch açılarını türetir.
     */
    private void updateVisualRotation() {
        Vec3 vel    = this.getDeltaMovement();
        double hSpd = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        prevVisualYaw   = visualYaw;
        prevVisualPitch = visualPitch;

        if (hSpd > 0.01 || Math.abs(vel.y) > 0.01) {
            double speed   = vel.length();
            float spinRate = (float)(speed * 18.0f);   // BlastTntEntity ile aynı katsayı
            visualYaw   += spinRate;
            visualPitch += spinRate * 0.5f;
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }


    private void breakAndDrop() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        BlockState state  = this.getProjectileBlockState();
        BlockPos   landPos = this.blockPosition();

        // Kırılma partikül efekti
        sl.levelEvent(2001, landPos, Block.getId(state));

        // Drop'ları spawn et — vanilla loot table, fortune 0, silk touch yok
        Block.dropResources(state, sl, landPos, null);
    }

    /**
     * Her entity'ye yalnızca 1 kez hasar verilir.
     */
    private void checkEntityCollisions() {
        AABB hitBox = this.getBoundingBox().inflate(0.12);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, hitBox);
        if (targets.isEmpty()) return;

        Vec3   vel = this.getDeltaMovement();
        double spd = vel.length();

        // Fırlatan oyuncuyu level'dan bul (hasar kaynağı için)
        Player shooter = (shooterUUID != null)
                ? this.level().getPlayerByUUID(shooterUUID)
                : null;

        for (LivingEntity target : targets) {
            // Kendine vurma koruması
            if (shooter != null && target.getUUID().equals(shooterUUID)) continue;
            if (hitEntities.contains(target.getUUID())) continue;

            hitEntities.add(target.getUUID());

            // ── Hasar: hıza orantılı (MIN … MAX) ─────────────────────────────
            float speedRatio = (float) Math.min(1.0, spd / MAX_SPEED_REF);
            float damageMultiplier = 1.0f; // Standart (Kum, Çakıl)
            BlockState state = this.getProjectileBlockState();

            if (state.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) {
                damageMultiplier = 2.5f;
            }

            else if (state.is(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE)) {
                damageMultiplier = 1.8f;
            }

            else if (state.is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)) {
                damageMultiplier = 0.6f;
            }


            float damage = (MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * speedRatio) * damageMultiplier;

            DamageSource src = (shooter instanceof ServerPlayer sp)
                    ? target.level().damageSources().playerAttack(sp)
                    : target.level().damageSources().fallingBlock(this);

            target.hurt(src, damage);

            // ── Knockback: bloğun geldiği yönde ──────────────────────────────
            int   lvl      = this.getSkillLevel();
            Vec3  kbDir    = vel.normalize();
            double kbHoriz = 0.55 + (lvl * 0.07);   // Lvl1=0.62 … Lvl5=0.90
            double kbVert  = 0.35 + (lvl * 0.04);   // Lvl1=0.39 … Lvl5=0.55

            target.setDeltaMovement(
                    kbDir.x * kbHoriz,
                    kbVert,
                    kbDir.z * kbHoriz
            );
            target.hurtMarked = true;

            // ── Çarpışma sesi: bloğun kendi SoundType'ından ──────────────────
            // getProjectileBlockState() sync'li field'dan okur — her blok kendi sesini çalar
            net.minecraft.world.level.block.SoundType soundType =
                    this.getProjectileBlockState().getSoundType();
            this.level().playSound(null,
                    this.getX(), this.getY(), this.getZ(),
                    soundType.getBreakSound(),   // Blok kırılma sesi çarpma anı için en uygun
                    SoundSource.BLOCKS,
                    soundType.getVolume() * 1.2f,
                    soundType.getPitch() * (0.9f + (float)(Math.random() * 0.2f)));

            // Çarptıktan sonra kırıl ve drop ver
            breakAndDrop();
            this.discard();
            return;
        }
    }

    // ── Getter / Setter ──────────────────────────────────────────────────────

    public int  getBlockStateId()           { return this.entityData.get(BLOCK_STATE_ID); }
    public void setBlockStateId(int id)     { this.entityData.set(BLOCK_STATE_ID, id); }

    public BlockState getProjectileBlockState() {
        return Block.stateById(this.getBlockStateId());
    }

    public int  getSkillLevel()             { return this.entityData.get(SKILL_LEVEL); }
    public void setSkillLevel(int lvl)      { this.entityData.set(SKILL_LEVEL, lvl); }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BlockStateId", this.getBlockStateId());
        tag.putInt("SkillLevel",   this.getSkillLevel());
        tag.putInt("Lifetime",     this.lifetime);
        if (shooterUUID != null) tag.putUUID("ShooterUUID", shooterUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BlockStateId")) setBlockStateId(tag.getInt("BlockStateId"));
        if (tag.contains("SkillLevel"))   setSkillLevel(tag.getInt("SkillLevel"));
        if (tag.contains("Lifetime"))     this.lifetime = tag.getInt("Lifetime");
        if (tag.hasUUID("ShooterUUID"))   this.shooterUUID = tag.getUUID("ShooterUUID");
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    // ── Network ──────────────────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    // ── Boyut ────────────────────────────────────────────────────────────────

    @Override
    public void refreshDimensions() {}

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.98f, 0.98f);
    }
}