package com.zipirhavaci.core.physics;

import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.core.capability.StaticProgressionData;
import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.ShieldBreakVisualPacket;
import com.zipirhavaci.network.SyncStaticProgressionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShieldRamMechanicHandler {

    // Aurası açık oyuncular arası çarpışma cooldown u
    private static final Map<UUID, Long> RAM_COOLDOWN = new ConcurrentHashMap<>();

    // Combo sistemi
    private static final Map<UUID, Map<UUID, Integer>> COMBO_TRACKER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COMBO_RESET_TIME = new ConcurrentHashMap<>();


    // Key: targetUUID -> (rammerUUID -> lastHitTime)
    private static final Map<UUID, Map<UUID, Long>> INNER_HIT_IMMUNITY = new ConcurrentHashMap<>();

    private static final long COMBO_TIMEOUT    = 5000L;
    private static final long RAM_COOLDOWN_MS  = 800L;
    private static final long INNER_IMMUNITY_MS = 1500L;
    private static final double SPRINT_SPEED_THRESHOLD = 0.18;

    // =========================================================
    //  BELLEK SIZINTI FIX: Oyuncu ayrılınca tüm kayıtları temizle
    // =========================================================
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();

        // 1. Temel Map Temizliği
        RAM_COOLDOWN.remove(uuid);
        COMBO_TRACKER.remove(uuid);
        COMBO_RESET_TIME.remove(uuid);
        INNER_HIT_IMMUNITY.remove(uuid);

        // 2. İÇ İÇE MAP TEMİZLİĞİ
        // Eğer bu oyuncu başka birinin immunity listesinde "hedef"  kaldıysa  siler.
        // Bellek sızıntısını (memory leak)  engelleyen yer.
        if (INNER_HIT_IMMUNITY != null) {
            INNER_HIT_IMMUNITY.values().forEach(targetMap -> {
                if (targetMap != null) targetMap.remove(uuid);
            });
        }

    }

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer rammer) || rammer.level().isClientSide) return;

        if (rammer.tickCount % 2 != 0) return;

        rammer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(rammerData -> {
            if (!rammerData.isAuraActive()) return;

            double collisionDist = getDynamicCollisionDistance(rammerData.getAuraLevel(), rammerData.getAuraTicksLeft());
            double innerZone     = collisionDist * 0.5;
            boolean isSprinting  = rammer.isSprinting() &&
                    rammer.getDeltaMovement().horizontalDistance() >= SPRINT_SPEED_THRESHOLD;

            rammer.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    rammer.getBoundingBox().inflate(collisionDist)).forEach(target -> {

                if (target == rammer) return;

                double distance = target.distanceTo(rammer);
                var cap = target.getCapability(StaticProgressionProvider.STATIC_PROGRESSION);

                if (cap.isPresent()) {
                    cap.ifPresent(targetData -> {
                        if (targetData.isAuraActive() && target instanceof ServerPlayer targetPlayer) {
                            // Aurası açık oyuncu → akıllı çarpışma sistemi
                            processSmartCollision(rammer, targetPlayer, rammerData, targetData, distance, collisionDist);
                        } else {
                            // Aurası kapalı oyuncu → kinetik alan
                            applyKineticField(rammer, target, distance, collisionDist, innerZone,
                                    rammerData.getAuraLevel(), isSprinting);
                        }
                    });
                } else {
                    // Mob → cooldown ile it
                    long now = System.currentTimeMillis();
                    if (now - RAM_COOLDOWN.getOrDefault(rammer.getUUID(), 0L) >= RAM_COOLDOWN_MS) {
                        pushDynamicTarget(rammer, target, distance, collisionDist, rammerData.getAuraLevel(), isSprinting);
                        RAM_COOLDOWN.put(rammer.getUUID(), now);
                    }
                }
            });
        });
    }


    private static void applyKineticField(ServerPlayer rammer, net.minecraft.world.entity.LivingEntity target,
                                          double distance, double outerRadius, double innerRadius,
                                          float auraLevel, boolean isSprinting) {
        long now = System.currentTimeMillis();
        UUID targetUUID = target.getUUID();
        UUID rammerUUID = rammer.getUUID();

        if (distance <= innerRadius) {
            // === İÇ BÖLGE: Güçlü fırlatma ===
            Map<UUID, Long> targetImmunities = INNER_HIT_IMMUNITY.computeIfAbsent(targetUUID, k -> new ConcurrentHashMap<>());
            long lastHit = targetImmunities.getOrDefault(rammerUUID, 0L);
            if (now - lastHit < INNER_IMMUNITY_MS) return;

            float basePower = (auraLevel >= 3.0f) ? 1.6f : (auraLevel >= 2.0f ? 1.2f : 0.9f);
            double finalPower = basePower + 2.8;
            double maxCap = (auraLevel >= 3.0f) ? 4.5 : 3.0;

            if (isSprinting) finalPower *= 1.5;
            finalPower = Math.min(finalPower, maxCap * 1.5);

            double yPush = 0.4 + (isSprinting ? 0.2 : 0.0);

            applyKnockback(rammer, target, finalPower, yPush);
            targetImmunities.put(rammerUUID, now);

            rammer.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.THORNS_HIT, SoundSource.PLAYERS, 0.9f, 0.3f);
            rammer.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 1.8f);

        } else {
            // === DIŞ BÖLGE: Hafif sürekli manyetik baskı ===

            double normalizedDist = (distance - innerRadius) / (outerRadius - innerRadius);
            double pushStrength   = (1.0 - normalizedDist) * 0.12; // Max 0.12 — sadece kaymak hissi

            Vec3 dir     = target.position().subtract(rammer.position()).normalize();
            Vec3 current = target.getDeltaMovement();

            double newX = current.x + dir.x * pushStrength;
            double newZ = current.z + dir.z * pushStrength;

            // Hız sınırı
            double maxOuterSpeed    = 0.25;
            double horizontalSpeed  = Math.sqrt(newX * newX + newZ * newZ);
            if (horizontalSpeed > maxOuterSpeed) {
                newX = (newX / horizontalSpeed) * maxOuterSpeed;
                newZ = (newZ / horizontalSpeed) * maxOuterSpeed;
            }

            target.setDeltaMovement(newX, current.y, newZ);
            target.hurtMarked = true;

            if (target instanceof ServerPlayer targetPlayer) {
                targetPlayer.hasImpulse = true;
                targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
            }

        }
    }

    private static void pushDynamicTarget(ServerPlayer rammer, net.minecraft.world.entity.LivingEntity target,
                                          double distance, double maxRadius, float auraLevel, boolean isSprinting) {
        // 1. Yakınlık Oranı
        float proximityRatio = (float)(1.0 - (distance / maxRadius));
        proximityRatio = Math.max(0.0f, Math.min(1.0f, proximityRatio));

        // 2. Temel Yatay Güç (Aura seviyesine göre kademeli artış)
        float basePower  = (auraLevel >= 3.0f) ? 1.3f : (auraLevel >= 2.0f ? 1.0f : 0.7f);
        double finalPower = basePower + (Math.pow(proximityRatio, 2) * 2.8);

        // 3. Hız Sınırı (MaxCap)
        double maxCap = (auraLevel >= 3.0f) ? 3.0 : 2.0;
        if (isSprinting) finalPower *= 1.3;
        finalPower = Math.min(finalPower, maxCap);

        // Adamı sadece 1-2 basamak yükseklikten kayacak kadar (0.22 - 0.30 max) havaya kaldır.
        double yPush = 0.18 + (proximityRatio * 0.12);

        // Fırlatmayı uygula
        applyKnockback(rammer, target, finalPower, yPush);
    }

    private static double getDynamicCollisionDistance(float level, int ticksLeft) {
        // Duration değerleri: 0.5=360, 1.0=480, 2.0=520, 3.0=600
        int maxTicks;
        if (level >= 3.0f) maxTicks = 600;
        else if (level >= 2.0f) maxTicks = 520;
        else if (level >= 1.0f) maxTicks = 480;
        else maxTicks = 360;

        float areaScale = Math.max(0.05f, (float) ticksLeft / (float) maxTicks);

        double baseDist;
        if      (level >= 3.0f) baseDist = 4.8;
        else if (level >= 2.0f) baseDist = 3.2;
        else if (level >= 1.0f) baseDist = 2.0;
        else                    baseDist = 1.2;

        return baseDist * areaScale;
    }

    // =========================================================
    //   ÇARPIŞMA SİSTEMİ — İki aurası açık oyuncu arası
    // =========================================================
    private static void processSmartCollision(ServerPlayer rammer, ServerPlayer target,
                                              StaticProgressionData rData, StaticProgressionData tData,
                                              double distance, double maxDistance) {
        long now = System.currentTimeMillis();
        if (now - RAM_COOLDOWN.getOrDefault(rammer.getUUID(), 0L) < RAM_COOLDOWN_MS) return;

        UUID rammerUUID = rammer.getUUID();
        UUID targetUUID = target.getUUID();

        // Combo timeout kontrolü
        long lastComboTime = COMBO_RESET_TIME.getOrDefault(rammerUUID, 0L);
        if (now - lastComboTime > COMBO_TIMEOUT) {
            COMBO_TRACKER.remove(rammerUUID);
        }

        Map<UUID, Integer> rammerCombos = COMBO_TRACKER.computeIfAbsent(rammerUUID, k -> new ConcurrentHashMap<>());
        int comboCount = rammerCombos.getOrDefault(targetUUID, 0) + 1;
        rammerCombos.put(targetUUID, comboCount);
        COMBO_RESET_TIME.put(rammerUUID, now);

        float comboMultiplier = 1.0f + Math.min(comboCount - 1, 4) * 0.15f;
        if (comboCount > 1) {
            rammer.sendSystemMessage(Component.literal("§e§l" + comboCount + "x COMBO! §6+" +
                    (int)((comboMultiplier - 1.0f) * 100) + "% AETHERIC MAGNITUDE"));
        }

        int rWeight = getLevelWeight(rData.getAuraLevel());
        int tWeight = getLevelWeight(tData.getAuraLevel());
        float weightRatio = (float) rWeight / (float) tWeight;

        float distanceRatio      = 1.0f - ((float) distance / (float) maxDistance);
        float distanceMultiplier = 0.5f + (distanceRatio * 1.0f);

        Vec3 attackDirection = target.position().subtract(rammer.position()).normalize();
        Vec3 targetLookDir   = target.getLookAngle();
        double dotProduct    = attackDirection.dot(targetLookDir);
        float directionBonus = (dotProduct < -0.5) ? 1.3f : 1.0f;

        float baseDrainPercent = (weightRatio * 0.5f) * distanceMultiplier * directionBonus * comboMultiplier;
        int currentTicks       = tData.getAuraTicksLeft();
        int drainAmount        = Math.max(1, (int)(currentTicks * baseDrainPercent));
        int remainingTicks     = currentTicks - drainAmount;

        double targetPower = Math.min(baseDrainPercent * 2.8 * distanceMultiplier * comboMultiplier, 3.0);
        Vec3 knockbackDir  = target.position().subtract(rammer.position()).normalize();

        if (!target.onGround()) {
            Vec3 currentVelocity = target.getDeltaMovement();
            Vec3 newVelocity = knockbackDir.scale(targetPower).add(currentVelocity.scale(0.3));
            target.setDeltaMovement(newVelocity.x, Math.max(0.4, newVelocity.y), newVelocity.z);
            target.hasImpulse = true;
            target.connection.send(new ClientboundSetEntityMotionPacket(target));
        } else {
            applyKnockback(rammer, target, targetPower, 0.4);
        }

        double rammerBackPower = Math.min((1.0 / weightRatio) * 0.4, 1.2);
        applyKnockback(target, rammer, rammerBackPower, 0.15);

        if (remainingTicks <= 0) {
            tData.setAuraActive(false);
            tData.setAuraTicksLeft(0);

            int baseEnergyBonus  = (int)(drainAmount * 0.1f);
            int comboEnergyBonus = (int)(baseEnergyBonus * (comboCount * 0.2f));
            int totalEnergyBonus = baseEnergyBonus + comboEnergyBonus;

            int rammerTicks = rData.getAuraTicksLeft();
            // Duration değerleri: 0.5=360, 1.0=480, 2.0=520, 3.0=600
            int maxRammerTicks;
            float rammerLevel = rData.getAuraLevel();
            if (rammerLevel >= 3.0f) maxRammerTicks = 600;
            else if (rammerLevel >= 2.0f) maxRammerTicks = 520;
            else if (rammerLevel >= 1.0f) maxRammerTicks = 480;
            else maxRammerTicks = 360;

            rData.setAuraTicksLeft(Math.min(maxRammerTicks, rammerTicks + totalEnergyBonus));

            playShieldBreakEffects(rammer, target, tData.getAuraLevel());
            PacketHandler.sendToTracking(target, new ShieldBreakVisualPacket(target.getId(), tData.getAuraLevel()));
            target.sendSystemMessage(Component.literal("§c§lAETHERIC SHATTERING—THY RADIANCE IS FRACTURED! §c§l"));

            if (comboCount > 1) {
                rammer.sendSystemMessage(Component.literal("§a§l+" + totalEnergyBonus + "§b§lSTATIC RESONANCE SURGING! §e(x" + comboCount + " HARMONIC MULTIPLIER)"));
            } else {
                rammer.sendSystemMessage(Component.literal("§b§l" + totalEnergyBonus + " AETHERIC UNITS CONSUMED—THY RESONANCE DEEPENS!"));
            }

            rammerCombos.remove(targetUUID);
        } else {
            tData.setAuraTicksLeft(remainingTicks);
            int drainPercent = (int)((drainAmount / (float)currentTicks) * 100);
            target.sendSystemMessage(Component.literal("§6§l-" + drainPercent + "% AETHERIC UNITS—THY RESONANCE WEAKENS!"));
            rammer.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.5f);
            rammer.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.4f, 2.0f);
        }

        RAM_COOLDOWN.put(rammer.getUUID(), now);
        rammer.getCooldowns().addCooldown(rammer.getUseItem().getItem(), 60);

        PacketHandler.sendToTracking(target, new SyncStaticProgressionPacket(tData, target.getId()));
        PacketHandler.sendToTracking(rammer, new SyncStaticProgressionPacket(rData, rammer.getId()));
    }

    private static void applyKnockback(ServerPlayer from, net.minecraft.world.entity.LivingEntity to,
                                       double power, double y) {
        Vec3 dir   = to.position().subtract(from.position()).normalize();
        double moveX = dir.x * power;
        double moveZ = dir.z * power;

        to.setDeltaMovement(moveX, y, moveZ);
        to.hurtMarked = true;

        if (to instanceof ServerPlayer targetPlayer) {
            targetPlayer.hasImpulse = true;
            targetPlayer.connection.send(new ClientboundSetEntityMotionPacket(targetPlayer));
        }
    }

    private static int getLevelWeight(float level) {
        if (level >= 3.0f) return 8;
        if (level >= 2.0f) return 4;
        if (level >= 1.0f) return 2;
        return 1;
    }

    private static void playShieldBreakEffects(ServerPlayer rammer, ServerPlayer target, float level) {
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.2F, 0.8F + (level * 0.1F));
    }

}