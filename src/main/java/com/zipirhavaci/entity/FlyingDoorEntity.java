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


public class FlyingDoorEntity extends Entity {

    // ── Synced data ──────────────────────────────────────────────────────────
    /** Kapı bloğunun global palette ID'si — renderer BlockState'e çevirir. */
    private static final EntityDataAccessor<Integer> BLOCK_STATE_ID =
            SynchedEntityData.defineId(FlyingDoorEntity.class, EntityDataSerializers.INT);

    /** Fırlatan oyuncunun seviyesi (1-5). */
    private static final EntityDataAccessor<Integer> SKILL_LEVEL =
            SynchedEntityData.defineId(FlyingDoorEntity.class, EntityDataSerializers.INT);

    /**
     * Üst yarı mı? (true = üst blok, menteşe pivotu alt bloktan farklıdır)
     * Renderer bunu kullanarak doğru pivot hesaplar.
     */
    private static final EntityDataAccessor<Boolean> IS_UPPER_HALF =
            SynchedEntityData.defineId(FlyingDoorEntity.class, EntityDataSerializers.BOOLEAN);

    // ── Fizik sabitleri ────────────
    private static final double GRAVITY  = 0.04;
    private static final double AIR_DRAG = 0.98;

    // ── Hasar sabitleri ───────────────────────────────────────────────────────
    private static final float  MAX_DAMAGE    = 5.0f;   // 2.5 kalp
    private static final float  MIN_DAMAGE    = 1.5f;   // 0.75 kalp
    private static final double MAX_SPEED_REF = 2.2;


    public float visualYaw       = 0f;
    public float prevVisualYaw   = 0f;
    public float visualPitch     = 0f;
    public float prevVisualPitch = 0f;
    public float visualRoll      = 0f;
    public float prevVisualRoll  = 0f;

    // ── Durum ─────────────────────────────────────────────────────────────────
    private UUID shooterUUID = null;
    private final Set<UUID> hitEntities = new HashSet<>();
    private int lifetime = 120; // 6 saniye

    // ── Dönüş hız bileşenleri — kapının "menteşe" dinamiği için ──────────────
    /**
     * Yatay hız bileşenine orantılı yaw spin hızı.
     * Kapının hızına göre menteşe etrafında ne kadar hızlı döndüğü.
     */
    private float yawSpinRate   = 0f;
    /**
     * Dikey hız bileşenine orantılı pitch spin hızı.
     * Kapının düşerken ileri-geri devrilme hızı.
     */
    private float pitchSpinRate = 0f;
    /** Menteşe tarafi sapması → her kapı biraz farklı yuvarlanır. */
    private float rollSpinRate  = 0f;

    // ── Constructor ──────────────────────────────────────────────────────────

    public FlyingDoorEntity(EntityType<? extends FlyingDoorEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = false; // Kapı uçarken çarpışma alanı yok — sadece hasar
    }

    /**
     * Fabrika metodu — DoorBlastHandler bu metodu çağırır.
     *
     * @param level       Sunucu dünyası
     * @param x,y,z       Spawn pozisyonu (bloğun merkezi)
     * @param state       Kapı bloğunun BlockState'i (renderer bunu çizer)
     * @param velocity    Başlangıç hız vektörü (DoorBlastHandler tarafından hesaplanır)
     * @param shooter     Ateşleyen oyuncu
     * @param skillLevel  1-5
     * @param isUpperHalf Üst yarı mı? (pivot hesabı için)
     */
    public static FlyingDoorEntity create(ServerLevel level,
                                          double x, double y, double z,
                                          BlockState state,
                                          Vec3 velocity,
                                          Player shooter,
                                          int skillLevel,
                                          boolean isUpperHalf) {
        FlyingDoorEntity entity = new FlyingDoorEntity(
                com.zipirhavaci.core.EntityRegistry.FLYING_DOOR.get(), level);

        entity.setPos(x, y, z);
        entity.setDeltaMovement(velocity);
        entity.shooterUUID = shooter.getUUID();

        entity.setBlockStateId(Block.getId(state));
        entity.setSkillLevel(skillLevel);
        entity.setUpperHalf(isUpperHalf);


        double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        entity.yawSpinRate   = (float)(hSpeed * 22.0f);

        entity.pitchSpinRate = (float)(Math.abs(velocity.y) * 14.0f);

        entity.rollSpinRate  = (float)(hSpeed * 6.0f);

        return entity;
    }

