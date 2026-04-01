package com.zipirhavaci.entity;

import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class LibratedSoulEntity extends PathfinderMob {

    // ── Sahne fazları ─────────────────────────────────────────────────────────
    private static final int PHASE_RISING      = 0;   //  0-40 tick  → yükseliş
    private static final int PHASE_TALKING     = 1;   // 40-120 tick → teşekkür baloncuğu
    private static final int PHASE_FLASH       = 2;   // 120-160 tick → flash + efekt
    private static final int PHASE_DEAD        = 3;   // bitti

    private int sceneTick = 0;
    private int flashCount = 0;
    private int nextFlashTick = 0;

    // Konuşma baloncuğu için mesaj döngüsü
    private static final String[] THANK_MESSAGES = {
            "§bThe tether snaps... §fI return to the stars.",
            "§bBound no longer... §fLight guide thee.",
            "§bMy essence mended... §fGo in radiance.",
            "§bStasis dissolved... §fFinally, peace.",
            "§bFrom dust to Aether... §fBless thee.",
    };
    private static final int MSG_CYCLE = 26; // her 26 tick'te mesaj değişir

    // ── Constructor ───────────────────────────────────────────────────────────
    public LibratedSoulEntity(EntityType<? extends LibratedSoulEntity> type, Level level) {
        super(type, level);
        this.setCustomName(Component.literal("§6Lost Soul"));
        this.setCustomNameVisible(true);
        this.setPersistenceRequired();
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    // ── Attributes ────────────────────────────────────────────────────────────
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 9999.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ── Tam ölümsüzlük ────────────────────────────────────────────────────────
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // ── Goals — bu entity hareket etmeyecek ───────────────────────────────────
    @Override
    protected void registerGoals() {
        // Kasıtlı olarak boş — entity kendi tick mantığıyla hareket eder
    }

    // ── Ana Tick / Sahne Yönetimi ─────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();

        sceneTick++;

        if (this.level().isClientSide) return; // Sunucu mantığı buradan sonra başlar

        switch (getCurrentPhase()) {
            case PHASE_RISING  -> tickRising();
            case PHASE_TALKING -> tickTalking();
            case PHASE_FLASH   -> tickFlash();
            default -> {}
        }
    }

    private int getCurrentPhase() {
        if (sceneTick < 40)  return PHASE_RISING;
        if (sceneTick < 120) return PHASE_TALKING;
        if (sceneTick < 160) return PHASE_FLASH;
        return PHASE_DEAD;
    }

    // ── Faz 1: Yükseliş (0-40 tick) ──────────────────────────────────────────
    private void tickRising() {
        // Yumuşak yükseliş — her tick 0.04 blok yukarı
        this.setDeltaMovement(0, 0.04, 0);
        this.hurtMarked = true;

        // Hafif parlayan partiküller
        if (this.level() instanceof ServerLevel sl && sceneTick % 3 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 0.9, getZ(),
                    2, 0.15, 0.2, 0.15, 0.01);
        }
    }

    // ── Faz 2: Teşekkür baloncuğu (40-120 tick) ──────────────────────────────
    private void tickTalking() {
        // Yavaşça dur
        this.setDeltaMovement(getDeltaMovement().scale(0.85));

        // Altın partiküller sürekli
        if (this.level() instanceof ServerLevel sl && sceneTick % 4 == 0) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    getX(), getY() + 1.0, getZ(),
                    3, 0.3, 0.4, 0.3, 0.02);
            sl.sendParticles(ParticleTypes.END_ROD,
                    getX(), getY() + 1.0, getZ(),
                    2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    // ── Faz 3: Flash + Ses + Yok Olma (120-160 tick) ─────────────────────────
    private void tickFlash() {
        if (this.level() instanceof ServerLevel sl) {

            // 3 flash: 120, 130, 140. tick civarında
            if (flashCount < 3 && sceneTick >= nextFlashTick) {
                doFlash(sl);
                flashCount++;
                nextFlashTick = sceneTick + 10;
            }

            // Son flash sonrası (150. tick) → ses + son patlama + ANIMA + discard
            if (sceneTick == 150) {
                doFinalBurst(sl);
            }
        }
    }

    private void doFlash(ServerLevel sl) {
        // Küçük patlama partikülleri (parlama efekti)
        sl.sendParticles(ParticleTypes.FLASH,
                getX(), getY() + 0.9, getZ(),
                1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.END_ROD,
                getX(), getY() + 0.9, getZ(),
                20, 0.5, 0.5, 0.5, 0.08);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                getX(), getY() + 0.9, getZ(),
                15, 0.4, 0.4, 0.4, 0.06);

        // Hızlandırılmış generic_explode — pitch yüksek, volume düşük
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.NEUTRAL,
                0.25f,
                2.0f);
    }

    private void doFinalBurst(ServerLevel sl) {
        // Son kutsallık patlaması
        sl.sendParticles(ParticleTypes.FLASH,
                getX(), getY() + 0.9, getZ(),
                1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.ENCHANT,
                getX(), getY() + 0.9, getZ(),
                40, 0.6, 0.6, 0.6, 0.15);
        sl.sendParticles(ParticleTypes.END_ROD,
                getX(), getY() + 0.9, getZ(),
                30, 0.6, 0.6, 0.6, 0.1);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                getX(), getY() + 0.9, getZ(),
                20, 0.4, 0.4, 0.4, 0.05);

        // Anima kitabını bırak
        ItemStack animaStack = new ItemStack(ItemRegistry.ANIMA_BOOK.get(), 1);
        spawnAtLocation(animaStack);

        // Yok ol
        this.discard();
    }

    // ── Renderer için getter ───────────────────────────────────────────────────
    public int getSceneTick() { return sceneTick; }

    // ── Konuşma baloncuğu metni (Renderer çağırır) ────────────────────────────
    public String getCurrentMessage() {
        int msgIndex = ((sceneTick - 40) / MSG_CYCLE) % THANK_MESSAGES.length;
        return THANK_MESSAGES[Math.max(0, msgIndex)];
    }

    public boolean isTalking() {
        return getCurrentPhase() == PHASE_TALKING;
    }

    // ── Misc ─────────────────────────────────────────────────────────────────
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean isAggressive()              { return false; }
    @Override public MobType getMobType()                { return MobType.UNDEFINED; }
    @Override public boolean isPushable()                { return false; }
}