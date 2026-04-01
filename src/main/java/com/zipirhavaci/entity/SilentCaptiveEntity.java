package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SilentCaptiveEntity extends PathfinderMob {

    // ── Client/Server Senkronizasyonu (Renderer'ın haberdar olması için) ──────
    private static final EntityDataAccessor<Boolean> DATA_WANTS_HELP = SynchedEntityData.defineId(SilentCaptiveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_IS_FLEEING = SynchedEntityData.defineId(SilentCaptiveEntity.class, EntityDataSerializers.BOOLEAN);


    // ── State bayrakları ──────────────────────────────────────────────────────
    private int fleeTicksLeft = 0;

    // ── YENİ: İksir titreme sayacı ────────────────────────────────────────────
    private int trembleTicks = 0;
    private static final int TREMBLE_DURATION = 40; // 2 saniye

    // Olta takibi
    private boolean pulledByFishingRod  = false;
    private boolean isAngryAtPlayer = false;
    private int     fishingRodPullTimer = 0;


    // Drop tipi
    private DeathType lastDeathType = DeathType.NONE;

    public enum DeathType { NONE, SHARP, BLUNT, FISHING_FALL }

    // ── Constructor ───────────────────────────────────────────────────────────
    public SilentCaptiveEntity(EntityType<? extends SilentCaptiveEntity> type, Level level) {
        super(type, level);
        this.setCustomName(net.minecraft.network.chat.Component.literal("§5§oWretched Soul"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WANTS_HELP, true);
        this.entityData.define(DATA_IS_FLEEING, false);
    }

    // ── Attributes ────────────────────────────────────────────────────────────
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,           20.0)
                .add(Attributes.MOVEMENT_SPEED,       0.32)
                .add(Attributes.FOLLOW_RANGE,         24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE,  0.0);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        // İksire koşma eyleminin önceliği yüksek olmalı (0 en yüksek)
        this.goalSelector.addGoal(0, new SeekPotionGoal(this));
        this.goalSelector.addGoal(1, new FleeGoal(this));
        this.goalSelector.addGoal(2, new ApproachGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();

        // ── YENİ: Her tick yakın iksiri tara ──────────────────────────────────
        tickCheckNearbyPotion();

        if (!this.level().isClientSide) {
            // ── YENİ: Titreme sayacı ──────────────────────────────────────────
            if (trembleTicks > 0) {
                trembleTicks--;

                // Her 3 tick'te ruh partikülleri (titreme görsel geribildirimi)
                if (this.level() instanceof ServerLevel sl && trembleTicks % 3 == 0) {
                    sl.sendParticles(ParticleTypes.SOUL,
                            getX(), getY() + 0.9, getZ(),
                            3, 0.2, 0.2, 0.2, 0.01);
                }

                // Titreme bitti → LibratedSoul spawn et ve yok ol
                if (trembleTicks == 0) {
                    spawnLibratedSoul();
                    this.kill();
                }
            }

            // Kaçma sayacını azalt
            if (fleeTicksLeft > 0) {
                fleeTicksLeft--;
                if (!this.isFleeing()) this.setFleeing(true);
            } else {
                if (this.isFleeing()) this.setFleeing(false);
                fleeTicksLeft = 0;
            }
        }

        // Olta sayacı
        if (pulledByFishingRod) {
            fishingRodPullTimer++;
            if (fishingRodPullTimer > 200) {
                pulledByFishingRod  = false;
                fishingRodPullTimer = 0;
            }
        }
    }

    // ── YENİ: Yakın iksir tarama ──────────────────────────────────────────────
    /**
     * Her tick yakındaki REDEMPTIONS_LIGHT ItemEntity'lerini tarar.
     * Bulursa: iksiri kaldırır, içme sesi çalar, titreme sürecini başlatır.
     */
    private void tickCheckNearbyPotion() {
        if (trembleTicks > 0) return;          // Zaten içme sürecinde
        if (this.level().isClientSide) return; // Sadece sunucu tarafı

        List<ItemEntity> items = this.level().getEntitiesOfClass(
                ItemEntity.class,
                this.getBoundingBox().inflate(1.2),
                ie -> ie.getItem().getItem() == ItemRegistry.REDEMPTIONS_LIGHT.get()
        );

        if (!items.isEmpty()) {
            items.get(0).kill(); // İksiri kaldır

            trembleTicks = TREMBLE_DURATION;
            this.setWantsHelp(false);
            this.setFleeing(false);
            this.getNavigation().stop();

            // İçme sesi
            this.level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.GENERIC_DRINK,
                    SoundSource.NEUTRAL,
                    1.0f, 1.0f);
        }
    }

    // ── YENİ: LibratedSoul spawn ──────────────────────────────────────────────
    private void spawnLibratedSoul() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        LibratedSoulEntity soul = new LibratedSoulEntity(
                EntityRegistry.LIBRATED_SOUL.get(), sl);

        // 40 blok içindeki en yakın oyuncuyu bul
        Player nearestPlayer = sl.getNearestPlayer(this, 40.0D);
        float yaw;

        if (nearestPlayer != null) {
            // Oyuncuya doğru bakış açısını hesapla
            double dX = nearestPlayer.getX() - this.getX();
            double dZ = nearestPlayer.getZ() - this.getZ();
            yaw = (float) (Mth.atan2(dZ, dX) * (180.0D / Math.PI)) - 90.0F;
        } else {
            // Oyuncu yoksa rastgele bir açı belirle
            yaw = this.random.nextFloat() * 360.0F;
        }

        // Ruhun pozisyonunu ve hesaplanan bakış açısını ayarla
        soul.moveTo(getX(), getY(), getZ(), yaw, 0);
        soul.setYHeadRot(yaw);
        soul.setYBodyRot(yaw);

        sl.addFreshEntity(soul);
    }

    // ── Hurt ──────────────────────────────────────────────────────────────────
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.getEntity() instanceof Mob && !(source.getEntity() instanceof Player)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 1. Olta kontrolü (Bozulmadı)
        if (source.getDirectEntity() instanceof FishingHook) {
            this.pulledByFishingRod = true;
            this.fishingRodPullTimer = 0;
            return false;
        }

        // 2. Hasar işlemini gerçekleştir
        boolean wasHurt = super.hurt(source, amount);

        if (wasHurt) {
            if (source.getEntity() instanceof Player) {
                this.isAngryAtPlayer = true;
                this.setWantsHelp(true);
            }

            this.setFleeing(true);
            this.fleeTicksLeft = 60;

            this.setTarget(null);
            this.getNavigation().stop();
        }

        return wasHurt;
    }

    // ── Interaction (sağ tık) ─────────────────────────────────────────────────
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            com.zipirhavaci.network.PacketHandler.sendToPlayer(
                    (net.minecraft.server.level.ServerPlayer) player,
                    new com.zipirhavaci.network.OpenCaptiveDialogPacket(this.getId())
            );
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    public void onPlayerAcceptedHelp() {
        this.setWantsHelp(false);
        this.setFleeing(false);
        this.isAngryAtPlayer = false;
        this.fleeTicksLeft = 0;
        this.getNavigation().stop();
    }

    // ── State Getters & Setters (Synced) ──────────────────────────────────────
    public boolean wantsHelp() {
        return this.entityData.get(DATA_WANTS_HELP);
    }

    public void setWantsHelp(boolean value) {
        this.entityData.set(DATA_WANTS_HELP, value);
    }

    public boolean isFleeing() {
        return this.entityData.get(DATA_IS_FLEEING);
    }

    public void setFleeing(boolean value) {
        this.entityData.set(DATA_IS_FLEEING, value);
    }

    public boolean isAngryAtPlayer() {
        return this.isAngryAtPlayer;
    }

    // ── Death ─────────────────────────────────────────────────────────────────
    @Override
    public void die(DamageSource cause) {
        lastDeathType = resolveDeathType(cause);
        super.die(cause);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // Titreşim sonrası kill() ile ölüyorsa drop verme (LibratedSoul zaten Anima bırakıyor)
        if (trembleTicks == 0 && lastDeathType == DeathType.NONE) return;

        // Normal ölüm drop'ları (orijinal ile aynı)
        spawnAtLocation(new ItemStack(Items.AMETHYST_SHARD, 2 + random.nextInt(3)));
        spawnAtLocation(new ItemStack(Items.ECHO_SHARD, 1));
        spawnAtLocation(new ItemStack(Items.NETHER_STAR, 1));

        float roll = random.nextFloat();
        switch (lastDeathType) {
            case SHARP        -> { /* drop yok */ }
            case BLUNT        -> { if (roll < 0.50f) spawnAtLocation(new ItemStack(ItemRegistry.CURSED_BOOK.get(), 1)); }
            case FISHING_FALL -> { if (roll < 0.70f) spawnAtLocation(new ItemStack(ItemRegistry.CURSED_BOOK.get(), 1)); }
            default           -> { /* drop yok */ }
        }
    }

    private DeathType resolveDeathType(DamageSource source) {
        if (pulledByFishingRod && source.getMsgId().equals("fall")) return DeathType.FISHING_FALL;
        if (source.getDirectEntity() instanceof FishingHook)         return DeathType.FISHING_FALL;

        if (source.getEntity() instanceof Player player) {
            return isSharpWeapon(player.getMainHandItem()) ? DeathType.SHARP : DeathType.BLUNT;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
            return DeathType.SHARP;
        }

        String id = source.getMsgId();
        if (id.contains("explosion") || id.contains("blast") || id.contains("fire")
                || id.contains("burn") || id.contains("lava")
                || id.equals("inFire") || id.equals("onFire") || id.equals("fireball")) {
            return DeathType.SHARP;
        }

        return DeathType.NONE;
    }

    private boolean isSharpWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof SwordItem || item instanceof TridentItem
                || item instanceof net.minecraft.world.item.AxeItem
                || item == Items.CROSSBOW || item == Items.BOW;
    }

    // ── Misc ──────────────────────────────────────────────────────────────────
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean isAggressive()              { return false; }
    @Override public MobType getMobType()                { return MobType.UNDEFINED; }

    // ── NBT ───────────────────────────────────────────────────────────────────
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WantsHelp", this.wantsHelp());
        tag.putBoolean("IsFleeing", this.isFleeing());
        tag.putBoolean("PulledByFishing", this.pulledByFishingRod);
        tag.putInt("FishingTimer", this.fishingRodPullTimer);
        tag.putInt("TrembleTicks", this.trembleTicks); // YENİ
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("WantsHelp")) {
            this.setWantsHelp(tag.getBoolean("WantsHelp"));
        } else {
            this.setWantsHelp(true);
        }

        if (tag.contains("IsFleeing")) {
            this.setFleeing(tag.getBoolean("IsFleeing"));
        }

        this.pulledByFishingRod = tag.getBoolean("PulledByFishing");
        this.fishingRodPullTimer = tag.getInt("FishingTimer");
        this.trembleTicks = tag.getInt("TrembleTicks"); // YENİ
    }

    // ── AI GOAL'LAR ───────────────────────────────────────────────────────────
    static class FleeGoal extends Goal {
        private final SilentCaptiveEntity mob;

        FleeGoal(SilentCaptiveEntity mob) {
            this.mob = mob;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse()           { return mob.isFleeing(); }
        @Override public boolean canContinueToUse() { return mob.isFleeing(); }

        @Override
        public void tick() {
            Player nearest = mob.level().getNearestPlayer(mob, 24.0);
            if (nearest == null) return;

            Vec3 away       = mob.position().subtract(nearest.position()).normalize().scale(2.0);
            Vec3 fleeTarget = mob.position().add(away);
            mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.4);
            mob.getLookControl().setLookAt(
                    mob.getX() + away.x, mob.getEyeY(), mob.getZ() + away.z, 30F, 30F);
        }
    }

    static class ApproachGoal extends Goal {
        private final SilentCaptiveEntity mob;
        private Player target;
        private static final double STOP_DIST = 1.8;

        ApproachGoal(SilentCaptiveEntity mob) {
            this.mob = mob;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.mob.isFleeing()) return false;
            if (this.mob.isAngryAtPlayer()) return false;
            if (!this.mob.wantsHelp()) return false;

            this.target = this.mob.level().getNearestPlayer(this.mob, 16.0);
            return this.target != null && !this.target.isSpectator();
        }

        @Override
        public boolean canContinueToUse() {
            if (this.mob.isFleeing() || this.mob.isAngryAtPlayer()) return false;
            if (!this.mob.wantsHelp()) return false;
            return this.target != null && this.target.isAlive() && this.mob.distanceTo(this.target) < 20.0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
            this.target = null;
        }

        @Override
        public void tick() {
            if (this.target == null) return;
            if (this.mob.isFleeing()) {
                this.stop();
                return;
            }

            this.mob.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            double dist = this.mob.distanceTo(this.target);

            if (dist > STOP_DIST) {
                this.mob.getNavigation().moveTo(this.target, 1.0D);
            } else {
                this.mob.getNavigation().stop();
            }
        }
    }

    static class SeekPotionGoal extends Goal {
        private final SilentCaptiveEntity mob;
        private ItemEntity targetPotion;
        private int checkCooldown = 0;

        SeekPotionGoal(SilentCaptiveEntity mob) {
            this.mob = mob;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.trembleTicks > 0) return false;

            // Her tick taramak yerine 10 tick'te bir tara
            if (checkCooldown > 0) {
                checkCooldown--;
                return false;
            }
            checkCooldown = 10;

            List<ItemEntity> items = mob.level().getEntitiesOfClass(
                    ItemEntity.class,
                    mob.getBoundingBox().inflate(20.0), // 20 blok yarıçap
                    ie -> ie.getItem().getItem() == ItemRegistry.REDEMPTIONS_LIGHT.get()
            );

            if (!items.isEmpty()) {
                // En yakın olan iksiri hedef olarak seç
                items.sort(java.util.Comparator.comparingDouble(mob::distanceToSqr));
                this.targetPotion = items.get(0);
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return targetPotion != null && targetPotion.isAlive() && mob.trembleTicks == 0;
        }

        @Override
        public void start() {
            // Hedefe doğru koşma hızı (1.2D)
            mob.getNavigation().moveTo(targetPotion, 1.2D);
        }

        @Override
        public void tick() {
            if (targetPotion != null) {
                mob.getLookControl().setLookAt(targetPotion, 30.0F, 30.0F);
                if (mob.getNavigation().isDone()) {
                    mob.getNavigation().moveTo(targetPotion, 1.2D);
                }
            }
        }
    }

}