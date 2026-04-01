package com.zipirhavaci.entity;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;


public class BlastTntEntity extends Entity {

    // ── Synced data ──────────────────────────────────────────────
    private static final EntityDataAccessor<Integer> FUSE =
            SynchedEntityData.defineId(BlastTntEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<String> ORIGIN_TYPE =
            SynchedEntityData.defineId(BlastTntEntity.class, EntityDataSerializers.STRING);

    // ── Sabitler ─────────────────────────────────────────────────
    private static final float  EXPLOSION_POWER = 4.0f;  // Vanilla TNT ile aynı
    private static final double GRAVITY         = 0.04;  // Vanilla TNT ile aynı

    // Ses ayarları
    private static final float FUSE_VOLUME   = 0.55f; // Fitil sesi — kısık
    private static final float FUSE_PITCH    = 0.72f; // Fitil sesi — yavaşlatılmış (1.0 = normal)
    private static final float FIRE_VOLUME   = 0.30f; // Kavrulma sesi — arka planda
    private static final float FIRE_PITCH    = 0.85f; // Kavrulma sesi — hafif pes
    private static final int   FIRE_INTERVAL = 12;    // Her 12 tick'te bir kavrulma sesi (~0.6 saniye)

    // ── Rotation takibi ──────────────────────────────────────────
    /** Bu tick'teki yaw — renderer partialTick ile interpole eder */
    public float visualYaw      = 0f;
    /** Önceki tick'teki yaw — smooth interpolasyon için */
    public float prevVisualYaw  = 0f;
    /** Bu tick'teki pitch */
    public float visualPitch     = 0f;
    /** Önceki tick'teki pitch */
    public float prevVisualPitch = 0f;

    // Önceki velocity
    private Vec3 prevVelocity = Vec3.ZERO;

    // Orijinal TNT'nin NBT snapshot'ı — patlama anında kullanılır
    // NBT ile kopyalama: type.create() + readAdditionalSaveData
    // originTnt referansı yerine NBT kullanıyoruz — discard sorunu yok
    private net.minecraft.nbt.CompoundTag originTntNbt = null;

    // Spawn sesinin çalındığını takip et — sadece bir kez çalsın
    private boolean fuseSoundPlayed = false;

    // ── Constructor ──────────────────────────────────────────────
    public BlastTntEntity(EntityType<? extends BlastTntEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    /**
     * Fabrika metodu — ItemBlastHandler bu metodu çağırır.
     */
    public static BlastTntEntity fromPrimedTnt(PrimedTnt originalTnt, Vec3 velocity) {
        Level level = originalTnt.level();

        BlastTntEntity entity = new BlastTntEntity(
                com.zipirhavaci.core.EntityRegistry.BLAST_TNT.get(), level);

        entity.setPos(originalTnt.getX(), originalTnt.getY(), originalTnt.getZ());
        entity.setDeltaMovement(velocity);
        entity.prevVelocity = velocity;

        // Hıza göre fuse ayarla — hızlı fırlatılan TNT daha uzun süre uçsun
        // Hız yüksekse fuse'u uzat, düşükse orijinal fuse kalsın
        double speed = velocity.length();
        int originalFuse = originalTnt.getFuse();
        // Hız 0..2.2 arası — max hızda fuse 2 katına çıkar
        int adjustedFuse = (int)(originalFuse * (1.0 + (speed / 2.2) * 0.5)); // max 1.5 kat
        entity.setFuse(adjustedFuse);

        ResourceLocation typeKey = ForgeRegistries.ENTITY_TYPES.getKey(originalTnt.getType());
        entity.setOriginType(typeKey != null ? typeKey.toString() : "minecraft:tnt");

        // NBT snapshot al — tam state kopyası
        try {
            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
            originalTnt.save(nbt);
            entity.originTntNbt = nbt;
        } catch (Exception ignored) {}

        return entity;
    }


    // ── Entity override'ları ─────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FUSE, 80);
        this.entityData.define(ORIGIN_TYPE, "minecraft:tnt");
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        // ── Yerçekimi ────────────────────────────────────────────
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -GRAVITY, 0));
        }

        // ── Hareket ──────────────────────────────────────────────
        this.move(MoverType.SELF, this.getDeltaMovement());

        // ── Hava direnci ─────────────────────────────────────────
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));

        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }

        // ── Rotation ─────────────────────────────────────────────
        // Her iki tarafta hesaplanır — client server'ı beklemez
        updateVisualRotation();
        prevVelocity = this.getDeltaMovement();

        // ── Sesler ───────────────────────────────────────────────
        if (this.level().isClientSide) {
            playFuseSoundClient();
        } else {
            playFireSoundServer();
        }

        // ── Fuse sayacı ──────────────────────────────────────────
        int fuse = this.getFuse() - 1;
        this.setFuse(fuse);

        if (fuse <= 0) {
            this.discard();
            if (!this.level().isClientSide) {
                explode();
            }
        } else {
            this.level().addParticle(
                    net.minecraft.core.particles.ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 0.5, this.getZ(),
                    0, 0, 0);
        }
    }

    /**
     * Fitil sesi — sadece local client, tek seferlik.
     * Spawn pozisyonundan çalar, kısa sürdüğü için sabit kalması sorun değil.
     */
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private void playFuseSoundClient() {
        if (fuseSoundPlayed) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        mc.level.playLocalSound(
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.TNT_PRIMED,
                SoundSource.BLOCKS,
                FUSE_VOLUME,
                FUSE_PITCH,
                false
        );
        fuseSoundPlayed = true;
    }

    /**
     * Kavrulma sesi — server tarafında her FIRE_INTERVAL tick'te çağrılır.
     * level.playSound(null, x, y, z) tüm yakın oyunculara güncel pozisyondan
     * ses paketi gönderir — herkes duyar, TNT'yi takip eder.
     */
    private void playFireSoundServer() {
        int fuse = this.getFuse();
        if (fuse % FIRE_INTERVAL != 0 || fuse <= 0) return;

        double speed    = this.getDeltaMovement().length();
        float dynVolume = FIRE_VOLUME + (float)(speed * 0.08f);
        float fuseRatio = (float) fuse / 80f;
        float dynPitch  = FIRE_PITCH + (1.0f - fuseRatio) * 0.15f;

        this.level().playSound(
                null,                          // null = tüm yakın oyunculara gönder
                this.getX(), this.getY(), this.getZ(),
                SoundEvents.FIRE_AMBIENT,
                SoundSource.BLOCKS,
                Math.min(dynVolume, 0.55f),
                dynPitch
        );
    }

    private void updateVisualRotation() {
        // Her iki tarafta da (server + client) çalışır
        // Client bağımsız hesaplar — server sync'ini beklemez, takılma olmaz
        Vec3 vel = this.getDeltaMovement();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        prevVisualYaw   = visualYaw;
        prevVisualPitch = visualPitch;

        if (hSpeed > 0.01 || Math.abs(vel.y) > 0.01) {
            double speed    = vel.length();
            float spinRate  = (float) (speed * 18.0f);
            visualYaw      += spinRate;
            visualPitch    += spinRate * 0.5f;
        }
    }

    private void explode() {
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            try {
                // Entity type'tan blok bul — MoreTNT'de "moretnt:cat_tnt" → "moretnt:cat_tnt" bloğu
                net.minecraft.resources.ResourceLocation typeId =
                        new net.minecraft.resources.ResourceLocation(this.getOriginType());
                net.minecraft.resources.ResourceLocation blockId =
                        new net.minecraft.resources.ResourceLocation(
                                typeId.getNamespace(), typeId.getPath());

                net.minecraft.world.level.block.Block block =
                        net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(blockId);

                if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                    // onCaughtFire öncesi mevcut entity listesini kaydet
                    // Sonra yeni eklenen PrimedTnt'yi bul, fuse=1 yap
                    net.minecraft.core.BlockPos pos = this.blockPosition();

                    // Spawn öncesi entity sayısını al
                    java.util.List<net.minecraft.world.entity.item.PrimedTnt> before =
                            sl.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class,
                                    this.getBoundingBox().inflate(2.0));
                    java.util.Set<Integer> beforeIds = new java.util.HashSet<>();
                    for (net.minecraft.world.entity.item.PrimedTnt t : before) beforeIds.add(t.getId());

                    block.onCaughtFire(block.defaultBlockState(), sl, pos, null, null);

                    // Yeni spawn olan PrimedTnt'yi bul, fuse=1 yap
                    java.util.List<net.minecraft.world.entity.item.PrimedTnt> after =
                            sl.getEntitiesOfClass(net.minecraft.world.entity.item.PrimedTnt.class,
                                    this.getBoundingBox().inflate(2.0));
                    for (net.minecraft.world.entity.item.PrimedTnt t : after) {
                        if (!beforeIds.contains(t.getId())) {
                            t.setInvisible(true);
                            t.setFuse(1);
                        }
                    }
                    return;
                }
            } catch (Exception ignored) {}

            // Vanilla TNT fallback
            try {
                net.minecraft.world.entity.item.PrimedTnt primedTnt =
                        new net.minecraft.world.entity.item.PrimedTnt(
                                net.minecraft.world.entity.EntityType.TNT, sl);
                primedTnt.setPos(this.getX(), this.getY(), this.getZ());
                primedTnt.setInvisible(true);
                primedTnt.setFuse(1);
                sl.addFreshEntity(primedTnt);
                return;
            } catch (Exception ignored) {}
        }

        // Son fallback
        this.level().explode(
                this,
                this.getX(), this.getY(), this.getZ(),
                EXPLOSION_POWER,
                Level.ExplosionInteraction.TNT
        );
    }

    // ── Getter / Setter ───────────────────────────────────────────

    public int getFuse()    { return this.entityData.get(FUSE); }
    public void setFuse(int fuse) { this.entityData.set(FUSE, fuse); }
    public String getOriginType()        { return this.entityData.get(ORIGIN_TYPE); }
    public void setOriginType(String t)  { this.entityData.set(ORIGIN_TYPE, t); }

    // ── NBT ──────────────────────────────────────────────────────

    /**
     * Server kapanınca BlastTntEntity kaydedilmez — dünya yüklendiğinde geri gelmez.
     * Oyuncu çıkıp geri gelirse (server ayakta) entity zaten level'da yaşamaya devam eder,
     * bu metot o senaryoda çağrılmaz.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Fuse", this.getFuse());
        tag.putString("OriginType", this.getOriginType());
        tag.putBoolean("FuseSoundPlayed", this.fuseSoundPlayed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.setFuse(tag.getInt("Fuse"));
        if (tag.contains("OriginType"))      this.setOriginType(tag.getString("OriginType"));
        if (tag.contains("FuseSoundPlayed")) this.fuseSoundPlayed = tag.getBoolean("FuseSoundPlayed");
    }

    // ── Network ──────────────────────────────────────────────────

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    // ── Boyut ────────────────────────────────────────────────────

    @Override
    public void refreshDimensions() {}

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(
            net.minecraft.world.entity.Pose pose) {
        return net.minecraft.world.entity.EntityDimensions.fixed(0.98f, 0.98f);
    }
}