package com.zipirhavaci.core.physics;

import com.zipirhavaci.core.capability.Soulbonddataprovider;
import com.zipirhavaci.entity.ThrownShieldEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public class SoulBondHandler {
    private static final Map<UUID, ThrownShieldEntity> LAST_SHIELDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    public static final Map<UUID, ThrownShieldEntity> ACTIVE_PULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, GameType> ORIGINAL_GAMEMODE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PACKET_TIME = new ConcurrentHashMap<>();
    public static boolean hasOriginalGameMode(UUID uuid) {
        return ORIGINAL_GAMEMODE.containsKey(uuid);
    }

    public static void removeOriginalGameMode(UUID uuid) {
        ORIGINAL_GAMEMODE.remove(uuid);
    }

    public static void registerThrownShield(UUID playerUUID, ThrownShieldEntity shield) {
        LAST_SHIELDS.put(playerUUID, shield);
    }

    public static void unregisterShield(UUID playerUUID, ThrownShieldEntity shield) {
        if (LAST_SHIELDS.get(playerUUID) == shield) {
            LAST_SHIELDS.remove(playerUUID);
        }
        if (ACTIVE_PULLS.containsKey(playerUUID)) {
            ACTIVE_PULLS.remove(playerUUID);
        }
    }

    public static void tryExecuteBond(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();

        if (now - LAST_PACKET_TIME.getOrDefault(uuid, 0L) < 500) return;
        LAST_PACKET_TIME.put(uuid, now);

        // 1. KALKAN ELDE Mİ KONTROLÜ
        if (player.getMainHandItem().getItem() instanceof ShieldItem ||
                player.getOffhandItem().getItem() instanceof ShieldItem) {
            return;
        }

        // 2. SKİLL ÖĞRENMİŞ Mİ?
        boolean hasSoulBond = player.getCapability(Soulbonddataprovider.SOUL_BOND)
                .map(data -> data.hasSoulBond())
                .orElse(false);

        if (!hasSoulBond) {
            return; // Sessizce çık, öğrenmemiş
        }

        // 3. COOLDOWN KONTROLÜ
        if (COOLDOWN.getOrDefault(uuid, 0L) > now) return;

        ThrownShieldEntity shield = LAST_SHIELDS.get(uuid);

        if (shield != null && !shield.isRemoved() && shield.isStuck()) {
            double distance = player.distanceTo(shield);

            if (distance <= 25.0) {
                // 4. SOUL SAND KONTROLÜ VE TÜKETİMİ
                if (!consumeSoulSand(player, 1)) {
                    player.displayClientMessage(
                            Component.literal("§c⚠ THE BOND STARVES... §fSacrifice Soul Sand to bind the tether! §c⚠"),
                            true
                    );
                    // Daha derinden gelen, ruhun kaçışını anlatan o ses
                    player.playNotifySound(SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.6f);
                    return;
                }

                // 5. RUHSAL BEDEL - Skill kullanım anında
                player.hurt(player.damageSources().magic(), 1.0f);
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));  // 3 saniye Wither

                ACTIVE_PULLS.put(uuid, shield);
                COOLDOWN.put(uuid, now + 8000);

                com.zipirhavaci.network.PacketHandler.sendToPlayer(player, new com.zipirhavaci.network.Syncsoulbondpacket());

                spawnBondParticles(player, shield, distance);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
            }
        }
    }

    private static boolean consumeSoulSand(ServerPlayer player, int amount) {
        // Envanteri tara, Soul Sand bul ve tüket
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.SOUL_SAND)) {
                if (stack.getCount() >= amount) {
                    stack.shrink(amount);
                    // Tüketim sesi
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.5f, 1.5f);
                    return true;
                }
            }
        }
        return false;
    }

    public static void onServerTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!ACTIVE_PULLS.containsKey(uuid)) {
            return;
        }

        ThrownShieldEntity shield = ACTIVE_PULLS.get(uuid);

        if (shield == null || shield.isRemoved()) {
            stopPulling(player, false);
            return;
        }

        double distance = player.distanceTo(shield);

        // --- MLG KORUMASI (2 BLOK KALA DUR) ---
        if (distance < 2.0) {
            stopPulling(player, false);
            return;
        }

        Vec3 target = shield.position().add(0, 0.5, 0);
        Vec3 dir = target.subtract(player.position()).normalize();

        // HAREKET ETMEDEN ÖNCE İLERİDEKİ BLOKLARI KONTROL ET
        if (willHitUnbreakableBlock(player, dir)) {
            stopPulling(player, true);
            return;
        }

        // GEÇİCİ SPECTATOR MODE - Collision bypass
        if (!ORIGINAL_GAMEMODE.containsKey(uuid)) {
            ORIGINAL_GAMEMODE.put(uuid, player.gameMode.getGameModeForPlayer());
        }
        player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);

        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20, 0, false, false));

        // 1.5 hızında fırlatma
        player.setDeltaMovement(dir.scale(1.5));

        player.fallDistance = 0;
        player.hurtMarked = true;

        // Efektler
        if (player.tickCount % 2 == 0) {
            ((ServerLevel)player.level()).sendParticles(ParticleTypes.SOUL,
                    player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.1, 0.1, 0.1, 0.02);

            if (player.level().random.nextInt(100) < 8) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }
    }

    private static boolean willHitUnbreakableBlock(ServerPlayer player, Vec3 direction) {
        Vec3 playerPos = player.position().add(0, 1.0, 0);

        for (int i = 1; i <= 2; i++) {
            BlockPos checkPos = BlockPos.containing(
                    playerPos.x + (direction.x * i),
                    playerPos.y + (direction.y * i),
                    playerPos.z + (direction.z * i)
            );

            BlockState blockState = player.level().getBlockState(checkPos);
            float destroySpeed = blockState.getDestroySpeed(player.level(), checkPos);

            if (destroySpeed < 0) {
                return true;
            }

            if (destroySpeed >= 50 && !blockState.liquid()) {
                return true;
            }
        }

        return false;
    }

    private static void stopPulling(ServerPlayer player, boolean snapped) {
        UUID uuid = player.getUUID();
        ACTIVE_PULLS.remove(uuid);

        // ESKİ GAMEMODE'A GERİ DÖN
        if (ORIGINAL_GAMEMODE.containsKey(uuid)) {
            player.gameMode.changeGameModeForPlayer(ORIGINAL_GAMEMODE.get(uuid));
            ORIGINAL_GAMEMODE.remove(uuid);
        }

        BlockPos pos = player.blockPosition();
        if (!player.level().getBlockState(pos).isAir() || !player.level().getBlockState(pos.above()).isAir()) {
            handleSafeExit(player);
        }

        // RUHSAL BİTKİNLİK
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, false));

        if (snapped) {
            ((ServerLevel)player.level()).sendParticles(ParticleTypes.LARGE_SMOKE,
                    player.getX(), player.getY() + 1, player.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
        }

        player.setDeltaMovement(player.getDeltaMovement().scale(0.3));
        player.hurtMarked = true;
    }

    private static void spawnBondParticles(ServerPlayer player, ThrownShieldEntity shield, double distance) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position().add(0, 1.0, 0);

        level.playSound(player, player.getX(), player.getY(), player.getZ(),
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.5F, 1.2F);

        Vec3 end = shield.position();
        Vec3 vector = end.subtract(start);
        int particleCount = (int) (distance * 2);
        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 point = start.add(vector.scale(t));
            level.sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static void handleSafeExit(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        Vec3 playerPos = player.position();

        // 5 blokluk genişleyen küp taraması
        for (int r = 1; r <= 5; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos check = pos.offset(x, y, z);

                        // 1. Lav ve Tehlike Kontrolü
                        if (player.level().getFluidState(check).is(net.minecraft.tags.FluidTags.LAVA) ||
                                player.level().getFluidState(check.above()).is(net.minecraft.tags.FluidTags.LAVA) ||
                                player.level().getFluidState(check.below()).is(net.minecraft.tags.FluidTags.LAVA)) {
                            continue;
                        }

                        // 2. Geçerlilik (Hava/Su ve Altında katı blok)
                        boolean isPassable = player.level().getBlockState(check).isAir() || !player.level().getFluidState(check).isEmpty();
                        boolean isPassableAbove = player.level().getBlockState(check.above()).isAir() || !player.level().getFluidState(check.above()).isEmpty();
                        boolean isFloorSolid = player.level().getBlockState(check.below()).isSolid();

                        if (isPassable && isPassableAbove && isFloorSolid) {
                            // 3. Kırılmaz Blok (Bedrock/Obsidian) Hattı Kontrolü
                            if (isBlockedByUnbreakable(player, playerPos, new Vec3(check.getX() + 0.5, check.getY(), check.getZ() + 0.5))) {
                                continue;
                            }

                            // BAŞARILI ÇIKIŞ
                            player.teleportTo(check.getX() + 0.5, check.getY(), check.getZ() + 0.5);
                            return;
                        }
                    }
                }
            }
        }

        // ---  Hiçbir güvenli nokta bulunamazsa ---
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
        player.displayClientMessage(Component.literal("§6⚠ REALITY FALTERS—NO FOOTHOLD IN THIS DOMAIN! §fTHY LANDING IS UNWRITTEN... §6⚠"), true);

    }

    // Yardımcı Metod: Arada Bedrock veya Obsidian var mı?
    private static boolean isBlockedByUnbreakable(ServerPlayer player, Vec3 start, Vec3 end) {
        double dist = start.distanceTo(end);
        Vec3 dir = end.subtract(start).normalize();

        for (double d = 0; d < dist; d += 0.5) {
            BlockPos p = BlockPos.containing(start.add(dir.scale(d)));
            BlockState state = player.level().getBlockState(p);

            if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK) ||
                    state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {
                return true;
            }
        }
        return false;
    }


    public static void onPlayerQuit(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();

            // 1. OYUNCU ÇEKİLİRKEN ÇIKARSA: Modunu eski haline döndür
            if (ACTIVE_PULLS.containsKey(uuid)) {
                if (ORIGINAL_GAMEMODE.containsKey(uuid)) {
                    player.gameMode.changeGameModeForPlayer(ORIGINAL_GAMEMODE.get(uuid));
                    ORIGINAL_GAMEMODE.remove(uuid);
                }
                ACTIVE_PULLS.remove(uuid);
            }

            // 2. HAFIZA TEMİZLİĞİ
            LAST_SHIELDS.remove(uuid);
            COOLDOWN.remove(uuid);


        }
    }

}