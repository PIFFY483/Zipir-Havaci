package com.zipirhavaci.core.physics;

import com.zipirhavaci.client.visuals.MeteorVisualEffects;
import com.zipirhavaci.core.ItemRegistry;
import com.zipirhavaci.item.MeteorImpactScrollItem;
import com.zipirhavaci.network.DamageLogS2CPacket;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.physics.VehiclePushHandler;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraftforge.common.util.TransformationHelper;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.*;
import com.mojang.math.Transformation;
import org.joml.Quaternionf;

import net.minecraft.world.level.ChunkPos;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.ibm.icu.impl.ValidIdentifiers.Datatype.x;
import static com.zipirhavaci.physics.MovementHandler.isIronLikeDoor;
import static com.zipirhavaci.physics.MovementHandler.triggerDoorShake;

@Mod.EventBusSubscriber(modid = "zipirhavaci", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ImpactReactionHandler {

    private static final Map<UUID, Integer> CHARGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SONIC_PLAYED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> IMPACT_LOCK = new ConcurrentHashMap<>();

    private static final int MAX_CHARGE = 50;
    private static final long LONG_COOLDOWN_MS = 25000;
    private static final long SHORT_COOLDOWN_MS = 3500;
    private static final Map<UUID, Integer> FALL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> MAX_FALL_SPEED = new ConcurrentHashMap<>();

    public static final Map<UUID, Set<Integer>> PLAYER_CRATERS_IDS = new ConcurrentHashMap<>();
    private static final List<ImpactDamageZone> ACTIVE_DAMAGE_ZONES = new ArrayList<>();
    private record ImpactDamageZone(Vec3 center, double radius, UUID owner, long expireTime) {}
    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger> CHUNK_CRATER_COUNT =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_CRATERS_PER_CHUNK = 150;
    private static final java.util.Random NOISE_RANDOM = new java.util.Random();
    private static final Map<ChunkPos, Set<Integer>> CHUNK_DISPLAYS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger activeCraterCount = new java.util.concurrent.atomic.AtomicInteger(0);
    public static final it.unimi.dsi.fastutil.longs.Long2ObjectMap<CraterBlockData> ACTIVE_CRATERS_MAP = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
    private static int ghostCleanupTimer = 0;


    private static final java.util.concurrent.ExecutorService CRATER_EXECUTOR =
            java.util.concurrent.Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                    r -> {
                        Thread t = new Thread(r, "ZipirKrater-Worker");
                        t.setDaemon(true);
                        return t;
                    }
            );


    public static class CraterBlockData {
        final long startTime;

        public final int destroyId;

        final UUID ownerUUID;

        public CraterBlockData(int destroyId, UUID ownerUUID) {
            this.destroyId = destroyId;
            this.startTime = System.currentTimeMillis();
            this.ownerUUID = ownerUUID;
        }

        boolean isExpired() { return System.currentTimeMillis() - startTime >= 20000; }
        int getElapsedSeconds() { return (int)((System.currentTimeMillis() - startTime) / 1000); }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            tick(e.player);
        }
    }



    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = player.level();

        // 1. Kontrol: Elimizdeki eşya bizim yeni Meteor Parşömeni mi?
        if (stack.getItem() == ItemRegistry.METEOR_IMPACT_SCROLL.get()) {

            if (level.isClientSide) {
                player.swing(event.getHand());
                return;
            }

            // 2. Sunucu tarafı işlemleri
            if (player instanceof ServerPlayer serverPlayer) {

                if (knowsMeteorSkill(serverPlayer)) {
                    serverPlayer.sendSystemMessage(Component.literal("§c⚠ THY SOUL ALREADY HARBORS THIS POWER—DO NOT PROFANE THE SCROLL! §c⚠"), true);
                    event.setCanceled(true);
                    return;
                }

                // Başarım (Advancement) ver
                var advancement = serverPlayer.getServer().getAdvancements().getAdvancement(
                        new net.minecraft.resources.ResourceLocation("zipirhavaci", "from_the_heaven")
                );

                if (advancement != null) {
                    serverPlayer.getAdvancements().award(advancement, "manual_trigger");

                    // Efektler
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0f, 1.2f);
                    serverPlayer.sendSystemMessage(Component.literal("§6☄ §dTHE BURNING SHATTERS ARE ETCHED UPON THY SOUL... §6☄"), false);

                    // 3. ZORLA SİLME (Dupe ve elde kalma koruması)
                    if (!serverPlayer.getAbilities().instabuild) {
                        stack.shrink(1);
                        if (stack.isEmpty()) {
                            serverPlayer.setItemInHand(event.getHand(), ItemStack.EMPTY);
                        }
                        // Envanteri zorla güncelle
                        serverPlayer.containerMenu.broadcastChanges();
                    }
                }
            }

            event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
        }
    }

    @Mod.EventBusSubscriber(modid = "zipirhavaci", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void buildContents(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {

            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.COMBAT) {
                event.accept(ItemRegistry.METEOR_IMPACT_SCROLL.get());
            }
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class LootHandler {
        @SubscribeEvent
        public static void onLootLoad(LootTableLoadEvent event) {
            if (event.getName().toString().equals("minecraft:chests/ancient_city")) {
                LootPool pool = LootPool.lootPool()
                        .name("meteor_skill_book")
                        .setRolls(ConstantValue.exactly(1))
                        .when(LootItemRandomChanceCondition.randomChance(0.15f))
                        .add(LootItem.lootTableItem(ItemRegistry.METEOR_IMPACT_SCROLL.get()))
                        .build();
                event.getTable().addPool(pool);
            }
        }
    }

    public static boolean knowsMeteorSkill(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            var advancement = serverPlayer.getServer().getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation("zipirhavaci", "from_the_heaven"));
            return advancement != null && serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone();
        }

        if (player.level().isClientSide) {
            return net.minecraft.client.Minecraft.getInstance().getConnection().getAdvancements()
                    .getAdvancements().get(new net.minecraft.resources.ResourceLocation("zipirhavaci", "from_the_heaven")) != null;
        }

        return false;
    }

    private static void tick(Player player) {
        // OPTİMİZASYON

        if (!knowsMeteorSkill(player)) {
            CHARGE.put(player.getUUID(), 0);
            return;
        }

        UUID id = player.getUUID();

        if (COOLDOWN.containsKey(id)) {
            Long cooldownTime = COOLDOWN.get(id);
            if (cooldownTime != null) {
                long remaining = cooldownTime - System.currentTimeMillis();
                if (remaining > 0 && !player.isCreative()) return;
            }
            COOLDOWN.remove(id);
            SONIC_PLAYED.remove(id);
            IMPACT_LOCK.remove(id);
        }

        if (!player.onGround() && player.isBlocking()) {
            FALL_TICKS.put(id, FALL_TICKS.getOrDefault(id, 0) + 1);
        } else {
            FALL_TICKS.remove(id);
        }

        ItemStack stack = player.getUseItem();

        if (!(stack.getItem() instanceof ShieldItem)) {
            int charge = CHARGE.getOrDefault(id, 0);
            double currentSpeed = player.getDeltaMovement().length();

            if (charge >= 35 && !IMPACT_LOCK.getOrDefault(id, false)) {
                if (currentSpeed > 1.3) {
                    COOLDOWN.put(id, System.currentTimeMillis() + SHORT_COOLDOWN_MS);
                }
            }

            CHARGE.put(id, 0);
            MAX_FALL_SPEED.remove(id);
            SONIC_PLAYED.remove(id);
            return;
        }

        Vec3 vel = player.getDeltaMovement();
        double currentSpeed = vel.length();
        int charge = CHARGE.getOrDefault(id, 0);

        boolean speedOverride = currentSpeed > 1.25;
        boolean freeFallCharge = FALL_TICKS.getOrDefault(id, 0) >= 50;

        if (speedOverride || freeFallCharge) {
            charge = Math.min(MAX_CHARGE, charge + (speedOverride ? 3 : 1));
            CHARGE.put(id, charge);
            if (player.level().isClientSide) {
                MeteorVisualEffects.updateAmbience(player, charge);
            }
        }

        if (charge >= 35 && !player.onGround()) {
            double lastMax = MAX_FALL_SPEED.getOrDefault(id, 0.0);
            if (currentSpeed > lastMax) MAX_FALL_SPEED.put(id, currentSpeed);
        }

        if (charge >= 50 && !SONIC_PLAYED.getOrDefault(id, false)) {
            SONIC_PLAYED.put(id, true);

            if (player.level().isClientSide) {
                MeteorVisualEffects.playSonicBoom(player);
            } else {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 0.5f);
            }
        }

        if (!player.level().isClientSide && charge >= 35 && !IMPACT_LOCK.getOrDefault(id, false)) {
            if (vel.lengthSqr() > 0.01) {
                Vec3 direction = vel.normalize();
                double raycastDistance = Math.max(3.4, currentSpeed + player.getBbWidth());

                Vec3[] offsets = new Vec3[]{
                        new Vec3(0, player.getBbHeight() * 0.5, 0),       // Merkez (Gövde)
                        new Vec3(0, 0.1, 0),                              // Ayaklar (Yere teğet geçişleri yakalar)
                        new Vec3(0, player.getBbHeight() * 0.9, 0),       // Baş hizası (Tavana/üst bloğa çarpmalar)
                        new Vec3(player.getBbWidth() * 0.4, player.getBbHeight() * 0.5, 0),  // Sağ tolerans
                        new Vec3(-player.getBbWidth() * 0.4, player.getBbHeight() * 0.5, 0)  // Sol tolerans
                };

                BlockHitResult validHit = null;

                for (Vec3 offset : offsets) {
                    Vec3 start = player.position().add(offset);
                    Vec3 end = start.add(direction.scale(raycastDistance));

                    HitResult hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

                    if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                        BlockState state = player.level().getBlockState(pos);

                        // Filtre
                        if (!state.getCollisionShape(player.level(), pos).isEmpty() &&
                                !state.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES)) {

                            validHit = (BlockHitResult) hit;
                            break;
                        }
                    }
                }

                if (validHit != null) {
                    IMPACT_LOCK.put(id, true);
                    double impactSpeed = Math.max(currentSpeed, MAX_FALL_SPEED.getOrDefault(id, 0.0));

                    Vec3 surfaceNormal = Vec3.atLowerCornerOf(validHit.getDirection().getNormal());
                    triggerImpact(player, impactSpeed, surfaceNormal, validHit.getLocation());

                    CHARGE.put(id, 0);
                    MAX_FALL_SPEED.remove(id);
                    COOLDOWN.put(id, System.currentTimeMillis() + LONG_COOLDOWN_MS);
                }
            }
        }

        if (player.onGround()) MAX_FALL_SPEED.remove(id);
    }

    public static void triggerImpact(Player player, double speed, Vec3 normal, Vec3 exactHitPos) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        MeteorVisualEffects.ImpactTemplate impact = MeteorVisualEffects.calculateImpact(speed, normal);
        player.fallDistance = 0;

        // Ses efektleri
        player.level().playSound(null, exactHitPos.x, exactHitPos.y, exactHitPos.z,
                net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.4f);
        player.level().playSound(null, exactHitPos.x, exactHitPos.y, exactHitPos.z,
                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);

        // KALKAN VE ZIRH HASAR HESAPLAMALARI
        ItemStack shield = player.getUseItem();
        if (shield.getItem() instanceof ShieldItem) {
            int damageAmount = Math.max(15, (int)(shield.getMaxDamage() * 0.10));
            shield.hurtAndBreak(damageAmount, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
        }

        double rawDamage = (speed * 4.5) * 0.90;
        float totalToughness = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.getItem() instanceof net.minecraft.world.item.ArmorItem ai) {
                totalToughness += ai.getToughness();
            }
        }
        double weightPenalty = totalToughness * 0.15;
        float totalRaw = (float)(rawDamage + weightPenalty);

        // Büyü indirimleri
        int ff = 0, bp = 0, pr = 0, fr = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty()) {
                ff += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FALL_PROTECTION, armor);
                bp += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLAST_PROTECTION, armor);
                pr += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, armor);
                fr += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.FIRE_PROTECTION, armor);
            }
        }

        float reduction = (ff/16f)*0.30f + (bp/16f)*0.44f + (pr/16f)*0.18f + (fr/16f)*0.30f;
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) reduction += 0.06f;

        // CAN KORUMA VE SELF-DAMAGE UYGULAMASI
        float calculatedDamage = totalRaw * (1.0f - Math.min(0.85f, reduction));
        float finalSelfDamage = Math.min(16.0f, calculatedDamage);
        float currentHealth = player.getHealth();

        if (currentHealth > 10.0f) {
            if (finalSelfDamage >= (currentHealth - 4.0f)) finalSelfDamage = currentHealth - 4.0f;
        } else if (currentHealth > 5.0f) {
            if (finalSelfDamage >= (currentHealth - 2.0f)) finalSelfDamage = currentHealth - 2.0f;
        }

        if (finalSelfDamage > 0.1f) {
            player.hurt(player.damageSources().flyIntoWall(), finalSelfDamage);
        }

        // DURUM EFEKTLERİ VE PATLAMA GÖRSELİ
        player.level().explode(null, player.getX(), player.getY(), player.getZ(), 0.0F, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 5));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30, 4));
        player.setXRot(player.getXRot() + 4.0F);

        //  ALT SİSTEM TETİKLEMELERİ (Hasar, Araçlar, Bloklar)
        applyMeteorDamage(player, speed, impact.radius(), impact.power());
        applyVehicleImpact(player, exactHitPos, speed);
        applyShieldImpact(player, exactHitPos, speed);

        // Blok görüntüleri
        spawnDynamicCracks(player, impact.radius(), normal, exactHitPos);

        if (player instanceof ServerPlayer sp) {
            MeteorVisualEffects.sendImpact(sp, impact.radius(), normal);
        }

        // FİZİK VE BOUNCE YÖNETİMİ
        player.setPos(player.getX(), player.getY() + 0.08, player.getZ());
        final Vec3 impactVelocity = player.getDeltaMovement();
        double dot = impactVelocity.dot(normal);
        Vec3 bounce = impactVelocity.subtract(normal.scale(2.0 * dot)).scale(0.65);
        double finalY = (normal.y > 0.5) ? Math.max(0.55, bounce.y) : bounce.y;
        player.setDeltaMovement(bounce.x, finalY, bounce.z);
        player.hurtMarked = true;

        // Motion Packet Sync
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                sp.getServer().execute(() -> {
                    if (!sp.isRemoved()) {
                        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
                    }
                });
            }, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        // KAPI VE ÇEVRE ETKİLEŞİMİ
        net.minecraft.core.BlockPos impactCenter = new net.minecraft.core.BlockPos(
                (int)Math.floor(exactHitPos.x), (int)Math.floor(exactHitPos.y), (int)Math.floor(exactHitPos.z));

        double breakRadiusSq = 9.0;    // 3.0 blok
        double shakeRadiusSq = 225.0;  // 15.0 blok
        int scanR = 15;

        net.minecraft.core.BlockPos.betweenClosedStream(
                impactCenter.offset(-scanR, -3, -scanR),
                impactCenter.offset(scanR, 3, scanR)
        ).forEach(pos -> {
            net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);
            if (com.zipirhavaci.physics.DoorBlastHandler.isDoor(state)) {
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                        state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    return;
                }

                double distSq = pos.distSqr(impactCenter);
                boolean isIron = com.zipirhavaci.physics.DoorBlastHandler.isIronLikeDoor(state);

                if (distSq <= breakRadiusSq && !isIron) {
                    com.zipirhavaci.physics.DoorBlastHandler.blastFromImpact(serverLevel, player, impactVelocity, speed);
                } else if (distSq <= shakeRadiusSq) {
                    net.minecraft.world.level.block.state.BlockState topState = serverLevel.getBlockState(pos.above());
                    triggerDoorShake(serverLevel, pos, state, topState, isIron);
                }
            }
        });

        ACTIVE_DAMAGE_ZONES.add(new ImpactDamageZone(
                exactHitPos,
                impact.radius(),
                player.getUUID(),
                System.currentTimeMillis() + 20000
        ));
    }

    private static void applyMeteorDamage(Player player, double impactSpeed, double radius, float power) {
        double userToughness = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        float skillMastery = (float) Math.min(1.2f, 0.5f + (userToughness * 0.05f));

        float baseImpactDamage = (float) (impactSpeed * 22.0f * skillMastery);
        float weightBonus = (float) (1.0f + (userToughness * 0.06f));
        double totalEffectRadius = radius + (1.0 + (power * 3.0));

        player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(totalEffectRadius)).forEach(target -> {
            if (target == player) return;

            double distance = target.distanceTo(player);
            double damageRatio = (distance <= 1.2) ? 1.0 : 1.0 - ((distance - 1.2) / (radius - 1.2));

            int targetBP = 0, targetPR = 0;
            float specialHeadReduction = 0;
            double targetToughness = target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);

            if (damageRatio > 0) {
                float baseDamage = (float)(baseImpactDamage * damageRatio * power * weightBonus);

                for (ItemStack armor : target.getArmorSlots()) {
                    if (!armor.isEmpty()) {
                        targetBP += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLAST_PROTECTION, armor);
                        targetPR += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.ALL_DAMAGE_PROTECTION, armor);

                        long currentTime = target.level().getGameTime();

                        // --- WITHER KAFASI ---
                        if (armor.is(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL)) {
                            specialHeadReduction = 0.15f;

                            // Wither Cooldown Kontrolü (5 Dakika)
                            long lastWither = target.getPersistentData().getLong("ZipirWitherCD");
                            if (currentTime >= lastWither + 6000) {
                                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0));
                                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1));
                                target.getPersistentData().putLong("ZipirWitherCD", currentTime);
                            }
                        }
                        // --- CREEPER KAFASI ---
                        else if (armor.is(net.minecraft.world.item.Items.CREEPER_HEAD)) {
                            specialHeadReduction = 0.25f;
                        }
                        // --- KAPLUMBAĞA KABUĞU ---
                        else if (armor.is(net.minecraft.world.item.Items.TURTLE_HELMET)) {
                            specialHeadReduction = 0.18f;
                        }
                    }
                }

                float toughnessRed = (float)(targetToughness * 0.025f);
                float bpRed = (targetBP / 16f) * 0.46f;
                float prRed = (targetPR / 16f) * 0.18f;

                float totalReduction = toughnessRed + bpRed + prRed + specialHeadReduction;
                float finalReduction = Math.min(0.80f, totalReduction);
                float finalDamage = baseDamage * (1.0f - finalReduction);

                float trueDamage = target.getMaxHealth() * 0.08f * (float)damageRatio;

                ItemStack helmet = target.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                if (helmet.is(net.minecraft.world.item.Items.TURTLE_HELMET)) {
                    long lastTurtle = target.getPersistentData().getLong("ZipirTurtleCD");
                    long currentTime = target.level().getGameTime();

                    if (currentTime >= lastTurtle + 6000) { // 5 Dakika Cooldown
                        if (finalDamage + trueDamage > 14.0f) { // 7 Kalp Sınırı
                            finalDamage = 14.0f - trueDamage; // Hasarı 7 kalbe sabitle

                            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0)); // 3s
                            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 7)); // 4s +15 Zırh
                            target.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 200, 1)); // 10s Kaçış

                            target.getPersistentData().putLong("ZipirTurtleCD", currentTime);
                            target.level().addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1, target.getZ(), 0, 0.2, 0);
                        }
                    }
                }

                target.getArmorSlots().forEach(stack -> {
                    if (!stack.isEmpty() && stack.isDamageableItem()) {
                        double durabilityDmg = (targetToughness < 2.0 ? 0.18 : 0.10);
                        stack.hurtAndBreak((int)(stack.getMaxDamage() * durabilityDmg), target, (e) -> e.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.CHEST));
                    }
                });

                if (player instanceof ServerPlayer sp) {
                    if (sp.getPersistentData().getBoolean("ZipirDamageLog")) {

                        String msg = "§6METEOR LOG -> §fTarget: §e" + target.getName().getString() +
                                " §f| Mastery: §b" + String.format("%.2f", skillMastery) +
                                " §f| Final Damage: §c" + String.format("%.2f", (finalDamage + trueDamage)) +
                                " §f| Remaining HP: §a" + String.format("%.2f", Math.max(0, target.getHealth() - (finalDamage + trueDamage)));

                        PacketHandler.sendToPlayer(sp, new DamageLogS2CPacket(msg));
                    }
                }

                target.hurt(player.level().damageSources().magic(), finalDamage + trueDamage);
            }

            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 3, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 1, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20, 0, false, false));

            double kbRatio = 1.0 - (distance / totalEffectRadius);
            if (kbRatio > 0) {
                float finalKb = (float)(3.75 * kbRatio * power) * Math.max(0.0f, 1.0f - ((targetBP * 0.05f) + (targetPR * 0.03f)));
                Vec3 knockDir = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(knockDir.x * finalKb, 0.5 * finalKb, knockDir.z * finalKb);
                target.hurtMarked = true;
            }
        });
    }


    private static void spawnDynamicCracks(Player player, double radius, Vec3 normal, Vec3 impactPoint) {

        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        BlockPos center = BlockPos.containing(impactPoint).relative(Direction.getNearest(normal.x, normal.y, normal.z).getOpposite());
        int r = (int) Math.ceil(radius);

        Vec3 helper = Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 tangent = normal.cross(helper).normalize();
        Vec3 bitangent = normal.cross(tangent).normalize();

        activeCraterCount.incrementAndGet();

        for (int i = 0; i < 3; i++) {
            final double ringRadius = radius * (0.6 + (i * 0.4));
            final int tickDelay = i * 2;
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                serverLevel.getServer().execute(() -> {
                    int particleCount = (int) (24 * ringRadius);
                    for (int j = 0; j < particleCount; j++) {
                        double angle = (j * 2 * Math.PI) / particleCount;
                        Vec3 spread = tangent.scale(Math.cos(angle) * ringRadius).add(bitangent.scale(Math.sin(angle) * ringRadius));
                        Vec3 spawnPos = player.position().add(normal.scale(0.2)).add(spread);

                        if (serverLevel.isLoaded(BlockPos.containing(spawnPos))) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                    spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.05, 0.05, 0.05, 0.02);
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                                    spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }
                });
            }, tickDelay * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        double radiusSq = radius * radius;

        java.util.List<BlockPos> validPositions = new java.util.ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (pos.distSqr(center) <= radiusSq) {
                        validPositions.add(pos);
                    }
                }
            }
        }

        java.util.Collections.shuffle(validPositions, NOISE_RANDOM);

        // --- ASYNC / SYNC YÜRÜTME ---
        if (r > 8) {
            CRATER_EXECUTOR.submit(() -> {
                try {
                    for (BlockPos pos : validPositions) {
                        serverLevel.getServer().execute(() -> {
                            processCraterLogic(player, serverLevel, pos, pos.distSqr(center), radius, impactPoint, normal, center);
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return;
        }

        for (BlockPos pos : validPositions) {
            processCraterLogic(player, serverLevel, pos, pos.distSqr(center), radius, impactPoint, normal, center);
        }

    }

    private static void processCraterLogic(Player player, net.minecraft.server.level.ServerLevel serverLevel, net.minecraft.core.BlockPos pos, double distSq, double radius, net.minecraft.world.phys.Vec3 impactPoint, net.minecraft.world.phys.Vec3 normal, net.minecraft.core.BlockPos center) {
        if (!serverLevel.isLoaded(pos)) return;

        long chunkKey = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        java.util.concurrent.atomic.AtomicInteger chunkCount = CHUNK_CRATER_COUNT.computeIfAbsent(
                chunkKey, k -> new java.util.concurrent.atomic.AtomicInteger(0)
        );

        if (chunkCount.get() >= MAX_CRATERS_PER_CHUNK) return;

        net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);

        if (state.isAir() || !state.getFluidState().isEmpty()) return;

        double dist = Math.sqrt(distSq);
        int id = pos.hashCode() ^ player.getId();

        serverLevel.destroyBlockProgress(id, pos, (int) (9 * (1.0 - dist / radius)));
        ACTIVE_CRATERS_MAP.put(pos.asLong(), new CraterBlockData(id, player.getUUID()));

        double innerBound = radius * 0.75;
        double outerBound = radius + 1.3;

        if (!state.getCollisionShape(serverLevel, pos).isEmpty()) {
            double noise = NOISE_RANDOM.nextDouble() * 0.4;
            if (dist + noise >= innerBound && dist <= outerBound) {
                try {
                    net.minecraft.world.entity.Display.BlockDisplay display = net.minecraft.world.entity.EntityType.BLOCK_DISPLAY.create(serverLevel);
                    if (display == null) return;

                    net.minecraft.world.phys.Vec3 radialDir = new net.minecraft.world.phys.Vec3(
                            pos.getX() - impactPoint.x,
                            pos.getY() - impactPoint.y,
                            pos.getZ() - impactPoint.z
                    ).normalize();

                    float intensity = (float) ((dist - innerBound) / (outerBound - innerBound));

                    float randomPitch = (NOISE_RANDOM.nextFloat() - 0.5f) * 60f * intensity;
                    float randomYaw = (NOISE_RANDOM.nextFloat() - 0.5f) * 60f * intensity;
                    float randomRoll = (NOISE_RANDOM.nextFloat() - 0.5f) * 60f * intensity;

                    org.joml.Quaternionf rotation = new org.joml.Quaternionf()
                            .rotateXYZ(
                                    (float)Math.toRadians(randomPitch),
                                    (float)Math.toRadians(randomYaw),
                                    (float)Math.toRadians(randomRoll)
                            );


                    net.minecraft.world.phys.Vec3 push = radialDir.scale(0.24).add(normal.scale(0.12)).scale(intensity);

                    com.mojang.math.Transformation trans = new com.mojang.math.Transformation(
                            new org.joml.Vector3f((float)push.x, (float)Math.max(push.y, -0.01), (float)push.z),
                            rotation,
                            new org.joml.Vector3f(1.06f + (NOISE_RANDOM.nextFloat() * 0.06f)),
                            null
                    );

                    net.minecraft.nbt.CompoundTag mainTag = new net.minecraft.nbt.CompoundTag();
                    net.minecraft.nbt.CompoundTag transTag = new net.minecraft.nbt.CompoundTag();

                    net.minecraft.nbt.ListTag translation = new net.minecraft.nbt.ListTag();
                    translation.add(net.minecraft.nbt.FloatTag.valueOf(trans.getTranslation().x()));
                    translation.add(net.minecraft.nbt.FloatTag.valueOf(trans.getTranslation().y()));
                    translation.add(net.minecraft.nbt.FloatTag.valueOf(trans.getTranslation().z()));
                    transTag.put("translation", translation);

                    net.minecraft.nbt.ListTag scale = new net.minecraft.nbt.ListTag();
                    scale.add(net.minecraft.nbt.FloatTag.valueOf(trans.getScale().x()));
                    scale.add(net.minecraft.nbt.FloatTag.valueOf(trans.getScale().y()));
                    scale.add(net.minecraft.nbt.FloatTag.valueOf(trans.getScale().z()));
                    transTag.put("scale", scale);

                    org.joml.Quaternionf left = trans.getLeftRotation();
                    net.minecraft.nbt.ListTag leftRot = new net.minecraft.nbt.ListTag();
                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.x));
                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.y));
                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.z));
                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.w));
                    transTag.put("left_rotation", leftRot);

                    net.minecraft.nbt.ListTag rightRot = new net.minecraft.nbt.ListTag();
                    rightRot.add(net.minecraft.nbt.FloatTag.valueOf(0f));
                    rightRot.add(net.minecraft.nbt.FloatTag.valueOf(0f));
                    rightRot.add(net.minecraft.nbt.FloatTag.valueOf(0f));
                    rightRot.add(net.minecraft.nbt.FloatTag.valueOf(1f));
                    transTag.put("right_rotation", rightRot);

                    mainTag.put("transformation", transTag);
                    mainTag.putInt("interpolation_duration", 2);
                    mainTag.putInt("start_interpolation", 0);
                    mainTag.putInt("teleport_duration", 0);

                    net.minecraft.nbt.CompoundTag brightnessTag = new net.minecraft.nbt.CompoundTag();
                    brightnessTag.putInt("block", 15);
                    brightnessTag.putInt("sky", 15);
                    mainTag.put("brightness", brightnessTag);

                    mainTag.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(state));
                    mainTag.putFloat("view_range", 0.8f);

                    mainTag.putByte("Persistent", (byte) 0);

                    mainTag.putBoolean("NoSave", true);

                    display.load(mainTag);

                    double yOffset = (normal.y > 0) ? 0.02 : 0;

                    display.setPos(
                            pos.getX() + (normal.x * 0.04),
                            pos.getY() + (normal.y * 0.04) + yOffset,
                            pos.getZ() + (normal.z * 0.04)
                    );

                    long craterUID = pos.asLong(); // Eşsiz etiket
                    display.addTag("zipir_krater_fx");
                    display.addTag("zipir_id_" + pos.asLong());
                    display.getPersistentData().putLong("crater_origin_pos", pos.asLong());

                    display.getPersistentData().putBoolean("zipir_no_save", true);

                    if (serverLevel.addFreshEntity(display)) {

                        int displayId = display.getId();
                        PLAYER_CRATERS_IDS.computeIfAbsent(player.getUUID(), k -> new java.util.HashSet<>()).add(displayId);
                        ACTIVE_CRATERS_MAP.put(pos.asLong(), new CraterBlockData(id, player.getUUID()));
                        chunkCount.incrementAndGet();

                        com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                            serverLevel.getServer().execute(() -> {
                                net.minecraft.world.entity.Entity e = serverLevel.getEntity(displayId);
                                if (e != null && !e.isRemoved()) {
                                    ImpactReactionHandler.onDisplayRemoved(e.getId(), new ChunkPos(e.blockPosition()));
                                    e.discard();
                                }
                            });
                        }, 20, java.util.concurrent.TimeUnit.SECONDS);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // OPTİMİZASYON
    private static final java.util.concurrent.atomic.AtomicLong LAST_CRATER_UPDATE = new java.util.concurrent.atomic.AtomicLong(0);

    public static void updateActiveCraters(net.minecraft.server.level.ServerLevel sl) {

        if (ACTIVE_CRATERS_MAP.isEmpty() && ACTIVE_DAMAGE_ZONES.isEmpty()) return;

        long now = System.currentTimeMillis();
        // 500ms
        if (now - LAST_CRATER_UPDATE.get() < 500) return;
        LAST_CRATER_UPDATE.set(now);

        if (!ACTIVE_DAMAGE_ZONES.isEmpty()) {
            ACTIVE_DAMAGE_ZONES.removeIf(zone -> now > zone.expireTime());

            // Zoneları gruplara ayır (Mesafe bazlı)
            List<List<ImpactDamageZone>> groups = new ArrayList<>();

            for (ImpactDamageZone zone : ACTIVE_DAMAGE_ZONES) {
                boolean added = false;
                for (List<ImpactDamageZone> group : groups) {
                    // Grubun ilk elemanına olan mesafeye bak (veya merkeze)
                    if (zone.center().distanceToSqr(group.get(0).center()) < 14400) { // 120 * 120 = 14400
                        group.add(zone);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    List<ImpactDamageZone> newGroup = new ArrayList<>();
                    newGroup.add(zone);
                    groups.add(newGroup);
                }
            }

            // Her grubu kendi combinedBox ı ile tara
            for (List<ImpactDamageZone> group : groups) {
                AABB combinedBox = null;
                for (ImpactDamageZone z : group) {
                    double r = z.radius();
                    AABB effectBox = AABB.ofSize(z.center(), r * 2.5, r * 2.5, r * 2.5);
                    combinedBox = (combinedBox == null) ? effectBox : combinedBox.minmax(effectBox);
                }

                if (combinedBox != null) {
                    List<LivingEntity> entitiesInGroup = sl.getEntitiesOfClass(LivingEntity.class, combinedBox);
                    for (ImpactDamageZone z : group) {
                        double rSq = z.radius() * z.radius();
                        for (LivingEntity e : entitiesInGroup) {
                            if (e.position().distanceToSqr(z.center()) <= rSq) {
                                float damage = (e instanceof Player p && p.getUUID().equals(z.owner())) ? 0.12f : 0.25f;
                                e.hurt(sl.damageSources().genericKill(), damage);
                            }
                        }
                    }
                }
            }
        }

        if (!ACTIVE_CRATERS_MAP.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<Long, CraterBlockData>> it = ACTIVE_CRATERS_MAP.entrySet().iterator();

            while (it.hasNext()) {
                java.util.Map.Entry<Long, CraterBlockData> entry = it.next();
                net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(entry.getKey());
                CraterBlockData data = entry.getValue();

                //Chunk kontrolü
                if (!sl.isLoaded(pos)) {
                    ImpactReactionHandler.forceClearChunkData(new ChunkPos(pos));
                    it.remove();
                    continue;
                }

                Player owner = sl.getPlayerByUUID(data.ownerUUID);
                boolean isFarAway = (owner != null && owner.position().distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 25600);

                if (data.isExpired() || isFarAway) {
                    sl.destroyBlockProgress(data.destroyId, pos, -1);

                    sl.getEntities(EntityTypeTest.forClass(Entity.class),
                                    new AABB(pos).inflate(1.5),
                                    e -> e.getTags().contains("zipir_krater_fx"))
                            .forEach(e -> {
                                if (!e.isRemoved()) {
                                    ImpactReactionHandler.onDisplayRemoved(e.getId(), new ChunkPos(e.blockPosition()));
                                    e.discard();
                                }
                            });

                    it.remove();
                    continue;
                }

                //Efektler
                int elapsed = data.getElapsedSeconds();

                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);

                if (elapsed % 3 == 0) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.1, 0.02, 0.1, 0.01);
                }

                if (elapsed % 5 == 0) {
                    sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.2f, 0.6f);
                }
            }
        }

        if (++ghostCleanupTimer >= 200) {
            ghostCleanupTimer = 0;
            runGhostCleanup(sl);
        }

    }

    public static long getRemainingCooldown(UUID playerUUID) {
        if (!COOLDOWN.containsKey(playerUUID)) return 0;
        return Math.max(0, COOLDOWN.get(playerUUID) - System.currentTimeMillis());
    }


    public static void onPlayerQuit(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        java.util.UUID uuid = event.getEntity().getUUID();
        net.minecraft.world.entity.player.Player player = event.getEntity();

        java.util.Set<Integer> craterIds = PLAYER_CRATERS_IDS.remove(uuid);

        if (craterIds != null && player.level() instanceof ServerLevel sl) {
            for (int entityId : craterIds) {
                Entity crater = sl.getEntity(entityId);
                if (crater != null) {
                    ImpactReactionHandler.onDisplayRemoved(crater.getId(), new ChunkPos(crater.blockPosition()));
                    crater.discard();
                }
            }
        }

        if (player.level() instanceof ServerLevel sl) {
            ACTIVE_CRATERS_MAP.entrySet().removeIf(entry -> {
                CraterBlockData data = entry.getValue();
                if (data.ownerUUID.equals(uuid)) {
                    BlockPos pos = BlockPos.of(entry.getKey());

                    sl.destroyBlockProgress(data.destroyId, pos, -1);

                    sl.getEntities(EntityTypeTest.forClass(Entity.class),
                                    new AABB(pos).inflate(1.5),
                                    e -> e.getTags().contains("zipir_krater_fx"))
                            .forEach(e -> {
                                if (!e.isRemoved()) {
                                    ImpactReactionHandler.onDisplayRemoved(e.getId(), new ChunkPos(e.blockPosition()));
                                    e.discard();
                                }
                            });

                    return true;
                }
                return false;
            });

            ACTIVE_DAMAGE_ZONES.removeIf(zone -> zone.owner().equals(uuid));
        }


        CHARGE.remove(uuid);
        COOLDOWN.remove(uuid);
        SONIC_PLAYED.remove(uuid);
        IMPACT_LOCK.remove(uuid);
        FALL_TICKS.remove(uuid);
        MAX_FALL_SPEED.remove(uuid);

        activeCraterCount.updateAndGet(v -> Math.max(0, v));
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        activeCraterCount.set(0);
        ACTIVE_CRATERS_MAP.clear();
        ACTIVE_DAMAGE_ZONES.clear();
        PLAYER_CRATERS_IDS.clear();
    }

    public static void onDisplayRemoved(int displayId, net.minecraft.world.level.ChunkPos chunkPos) {

        java.util.Set<Integer> displays = CHUNK_DISPLAYS.get(chunkPos);
        if (displays != null) {
            synchronized (displays) {
                if (!displays.contains(displayId)) return;
                displays.remove(displayId);
                if (displays.isEmpty()) {
                    CHUNK_DISPLAYS.remove(chunkPos);
                }
            }
        }

        PLAYER_CRATERS_IDS.values().forEach(list -> {
            synchronized (list) {
                list.remove(Integer.valueOf(displayId));
            }
        });

        activeCraterCount.updateAndGet(v -> Math.max(0, v - 1));

        long chunkKey = chunkPos.toLong();
        java.util.concurrent.atomic.AtomicInteger atomicCount = CHUNK_CRATER_COUNT.get(chunkKey);
        if (atomicCount != null) {
            if (atomicCount.get() > 1) {
                atomicCount.decrementAndGet();
            } else {
                CHUNK_CRATER_COUNT.remove(chunkKey);
            }
        }
    }

    public static void forceClearChunkData(net.minecraft.world.level.ChunkPos pos) {
        CHUNK_CRATER_COUNT.remove(pos.toLong());
        CHUNK_DISPLAYS.remove(pos);

        ACTIVE_CRATERS_MAP.keySet().removeIf(blockPosLong -> {
            int chunkX = (int) (blockPosLong >> 38);
            int chunkZ = (int) (blockPosLong << 26 >> 38);
            return chunkX == pos.x && chunkZ == pos.z;
        });
    }

    public static void clearChunkCache(net.minecraft.world.level.ChunkPos pos) {
        CHUNK_DISPLAYS.remove(pos);
    }


    public static void onChunkUnloadCleanup(ServerLevel sl, ChunkPos cp) {

        List<Display.BlockDisplay> toRemove = new ArrayList<>();

        List<Display.BlockDisplay> displays =
                sl.getEntitiesOfClass(Display.BlockDisplay.class,
                        new AABB(
                                cp.getMinBlockX(), sl.getMinBuildHeight(), cp.getMinBlockZ(),
                                cp.getMaxBlockX() + 1, sl.getMaxBuildHeight(), cp.getMaxBlockZ() + 1
                        ),
                        d -> d.getTags().contains("zipir_krater_fx")
                );

        for (Display.BlockDisplay display : displays) {
            toRemove.add(display);
        }

        for (Display.BlockDisplay display : toRemove) {
            ClientboundRemoveEntitiesPacket pkt =
                    new ClientboundRemoveEntitiesPacket(display.getId());
            sl.players().forEach(p -> {
                if (p.chunkPosition().getChessboardDistance(cp) <= 10)
                    p.connection.send(pkt);
            });
            onDisplayRemoved(display.getId(), cp);
            display.discard();
        }

        ACTIVE_CRATERS_MAP.keySet().removeIf(posLong -> {
            int cx = (int)(posLong >> 38);
            int cz = (int)(posLong << 26 >> 38);
            return cx == cp.x && cz == cp.z;
        });

        ACTIVE_DAMAGE_ZONES.removeIf(zone -> {
            Vec3 c = zone.center();
            return ((int) Math.floor(c.x) >> 4) == cp.x
                    && ((int) Math.floor(c.z) >> 4) == cp.z;
        });

        forceClearChunkData(cp);
        clearChunkCache(cp);
    }


    public static void runGhostCleanup(ServerLevel sl) {
        List<Display.BlockDisplay> ghosts = new ArrayList<>();

        List<Display.BlockDisplay> displays =
                sl.getEntitiesOfClass(Display.BlockDisplay.class,
                        new AABB(
                                Integer.MIN_VALUE, sl.getMinBuildHeight(), Integer.MIN_VALUE,
                                Integer.MAX_VALUE, sl.getMaxBuildHeight(), Integer.MAX_VALUE
                        ),
                        d -> d.getTags().contains("zipir_krater_fx")
                );

        for (Display.BlockDisplay display : displays) {
            long originPos = display.getPersistentData().getLong("crater_origin_pos");

            if (originPos == 0 || !ACTIVE_CRATERS_MAP.containsKey(originPos)) {
                ghosts.add(display);
            }
        }

        for (Display.BlockDisplay display : ghosts) {
            ChunkPos cp = new ChunkPos(display.blockPosition());
            ClientboundRemoveEntitiesPacket pkt =
                    new ClientboundRemoveEntitiesPacket(display.getId());
            sl.players().forEach(p -> p.connection.send(pkt));
            onDisplayRemoved(display.getId(), cp);
            display.discard();
        }
    }

    public static void globalCleanupTick(net.minecraft.world.level.Level level) {

    }

    private static void applyVehicleImpact(Player player, Vec3 impactPos, double impactSpeed) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        double radius = 6.0 + (impactSpeed * 1.5);
        AABB box = new AABB(
                impactPos.x - radius, impactPos.y - radius, impactPos.z - radius,
                impactPos.x + radius, impactPos.y + radius, impactPos.z + radius
        );
        serverLevel.getEntities(player, box).forEach(entity -> {
            if (!(entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart)
                    && !(entity instanceof net.minecraft.world.entity.vehicle.Boat)
                    && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) return;
            Vec3 toEntity = entity.position().subtract(impactPos);
            double dist = toEntity.length();
            if (dist < 0.1) return;
            double distFactor = Math.max(0.2, 1.0 - (dist / radius));
            com.zipirhavaci.physics.VehiclePushHandler.tryPushVehicle(entity, toEntity.normalize(), impactSpeed * distFactor * 1.8);
        });

    }

    private static void applyShieldImpact(Player player, Vec3 impactPos, double impactSpeed) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        double nerfedPower = impactSpeed * 0.65; // %35 azaltılmış güç
        double radius = 6.0 + (impactSpeed * 1.5);

        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                impactPos.x - radius, impactPos.y - radius, impactPos.z - radius,
                impactPos.x + radius, impactPos.y + radius, impactPos.z + radius
        );

        serverLevel.getEntitiesOfClass(com.zipirhavaci.entity.ThrownShieldEntity.class, box).forEach(shield -> {
            net.minecraft.world.phys.Vec3 toShield = shield.position().subtract(impactPos);
            double dist = toShield.length();
            if (dist < 0.1) return;

            net.minecraft.world.phys.Vec3 dirNorm = toShield.normalize();
            double distFactor = Math.max(0.2, 1.0 - (dist / radius));

            shield.applySuperSkillPush(dirNorm, nerfedPower * distFactor);
        });
    }
}
