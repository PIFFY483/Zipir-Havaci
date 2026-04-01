package com.zipirhavaci.entity;

import com.zipirhavaci.core.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import com.zipirhavaci.physics.MovementHandler;
import net.minecraft.world.level.block.TntBlock;
import com.zipirhavaci.entity.BlastItemEntity;
import com.zipirhavaci.entity.BlastTntEntity;
import com.zipirhavaci.entity.FallingBlockProjectileEntity;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ThrownShieldEntity extends ThrowableItemProjectile {

    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(ThrownShieldEntity.class, EntityDataSerializers.BOOLEAN);
    private float damageMultiplier = 1.0f;
    private BlockPos stuckPos = null;
    private int brokenGlassCount = 0; // Kırılan cam sayısını takip eder
    public int getJitterTicks() { return this.entityData.get(JITTER_TICKS); }
    public void setJitterTicks(int ticks) { this.entityData.set(JITTER_TICKS, ticks); }
    private int lavaTimer = 0; // Lav içinde kalma süresini tutar

    private Vec3 initialTargetPoint;

    public void setInitialTarget(Vec3 pos) {
        this.initialTargetPoint = pos;
    }

    private boolean preventAutoWaterLogic = false;


    private ItemStack originalShieldItem = new ItemStack(Items.SHIELD);

    public ThrownShieldEntity(EntityType<? extends ThrownShieldEntity> type, Level level) {
        super(type, level);
    }

    public ThrownShieldEntity(Level level, LivingEntity shooter, float charge) {
        super(EntityRegistry.THROWN_SHIELD.get(), shooter, level);
        this.damageMultiplier = charge;


        if (!level.isClientSide && shooter instanceof net.minecraft.server.level.ServerPlayer sPlayer) {
            // Sunucuya "En son fırlatılan kalkan bu"
            com.zipirhavaci.core.physics.SoulBondHandler.registerThrownShield(sPlayer.getUUID(), this);
        }

        if (level.isClientSide) {
            //  ekranına "İp bu kalkana bağlanacak"
            com.zipirhavaci.client.KeyInputHandlerEvents.lastThrownShield = this;
        }
        // ---------------------------------------------------------
    }


    public void setShieldItem(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            this.originalShieldItem = stack.copy();
        }
    }

    public ItemStack getShieldItem() {
        return this.originalShieldItem;
    }

    @Override
    public ItemStack getItem() {
        // Bu satır olmazsa kalkan havada görünmez veya varsayılan boş kalkan görünür
        return this.originalShieldItem;
    }


    @Override
    public boolean canBeCollidedWith() {
        return true; // Bu olmazsa sağ tık kalkana çarpmadan içinden geçer
    }

    @Override
    public float getPickRadius() {

        return 0.08F;
    }


    @Override
    public boolean isPickable() {
        return true; // Kalkanın fare ile seçilebilir olmasını sağlar
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(STUCK, false);
        this.entityData.define(JITTER_TICKS, 0);
    }


    @Override
    public boolean isPushedByFluid() {

        return !this.isStuck();
    }

    @Override
    protected void doWaterSplashEffect() {

    }

    @Override
    public boolean isInWater() {
        // Eğer spamı engelleme modundaysak, oyuna "Suda değilim" yalanını at.
        if (preventAutoWaterLogic) return false;

        // Diğer durumlarda gerçeği söyle
        return super.isInWater();
    }


    public boolean isStuck() {
        return this.entityData.get(STUCK);
    }

    private static final EntityDataAccessor<Integer> JITTER_TICKS = SynchedEntityData.defineId(ThrownShieldEntity.class, EntityDataSerializers.INT);

    public void setStuck(boolean stuck) {
        this.entityData.set(STUCK, stuck);
    }

    // ... STUCK verisi ve diğer tanımlar ...
    @Override
    public void tick() {

        if (this.isRemoved()) return;
        // --- 0. RUH BAĞI VE KAYIT SİSTEMİ  ---
        if (this.tickCount < 5 && (com.zipirhavaci.client.KeyInputHandlerEvents.lastThrownShield != this)) {
            if (!this.level().isClientSide && this.getOwner() instanceof net.minecraft.server.level.ServerPlayer sPlayer) {
                com.zipirhavaci.core.physics.SoulBondHandler.registerThrownShield(sPlayer.getUUID(), this);
            }
            if (this.level().isClientSide && this.getOwner() != null) {
                if (this.getOwner() == net.minecraft.client.Minecraft.getInstance().player) {
                    com.zipirhavaci.client.KeyInputHandlerEvents.lastThrownShield = this;
                }
            }
        }

        // --- 1. AGRESİF NİŞAN DÜZELTME  ---
        if (!this.level().isClientSide && this.tickCount <= 10 && this.getOwner() != null && this.initialTargetPoint != null) {
            Vec3 currentVel = this.getDeltaMovement();
            double speed = currentVel.length();
            Vec3 targetDir = this.initialTargetPoint.subtract(this.position()).normalize();
            float interpolation = (11 - this.tickCount) * 0.015f;
            Vec3 newVel = currentVel.scale(1.0 - interpolation).add(targetDir.scale(speed * interpolation));
            this.setDeltaMovement(newVel);
            this.hasImpulse = true;
        }

        this.preventAutoWaterLogic = true;
        super.tick(); // ANA FİZİK

        // --- [YENİ] AKILLI STUCK KONTROLÜ  ---
        if (!this.level().isClientSide && this.isStuck() && this.stuckPos != null) {
            BlockState currentState = this.level().getBlockState(this.stuckPos);

            if (currentState.isAir() || currentState.is(Blocks.WATER)) {
                this.setStuck(false);
                this.setNoGravity(false);
                this.stuckPos = null;
                this.hasImpulse = true; // Senkronizasyon mühürü
            } else {
                double distSq = this.distanceToSqr(Vec3.atCenterOf(this.stuckPos));
                if (distSq > 1.44) {
                    this.setStuck(false);
                    this.setNoGravity(false);
                    this.stuckPos = null;
                    this.hasImpulse = true; // Hareketlendiğini bildir
                } else if (distSq > 0.001) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.hasImpulse = true; // Durduğunu bildir
                }
            }
        }


        // --- 2. LAV KONTROLÜ  ---
        if (this.isInLava()) {
            lavaTimer++;
            if (this.level().isClientSide && this.level().random.nextInt(3) == 0) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        this.getX(), this.getY(), this.getZ(), 0, 0.1, 0);
            }
            if (lavaTimer >= 200) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.GENERIC_BURN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                this.discard();
                return;
            }
        } else {
            lavaTimer = 0;
        }

        this.preventAutoWaterLogic = false;

        // --- 3. JITTER VE SU ETKİSİ  ---
        if (this.getJitterTicks() > 0) {
            this.setJitterTicks(this.getJitterTicks() - 1);
        }

        if (this.isInWater() && !this.isStuck()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        }

        if (this.isInWater()) {
            if (this.isOnFire()) {
                this.clearFire();
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS, 0.7F, 1.6F);
            }
            if (this.level().isClientSide) {
                float chance = this.isStuck() ? 0.05F : 0.15F;
                if (this.level().random.nextFloat() < chance) {
                    this.level().addParticle(ParticleTypes.BUBBLE,
                            this.getX(), this.getY(), this.getZ(),
                            0, (this.isStuck() ? 0 : 0.02), 0);
                }
            }
        }

        // --- 4. SU SEKTİRME VE KAYMA  ---
        if (!this.isStuck()) {
            BlockPos posBelow = new BlockPos((int)this.getX(), (int)(this.getY() - 0.2), (int)this.getZ());
            BlockState blockBelow = this.level().getBlockState(posBelow);
            if (blockBelow.is(Blocks.WATER) && this.level().getBlockState(posBelow.above()).isAir()) {
                Vec3 movement = this.getDeltaMovement();
                double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
                double verticalSpeed = -movement.y;

                if (verticalSpeed > 0 && (verticalSpeed / horizontalSpeed) < 0.57 && horizontalSpeed > 0.25) {
                    this.setDeltaMovement(movement.x * 0.7, Math.abs(movement.y) * 0.45, movement.z * 0.7);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1.0F, 1.5F);
                    for (int i = 0; i < 5; i++) {
                        this.level().addParticle(ParticleTypes.SPLASH,
                                this.getX() + (this.random.nextDouble() - 0.5),
                                this.getY(),
                                this.getZ() + (this.random.nextDouble() - 0.5),
                                movement.x, 0.5, movement.z);
                    }
                } else if (horizontalSpeed > 0.15) {
                    this.setDeltaMovement(movement.x * 0.96, -0.005, movement.z * 0.96);
                    if (this.tickCount % 12 == 0 && this.level().isClientSide) {
                        this.level().addParticle(ParticleTypes.SMALL_FLAME,
                                this.getX(), this.getY(), this.getZ(), 0, 0.02, 0);
                    }
                }
            }
        }

        // --- 5. GÖRSEL EFEKTLER  ---
        if (!this.isStuck() && this.level().isClientSide) {
            Vec3 mov = this.getDeltaMovement();
            float speed = (float) mov.length();
            if (speed > 0.5) {
                for (int j = 0; j < 2; j++) {
                    this.level().addParticle(ParticleTypes.END_ROD,
                            this.getX() - mov.x * j, this.getY() - mov.y * j, this.getZ() - mov.z * j,
                            0, 0.01, 0);
                }
            }
            if (this.tickCount % 4 == 0 && speed > 0.3) {
                for (int i = 0; i < 12; i++) {
                    double angle = i * Math.PI / 6.0;
                    double offsetX = Math.cos(angle) * 0.7;
                    double offsetY = Math.sin(angle) * 0.7;
                    this.level().addParticle(ParticleTypes.FIREWORK,
                            this.getX() - mov.x * 0.5 + offsetX, this.getY() + offsetY, this.getZ() - mov.z * 0.5,
                            offsetX * 0.05, offsetY * 0.05, 0);
                }
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                        0.5F, 1.2F + speed * 0.3F, false);
            }
        }
    }


    @Override
    protected void onHitBlock(BlockHitResult result) {
        BlockPos pos     = result.getBlockPos();
        BlockState state = this.level().getBlockState(pos);

        if (state.is(Blocks.WATER) || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS) || state.isAir()) return;

        // === HASAR SİSTEMİ ===
        this.applyCollisionDamage();
        if (this.isRemoved()) return;

        Vec3 movement  = this.getDeltaMovement();
        double speed   = movement.length();
        Direction face = result.getDirection();
        Vec3 normal    = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());

        // ── 0. SU ÜSTÜNDE TAŞ SEKTİRME ───────────────────────────────────────
        if (state.is(Blocks.WATER)) {
            BlockPos abovePos = pos.above();
            if (this.level().getBlockState(abovePos).isAir()) {
                double vv = -movement.y;
                double hv = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
                if (vv > 0 && (vv / hv) < 0.57) {
                    this.setDeltaMovement(movement.x, Math.abs(movement.y) * 0.6, movement.z);
                    if (this.level().isClientSide) {
                        for (int i = 0; i < 12; ++i) {
                            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                    this.getX(), this.getY(), this.getZ(),
                                    (random.nextDouble() - 0.5) * 0.3, 0.15,
                                    (random.nextDouble() - 0.5) * 0.3);
                        }
                    }
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1.2F, 1.4F);
                    return;
                }
            }
        }

        if (state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.isAir()) return;

        // ── 1. ÇATLAMA SİSTEMİ ───────────────────────────────────────────────
        if (!this.level().isClientSide) {
            int uniqueCrackId = this.getId() + this.level().random.nextInt(10000);
            int crackStage    = (int) Math.min(9, 3 + (speed * 2));
            this.level().destroyBlockProgress(uniqueCrackId, pos, crackStage);
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                if (this.level() != null && this.level().getServer() != null) {
                    this.level().getServer().execute(
                            () -> this.level().destroyBlockProgress(uniqueCrackId, pos, -1));
                }
            }, 20, java.util.concurrent.TimeUnit.SECONDS);
        }

        // ── 2. CAM KIRMA ──────────────────────────────────────────────────────
        boolean isPane = state.getBlock() instanceof IronBarsBlock
                || state.getBlock().getName().getString().toLowerCase().contains("pane");
        if (state.is(net.minecraft.tags.BlockTags.IMPERMEABLE) || isPane) {
            if (this.brokenGlassCount < (isPane ? 10 : 4) && speed > (isPane ? 0.25 : 0.45)) {
                this.level().destroyBlock(pos, true);
                this.brokenGlassCount++;
                this.setDeltaMovement(movement.scale(0.92));
                return;
            }
        }
        this.brokenGlassCount = 0;


        // ════════════════════════════════════════════════════════════════════════
        if (!this.level().isClientSide
                && this.level() instanceof net.minecraft.server.level.ServerLevel serverLvl
                && MovementHandler.isDoor(state)) {

            // Alt yarının BlockPos'unu bul
            BlockPos bottomPos;
            net.minecraft.world.level.block.state.BlockState bottomState;
            net.minecraft.world.level.block.state.BlockState topState;

            boolean hasHalf = state.hasProperty(net.minecraft.world.level.block.DoorBlock.HALF);
            if (hasHalf && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
                bottomPos   = pos.below();
                bottomState = serverLvl.getBlockState(bottomPos);
                topState    = state;
            } else {
                bottomPos   = pos;
                bottomState = state;
                topState    = hasHalf ? serverLvl.getBlockState(pos.above())
                        : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }

            boolean isIron = MovementHandler.isIronLikeDoor(bottomState);
            MovementHandler.triggerDoorShake(serverLvl, bottomPos, bottomState, topState, isIron);

            // Kalkan kapıdan seker
            playImpactSound(state, speed);
            if (this.level().isClientSide) spawnImpactParticles(state, face);
            double reflectDot = movement.dot(normal);
            this.setDeltaMovement(movement.subtract(normal.scale(2 * reflectDot)).scale(0.55));

            // Kalkan çarptıktan sonra yakındaki drop itemleri fırlat
            if (!this.level().isClientSide) launchNearbyItems(movement, speed);
            return;
        }

        // ════════════════════════════════════════════════════════════════════════
        //   TNT BLOĞU (tutuşmamış) → tutuştur + BlastTntEntity olarak fırlat
        // ════════════════════════════════════════════════════════════════════════
        if (!this.level().isClientSide && state.getBlock() instanceof TntBlock && speed > 0.4) {
            // Bloğu dünyadan kaldır, PrimedTnt oluşturup BlastTntEntity'e çevir
            this.level().removeBlock(pos, false);

            // Sahte PrimedTnt oluştur — fromPrimedTnt için gerekli
            net.minecraft.world.entity.item.PrimedTnt tempTnt =
                    new net.minecraft.world.entity.item.PrimedTnt(
                            this.level(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            this.getOwner() instanceof net.minecraft.world.entity.LivingEntity le ? le : null);
            // Fuse 80 tick (vanilla)

            Vec3 launchDir = movement.normalize();
            double power   = Math.max(0.5, speed * 0.9);
            Vec3 vel = new Vec3(launchDir.x * power, Math.abs(launchDir.y) * power + 0.12, launchDir.z * power);

            BlastTntEntity blastTnt = BlastTntEntity.fromPrimedTnt(tempTnt, vel);
            this.level().addFreshEntity(blastTnt);

            this.level().playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);

            // Kalkan seker
            playImpactSound(state, speed);
            if (this.level().isClientSide) spawnImpactParticles(state, face);
            double reflectDot = movement.dot(normal);
            this.setDeltaMovement(movement.subtract(normal.scale(2 * reflectDot)).scale(0.50));
            return;
        }

        // ════════════════════════════════════════════════════════════════════════
        //   FALLING BLOCK (yerleşik kum/çakıl vb.)  uçur
        // ════════════════════════════════════════════════════════════════════════
        if (!this.level().isClientSide
                && state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock
                && this.level() instanceof net.minecraft.server.level.ServerLevel serverLvl2) {

            double power   = Math.max(0.4, speed * 0.85);
            Vec3 launchDir = movement.normalize();
            Vec3 vel = new Vec3(launchDir.x * power, Math.abs(launchDir.y) * power + 0.18, launchDir.z * power);

            this.level().removeBlock(pos, false);
            FallingBlockProjectileEntity proj = FallingBlockProjectileEntity.create(
                    serverLvl2,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    state, vel,
                    this.getOwner() instanceof net.minecraft.world.entity.player.Player p ? p : null,
                    3);
            this.level().addFreshEntity(proj);

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);

            playImpactSound(state, speed);
            if (this.level().isClientSide) spawnImpactParticles(state, face);
            double reflectDot = movement.dot(normal);
            this.setDeltaMovement(movement.subtract(normal.scale(2 * reflectDot)).scale(0.50));
            return;
        }

        // ── 3. AÇI VE KARAR ───────────────────────────────────────────────────
        boolean canStick     = isStuckable(state);
        boolean isSharpAngle = Math.abs(movement.normalize().dot(normal)) < 0.62;

        // ── 4. SAPLANMA ───────────────────────────────────────────────────────
        if ((canStick && !isSharpAngle) || speed < 0.15) {
            if (!this.level().isClientSide) {
                this.setStuck(true);
                this.setJitterTicks(16);
                playImpactSound(state, speed);
                if (!canStick) {
                    this.level().broadcastEntityEvent(this, (byte) 3);
                }
                this.stuckPos = pos;
                this.setNoGravity(true);
                this.setDeltaMovement(Vec3.ZERO);
                this.setPos(result.getLocation().x + face.getStepX() * 0.1,
                        result.getLocation().y + face.getStepY() * 0.1,
                        result.getLocation().z + face.getStepZ() * 0.1);

                // Saplanma noktası yakınındaki itemleri de fırlat
                launchNearbyItems(movement, speed);
            }
            if (this.level().isClientSide && canStick) {
                spawnImpactParticles(state, face);
            }
            return;
        }

        // ── 5. SEKİŞ ─────────────────────────────────────────────────────────
        this.setStuck(false);
        playImpactSound(state, speed);
        if (this.level().isClientSide) spawnImpactParticles(state, face);

        double friction   = canStick ? 0.3 : 0.5;
        double reflectDot = movement.dot(normal);
        this.setDeltaMovement(movement.subtract(normal.scale(2 * reflectDot)).scale(friction));

        // Sekiş noktasındaki itemleri fırlat
        if (!this.level().isClientSide) launchNearbyItems(movement, speed);
    }

    // onHitBlock içindeki ses kısmını şu şekilde güncelle:
    private void playImpactSound(BlockState state, double speed) {
        boolean stuck = this.isStuck();
        float volume = stuck ? 1.3f : (float) Math.min(speed, 1.0);
        float pitch = (float) (0.8 + (speed * 0.2));

        // --- 1. ÖZEL DOKU KONTROLLERİ ---
        boolean isHoney = state.is(Blocks.HONEY_BLOCK);
        boolean isSlime = state.is(Blocks.SLIME_BLOCK);
        // Kum tag'ine sahip tüm blokları (kum, kırmızı kum vb.) yakala
        boolean isSand = state.is(net.minecraft.tags.BlockTags.SAND);
        boolean isStuckable = isStuckable(state);

        if (!isStuckable && !isHoney && !isSlime && !isSand) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.12f, 2.0F);
        }

        // --- 2. YUMUŞAK YÜZEY SESLERİ ---
        if (isHoney) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, volume, pitch);
        } else if (isSlime) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, volume, pitch);
        } else if (isSand) {
            // Kuma çarpınca o meşhur kum serpme/koyma sesi
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.SAND_PLACE, SoundSource.PLAYERS, volume, pitch);
        }

        // ---  REZONANS SİSTEMİ ---
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BAMBOO_WOOD_PLACE, SoundSource.PLAYERS, volume * 1.1f, pitch - 0.2f);

        if (stuck) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BAMBOO_WOOD_STEP, SoundSource.PLAYERS, volume * 0.8f, pitch + 0.4f);

            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                if (this.level() != null) {
                    this.level().getServer().execute(() -> {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.BAMBOO_WOOD_STEP, SoundSource.PLAYERS, volume * 0.5f, pitch + 0.8f);
                    });
                }
            }, 8, java.util.concurrent.TimeUnit.MILLISECONDS);

            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                if (this.level() != null) {
                    this.level().getServer().execute(() -> {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.BAMBOO_WOOD_STEP, SoundSource.PLAYERS, volume * 0.2f, pitch + 1.2f);
                    });
                }
            }, 16, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.BAMBOO_WOOD_STEP, SoundSource.PLAYERS, volume * 0.8f, pitch + 0.6f);
        }

        // Toprak/Kar dokusu
        if (state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SNOW)) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, volume * 0.5f, pitch);
        }
    }

    private void spawnImpactParticles(BlockState state, Direction face) {
        if (this.level().isClientSide) {

            for (int k = 0; k < 25; k++) { // Miktar artırıldı

                double spawnX = this.getX() + face.getStepX() * 0.1;
                double spawnY = this.getY() + face.getStepY() * 0.1;
                double spawnZ = this.getZ() + face.getStepZ() * 0.1;

                double vx = face.getStepX() * 0.2 + (random.nextDouble() - 0.5) * 0.2;
                double vy = face.getStepY() * 0.2 + (random.nextDouble() - 0.5) * 0.2;
                double vz = face.getStepZ() * 0.2 + (random.nextDouble() - 0.5) * 0.2;

                this.level().addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                        spawnX, spawnY, spawnZ, vx, vy, vz);
            }

            // --- 2. HAVA ŞOKU VE BULUTLAR
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI * 2 / 20;
                double dx = Math.cos(angle) * 0.2;
                double dy = Math.sin(angle) * 0.2;

                Vec3 particlePos;

                if (face.getAxis() == Direction.Axis.Y) {
                    particlePos = new Vec3(dx, 0.05 * face.getStepY(), dy);
                } else if (face.getAxis() == Direction.Axis.X) {
                    particlePos = new Vec3(0.05 * face.getStepX(), dx, dy);
                } else {
                    particlePos = new Vec3(dx, dy, 0.05 * face.getStepZ());
                }

                this.level().addParticle(ParticleTypes.CLOUD,
                        this.getX(), this.getY(), this.getZ(),
                        particlePos.x, particlePos.y, particlePos.z);

                this.level().addParticle(ParticleTypes.FIREWORK,
                        this.getX(), this.getY(), this.getZ(),
                        particlePos.x * 0.8, particlePos.y * 0.8, particlePos.z * 0.8);


                if (!isStuckable(state)) {
                    if (this.level().random.nextFloat() > 0.7f) {
                        this.level().addParticle(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
                    }
                }
            }

            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.2F, 2.0F, false);
        }
    }


    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.isStuck()) {
            if (!this.level().isClientSide) {
                // Yeni kalkan oluşturmak yerine sakladığımız orijinal kalkanı ver.
                ItemStack shieldStack = this.getShieldItem();

                if (player.getInventory().add(shieldStack)) {
                    this.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                    this.discard();
                } else {
                    this.spawnAtLocation(shieldStack);
                    this.discard();
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    private boolean isStuckable(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LOGS) ||
                state.is(net.minecraft.tags.BlockTags.PLANKS) ||
                state.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS) ||
                state.is(net.minecraft.tags.BlockTags.WOODEN_SLABS) ||
                state.is(net.minecraft.tags.BlockTags.WOODEN_FENCES) ||
                state.is(net.minecraft.tags.BlockTags.DIRT) ||
                state.is(Blocks.DIRT_PATH) ||
                state.is(net.minecraft.tags.BlockTags.LEAVES) ||
                state.is(net.minecraft.tags.BlockTags.SNOW) ||
                state.is(net.minecraft.tags.BlockTags.WOOL) ||
                state.is(Blocks.HAY_BLOCK) ||
                state.is(net.minecraft.tags.BlockTags.STAIRS) ||
                state.is(net.minecraft.tags.BlockTags.SLABS) ||
                state.is(Blocks.ICE) ||
                state.is(Blocks.PACKED_ICE) ||
                state.is(Blocks.BLUE_ICE) ||
                state.is(Blocks.FROSTED_ICE);
    }

    @Override protected Item getDefaultItem() { return Items.SHIELD; }
    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.isStuck()) return;

        this.applyCollisionDamage();
        if (this.isRemoved()) return;

        net.minecraft.world.entity.Entity victim = result.getEntity();
        Vec3 movement = this.getDeltaMovement();
        double speed  = movement.length();

        // ════════════════════════════════════════════════════════════════════════
        //                 FallingBlockProjectileEntity
        // ════════════════════════════════════════════════════════════════════════
        if (victim instanceof FallingBlockProjectileEntity fallingProj) {
            if (!this.level().isClientSide) {
                Vec3 newVel = movement.normalize().scale(Math.min(speed * 1.1, 2.5)).add(0, 0.12, 0);
                fallingProj.setDeltaMovement(newVel);
                fallingProj.hasImpulse = true;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
            this.setDeltaMovement(movement.scale(-0.45).add(0, 0.15, 0));
            return;
        }

        // ════════════════════════════════════════════════════════════════════════
        //   Vanilla FallingBlockEntity — taş, çakıl, kum, HERHANGI bir düşen blok
        //    (FallingBlock subclass kontrolü YOK — her FallingBlockEntity çalışır)
        // ════════════════════════════════════════════════════════════════════════
        if (victim instanceof FallingBlockEntity vanillaFalling
                && vanillaFalling.getBlockState().getBlock()
                instanceof net.minecraft.world.level.block.FallingBlock) {

            if (!this.level().isClientSide) {
                Vec3 newVel = movement.normalize()
                        .scale(Math.min(speed * 0.95, 2.3))
                        .add(0, 0.10, 0);
                vanillaFalling.setDeltaMovement(newVel);
                vanillaFalling.hasImpulse = true;

                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.SAND_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1.3F);
            }
            this.setDeltaMovement(movement.scale(-0.45).add(0, 0.15, 0));
            return;
        }

        // ════════════════════════════════════════════════════════════════════════
        //   BlastTntEntity (senin uçan TNT entity'n)
        // ════════════════════════════════════════════════════════════════════════
        if (victim instanceof BlastTntEntity blastTnt) {
            if (!this.level().isClientSide) {
                Vec3 newVel = movement.normalize().scale(Math.min(speed * 1.2, 2.5)).add(0, 0.08, 0);
                blastTnt.setDeltaMovement(newVel);
                blastTnt.hasImpulse = true;
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.5F, 1.5F);
            }
            this.setDeltaMovement(movement.scale(-0.40).add(0, 0.20, 0));
            return;
        }

        // ════════════════════════════════════════════════════════════════════════
        //   Vanilla PrimedTnt (tutuşmuş, ama henüz BlastTnt değil)
        //           BlastTntEntitye çevir ve fırlat
        // ════════════════════════════════════════════════════════════════════════
        if (victim instanceof PrimedTnt primedTnt) {
            if (!this.level().isClientSide) {
                Vec3 newVel = movement.normalize().scale(Math.min(speed * 1.1, 2.2)).add(0, 0.08, 0);
                BlastTntEntity blastTnt = BlastTntEntity.fromPrimedTnt(primedTnt, newVel);
                primedTnt.discard();
                this.level().addFreshEntity(blastTnt);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_HIT, SoundSource.PLAYERS, 0.5F, 1.5F);
            }
            this.setDeltaMovement(movement.scale(-0.40).add(0, 0.20, 0));
            return;
        }

        // ──────────────────────

        if (!this.level().isClientSide && com.zipirhavaci.physics.VehiclePushHandler.tryPushVehicle(victim, movement.normalize(), speed * 1.1)) {
            this.setDeltaMovement(movement.scale(-0.45).add(0, 0.18, 0));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.WOOD_HIT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.1F);
            return;
        }

        float totalDamage = (float) (1.0f + (speed * 2.1f));

        if (victim instanceof net.minecraft.world.entity.LivingEntity livingVictim) {
            float toughness = (float) livingVictim.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
            totalDamage *= 1.0f + (toughness * 0.04f);
        }

        float rawPart    = totalDamage * 0.04f;
        float normalPart = totalDamage * 0.96f;
        victim.hurt(this.damageSources().thrown(this, this.getOwner()), normalPart);
        victim.hurt(this.level().damageSources().genericKill(), rawPart);

        if (!this.level().isClientSide && victim instanceof net.minecraft.world.entity.LivingEntity livingVictim) {
            for (net.minecraft.world.item.ItemStack armorStack : livingVictim.getArmorSlots()) {
                if (!armorStack.isEmpty() && armorStack.isDamageableItem()) {
                    int dmg = Math.max(1, (int) (armorStack.getMaxDamage() * 0.03));
                    armorStack.hurtAndBreak(dmg, livingVictim,
                            p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
                }
            }
            double knockbackStrength = speed * 1.5;
            livingVictim.knockback(knockbackStrength, -movement.x, -movement.z);
        }

        this.setDeltaMovement(movement.scale(-0.4D).add(0, 0.2D, 0));
    }

    public void applySuperSkillPush(Vec3 direction, double power) {
        if (!this.level().isClientSide) {
            // 1. Kalkan saplanmışsa onu serbest bırak
            if (this.isStuck()) {
                this.setStuck(false);
                this.stuckPos = null;
                this.setNoGravity(false);
            }

            // 2. Havalanma ve Fırlama İvmesi
            // Kalkan hafif bir obje olduğu için power değerini biraz yüksek tut
            Vec3 newVel = new Vec3(
                    direction.x * power * 1.2,
                    Math.abs(direction.y) * power * 0.5 + 0.25,
                    direction.z * power * 1.2
            );

            this.setDeltaMovement(newVel);
            this.hasImpulse = true; // İstemciyle (Client) anında senkronize olması için şart

            // 3. Şiddetli rüzgar / metale vurma sesi
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.ANVIL_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.8F);
        }
    }


