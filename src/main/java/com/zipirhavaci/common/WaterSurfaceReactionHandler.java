package com.zipirhavaci.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaterSurfaceReactionHandler {

    // Pozisyon cooldown: BlockPos → kalan tick sayısı
    private static final Map<BlockPos, Integer> COOLDOWN_MAP = new ConcurrentHashMap<>();

    // Global aktif yüzey sayısı
    private static final int MAX_ACTIVE_SURFACES = 500;
    private static final int COOLDOWN_TICKS = 5;
    private static final Map<java.util.UUID, Integer> ENTITY_COOLDOWN = new ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();

    // ===== ENTITY TICK =====
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        // 1. Global Kontrol
        if (!com.zipirhavaci.client.config.HudConfig.getInstance().waterBubblesEnabled) return;

        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !(entity.level() instanceof ServerLevel level)) return;
        if (entity instanceof Arrow) return;

        // 2. VARLIK COOLDOWN KONTROLÜ (SES SPAMINI ENGELLER)
        if (ENTITY_COOLDOWN.getOrDefault(entity.getUUID(), 0) > 0) {
            return; // Varlık hala bekleme süresinde, hiçbir şey yapma
        }

        checkAndSpawnBubbles(entity, level);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;

        tickCooldowns();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;


        tickCooldowns();

        // VARLIK COOLDOWNLARINI GÜNCELLE VE TEMİZLE

        ENTITY_COOLDOWN.keySet().forEach(uuid -> {
            ENTITY_COOLDOWN.compute(uuid, (key, value) -> {
                if (value == null) return null;
                int newValue = value - 1;
                return newValue <= 0 ? null : newValue; // null dönerse ConcurrentHashMap o kaydı siler
            });
        });
    }


    private static void checkAndSpawnBubbles(Entity entity, ServerLevel level) {
        // 1. Hareket Kontrolü (Hız karesi üzerinden gitmek kök almaktan [length()] daha hızlıdır)
        Vec3 vel = entity.getDeltaMovement();
        if (vel.lengthSqr() < 0.01) return;

        Vec3 nextPos = entity.position().add(vel);
        BlockPos nextBlock = BlockPos.containing(nextPos);

        //  Su ve Yüzey Kontrolü
        BlockState nextState = level.getBlockState(nextBlock);
        if (!nextState.getFluidState().is(Fluids.WATER)) return;

        BlockPos above = nextBlock.above();
        if (!level.getBlockState(above).isAir()) return;

        // Bu blok yakın zamanda tetiklendi mi?
        if (COOLDOWN_MAP.containsKey(nextBlock)) return;

        if (COOLDOWN_MAP.size() >= MAX_ACTIVE_SURFACES) return;

        spawnBubbles(entity, level, nextBlock, vel);

        COOLDOWN_MAP.put(nextBlock, COOLDOWN_TICKS); // Bloğu koru
        ENTITY_COOLDOWN.put(entity.getUUID(), 15);   // Varlığı 15 tick sustur (Spam engelleyici)
    }

    private static void spawnBubbles(Entity entity, ServerLevel level, BlockPos waterPos, Vec3 vel) {
        // ===================== GLOBAL AÇ/KAPA KONTROLÜ =====================

        if (!com.zipirhavaci.client.config.HudConfig.getInstance().waterBubblesEnabled) {
            return;
        }
        // ===================================================================

        double speed = vel.length();
        int count = (int) Math.min(10, Math.max(5, speed * 3));

        double x = waterPos.getX() + 0.5;
        double y = waterPos.getY() + 0.9;
        double z = waterPos.getZ() + 0.5;

        // 1. DALGA: BUBBLE_COLUMN_UP — Hemen tetiklenir
        for (int i = 0; i < count; i++) {
            double offsetX = (RANDOM.nextDouble() - 0.5) * 0.6;
            double offsetZ = (RANDOM.nextDouble() - 0.5) * 0.6;
            level.sendParticles(
                    ParticleTypes.BUBBLE_COLUMN_UP,
                    x + offsetX, y, z + offsetZ,
                    1,
                    0.04, 0.02, 0.04,
                    0.04
            );
        }

        // 2. DALGA: Normal BUBBLE — 3 tick sonra tetiklenir (Scheduler)
        com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {

                if (!com.zipirhavaci.client.config.HudConfig.getInstance().waterBubblesEnabled) return;
                if (!level.isLoaded(waterPos)) return;

                for (int i = 0; i < count / 2; i++) {
                    double offsetX = (RANDOM.nextDouble() - 0.5) * 0.4;
                    double offsetZ = (RANDOM.nextDouble() - 0.5) * 0.4;
                    level.sendParticles(
                            ParticleTypes.BUBBLE,
                            x + offsetX, y + 0.1, z + offsetZ,
                            1,
                            0.03, 0.05, 0.03,
                            0.08
                    );
                }
            });
        }, 150, java.util.concurrent.TimeUnit.MILLISECONDS);

        level.playSound(null, waterPos,
                net.minecraft.sounds.SoundEvents.GENERIC_SPLASH,
                net.minecraft.sounds.SoundSource.BLOCKS,
                0.3f, 1.2f + RANDOM.nextFloat() * 0.4f);
    }


    public static void spawnPushBubbles(net.minecraft.server.level.ServerPlayer player,
                                        ServerLevel level) {
        double radius = 3.5;
        BlockPos center = player.blockPosition();

        int minX = (int)(center.getX() - radius);
        int maxX = (int)(center.getX() + radius);
        int minZ = (int)(center.getZ() - radius);
        int maxZ = (int)(center.getZ() + radius);

        int minY = center.getY() - 2;
        int maxY = center.getY() + 2;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Daire sınırı kontrolü
                double dx = x + 0.5 - player.getX();
                double dz = z + 0.5 - player.getZ();
                if (dx * dx + dz * dz > radius * radius) continue;

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).getFluidState().is(Fluids.WATER)) continue;
                    if (!level.getBlockState(pos.above()).isAir()) continue;

                    // Cooldown ve global limit kontrolü
                    if (COOLDOWN_MAP.containsKey(pos)) continue;
                    if (COOLDOWN_MAP.size() >= MAX_ACTIVE_SURFACES) return;

                    // Push için daha fazla kabarcık — hız yüksek
                    spawnBubbles(player, level, pos, player.getDeltaMovement().add(0, 1.5, 0));
                    COOLDOWN_MAP.put(pos, COOLDOWN_TICKS);
                }
            }
        }
    }



    private static void tickCooldowns() {
        Iterator<Map.Entry<BlockPos, Integer>> it = COOLDOWN_MAP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public static void spawnSuperSkillBubbles(Player player, ServerLevel level, double currentRadius, Vec3 lookVec) {
        BlockPos center = player.blockPosition();
        int r = (int) Math.ceil(currentRadius);

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double distSq = x * x + z * z;

                if (distSq <= currentRadius * currentRadius && distSq >= Math.pow(currentRadius - 1.5, 2)) {

                    Vec3 relPos = new Vec3(x, 0, z).normalize();
                    if (relPos.dot(lookVec) < 0.4) continue;

                    BlockPos targetPos = center.offset(x, 0, z);

                    for (int y = -1; y <= 1; y++) {
                        BlockPos checkPos = targetPos.above(y);
                        if (level.getFluidState(checkPos).is(net.minecraft.world.level.material.Fluids.WATER) &&
                                level.getBlockState(checkPos.above()).isAir()) {

                            spawnBubbles(player, level, checkPos, lookVec.scale(0.5).add(0, 0.8, 0));
                        }
                    }
                }
            }
        }
    }

}