    // ── SynchedData ──────────────────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK_STATE_ID, Block.getId(
                net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()));
        this.entityData.define(SKILL_LEVEL, 1);
        this.entityData.define(IS_UPPER_HALF, false);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (--lifetime <= 0) {
            if (!this.level().isClientSide) breakAndDrop();
            this.discard();
            return;
        }

        // ── Yerçekimi ────────────────────────────────────────────────────────
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -GRAVITY, 0));
        }

        // ── Hareket ──────────────────────────────────────────────────────────
        this.move(MoverType.SELF, this.getDeltaMovement());

        // ── Hava direnci ─────────────────────────────────────────────────────
        this.setDeltaMovement(this.getDeltaMovement().scale(AIR_DRAG));

        // ── Yere çarpınca kırıl ──────────────────────────────────────────────
        if (this.onGround()) {
            if (!this.level().isClientSide) breakAndDrop();
            this.discard();
            return;
        }

        // ── Duvara çarptı (yatay collision) — yavaşlat, birkaç tick sonra kırıl
        if (this.horizontalCollision && !this.level().isClientSide) {
            // Momentum absorbe et, düş
            Vec3 vel = this.getDeltaMovement();
            this.setDeltaMovement(vel.x * 0.2, vel.y, vel.z * 0.2);
        }

        // ── Rotation güncelle ────────────────────────────────────────────────
        updateVisualRotation();

        // ── Çarpışma (sadece server) ─────────────────────────────────────────
        if (!this.level().isClientSide) {
            checkEntityCollisions();
        }
    }


    private void updateVisualRotation() {
        Vec3  vel  = this.getDeltaMovement();
        double spd = vel.length();

        prevVisualYaw   = visualYaw;
        prevVisualPitch = visualPitch;
        prevVisualRoll  = visualRoll;

        if (spd > 0.01) {
            // Spin oranları hızla kademeli olarak düşer (hava direnci etkisi)
            yawSpinRate   *= 0.97f;
            pitchSpinRate *= 0.97f;
            rollSpinRate  *= 0.97f;

            visualYaw   += yawSpinRate;
            visualPitch += pitchSpinRate;
            visualRoll  += rollSpinRate;
        }
    }


    private void breakAndDrop() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        BlockState state   = this.getDoorBlockState();
        BlockPos   landPos = this.blockPosition();


        sl.levelEvent(2001, landPos, Block.getId(state));


        Block.dropResources(state, sl, landPos, null);


        net.minecraft.world.level.block.SoundType snd = state.getSoundType();
        sl.playSound(null, landPos,
                snd.getBreakSound(),
                SoundSource.BLOCKS,
                snd.getVolume() * 1.1f,
                snd.getPitch() * (0.85f + (float)(Math.random() * 0.3f)));
    }

    /**
     * Kapı çarptığında hasar + knockback uygular.
     * Her entity'ye yalnızca 1 kez hasar verilir.
     */
    private void checkEntityCollisions() {
        AABB hitBox = this.getBoundingBox().inflate(0.15);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, hitBox);
        if (targets.isEmpty()) return;

        Vec3   vel = this.getDeltaMovement();
        double spd = vel.length();

        Player shooter = (shooterUUID != null)
                ? this.level().getPlayerByUUID(shooterUUID)
                : null;

        for (LivingEntity target : targets) {
            if (shooter != null && target.getUUID().equals(shooterUUID)) continue;
            if (hitEntities.contains(target.getUUID())) continue;

            hitEntities.add(target.getUUID());

            // ── Hasar: hıza orantılı ─────────────────────────────────────────
            float speedRatio = (float) Math.min(1.0, spd / MAX_SPEED_REF);
            float damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * speedRatio;

            DamageSource src = (shooter instanceof ServerPlayer sp)
                    ? target.level().damageSources().playerAttack(sp)
                    : target.level().damageSources().fallingBlock(this);

            target.hurt(src, damage);

            // ── Knockback: kapının geldiği yönde ─────────────────────────────
            int    lvl      = this.getSkillLevel();
            Vec3   kbDir    = vel.normalize();
            double kbHoriz  = 0.55 + (lvl * 0.07);
            double kbVert   = 0.35 + (lvl * 0.04);

            target.setDeltaMovement(
                    kbDir.x * kbHoriz,
                    kbVert,
                    kbDir.z * kbHoriz
            );
            target.hurtMarked = true;

            // ── Çarpışma sesi ─────────────────────────────────────────────────
            net.minecraft.world.level.block.SoundType soundType =
                    this.getDoorBlockState().getSoundType();
            this.level().playSound(null,
                    this.getX(), this.getY(), this.getZ(),
                    soundType.getBreakSound(),
                    SoundSource.BLOCKS,
                    soundType.getVolume() * 1.3f,
                    soundType.getPitch() * (0.9f + (float)(Math.random() * 0.2f)));


            breakAndDrop();
            this.discard();
            return;
        }
    }

    // ── Getter / Setter ──────────────────────────────────────────────────────

    public int  getBlockStateId()             { return this.entityData.get(BLOCK_STATE_ID); }
    public void setBlockStateId(int id)       { this.entityData.set(BLOCK_STATE_ID, id); }

    public BlockState getDoorBlockState() {
        return Block.stateById(this.getBlockStateId());
    }

    public int  getSkillLevel()               { return this.entityData.get(SKILL_LEVEL); }
    public void setSkillLevel(int lvl)        { this.entityData.set(SKILL_LEVEL, lvl); }

    public boolean isUpperHalf()              { return this.entityData.get(IS_UPPER_HALF); }
    public void    setUpperHalf(boolean val)  { this.entityData.set(IS_UPPER_HALF, val); }

    // ── NBT ──────────────────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BlockStateId", this.getBlockStateId());
        tag.putInt("SkillLevel",   this.getSkillLevel());
        tag.putInt("Lifetime",     this.lifetime);
        tag.putBoolean("UpperHalf", this.isUpperHalf());
        if (shooterUUID != null) tag.putUUID("ShooterUUID", shooterUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("BlockStateId")) setBlockStateId(tag.getInt("BlockStateId"));
        if (tag.contains("SkillLevel"))   setSkillLevel(tag.getInt("SkillLevel"));
        if (tag.contains("Lifetime"))     this.lifetime = tag.getInt("Lifetime");
        if (tag.contains("UpperHalf"))    setUpperHalf(tag.getBoolean("UpperHalf"));
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

        return EntityDimensions.fixed(1.0f, 1.0f);
    }
}