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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.ibm.icu.impl.ValidIdentifiers.Datatype.x;
import static com.zipirhavaci.physics.MovementHandler.isIronLikeDoor;
import static com.zipirhavaci.physics.MovementHandler.triggerDoorShake;

@Mod.EventBusSubscriber(modid = "zipirhavaci", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ImpactReactionHandler {

    private static final Map<UUID, List<net.minecraft.world.entity.Entity>> PLAYER_CRATERS = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> CHARGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SONIC_PLAYED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> IMPACT_LOCK = new ConcurrentHashMap<>();

    private static final int MAX_CHARGE = 50;
    private static final long LONG_COOLDOWN_MS = 25000;
    private static final long SHORT_COOLDOWN_MS = 3500;
    private static final Map<UUID, Integer> FALL_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> MAX_FALL_SPEED = new ConcurrentHashMap<>();
    private static int activeCraterCount = 0;
    private static final CompoundTag REUSABLE_BRIGHTNESS = new CompoundTag();
    private static final List<Display.BlockDisplay> SPAWN_BATCH = new java.util.ArrayList<>();
    private static final java.util.Map<Integer, com.mojang.math.Transformation> TRANSFORMATION_CACHE = new java.util.HashMap<>();
    private static final CompoundTag REUSABLE_NBT = new CompoundTag();
    private static final CompoundTag REUSABLE_TRANS_TAG = new CompoundTag();



    // OPTİMİZASYON: Aktif krater blokları (Thread-safe)
    private static final Map<BlockPos, CraterBlockData> ACTIVE_CRATERS = new ConcurrentHashMap<>();

    private static class CraterBlockData {
        final long startTime;
        final int destroyId;
        final UUID ownerUUID;

        CraterBlockData(int destroyId, UUID ownerUUID) {
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
        // OPTİMİZASYON: Server da krater bloklarını güncelle

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
                double raycastDistance = Math.min(3.4, 1.0 + (currentSpeed * 0.8));

                Vec3[] checkPoints = {
                        player.position(),
                        player.position().add(0.3, 0, 0),
                        player.position().add(-0.3, 0, 0),
                        player.position().add(0, player.getBbHeight() * 0.5, 0),
                        player.position().add(0, player.getBbHeight() * 0.9, 0)
                };

                HitResult closestHit = null;
                double closestDistance = Double.MAX_VALUE;

                for (Vec3 checkPoint : checkPoints) {
                    Vec3 end = checkPoint.add(direction.scale(raycastDistance));
                    HitResult hit = player.level().clip(new ClipContext(checkPoint, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

                    if (hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = ((BlockHitResult)hit).getBlockPos();
                        BlockState state = player.level().getBlockState(pos);

                        //filtre
                        if (state.getCollisionShape(player.level(), pos).isEmpty() ||
                                state.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES)) {
                            continue; // Bu bir bitki, taramaya devam et (meteoru durdurma)
                        }
                    }

                    if (hit.getType() != HitResult.Type.MISS) {
                        double d = checkPoint.distanceTo(hit.getLocation());
                        if (d < closestDistance) {
                            closestDistance = d;
                            closestHit = hit;
                        }
                    }
                }

                if (closestHit != null) {
                    IMPACT_LOCK.put(id, true);
                    double impactSpeed = Math.max(currentSpeed, MAX_FALL_SPEED.getOrDefault(id, 0.0));
                    Vec3 exactHitPos = closestHit.getLocation();

                    Vec3 surfaceNormal = new Vec3(0, 1, 0);
                    if (closestHit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
                        surfaceNormal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
                    }

                    triggerImpact(player, impactSpeed, surfaceNormal, exactHitPos);

                    CHARGE.put(id, 0);
                    MAX_FALL_SPEED.remove(id);
                    COOLDOWN.put(id, System.currentTimeMillis() + LONG_COOLDOWN_MS);
                }
            }
        }

        if (player.onGround()) MAX_FALL_SPEED.remove(id);
    }

    public static void triggerImpact(Player player, double speed, Vec3 normal, Vec3 exactHitPos) {



        MeteorVisualEffects.ImpactTemplate impact = MeteorVisualEffects.calculateImpact(speed, normal);
        player.fallDistance = 0;

        player.level().playSound(null, exactHitPos.x, exactHitPos.y, exactHitPos.z,
                net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.4f);
        player.level().playSound(null, exactHitPos.x, exactHitPos.y, exactHitPos.z,
                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);

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

        float calculatedDamage = totalRaw * (1.0f - Math.min(0.85f, reduction));
        float finalSelfDamage = Math.min(16.0f, calculatedDamage);
        float currentHealth = player.getHealth();

        if (currentHealth > 10.0f) {
            if (finalSelfDamage >= (currentHealth - 4.0f)) finalSelfDamage = currentHealth - 4.0f;
        }
        else if (currentHealth > 5.0f) {
            if (finalSelfDamage >= (currentHealth - 2.0f)) finalSelfDamage = currentHealth - 2.0f;
        }

        if (finalSelfDamage > 0.1f) {
            player.hurt(player.damageSources().flyIntoWall(), finalSelfDamage);
        }

        player.level().explode(null, player.getX(), player.getY(), player.getZ(), 0.0F, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);

        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 35, 5));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 30, 4));
        player.setXRot(player.getXRot() + 4.0F);

        applyMeteorDamage(player, speed, impact.radius(), impact.power());
        applyVehicleImpact(player, exactHitPos, speed);
        applyShieldImpact(player, exactHitPos, speed);
        spawnDynamicCracks(player, impact.radius(), normal, exactHitPos);

        if (player instanceof ServerPlayer sp) {
            MeteorVisualEffects.sendImpact(sp, impact.radius(), normal);
        }

        player.setPos(player.getX(), player.getY() + 0.08, player.getZ());

        final Vec3 impactVelocity = player.getDeltaMovement();
        double dot = impactVelocity.dot(normal);
        Vec3 bounce = impactVelocity.subtract(normal.scale(2.0 * dot)).scale(0.65);

        double finalY = (normal.y > 0.5) ? Math.max(0.55, bounce.y) : bounce.y;
        player.setDeltaMovement(bounce.x, finalY, bounce.z);
        player.hurtMarked = true;

        // YENİ HALİ (Thread-Safe)
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));

            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                // Scheduler'dan Ana Sunucu Thread
                sp.getServer().execute(() -> {
                    if (!sp.isRemoved()) { // Oyuncu hala oyunda mı?
                        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
                    }
                });
            }, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Çarpışma merkezini ve mesafeleri belirle
            net.minecraft.core.BlockPos impactCenter = new net.minecraft.core.BlockPos(
                    (int)Math.floor(exactHitPos.x), (int)Math.floor(exactHitPos.y), (int)Math.floor(exactHitPos.z));

            double breakRadiusSq = 9.0;    // 3.0 blok (3*3)
            double shakeRadiusSq = 225.0;  // 15.0 blok (15*15)

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


                    // 1. Durum: Çok yakın (3 blok altı) VE Demir değilse -> PARÇALA
                    if (distSq <= breakRadiusSq && !isIron) {
                        com.zipirhavaci.physics.DoorBlastHandler.blastFromImpact(serverLevel, player, impactVelocity, speed);
                    }
                    // 2. Durum: (3-15 blok arası mesafe) VEYA (Çok yakın ama Demir kapı ise) -> SALLA
                    else if (distSq <= shakeRadiusSq) {
                        net.minecraft.world.level.block.state.BlockState topState = serverLevel.getBlockState(pos.above());
                        // MovementHandler içindeki metodu çağırıyoruz
                        triggerDoorShake(serverLevel, pos, state, topState, isIron);
                    }
                }
            });
        }

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

                // --- KAPLUMBAĞA GÜVENLİ EVİM ENTEGRASYONU ---
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
                // --- ENTEGRASYON SONU ---

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

        activeCraterCount++;

        for (int i = 0; i < 3; i++) {
            final double ringRadius = radius * (0.6 + (i * 0.4));
            final int tickDelay = i * 2;
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                // İşlemi sunucu kuyruğuna ekle
                serverLevel.getServer().execute(() -> {
                    int particleCount = (int) (24 * ringRadius);
                    for (int j = 0; j < particleCount; j++) {
                        double angle = (j * 2 * Math.PI) / particleCount;
                        Vec3 spread = tangent.scale(Math.cos(angle) * ringRadius).add(bitangent.scale(Math.sin(angle) * ringRadius));
                        Vec3 spawnPos = player.position().add(normal.scale(0.2)).add(spread);

                        // Chunk yüklü mü kontrolü ekledik (Ekstra Güvenlik)
                        if (serverLevel.isLoaded(BlockPos.containing(spawnPos))) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                    spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.05, 0.05, 0.05, 0.02);
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                                    spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }
                });
            }, tickDelay * 50, TimeUnit.MILLISECONDS);
        }

        double radiusSq = radius * radius;

        java.util.Random sharedRandom = new java.util.Random();
        
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    net.minecraft.core.BlockPos pos = center.offset(x, y, z);
                    double distSq = pos.distSqr(center);

                    if (distSq > radiusSq) continue;
                    if (!serverLevel.isLoaded(pos)) continue;

                    net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
                    if (state.isAir()) continue;

                    double dist = Math.sqrt(distSq);
                    int id = pos.hashCode() ^ player.getId();

                    player.level().destroyBlockProgress(id, pos, (int) (9 * (1.0 - dist / radius)));
                    ACTIVE_CRATERS.put(pos, new CraterBlockData(id, player.getUUID()));

                    double innerBound = radius * 0.75;
                    double outerBound = radius + 1.3;

                    if (state.isSolidRender(player.level(), pos)) {
                        double noise = sharedRandom.nextDouble() * 0.4;
                        if (dist + noise >= innerBound && dist <= outerBound) {
                            
                            net.minecraft.world.entity.Display.BlockDisplay display = new net.minecraft.world.entity.Display.BlockDisplay(EntityType.BLOCK_DISPLAY, serverLevel);
                            serverLevel.addFreshEntity(display);
                            display.setPos(x, y, z); 

                            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                            display.saveWithoutId(nbt);

                            if (display != null) {
                                try {
                                    
                                    nbt.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(state));

                                    // HESAPLAMALAR
                                    net.minecraft.world.phys.Vec3 radialDir = new net.minecraft.world.phys.Vec3(
                                            pos.getX() - impactPoint.x,
                                            pos.getY() - impactPoint.y,
                                            pos.getZ() - impactPoint.z
                                    ).normalize();

                                    double yOffset = (normal.y > 0) ? 0.02 : 0;
                                    // Pozisyon güncelleme
                                    display.setPos(pos.getX() + (normal.x * 0.04),
                                            pos.getY() + (normal.y * 0.04) + yOffset,
                                            pos.getZ() + (normal.z * 0.04));

                                    float intensity = (float) ((dist - innerBound) / (outerBound - innerBound));
                                    float randRot = (sharedRandom.nextFloat() - 0.5f) * 0.15f;

                                    net.minecraft.world.phys.Vec3 push = radialDir.scale(0.12).add(normal.scale(0.06)).scale(intensity);

                                    // Görüntüyü yamultan 
                                    com.mojang.math.Transformation trans = new com.mojang.math.Transformation(
                                            new org.joml.Vector3f((float)push.x, (float)Math.max(push.y, -0.01f), (float)push.z),
                                            new org.joml.Quaternionf()
                                                    .rotateX((float)z * 0.06f * intensity + randRot)
                                                    .rotateZ((float)-x * 0.06f * intensity + randRot),
                                            new org.joml.Vector3f(1.04f + (sharedRandom.nextFloat() * 0.02f)),
                                            null
                                    );

                                    
                                    net.minecraft.nbt.CompoundTag transTag = new net.minecraft.nbt.CompoundTag();
                                    display.saveWithoutId(nbt); 

                                    
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

                                    // Left Rotation 
                                    org.joml.Quaternionf left = trans.getLeftRotation();
                                    net.minecraft.nbt.ListTag leftRot = new net.minecraft.nbt.ListTag();
                                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.x));
                                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.y));
                                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.z));
                                    leftRot.add(net.minecraft.nbt.FloatTag.valueOf(left.w));
                                    transTag.put("left_rotation", leftRot);

                                    // Right Rotation 
                                    org.joml.Quaternionf right = trans.getRightRotation();
                                    if (right != null) {
                                        net.minecraft.nbt.ListTag rightRot = new net.minecraft.nbt.ListTag();
                                        rightRot.add(net.minecraft.nbt.FloatTag.valueOf(right.x));
                                        rightRot.add(net.minecraft.nbt.FloatTag.valueOf(right.y));
                                        rightRot.add(net.minecraft.nbt.FloatTag.valueOf(right.z));
                                        rightRot.add(net.minecraft.nbt.FloatTag.valueOf(right.w));
                                        transTag.put("right_rotation", rightRot);
                                    }

                                    
                                    nbt.put("transformation", transTag);
                                    display.addTag("zipir_krater_fx");
                                    nbt.putBoolean("NoSave", true);

                                    // Brightness ayarı
                                    net.minecraft.nbt.CompoundTag brightness = new net.minecraft.nbt.CompoundTag();
                                    brightness.putInt("block", 7);
                                    brightness.putInt("sky", 7);
                                    nbt.put("brightness", brightness);
                                    
                                    nbt.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(state));
                                    
                                    display.load(nbt);
                                    serverLevel.addFreshEntity(display);

                                    PLAYER_CRATERS.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayList<>()).add(display);

                                    // Zamanlayıcı
                                    com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                                        serverLevel.getServer().execute(() -> {
                                            if (display != null && !display.isRemoved()) {
                                                display.discard();
                                                if (activeCraterCount > 0) activeCraterCount--;
                                            }
                                        });
                                    }, 20, java.util.concurrent.TimeUnit.SECONDS);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
        SPAWN_BATCH.clear();
        
    }

    // OPTİMİZASYON: Aktif kraterleri saniyede 1 kez güncelle
    private static final java.util.concurrent.atomic.AtomicLong LAST_CRATER_UPDATE = new java.util.concurrent.atomic.AtomicLong(0);

    public static void updateActiveCraters(net.minecraft.server.level.ServerLevel sl) {
        if (ACTIVE_CRATERS.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - LAST_CRATER_UPDATE.get() < 1000) return;
        LAST_CRATER_UPDATE.set(now);

        // 2. DÖNGÜ VE MANTIK ANALİZİ
        Iterator<Map.Entry<BlockPos, CraterBlockData>> it = ACTIVE_CRATERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, CraterBlockData> entry = it.next();
            BlockPos pos = entry.getKey();
            CraterBlockData data = entry.getValue();

            // SÜRE DOLMA KONTROLÜ
            if (data.isExpired()) {
                if (sl.isLoaded(pos)) {
                    sl.destroyBlockProgress(data.destroyId, pos, -1);
                    sl.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos).inflate(1.2),
                            e -> e.getTags().contains("zipir_krater_fx")).forEach(net.minecraft.world.entity.Entity::discard);
                }
                if (activeCraterCount > 0) activeCraterCount--;
                it.remove();
                continue;
            }

            if (!sl.isLoaded(pos)) continue;

            int elapsed = data.getElapsedSeconds();

            // GÖRSEL EFEKTLER (SMOKE)
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);

            // ATEŞ EFEKTİ
            if (elapsed % 3 == 0) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.1, 0.02, 0.1, 0.01);
            }

            // ALAN HASARI
            List<LivingEntity> entities = sl.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()));
            for (LivingEntity e : entities) {
                float damage = 0.25f;
                if (e instanceof Player p && p.getUUID().equals(data.ownerUUID)) {
                    damage = 0.12f;
                }
                e.hurt(sl.damageSources().genericKill(), damage);
            }

            // SES
            if (elapsed % 5 == 0) {
                sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.2f, 0.6f);
            }
        }
    }

    public static long getRemainingCooldown(UUID playerUUID) {
        if (!COOLDOWN.containsKey(playerUUID)) return 0;
        return Math.max(0, COOLDOWN.get(playerUUID) - System.currentTimeMillis());
    }


    public static void onPlayerQuit(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();

        // 1. ENTITY TEMİZLİĞİ (Blok Displayleri Siler)
        List<net.minecraft.world.entity.Entity> craters = PLAYER_CRATERS.remove(uuid);
        if (craters != null) {
            for (net.minecraft.world.entity.Entity crater : craters) {
                if (crater != null && crater.isAlive()) {
                    crater.discard();
                    if (activeCraterCount > 0) activeCraterCount--;
                }
            }
        }

        // 2. BLOK PROGRESS TEMİZLİĞİ
        // Oyuncunun oluşturduğu çatlakları ACTIVE_CRATERS map
        if (event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel sl) {
            ACTIVE_CRATERS.entrySet().removeIf(entry -> {
                if (entry.getValue().ownerUUID.equals(uuid)) {
                    sl.destroyBlockProgress(entry.getValue().destroyId, entry.getKey(), -1);
                    return true;
                }
                return false;
            });
        }

        // 3. GENEL VERİ TEMİZLİĞİ
        CHARGE.remove(uuid);
        COOLDOWN.remove(uuid);
        SONIC_PLAYED.remove(uuid);
        IMPACT_LOCK.remove(uuid);
        FALL_TICKS.remove(uuid);
        MAX_FALL_SPEED.remove(uuid);

        if (activeCraterCount < 0) activeCraterCount = 0;
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        activeCraterCount = 0;
        ACTIVE_CRATERS.clear();
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
