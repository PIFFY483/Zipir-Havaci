package com.zipirhavaci.common;

import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.core.capability.StaticProgressionData;
import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.core.physics.DarkAuraHandler;
import com.zipirhavaci.core.physics.ImpactReactionHandler;
import com.zipirhavaci.core.physics.ShieldRamMechanicHandler;
import com.zipirhavaci.core.physics.SoulBondHandler;
import com.zipirhavaci.energy.EnergyNetworkManager;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.SyncCraterPacket;
import com.zipirhavaci.network.SyncStaticProgressionPacket;
import com.zipirhavaci.physics.MovementHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.zipirhavaci.core.physics.ImpactReactionHandler.PLAYER_CRATERS_IDS;
import static com.zipirhavaci.core.physics.SoulBondHandler.ACTIVE_PULLS;


@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonModEvents {

    private static final java.util.concurrent.ConcurrentHashMap<
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            int[]> SCANNED_BOUNDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int N=0, NE=1, E=2, SE=3, S=4, SW=5, W=6, NW=7, CENTER=8;

    private static int[] getDirectionAndDist(int chunkX, int chunkZ) {
        if (chunkX == 0 && chunkZ == 0) return new int[]{CENTER, 0};
        double angle = Math.toDegrees(Math.atan2(chunkX, -chunkZ));
        if (angle < 0) angle += 360;
        int dir = (int)((angle + 22.5) / 45) % 8;
        int dist = Math.max(Math.abs(chunkX), Math.abs(chunkZ));
        return new int[]{dir, dist};
    }

    private static boolean isAlreadyScanned(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
            int chunkX, int chunkZ) {
        int[] bounds = SCANNED_BOUNDS.get(dim);
        if (bounds == null) return false;
        int[] dd = getDirectionAndDist(chunkX, chunkZ);
        return bounds[dd[0]] >= dd[1];
    }

    private static void markScanned(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
            int chunkX, int chunkZ) {
        int[] bounds = SCANNED_BOUNDS.computeIfAbsent(dim, k -> new int[9]);
        int[] dd = getDirectionAndDist(chunkX, chunkZ);
        if (bounds[dd[0]] < dd[1]) bounds[dd[0]] = dd[1];
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        if (event.player instanceof ServerPlayer serverPlayer) {
            SoulBondHandler.onServerTick(serverPlayer);

            if (serverPlayer.isAlive() && serverPlayer.isSpectator() &&
                    !SoulBondHandler.ACTIVE_PULLS.containsKey(serverPlayer.getUUID()) &&
                    SoulBondHandler.hasOriginalGameMode(serverPlayer.getUUID())) {

                serverPlayer.setGameMode(GameType.SURVIVAL);
                SoulBondHandler.removeOriginalGameMode(serverPlayer.getUUID()); // Kaydı temizle ki döngüye girmesin
            }
            // -------------------------

            serverPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {

                // ─── DARK AURA BYPASS ───────────────────────────
                if (data.isCursed()) {
                    DarkAuraHandler.onCursedPlayerTick(serverPlayer, data);
                    DarkAuraHandler.tickDarkAura(serverPlayer, data);
                    return;
                }
                // ────────────────────

                if (!data.isAuraActive()) return;

                int currentTicks = data.getAuraTicksLeft();
                data.setAuraTicksLeft(--currentTicks);

                if (currentTicks % 5 == 0) {
                    PacketHandler.sendToTracking(serverPlayer, new SyncStaticProgressionPacket(data, serverPlayer.getId()));
                }

                if (currentTicks <= 0) {
                    deactivateAura(serverPlayer, data);
                    return;
                }

                applyAuraEffectsWithPhysics(serverPlayer, data.getAuraLevel());

                if (serverPlayer.tickCount % 5 == 0) {
                    handlePassivePushField(serverPlayer, data, data.getAuraLevel());
                }
            });
        }
    }

    private static void applyAuraEffectsWithPhysics(ServerPlayer player, float level) {
        int speedAmp = (level >= 3.0f) ? 2 : (level >= 1.0f ? 1 : 0);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, speedAmp, false, false, true));

        if (level >= 3.0f) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 1, false, false, true));
            if (!player.onGround()) {
                Vec3 look = player.getLookAngle();
                player.setDeltaMovement(player.getDeltaMovement().add(look.x * 0.06, 0, look.z * 0.06));
                player.hurtMarked = true;
            }
        }
    }

    private static void deactivateAura(ServerPlayer player, StaticProgressionData data) {
        data.setAuraActive(false);
        data.setLastAuraUseTime(System.currentTimeMillis());
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.JUMP);
        player.sendSystemMessage(Component.literal("§cAura Resonance Exhausted"));
        PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
    }

    private static void handlePassivePushField(ServerPlayer serverPlayer, StaticProgressionData data, float level) {
        int maxTicks;
        if (level >= 3.0f) maxTicks = 600;
        else if (level >= 2.0f) maxTicks = 520;
        else if (level >= 1.0f) maxTicks = 480;
        else maxTicks = 360;

        float energyRatio = Math.max(0.05f, (float) data.getAuraTicksLeft() / (float) maxTicks);
        double range = ((level >= 3.0f) ? 5.0 : 3.0) * energyRatio;
        float pushPower = (level >= 3.0f) ? 0.7f : 0.4f;

        serverPlayer.level().getEntitiesOfClass(LivingEntity.class, serverPlayer.getBoundingBox().inflate(range), target -> {
            if (target == serverPlayer) return false;
            return target.getCapability(StaticProgressionProvider.STATIC_PROGRESSION)
                    .map(tData -> !tData.isAuraActive())
                    .orElse(true);
        }).forEach(target -> {
            applyPassivePush(serverPlayer, target, range, pushPower, level, energyRatio);
        });
    }

    private static void applyPassivePush(ServerPlayer serverPlayer, net.minecraft.world.entity.LivingEntity target,
                                         double dynamicRange, float basePushPower, float level, float energyRatio) {
        double distance      = target.distanceTo(serverPlayer);
        float distanceRatio  = 1.0f - ((float) distance / (float) dynamicRange);
        float smartPushPower = basePushPower * (0.5f + distanceRatio * 0.5f);

        net.minecraft.world.phys.Vec3 dir = target.position().subtract(serverPlayer.position()).normalize();
        target.push(dir.x * smartPushPower, 0.15, dir.z * smartPushPower);

        if (level >= 3.0f && distance < dynamicRange * 0.5) {
            int slowDuration = (int)(60 * distanceRatio);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, slowDuration, 0));
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
                // Lanetli oyuncu: strike sıfırlanmaz (zaten kullanamıyor), aura kapalı kalır
                // Lanet de silinmez — onPlayerClone'da kopyalanacak
                if (data.isCursed()) return;

                if (data.getStrikeCount() > 0) {
                    data.setStrikeCount(0);
                    data.setAuraActive(false);
                    data.setAuraTicksLeft(0);
                    player.sendSystemMessage(Component.literal("§cTHY SOUL HAS BUCKLED UNDER THE WEIGHT, THY LEGACY HAS WITHERED!"));
                    PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
                }
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
                PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        oldPlayer.reviveCaps();

        // ─── ANIMA TAG  ───
        if (oldPlayer.getTags().contains("has_anima_light")) {
            newPlayer.addTag("has_anima_light");
        }

        oldPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(oldData -> {
            newPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(newData -> {
                // 1. HER DURUMDA KOPYALANANLAR
                newData.setAuraLevel(oldData.getAuraLevel());
                newData.setLightningResist(oldData.getLightningResist());
                newData.setLastRodPos(oldData.getLastRodPos());
                newData.setTrainingStartTime(oldData.getTrainingStartTime());
                newData.setCursed(oldData.isCursed());
                newData.setLastDeathDenyTime(oldData.getLastDeathDenyTime());
                newData.setDarkAuraLevel(oldData.getDarkAuraLevel());
                newData.setLastDarkAuraUseTime(oldData.getLastDarkAuraUseTime());

                // YENİ/KRİTİK: Ritüel ilerlemesi ve duman görünürlüğü (Her iki durumda da korunmalı)
                newData.setSmokeVisible(oldData.isSmokeVisible());
                newData.setRitualStep(oldData.getRitualStep());

                if (event.isWasDeath()) {
                    // 2. SADECE ÖLÜM DURUMUNDA SIFIRLANANLAR
                    newData.setStrikeCount(0);
                    newData.setAuraActive(false);
                    newData.setAuraTicksLeft(0);
                    newData.setDarkAuraActive(false);
                    newData.setDarkAuraTicksLeft(0);

                    if (oldPlayer instanceof ServerPlayer sOld && newPlayer instanceof ServerPlayer sNew) {
                        grantAdvancementIfHad(sOld, sNew, new ResourceLocation(ZipirHavaci.MOD_ID, "soul_bound"));
                        grantAdvancementIfHad(sOld, sNew, new ResourceLocation(ZipirHavaci.MOD_ID, "from_the_heaven"));
                        grantAdvancementIfHad(sOld, sNew, new ResourceLocation(ZipirHavaci.MOD_ID, "light_from_crumbs"));

                        // ─── ANIMA (CRUMBS) PASİFİ (+4 KALP) ───
                        if (sNew.getTags().contains("has_anima_light")) {
                            var maxHealth = sNew.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                            UUID animaBonusId = UUID.fromString("f470298a-211c-4f71-a9f8-4f243e887f86");
                            if (maxHealth != null && maxHealth.getModifier(animaBonusId) == null) {
                                maxHealth.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                        animaBonusId, "Anima Light Bonus", 8.0,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                            }
                        }

                        // ─── DARK AURA RİTÜEL PASİFİ (+1 KALP) ───
                        if (!newData.isSmokeVisible()) {
                            var maxHealth = sNew.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                            UUID ritualBonusId = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
                            if (maxHealth != null && maxHealth.getModifier(ritualBonusId) == null) {
                                maxHealth.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                        ritualBonusId, "Dark Aura Ritual HP", 2.0,
                                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                            }
                        }

                        // Canı tazele
                        sNew.setHealth(sNew.getMaxHealth());
                    }
                } else {
                    // 3. BOYUT GEÇİŞİ (NETHER/END): Her şey olduğu gibi korunur
                    newData.setStrikeCount(oldData.getStrikeCount());
                    newData.setAuraActive(oldData.isAuraActive());
                    newData.setAuraTicksLeft(oldData.getAuraTicksLeft());
                    newData.setLastAuraUseTime(oldData.getLastAuraUseTime());
                    newData.setDarkAuraActive(oldData.isDarkAuraActive());
                    newData.setDarkAuraTicksLeft(oldData.getDarkAuraTicksLeft());
                }

                if (newPlayer instanceof ServerPlayer sp) {
                    PacketHandler.sendToPlayer(sp, new SyncStaticProgressionPacket(newData, sp.getId()));
                }
            });
        });

        oldPlayer.invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            SoulBondHandler.onPlayerQuit(event);
            ImpactReactionHandler.onPlayerQuit(event);
            ShieldRamMechanicHandler.onPlayerLoggedOut(event);
            MovementHandler.cleanup(uuid, player);
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof net.minecraft.server.level.ServerLevel sl) {
            ImpactReactionHandler.updateActiveCraters(sl);
            EnergyNetworkManager.get(sl).tick(sl);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        EnergyNetworkManager.get(sl).onChunkLoaded(sl, chunk);
        markScanned(sl.dimension(), chunk.getPos().x, chunk.getPos().z);

        ChunkPos cp = chunk.getPos();

    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ChunkPos cp = chunk.getPos();
        EnergyNetworkManager.get(sl).onChunkUnloaded(sl, chunk);
        ImpactReactionHandler.onChunkUnloadCleanup(sl, cp);
    }


    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {

        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide && event.level.getGameTime() % 20 == 0) {
            ImpactReactionHandler.globalCleanupTick(event.level);
        }
    }

    @SubscribeEvent
    public static void onLevelSave(net.minecraftforge.event.level.LevelEvent.Save event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        PLAYER_CRATERS_IDS.values().forEach(activeIds -> {

            if (activeIds != null && !activeIds.isEmpty()) {

                new java.util.HashSet<>(activeIds).forEach(id -> {

                    net.minecraft.world.entity.Entity e = sl.getEntity(id);

                    if (e instanceof net.minecraft.world.entity.Display.BlockDisplay display) {
                        net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(display.blockPosition());

                        ImpactReactionHandler.onDisplayRemoved(id, cp);
                        display.discard();
                    }
                });
            }
        });
    }


    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncPlayerData(event.getEntity());
        MovementHandler.cleanup(event.getEntity().getUUID(), event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncPlayerData(event.getEntity());
    }

    private static void syncPlayerData(Player player) {
        if (player instanceof ServerPlayer sPlayer) {
            sPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
                PacketHandler.sendToTracking(sPlayer, new SyncStaticProgressionPacket(data, sPlayer.getId()));
            });
        }
    }

    private static void grantAdvancementIfHad(ServerPlayer oldPlayer, ServerPlayer newPlayer, ResourceLocation advancementId) {
        var advancement = oldPlayer.getServer().getAdvancements().getAdvancement(advancementId);
        if (advancement != null && oldPlayer.getAdvancements().getOrStartProgress(advancement).isDone()) {
            newPlayer.getAdvancements().award(advancement, "manual_trigger");
        }
    }


    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerLevel serverLevel = event.getLevel();
        ChunkPos cp = event.getPos();
        ServerPlayer player = (ServerPlayer) event.getPlayer();

        for (int delay = 5; delay <= 20; delay += 5) {
            int finalDelay = delay;
            serverLevel.getServer().execute(() -> {

            });
        }

        serverLevel.getServer().execute(() ->
                serverLevel.getServer().execute(() ->
                        serverLevel.getServer().execute(() -> {
                            List<Long> validIds = ImpactReactionHandler.ACTIVE_CRATERS_MAP.keySet().stream()
                                    .filter(posLong -> {
                                        BlockPos p = BlockPos.of(posLong);
                                        return (p.getX() >> 4) == cp.x && (p.getZ() >> 4) == cp.z;
                                    }).collect(java.util.stream.Collectors.toList());

                            PacketHandler.sendToPlayer(player, new SyncCraterPacket(cp, validIds));
                        })
                )
        );
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.Display.BlockDisplay display) {

            if (display.getTags().contains("zipir_krater_fx")) {
                long originPos = display.getPersistentData().getLong("crater_origin_pos");

                if (originPos == 0 || !com.zipirhavaci.core.physics.ImpactReactionHandler.ACTIVE_CRATERS_MAP.containsKey(originPos)) {

                    event.setCanceled(true);
                    display.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer target)) return;
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) return;

        target.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            // Lanetliyse projectile deflection çalışmaz
            if (data.isCursed()) return;
            if (!data.isAuraActive()) return;

            float level = data.getAuraLevel();
            int ticksLeft = data.getAuraTicksLeft();

            int maxTicks;
            if (level >= 3.0f) maxTicks = 600;
            else if (level >= 2.0f) maxTicks = 520;
            else if (level >= 1.0f) maxTicks = 480;
            else maxTicks = 360;

            float energyRatio = Math.max(0.05f, (float) ticksLeft / (float) maxTicks);

            if (energyRatio > 0.30f) {
                event.setCanceled(true);

                Vec3 targetPos = target.position();
                Vec3 projPos   = projectile.position();
                Vec3 deflectDirection = projPos.subtract(targetPos).normalize();

                double deflectPower = (level >= 3.0f) ? 1.5 : (level >= 2.0f) ? 1.2 : 1.0;
                projectile.setDeltaMovement(deflectDirection.scale(deflectPower));

                if (projectile instanceof AbstractArrow arrow) {
                    arrow.setNoPhysics(false);
                }

                target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.2f + (level * 0.2f));

                if (target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            net.minecraft.core.particles.ParticleTypes.CRIT,
                            projPos.x, projPos.y, projPos.z,
                            5, 0.2, 0.2, 0.2, 0.1
                    );
                }
            }
        });
    }

    @Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {

        @SubscribeEvent
        public static void onEntityAttributeCreation(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
            event.put(
                    com.zipirhavaci.core.EntityRegistry.SILENT_CAPTIVE.get(),
                    com.zipirhavaci.entity.SilentCaptiveEntity.createAttributes().build()
            );

            event.put(
                    com.zipirhavaci.core.EntityRegistry.LIBRATED_SOUL.get(),
                    com.zipirhavaci.entity.LibratedSoulEntity.createAttributes().build()
            );
        }
    }
}