// ══════════════════════════════════════════════════════════════════════════
    private void launchNearbyItems(Vec3 shieldVel, double shieldSpeed) {
        // Sadece sunucu, sadece hareket halindeyken anlamlı
        if (this.level().isClientSide) return;
        if (shieldSpeed < 0.3) return;

        final double SCAN_RADIUS    = 2.5;
        final double SCAN_RADIUS_SQ = SCAN_RADIUS * SCAN_RADIUS;
        final double MAX_ITEM_SPEED = 2.0;

        AABB box = this.getBoundingBox().inflate(SCAN_RADIUS);
        List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, box);
        if (items.isEmpty()) return;

        Vec3 dir    = shieldVel.normalize();
        Vec3 origin = this.position();

        for (ItemEntity item : items) {
            // Zaten BlastItem ise atla
            if (item instanceof BlastItemEntity) continue;

            Vec3   toItem  = item.position().subtract(origin);
            double distSq  = toItem.lengthSqr();
            if (distSq > SCAN_RADIUS_SQ || distSq < 0.01) continue;

            double dist    = Math.sqrt(distSq);
            double dot     = toItem.normalize().dot(dir);
            // Arkada kalan itemleri da az miktarda etkile (patlama dalgası hissi)
            if (dot < -0.3) continue;

            // Güç: kalkan hızı × mesafe faktörü × yön faktörü
            double distFactor = Math.max(0.25, 1.0 - (dist / SCAN_RADIUS) * 0.7);
            double dotFactor  = Math.max(0.2, dot);
            double spd        = shieldSpeed * dotFactor * distFactor * 0.75;
            spd               = Math.min(spd, MAX_ITEM_SPEED);

            Vec3 vel = new Vec3(
                    dir.x * spd,
                    Math.abs(dir.y) * spd + 0.20,
                    dir.z * spd
            );

            net.minecraft.world.item.ItemStack stack = item.getItem().copy();
            item.discard();

            BlastItemEntity blast = new BlastItemEntity(
                    this.level(),
                    item.getX(), item.getY(), item.getZ(),
                    stack, vel);
            this.level().addFreshEntity(blast);
        }
    }

    // 1. Veriyi Diske Kaydet (Dünyadan çıkarken)
    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.put("OriginalShield", this.originalShieldItem.save(new CompoundTag()));
        nbt.putBoolean("Stuck", this.isStuck());
        if (this.stuckPos != null) {
            nbt.putInt("StuckX", this.stuckPos.getX());
            nbt.putInt("StuckY", this.stuckPos.getY());
            nbt.putInt("StuckZ", this.stuckPos.getZ());
        }
    }

    // 2. Veriyi Diskten Oku (Dünyaya tekrar girerken)
    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setStuck(nbt.getBoolean("Stuck"));
        if (nbt.contains("StuckX")) {
            this.stuckPos = new BlockPos(nbt.getInt("StuckX"), nbt.getInt("StuckY"), nbt.getInt("StuckZ"));
        }
        if (nbt.contains("OriginalShield", 10)) {
            this.originalShieldItem = ItemStack.of(nbt.getCompound("OriginalShield"));
        }
    }


    public void handleEntityEvent(byte id) {
        if (id == 3) {
            // Kalkanın "stuckPos" ve yüzeyini bulmak için çarptığı bloğa bak
            // Kalkanın merkezinden, gidiş yönünün tersindeki bloğu al
            Direction face = Direction.UP; // Varsayılan

            // Kalkanın çarpma anındaki yüzeyini bulmak için basit bir tarama
            for (Direction d : Direction.values()) {
                if (!this.level().getBlockState(this.blockPosition().relative(d)).isAir()) {
                    face = d.getOpposite();
                    break;
                }
            }

            BlockState state = this.level().getBlockState(this.blockPosition().relative(face.getOpposite()));
            spawnImpactParticles(state, face);
        } else {
            super.handleEntityEvent(id);
        }
    }


    private boolean hasDamagedOnHit = false;

    // Hasar verme mantığını yürüten yardımcı metod
    private void applyCollisionDamage() {
        // Sadece sunucu tarafında, kalkan henüz hasar almadıysa ve kalkan mevcutsa çalışır
        if (!this.level().isClientSide && !hasDamagedOnHit && !this.originalShieldItem.isEmpty()) {
            int maxDurability = this.originalShieldItem.getMaxDamage();

            int damageAmount = Math.max(1, (int) (maxDurability * 0.02));

            // Minecraft'ın kendi hasar metodunu kullan (.hurt)
            // Bu sayede Unbreaking (Kırılmazlık) büyüsü varsa hasar alma ihtimali azalır.
            boolean isBroken = this.originalShieldItem.hurt(damageAmount, this.level().getRandom(),
                    this.getOwner() instanceof net.minecraft.server.level.ServerPlayer sPlayer ? sPlayer : null);

            this.hasDamagedOnHit = true; // Sadece İLK çarpışmada hasar alması için

            // Eğer kalkanın canı biterse (Kırılırsa)
            if (isBroken) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
                this.discard(); // Kalkanı dünyadan sil (Kırıldı)
            }
        }
    }


    @Override
    public void remove(RemovalReason reason) {
        // Sunucu tarafında bağı kopar
        if (!this.level().isClientSide && this.getOwner() instanceof net.minecraft.server.level.ServerPlayer sPlayer) {
            com.zipirhavaci.core.physics.SoulBondHandler.unregisterShield(sPlayer.getUUID(), this);
        }

        // İstemci tarafında bağı kopar
        if (this.level().isClientSide) {
            if (com.zipirhavaci.client.KeyInputHandlerEvents.lastThrownShield == this) {
                com.zipirhavaci.client.KeyInputHandlerEvents.lastThrownShield = null;
            }
        }

        super.remove(reason);
    }

}