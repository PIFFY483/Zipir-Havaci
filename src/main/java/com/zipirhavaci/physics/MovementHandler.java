package com.zipirhavaci.physics;

import com.zipirhavaci.client.visuals.GauntletVisuals;
import com.zipirhavaci.core.*;
import com.zipirhavaci.client.AnimationManager;
import com.zipirhavaci.item.ZipirAviatorItem;
import com.zipirhavaci.network.*;
import com.zipirhavaci.block.entity.DoorShakeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import software.bernie.geckolib.animatable.GeoItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.TriggerCameraShakePacket;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MovementHandler {

    // Fizik Limitleri
    private static final double LIMIT_Y = -1.9;
    private static final double WEIGHT_THRESHOLD = 46.0;

    public static void executeLaunchFPV(Player player, ItemStack stack) {
        executeLaunch(player, stack, "fire");
    }

    public static void executeLaunchTPV(Player player, ItemStack stack) {
        executeLaunch(player, stack, "tpv_fire");
    }

    public static void executeLaunch(Player player, ItemStack stack) {
        executeLaunch(player, stack, "fire");
    }

    private static final java.util.Map<java.util.UUID, Integer> RECHARGE_TIMER = new java.util.HashMap<>();

    // --- ANA MOTOR BLOK ---
    public static void executeLaunch(Player player, ItemStack stack, String animName) {
        if (player.level().isClientSide) return;
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;

        ServerLevel level = (ServerLevel) player.level();
        ServerPlayer serverPlayer = (ServerPlayer) player;
        CompoundTag nbt = stack.getOrCreateTag();
        int uses = nbt.getInt("Uses");
        int mode = nbt.getInt("AviatorMode"); // 0: PULL, 1: PUSH

        // --- AĞIRLIK HESAPLAMALARI ---
        double armor = player.getAttributeValue(Attributes.ARMOR);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double totalWeight = Mth.clamp(armor + toughness, 0, 60);
        double weightRatio = totalWeight / 60.0;

        // --- MEVCUT DEĞİŞKENLER ---
        double mobilityPenalty = 1.0 - (weightRatio * 0.20);
        double powerBoost = 1.0 + (weightRatio * 0.20);
        double damageBoost = 1.0 + (weightRatio * 0.05);

        // --- GÜÇ HESAPLAMA ---
        boolean isOffhandEmpty = player.getOffhandItem().isEmpty();
        double offhandMultiplier = isOffhandEmpty ? 1.15 : 1.0;
        double finalPower = AviatorConstants.LAUNCH_POWER * offhandMultiplier * mobilityPenalty;

        // --- OVERLOAD KONTROLÜ ---
        double currentY = player.getDeltaMovement().y;
        boolean isOverloaded = false;

        if (mode == 1 && currentY < LIMIT_Y) {
            if (totalWeight > WEIGHT_THRESHOLD) {
                isOverloaded = true;
            } else {
                double limitPenalty = 1.0 - (totalWeight / WEIGHT_THRESHOLD) * 0.9;
                finalPower *= limitPenalty;
            }
        }

        // --- ETRAFTAKİ VARLIKLARA ETKİ ---
        Vec3 look = player.getLookAngle();
        AABB area = player.getBoundingBox().inflate(3.5);
        List<Entity> entities = level.getEntities(player, area);

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity living && entity != player) {
                float finalDamage = 7.0f * (float)damageBoost;
                living.hurt(player.damageSources().playerAttack(player), finalDamage);

                Vec3 dir = entity.position().subtract(player.position()).normalize();
                double pushMult = (mode == 1) ? 2.2 : 1.0;
                double finalPush = pushMult * powerBoost;
                living.setDeltaMovement(dir.x * finalPush, 0.5, dir.z * finalPush);
                living.hurtMarked = true;
            }
        }

        // --- OYUNCU FIRLATMA VE MANTIKSAL BİRLEŞTİRME ---
        if (isOverloaded && mode == 1) {
            // DURUM A: ARIZA (Duman çıkar ama fırlatma)
            nbt.putInt("ZipirOverloadTimer", 20);
            int slot = serverPlayer.getInventory().selected;
            if (slot != -1) PacketHandler.sendToPlayer(serverPlayer, new SyncItemNBTPacket(slot, nbt));
            PacketHandler.sendToPlayer(serverPlayer, new PlayAviatorFollowSoundPacket(player.getId(), 0.40F));
        }
        else {
            // DURUM B: NORMAL ÇALIŞMA (PULL veya PUSH Uygun)
            CompoundTag pData = player.getPersistentData();

            // --- 1. EN BAŞTA: SÜRTÜNME BYPASS (noPhysics - 1 Tick) ---

            player.noPhysics = true;
            pData.putBoolean("NoPhysicsResetNextTick", true);

            // --- 2. HESAPLAMALAR (Miras ve Güç) ---
            double oldX = pData.getDouble("MomX") * 0.10;
            double oldY = pData.getDouble("MomY") * 0.10;
            double oldZ = pData.getDouble("MomZ") * 0.10;
            double oldStored = pData.getDouble("AviatorMomentum") * 0.10;

            double balancedPower = finalPower * 0.65;

            // --- 3. ATEŞLEME (LAUNCH) ---
            if (mode == 0) { // PULL (Çekme)

                player.setDeltaMovement(
                        (look.x * balancedPower) + oldX,
                        (look.y * (balancedPower * 1.1)) + 0.15 + oldY,
                        (look.z * balancedPower) + oldZ
                );

                pData.putDouble("MomX", (look.x * balancedPower) + oldX);
                pData.putDouble("MomY", (look.y * (balancedPower * 1.1)) + oldY);
                pData.putDouble("MomZ", (look.z * balancedPower) + oldZ);
                pData.putDouble("DashDirX", look.x);
                pData.putDouble("DashDirY", look.y);
                pData.putDouble("DashDirZ", look.z);
            }
            else if (mode == 1) { // PUSH (İtme)
                if (look.y > 0.7) { //  Çakılış
                    player.setDeltaMovement(0, -2.5, 0);
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> AnimationManager::triggerSquash);
                    pData.remove("MomX"); pData.remove("MomY"); pData.remove("MomZ");
                } else {
                    double pushBack = balancedPower * 0.85;

                    player.setDeltaMovement(
                            (-look.x * pushBack) + oldX,
                            (-look.y * pushBack * 0.8) + 0.15 + oldY,
                            (-look.z * pushBack) + oldZ
                    );

                    pData.putDouble("MomX", (-look.x * pushBack) + oldX);
                    pData.putDouble("MomY", (-look.y * pushBack * 0.8) + oldY);
                    pData.putDouble("MomZ", (-look.z * pushBack) + oldZ);
                    pData.putDouble("DashDirX", -look.x);
                    pData.putDouble("DashDirY", -look.y);
                    pData.putDouble("DashDirZ", -look.z);
                }
                // Her durumda kapıları salla
                net.minecraft.core.Direction dir = player.getDirection();
                net.minecraft.core.BlockPos frontPos = player.blockPosition().relative(dir);

                // Öndeki blokların state lerini al
                BlockState stateFeet = level.getBlockState(frontPos);
                BlockState stateFace = level.getBlockState(frontPos.above());

                boolean doorAtFace = com.zipirhavaci.physics.DoorBlastHandler.isDoor(stateFace);
                boolean doorAtFeet = com.zipirhavaci.physics.DoorBlastHandler.isDoor(stateFeet);

                if (doorAtFace || doorAtFeet) {

                    BlockState targetState = doorAtFeet ? stateFeet : stateFace;
                    BlockPos targetPos = doorAtFeet ? frontPos : frontPos.above();

                    // DEMİR KONTROLÜ kapı demir mi?
                    if (com.zipirhavaci.physics.DoorBlastHandler.isIronLikeDoor(targetState)) {

                        com.zipirhavaci.physics.MovementHandler.shakeDoors(player, look, level);
                    } else {

                        com.zipirhavaci.physics.DoorBlastHandler.blast(level, player, look, 1);
                    }
                } else {
                    // Yakında (1 blokta) kapı yoksa, 4.5 blok menzildekileri salla
                    com.zipirhavaci.physics.MovementHandler.shakeDoors(player, look, level);
                }
                 // --- KONTROL BİTİŞ ---

                com.zipirhavaci.common.WaterSurfaceReactionHandler.spawnPushBubbles(serverPlayer, level);
                com.zipirhavaci.physics.ItemBlastHandler.blast(serverPlayer, level, look, balancedPower);
                com.zipirhavaci.physics.VehiclePushHandler.push(level, player, look, balancedPower);
            }

            // --- 4. VERİ KAYDI VE SENKRONİZASYON ---
            pData.putDouble("AviatorMomentum", balancedPower + oldStored);
            pData.putInt("AviatorDashTicks", 25);
            pData.putInt("AviatorOnGroundTicks", -3);
            player.hurtMarked = true; // Paketin Server -> Client geçiş

            SoundManager.playLaunchSound(level, player.position());
            GauntletVisuals.spawnFireRing(player);

            if (nbt.getInt("ZipirOverloadTimer") > 0) {
                nbt.putInt("ZipirOverloadTimer", 0);
                int slot = serverPlayer.getInventory().selected;
                if (slot != -1) PacketHandler.sendToPlayer(serverPlayer, new SyncItemNBTPacket(slot, nbt.copy()));
            }
        }

        // --- COOLDOWN VE ANIMASYON (KAYIPSIZ) ---
        if (stack.getItem() instanceof ZipirAviatorItem) {
            long instanceId = GeoItem.getOrAssignId(stack, level);
            int nextUses;

            if (uses >= 5) { // AŞIRI ISINMA
                nextUses = 6;
                player.getCooldowns().addCooldown(stack.getItem(), 12);
                PacketHandler.sendToPlayer(serverPlayer, new TriggerAnimationPacket(instanceId, "main_controller", animName));

                ZipirHavaci.SCHEDULER.schedule(() -> {
                    if (!player.isRemoved()) {
                        level.getServer().execute(() -> {
                            CompoundTag tag = stack.getOrCreateTag();
                            tag.putLong("LastCooldownAnimTime", System.currentTimeMillis());
                            tag.putBoolean("WaitingForOverheat", true);
                            tag.putBoolean("HasReloadCredit", false);
                            tag.putInt("Uses", 6);
                            tag.putLong("LastUseTime", System.currentTimeMillis());

                            player.getCooldowns().addCooldown(stack.getItem(), 120);
                            PacketHandler.sendToPlayer(serverPlayer, new TriggerAnimationPacket(instanceId, "main_controller", "cooldown"));
                            PacketHandler.sendToPlayer(serverPlayer, new PlayAviatorFollowSoundPacket(player.getId(), 1.0F));

                            int slot = player.getInventory().findSlotMatchingItem(stack);
                            if (slot >= 0) PacketHandler.sendToPlayer(serverPlayer, new SyncItemNBTPacket(slot, tag.copy()));
                        });
                    }
                }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } else {
                nextUses = uses + 1;
                player.getCooldowns().addCooldown(stack.getItem(), 20);
                PacketHandler.sendToPlayer(serverPlayer, new TriggerAnimationPacket(instanceId, "main_controller", animName));
            }

            final int finalNextUses = nextUses;
            ZipirHavaci.SCHEDULER.schedule(() -> {
                if (!player.isRemoved()) {
                    level.getServer().execute(() -> {
                        CompoundTag tag = stack.getOrCreateTag();
                        tag.putInt("Uses", finalNextUses);
                        int slot = player.getInventory().findSlotMatchingItem(stack);
                        if (slot >= 0) PacketHandler.sendToPlayer(serverPlayer, new SyncItemNBTPacket(slot, tag.copy()));
                    });
                }
            }, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }




    public static void executeSuperSkill(Player player, int level, AviatorMode mode, ItemStack stack, boolean isTPV) {
        if (player.level().isClientSide) return;

        long lastUse = player.getPersistentData().getLong("SuperSkillLastExecutionTick");
        long currentTime = player.level().getGameTime();
        if (currentTime - lastUse < 16) return;
        player.getPersistentData().putLong("SuperSkillLastExecutionTick", currentTime);

        level = Mth.clamp(level, 1, 5);


        level = Mth.clamp(level, 1, 5);

        ServerLevel serverLevel = (ServerLevel) player.level();
        ServerPlayer serverPlayer = (ServerPlayer) player;

        // --- AĞIRLIK HESABI ---
        double armor     = player.getAttributeValue(Attributes.ARMOR);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double totalWeight = Mth.clamp(armor + toughness, 0, 60);
        double weightRatio = totalWeight / 60.0;

        // --- MERMİ KONTROLÜ ---
        CompoundTag nbt = stack.getOrCreateTag();
        int currentUses  = nbt.getInt("Uses");
        int remainingAmmo = 6 - currentUses;

        if (remainingAmmo < 2) return;

        int maxAffordableLevel = remainingAmmo - 1;
        level = Math.min(level, maxAffordableLevel);
        level = Mth.clamp(level, 1, 5);

        int ammoCost  = level + 1;
        int usesAfter = currentUses + ammoCost;


        // --- GÜÇ HESABI ---
        double basePower;
        if (level < 5) {
            basePower = 3.0 + (level * 1.5);
        } else {
            basePower = 16.0;
        }

        // PULL ve Genel Alan taraması için  range
        double range = 4.5 + (level * 1.6);
        // PUSH için  kısa menzil (Max ~4 blok)
        double pushFocusRange = 1.5 + (level * 0.5);

        Vec3 look     = player.getLookAngle();
        AABB area     = player.getBoundingBox().inflate(range);
        List<Entity> entities = serverLevel.getEntities(player, area);

        // --- AÇI VE MİNDOT HESAPLAMASI (ORTAK ALAN) ---
        // Trigonometrik işlem
        double minAngle = 80.0;
        double maxAngle = 110.0;
        double angleDeg = minAngle + ((level - 1) / 4.0) * (maxAngle - minAngle);
        double minDot = Math.cos(Math.toRadians(angleDeg / 2.0));

        int shakeTicks = 4 + (level * 4);
        float shakePower = 0.5f + (level * 0.5f);

        // ============================================================
        if (mode == AviatorMode.PULL) {
            // 1. DINAMIK ALAN (1.5 -> 5.0 blok)
            double effectRadius = 1.5 + (level - 1) * 0.875;
            Vec3 forward = look.normalize();
            Vec3 playerPos = player.position();

            // 2. VAKUM - İTİŞ
            double pullPowerMult = Math.max(0.5, 0.85 + (level * 0.18) - (weightRatio * 0.3));
            double finalPower = basePower * pullPowerMult * (0.38 + (level * 0.15));

            for (Entity entity : entities) {
                if (!(entity instanceof LivingEntity living) || entity == player) continue;

                if (living instanceof ServerPlayer targetPlayer) {
                    // Atanın yarısı güçte (0.25 + level * 0.25)
                    PacketHandler.sendToPlayer(targetPlayer, new TriggerCameraShakePacket(shakeTicks, shakePower * 0.5f));
                }

                Vec3 toEntity = living.position().subtract(playerPos);
                double dot = toEntity.normalize().dot(forward);

                if (dot > 0) { // VAKUM
                    Vec3 pullDir = playerPos.subtract(living.position()).normalize();
                    living.setDeltaMovement(pullDir.x * finalPower * 1.15, 0.20 + (level * 0.1), pullDir.z * finalPower * 1.15);
                } else { // İTİŞ
                    Vec3 pushDir = forward.scale(-0.85);
                    living.setDeltaMovement(pushDir.x * finalPower * 1.15, 0.10 + (level * 0.05), pushDir.z * finalPower * 1.15);
                }
                living.hurtMarked = true;
            }

            // 3. GÖRSEL ŞOK DALGASI
            for (int i = 0; i < 360; i += (30 - level * 4)) {
                double angle = Math.toRadians(i);
                double ox = Math.cos(angle) * effectRadius;
                double oz = Math.sin(angle) * effectRadius;
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                        player.getX() + ox, player.getY() + 1.0, player.getZ() + oz,
                        1, 0, 0, 0, 0.0);
            }

            // 4. OYUNCU FIRLATMA
            double playerPull = AviatorConstants.LAUNCH_POWER * (1.1 + level * 1.55);
            double yMult = (level >= 5) ? 0.92 : 0.88;

            // --- ANTI-FRICTION KICK (Yerden Koparma) ---
            //  yerdeyse, sürtünme momentumu yutmasın  0.25 blok yukarı taşı.
            if (player.onGround()) {
                player.setPos(player.getX(), player.getY() + 0.25, player.getZ());
            }

            // 1. ANLIK İTKİ
            double initialY = (look.y * playerPull * yMult) + 0.15;

            player.setDeltaMovement(
                    look.x * playerPull,
                    Math.max(initialY, -3.9), // İlk fırlatma anında sınırı koru
                    look.z * playerPull
            );

            // 2. MOMENTUM VE MİRAS VERİSİ
            CompoundTag pData = player.getPersistentData();

            // Eskiden kalan momentumların %10'unu alıyoruz
            double oldX = pData.getDouble("MomX") * 0.10;
            double oldY = pData.getDouble("MomY") * 0.10;
            double oldZ = pData.getDouble("MomZ") * 0.10;
            double oldStored = pData.getDouble("AviatorMomentum") * 0.10;

            double newBaseMomentum = AviatorConstants.LAUNCH_POWER * (0.8 + level * 0.8);

            // 3. YÖN KİLİDİ
            pData.putDouble("DashDirX", look.x);
            pData.putDouble("DashDirY", look.y);
            pData.putDouble("DashDirZ", look.z);

            //  ana momentum, bağımsız eksenler mirasla  kaydet
            pData.putDouble("AviatorMomentum", newBaseMomentum + oldStored);

            pData.putDouble("MomX", (look.x * newBaseMomentum) + oldX);
            pData.putDouble("MomY", (look.y * newBaseMomentum * yMult) + oldY);
            pData.putDouble("MomZ", (look.z * newBaseMomentum) + oldZ);

            pData.putInt("AviatorDashTicks", 25 + (level * 10));

            // Fırlatıldığı an yer sayacı SIFIRLANMALI
            pData.putInt("AviatorOnGroundTicks", -3);

            player.hurtMarked = true;

            Vec3 backPos = playerPos.subtract(forward.scale(0.4));
            serverLevel.sendParticles(ParticleTypes.FLAME, backPos.x, backPos.y + 0.5, backPos.z, 4 * level, 0.1, 0.1, 0.1, 0.01);
        } else { // PUSH
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getLookAngle().scale(3.0));

            HitResult hit = serverLevel.clip(new ClipContext(
                    eye,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));

            boolean closeSurface = hit.getType() == HitResult.Type.BLOCK;
            boolean groundBlast = (player.getXRot() >= 80.0f || closeSurface) && level >= 3;

            double pushForwardRange = 5.5 + (level * 0.8);
            double pushSideRange = 2.0 + (level * 0.2);

            AABB pushArea = player.getBoundingBox().inflate(pushForwardRange);
            List<Entity> pushEntities = serverLevel.getEntities(player, pushArea);

            double pushPowerMult  = 1.0 + (weightRatio * 0.7);
            double finalPushPower = basePower * pushPowerMult;

            Vec3 lookNorm = look.normalize();

            double halfAngleRad = Math.toRadians(angleDeg / 2.0);


            // ===================== SHOCKWAVE GÖRSELLERİ =====================
            int shockSteps = 3;
            long[] shockDelays = new long[]{0, 100, 200};
            java.util.Random random = new java.util.Random();

            for (int step = 0; step < shockSteps; step++) {
                final long delay = shockDelays[step];
                final double finalRadiusStep = pushForwardRange * (0.33 * (step + 1));
                final int finalLevel = level;
                final int currentStep = step;

                com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                    serverLevel.getServer().execute(() -> {

                        com.zipirhavaci.common.WaterSurfaceReactionHandler.spawnSuperSkillBubbles(
                                player,
                                serverLevel,
                                finalRadiusStep,
                                lookNorm
                        );

                        spawnSuperCracks(serverLevel, player, finalRadiusStep, finalLevel, 1, minDot);

                        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.9, 0);
                        Vec3 forward = lookNorm;

                        Vec3 tempUp = Math.abs(forward.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                        Vec3 right = forward.cross(tempUp).normalize();
                        Vec3 up = right.cross(forward).normalize();

                        //  MERKEZDE TURUNCU ÇEMBERLER
                        int orangePoints = 12 + (currentStep * 6);
                        double orangeRadius = finalRadiusStep * 0.4;
                        for (int i = 0; i < orangePoints; i++) {
                            double angle = (2 * Math.PI * i) / orangePoints;
                            Vec3 pos = origin.add(forward.scale(finalRadiusStep * 0.2))
                                    .add(right.scale(Math.cos(angle) * orangeRadius))
                                    .add(up.scale(Math.sin(angle) * orangeRadius));

                            serverLevel.sendParticles(
                                    new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.45f, 0.0f), 1.2f),
                                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0
                            );
                        }

                        // Beyaz tozları 2 alt-adımda spawnlayarak ömrünü görsel olarak uzat
                        for (int subStep = 0; subStep < 2; subStep++) {
                            final double subOffset = subStep * 0.1; // Hafif konum kayması
                            int edgePoints = 45; // Sıklığı biraz artırdım
                            double angleLimit = halfAngleRad;

                            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                                serverLevel.getServer().execute(() -> {
                                    for (int i = 0; i <= edgePoints; i++) {
                                        double angle = -angleLimit + (angleLimit * 2) * (i / (double) edgePoints);
                                        Vec3 edge = forward.scale(Math.cos(angle) * (finalRadiusStep + subOffset))
                                                .add(right.scale(Math.sin(angle) * (finalRadiusStep + subOffset)))
                                                .add(origin);

                                        serverLevel.sendParticles(
                                                new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 1f, 1f), 0.85f),
                                                edge.x, edge.y, edge.z, 1, 0, 0, 0, 0
                                        );
                                        // Kalıcılık hissi için White Ash'i her adımda basıyoruz
                                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                                                edge.x, edge.y, edge.z, 1, 0.03, 0.03, 0.03, 0.01);
                                    }
                                });
                            }, subStep * 40L, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }

                        int smokeCount = 3 + (finalLevel * 2);
                        double smokeAngleLimit = halfAngleRad * 0.85; // biraz içerde kalsın
                        for (int i = 0; i < smokeCount; i++) {
                            double rDist = 1.0 + (random.nextDouble() * (finalRadiusStep - 1.0));
                            double rAngle = -smokeAngleLimit + (random.nextDouble() * (smokeAngleLimit * 2));

                            Vec3 smokePos = origin.add(forward.scale(Math.cos(rAngle) * rDist))
                                    .add(right.scale(Math.sin(rAngle) * rDist))
                                    .add(up.scale((random.nextDouble() - 0.5) * 0.5)); // Hafif y sapması

                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                                    smokePos.x, smokePos.y, smokePos.z, 1, 0.05, 0.05, 0.05, 0.02);
                        }

                        if (currentStep == shockSteps - 1) {
                            int haloPoints = 36;
                            double haloRadius = finalRadiusStep * 0.8;
                            double rotationSpeed = System.currentTimeMillis() / 80.0;

                            for (int i = 0; i < haloPoints; i++) {
                                double angle = (2 * Math.PI * i) / haloPoints + rotationSpeed;
                                Vec3 pos = origin.add(forward.scale(finalRadiusStep))
                                        .add(right.scale(Math.cos(angle) * haloRadius))
                                        .add(up.scale(Math.sin(angle) * haloRadius));

                                serverLevel.sendParticles(
                                        new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.92f, 0.96f, 1.0f), 1.6f),
                                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0
                                );
                                if (i % 3 == 0) {
                                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                                            pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.01);
                                }
                            }
                        }
                    });
                }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            // ===================== ENTITY PUSH  =====================
            Vec3 rightVec = new Vec3(-lookNorm.z, 0, lookNorm.x);

            for (Entity entity : pushEntities) {
                //  KONTROL: Atan oyuncunun kendisi mi?
                if (entity == player) continue;

                // --- KALKAN VE GENEL GÜÇ İÇİN SEVİYE ÖLÇEĞİ ---
                // Seviyeler arası farkı açmak için üssel hesaplama (1/5^2 = 0.04, 5/5^2 = 1.0)
                double levelProgression = Math.pow(level / 5.0, 2);

                double nerfedPower = finalPushPower * 0.48;

                // ARAÇ İTME
                if (com.zipirhavaci.physics.VehiclePushHandler.tryPushVehicle(entity, lookNorm, nerfedPower * 1.3)) continue;

                //  KALKAN UÇURMA
                if (entity instanceof com.zipirhavaci.entity.ThrownShieldEntity shield) {
                    Vec3 toShield = shield.position().subtract(player.position());
                    double dist = toShield.length();

                    if (dist > 0.001 && dist <= pushForwardRange) {
                        Vec3 dirNorm = toShield.normalize();
                        double dot = dirNorm.dot(lookNorm);

                        if (groundBlast || dot >= 0.17) {
                            // Mesafe çarpanı
                            double distFactor = Math.max(0.2, 1.0 - (dist / pushForwardRange) * 0.6);

                            double shieldFinalPower = nerfedPower * levelProgression * distFactor;

                            shield.applySuperSkillPush(dirNorm, shieldFinalPower);
                        }
                    }
                    continue;
                }

                //  CANLI VARLIK KONTROLÜ
                if (!(entity instanceof LivingEntity living) || entity == player) continue;

                //  OYUNCU SARSINTISI
                if (entity instanceof ServerPlayer targetPlayer) {
                    double distance = targetPlayer.distanceTo(player);
                    double maxRange = pushForwardRange;
                    double distanceFactor = Mth.clamp(1.0 - (distance / maxRange), 0.1, 1.0);
                    float victimIntensity = (float) (shakePower * 1.5f * distanceFactor);

                    PacketHandler.sendToPlayer(targetPlayer, new TriggerCameraShakePacket(shakeTicks, victimIntensity));
                }

                //  MESAFE VE YÖN HESAPLAMALARI
                Vec3 toEntity = living.position().subtract(player.position());
                double dist = toEntity.length();
                if (dist <= 0.001) continue;

                Vec3 dirNorm = toEntity.normalize();
                double dot = dirNorm.dot(lookNorm);

                if (!groundBlast) {
                    if (dot < 0.17) continue;
                    double maxSide = 9.0;
                    double sideLimit = Math.min(pushSideRange + (dist * 0.25), maxSide);
                    double sideDist = Math.abs(toEntity.dot(rightVec));
                    if (sideDist > sideLimit) continue;
                }

                //  HASAR VERME
                float damage = (float) (1.8f * level * (1.0 + weightRatio * 0.2));
                living.hurt(player.damageSources().playerAttack(player), damage);

                //  FIRLATMA VE KNOCKBACK
                double yBoost = groundBlast ? 1.0 : ((level == 5) ? 0.85 : (0.25 + level * 0.09));
                double blastMult = groundBlast ? 1.5 : 1.0;

                living.setDeltaMovement(
                        dirNorm.x * nerfedPower * blastMult,
                        yBoost,
                        dirNorm.z * nerfedPower * blastMult
                );

                living.hurtMarked = true;
            }

            FallingBlockBlastHandler.blast(serverLevel, player, look, level);
            if (serverLevel instanceof net.minecraft.server.level.ServerLevel) {
                double dynamicBreakRangeSq = Math.pow(pushForwardRange * 0.5, 2);
                double dynamicShakeRangeSq = Math.pow(pushForwardRange, 2);

                Vec3 origin = player.position();
                // lookNorm tekrar gerek yok

                int scanR = (int) Math.ceil(pushForwardRange);
                final int finalSkillLevel = level;

                net.minecraft.core.BlockPos.betweenClosedStream(
                        player.blockPosition().offset(-scanR, -2, -scanR),
                        player.blockPosition().offset(scanR, 2, scanR)
                ).forEach(pos -> {
                    BlockState state = serverLevel.getBlockState(pos);

                    if (com.zipirhavaci.physics.DoorBlastHandler.isDoor(state)) {
                        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                                state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                            return;
                        }

                        Vec3 toDoor = new Vec3(pos.getX() + 0.5 - origin.x, 0, pos.getZ() + 0.5 - origin.z);
                        double distSq = toDoor.lengthSqr();
                        if (distSq > 0.001) {
                            double dot = toDoor.normalize().dot(lookNorm);
                            if (dot < 0.45) return;
                        }

                        boolean isIron = com.zipirhavaci.physics.DoorBlastHandler.isIronLikeDoor(state);

                        if (distSq <= dynamicBreakRangeSq && !isIron) {
                            com.zipirhavaci.physics.DoorBlastHandler.blast(serverLevel, player, look, finalSkillLevel);
                        }
                        else if (distSq <= dynamicShakeRangeSq) {
                            BlockState topState = serverLevel.getBlockState(pos.above());
                            com.zipirhavaci.physics.MovementHandler.triggerDoorShake(serverLevel, pos.immutable(), state, topState, isIron);
                        }
                    }
                });
            }

            double recoil = groundBlast ? 0.05 : (0.10 + (level * 0.025));
            player.push(-look.x * recoil, groundBlast ? 0.15 : 0.03, -look.z * recoil);

            if (groundBlast) {

                spawnGroundBlast(serverLevel, player, level, hit);
            }
        }

        // --- MERMİ TÜKETİMİ + COOLDOWN  ---
        nbt.putInt("Uses", usesAfter);
        long instanceId = GeoItem.getOrAssignId(stack, serverLevel);
        String animName = isTPV ? "tpv_fire" : "fire";
        PacketHandler.sendToPlayer(serverPlayer, new TriggerAnimationPacket(instanceId, "main_controller", animName));

        if (usesAfter >= 5) {
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                if (!player.isRemoved()) {
                    serverLevel.getServer().execute(() -> {
                        CompoundTag tag = stack.getOrCreateTag();
                        tag.putLong("LastCooldownAnimTime", System.currentTimeMillis());
                        tag.putBoolean("WaitingForOverheat", true);
                        tag.putBoolean("HasReloadCredit", false);
                        tag.putInt("Uses", 6);
                        tag.putLong("LastUseTime", System.currentTimeMillis());

                        player.getCooldowns().addCooldown(stack.getItem(), 120);
                        PacketHandler.sendToPlayer(serverPlayer, new TriggerAnimationPacket(instanceId, "main_controller", "cooldown"));
                        PacketHandler.sendToPlayer(serverPlayer, new PlayAviatorFollowSoundPacket(player.getId(), 1.0F));

                        int slot = player.getInventory().findSlotMatchingItem(stack);
                        if (slot >= 0) {
                            PacketHandler.sendToPlayer(serverPlayer,
                                    new SyncItemNBTPacket(slot, tag.copy()));
                        }
                    });
                }
            }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            player.getCooldowns().addCooldown(stack.getItem(), 20);
            int slot = player.getInventory().findSlotMatchingItem(stack);
            if (slot >= 0) {
                PacketHandler.sendToPlayer(serverPlayer, new SyncItemNBTPacket(slot, nbt.copy()));
            }
        }

        // --- EFEKTLER ---
        GauntletVisuals.spawnFireRing(player);
        spawnFunnelRings(serverLevel, player, look, level, mode);

        float bassVolume = 1.0f + (level * 0.15f); // Max 1.75
        float bassPitch  = 1.1f - (level * 0.12f); // Lvl 1: 0.98 (Normal), Lvl 5: 0.50

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS,
                bassVolume,
                bassPitch);

        //  KESKİN YIRTILMA
        float sharpVolume = 0.3f + (level * 0.12f);
        float sharpPitch  = 1.2f + (level * 0.15f); // Lvl 1: 1.35 (Hafif), Lvl 5: 1.95 (tiz)

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS,
                sharpVolume,
                sharpPitch);


        CompoundTag pData = player.getPersistentData();


        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            PacketHandler.sendToPlayer(sp, new TriggerCameraShakePacket(shakeTicks, shakePower));
        }

        player.hurtMarked = true;

    }

    private static void spawnGroundBlast(ServerLevel serverLevel, Player player, int level, HitResult hit) {
        double blastRadius = 3.5 + (level * 0.8);

        // 1. PATLAMA VE SARSINTI
        serverLevel.explode(null,
                player.getX(), player.getY(), player.getZ(),
                (float)(1.5 + level * 0.5),
                false,
                net.minecraft.world.level.Level.ExplosionInteraction.NONE);

        // 2. 3D KRATER KIRIKLARI
        spawnSuperCracks(serverLevel, player, blastRadius, level, 2, 0.0);

        // 3. DALGA DALGA YAYILAN PARTİKÜL EFEKTLERİ
        Vec3 origin = player.position();
        for (int ring = 0; ring < 3; ring++) {
            final double currentRingRadius = blastRadius * (0.4 + (ring * 0.3));
            final int tickDelay = ring * 3;
            com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
                serverLevel.getServer().execute(() -> {
                    int particleCount = (int) (15 + (currentRingRadius * 8));
                    for (int i = 0; i < particleCount; i++) {
                        double angle = (i * 2 * Math.PI) / particleCount;
                        double offsetX = Math.cos(angle) * currentRingRadius;
                        double offsetZ = Math.sin(angle) * currentRingRadius;
                        Vec3 spawnPos = origin.add(offsetX, 0.1, offsetZ);
                        if (serverLevel.isLoaded(BlockPos.containing(spawnPos))) {
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                                    spawnPos.x, spawnPos.y, spawnPos.z, 1, 0, 0.05, 0, 0.02);
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                    spawnPos.x, spawnPos.y + 0.2, spawnPos.z, 1, 0.1, 0.1, 0.1, 0.015);
                            if (level >= 5) {
                                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                                        spawnPos.x, spawnPos.y, spawnPos.z, 1, 0.05, 0.05, 0.05, 0.01);
                            }
                        }
                    }
                    if (currentRingRadius > 2.0) {
                        serverLevel.playSound(null, origin.x, origin.y, origin.z,
                                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                                net.minecraft.sounds.SoundSource.PLAYERS,
                                0.5f, 0.6f + (float)(currentRingRadius * 0.1));
                    }
                });
            }, tickDelay * 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        final double totalR = blastRadius;
        final double breakR = Math.max(0.5, totalR - 2.0); // En dış 2 blok hariç

        final double breakRSq = breakR * breakR;
        final double shakeRSq = totalR * totalR;
        final int finalLvl = level;
        int scanRadius = (int) Math.ceil(totalR);

        // 1. etraftaki tüm geçerli kapı konumlarını bir listeye topla (Çakışmayı önler)
        java.util.List<BlockPos> doorPositions = new java.util.ArrayList<>();
        BlockPos centerPos = player.blockPosition();

        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int z = -scanRadius; z <= scanRadius; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos checkPos = centerPos.offset(x, y, z);
                    BlockState st = serverLevel.getBlockState(checkPos);

                    if (com.zipirhavaci.physics.DoorBlastHandler.isDoor(st)) {
                        // Sadece alt yarıyı listeye ekle
                        if (st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                                st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                            doorPositions.add(checkPos.immutable());
                        }
                    }
                }
            }
        }

        // 2. Listeye aldığın her kapı için mesafeyi kontrol et ve işlemi uygula
        for (BlockPos pos : doorPositions) {
            double distSq = pos.distSqr(centerPos);
            BlockState state = serverLevel.getBlockState(pos);
            boolean isIron = com.zipirhavaci.physics.DoorBlastHandler.isIronLikeDoor(state);

            if (distSq <= breakRSq && !isIron) {

                Vec3 targetDir = new Vec3(pos.getX() - centerPos.getX(), 0.1, pos.getZ() - centerPos.getZ()).normalize();
                com.zipirhavaci.physics.DoorBlastHandler.blast(serverLevel, player, targetDir, finalLvl);
            }
            else if (distSq <= shakeRSq) {
                // DIŞ HALKADA VEYA DEMİR: Salla
                BlockState topState = serverLevel.getBlockState(pos.above());
                com.zipirhavaci.physics.MovementHandler.triggerDoorShake(serverLevel, pos, state, topState, isIron);
            }
        }

        double levelProgression = Math.pow(level / 5.0, 2);

        AABB shieldBox = player.getBoundingBox().inflate(blastRadius + 2.0);

        serverLevel.getEntitiesOfClass(com.zipirhavaci.entity.ThrownShieldEntity.class, shieldBox).forEach(shield -> {


            Vec3 toShield = shield.position().subtract(player.position());
            double dist = toShield.length();
            if (dist < 0.1) return;

            Vec3 dirNorm = toShield.normalize();
            double distFactor = Math.max(0.2, 1.0 - (dist / blastRadius));

            double shieldFinalPower = (3.5 + (level * 0.5)) * 0.45 * levelProgression * distFactor;

            // Kalkanı fırlat
            shield.applySuperSkillPush(dirNorm.add(0, 0.3, 0).normalize(), shieldFinalPower);
        });


        // ===================== DİNAMİK YÜZEY GERİ TEPME (BLAST JUMP) =====================
        double baseForce = 0.55 + (level * 0.12);

        double wallMultiplier = 1.8;
        Vec3 recoilVec = Vec3.ZERO;

        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            Direction face = blockHit.getDirection();

            if (face == Direction.UP) { // --- YERE VURMA (AYNI KALDI) ---
                double yJump = 0.35 + (level * 0.1);
                recoilVec = Vec3.atLowerCornerOf(face.getNormal()).scale(baseForce).add(0, yJump, 0);
            }
            else if (face == Direction.DOWN) { // --- TAVANA VURMA ---
                recoilVec = Vec3.atLowerCornerOf(face.getNormal()).scale(baseForce);
            }
            else { // --- DUVARA VURMA  ---

                recoilVec = Vec3.atLowerCornerOf(face.getNormal()).scale(baseForce * wallMultiplier);

                // Sürtünmeyi önle ve ivmeyi koru  hafif bir Y desteği
                recoilVec = recoilVec.add(0, 0.28, 0);
            }
        } else {
            // Blok yoksa standart yukarı itiş
            recoilVec = new Vec3(0, 0.7 + (level * 0.1), 0);
        }

        player.push(recoilVec.x, recoilVec.y, recoilVec.z);
        player.hurtMarked = true;
    }


    private static void spawnSuperCracks(ServerLevel serverLevel, Player player,
                                         double radius, int level, int shapeType,
                                         double minDot) {
        BlockPos center = player.blockPosition();
        net.minecraft.util.RandomSource random = player.getRandom();

        Vec3 look = player.getLookAngle().normalize();
        double lookX = look.x;
        double lookZ = look.z;

        double hLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (hLen > 0) { lookX /= hLen; lookZ /= hLen; }

        int r = (shapeType == 0) ? 1 : (int) Math.ceil(radius);
        double radiusSq = radius * radius;
        double crackChance = 0.45 + (level * 0.10);
        double maxHeight = 4.5 + (level * 0.3);
        double minHeight = 2.0;

        BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
        int playerId = player.getId();

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (shapeType == 0 && (Math.abs(x) > 1 || Math.abs(z) > 1)) continue;

                boolean lastBlockWasSolid = false;

                for (int y = -(int)maxHeight; y <= (int)maxHeight; y++) {
                    mPos.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    double distSq = x * x + y * y + z * z;
                    if (distSq < 0.2 || distSq > radiusSq) continue;

                    if (shapeType == 1) {
                        double dx = x;
                        double dz = z;
                        double lenSq = dx * dx + dz * dz;

                        if (lenSq > 0) {
                            double invLen = 1.0 / Math.sqrt(lenSq);
                            double dot = (dx * invLen) * lookX + (dz * invLen) * lookZ;

                            if (dot < minDot) continue;
                        }

                        double t = Math.sqrt(distSq) / radius;
                        double currentHeight = minHeight + (t * Math.sqrt(t)) * (maxHeight - minHeight);
                        if (Math.abs(y) > currentHeight) continue;
                    } else if (shapeType == 2 && Math.abs(y) > 3.5) continue;

                    if (!serverLevel.isLoaded(mPos)) continue;
                    BlockState state = serverLevel.getBlockState(mPos);
                    boolean solid = !state.isAir() && !state.getCollisionShape(serverLevel, mPos).isEmpty();

                    if (!solid && !lastBlockWasSolid) continue; // boş arka blok varsa kırık geçmez
                    if (random.nextFloat() > crackChance) {
                        lastBlockWasSolid = solid;
                        continue;
                    }

                    lastBlockWasSolid = solid;
                    if (!solid) continue;

                    double dist = Math.sqrt(distSq);
                    int crackLevel = Mth.clamp((int)(9 * (1.0 - (dist / radius))), 4, 9);
                    int destroyId = mPos.hashCode() ^ playerId;
                    final BlockPos fPos = mPos.immutable();

                    serverLevel.destroyBlockProgress(destroyId, fPos, crackLevel);
                    startFadeCycle(serverLevel, fPos, destroyId, crackLevel);
                }
            }
        }
    }

    // Scheduler yükünü metot dışına çıkararak temizle
    private static void startFadeCycle(ServerLevel level, BlockPos pos, int dId, int startCrack) {
        // 1. GÜNCELLEME (4. Saniye): İlk belirgin iyileşme
        com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (level.isLoaded(pos)) {
                    // Kırığı %60'a düşür
                    int next = Math.max(1, (int)(startCrack * 0.6));
                    level.destroyBlockProgress(dId, pos, next);
                }
            });
        }, 4000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 2. GÜNCELLEME (7. Saniye): Kılcal çatlak seviyesi
        com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (level.isLoaded(pos)) {
                    // Kırığı %30'a düşür
                    int next = Math.max(0, (int)(startCrack * 0.3));
                    level.destroyBlockProgress(dId, pos, next);
                }
            });
        }, 7000L, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 3. GÜNCELLEME (10. Saniye): Tamamen temizle
        com.zipirhavaci.core.ZipirHavaci.SCHEDULER.schedule(() -> {
            level.getServer().execute(() -> {
                if (level.isLoaded(pos)) {
                    level.destroyBlockProgress(dId, pos, -1);
                }
            });
        }, 10000L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }



    private static void spawnFunnelRings(ServerLevel level, Player player, Vec3 look, int chargeLevel, AviatorMode mode) {
        Vec3 origin  = player.position().add(0, player.getBbHeight() * 0.9, 0);
        Vec3 forward = look.normalize();

        Vec3 arbitraryUp = Math.abs(forward.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = forward.cross(arbitraryUp).normalize();
        Vec3 up    = right.cross(forward).normalize();

        int ringCount  = chargeLevel + 2 + (chargeLevel == 5 ? 1 : 0);
        int pointsBase = 12 + (chargeLevel * 2);
        float maxRadius = 0.55f + (chargeLevel * 0.12f);
        float minRadius = 0.08f;
        float spacing   = 0.70f;
        float dustSize  = 0.55f + (chargeLevel * 0.07f);

        double rx = right.x, ry = right.y, rz = right.z;
        double ux = up.x, uy = up.y, uz = up.z;

        for (int ring = 0; ring < ringCount; ring++) {
            float dist = spacing * (ring + 1);
            float t = (ringCount > 1) ? (float) ring / (ringCount - 1) : 0f;

            float radius = (mode == AviatorMode.PULL)
                    ? maxRadius + t * (minRadius - maxRadius)
                    : minRadius + t * (maxRadius - minRadius);

            Vec3 center = origin.add(forward.scale(dist));
            double cx = center.x, cy = center.y, cz = center.z;

            float r = (mode == AviatorMode.PULL) ? 0.0f : 1.0f;
            float g = (mode == AviatorMode.PULL) ? 0.90f + t * 0.10f : 0.45f - t * 0.35f;
            float b = (mode == AviatorMode.PULL) ? 1.0f : 0.0f;

            net.minecraft.core.particles.DustParticleOptions dust =
                    new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(r, g, b), dustSize);

            int points = pointsBase + ring * 3;
            double angleStep = (2 * Math.PI) / points;

            // Pull için son halka
            boolean isLastPullRing = (mode == AviatorMode.PULL && ring == ringCount - 1);
            boolean isLastPushRing = (mode == AviatorMode.PUSH && ring == ringCount - 1);

            for (int i = 0; i < points; i++) {
                double angle = i * angleStep;
                double cosVal = Math.cos(angle);
                double sinVal = Math.sin(angle);

                double cosR = cosVal * radius;
                double sinR = sinVal * radius;

                double px = cx + rx * cosR + ux * sinR;
                double py = cy + ry * cosR + uy * sinR;
                double pz = cz + rz * cosR + uz * sinR;

                level.sendParticles(dust, px, py, pz, 1, 0, 0, 0, 0);

                if (mode == AviatorMode.PULL && i % 3 == 0) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.INSTANT_EFFECT,
                            px, py, pz, 1, 0, 0, 0, 0);
                }

                if (isLastPushRing) {
                    double cosR105 = cosVal * (radius * 1.05);
                    double sinR105 = sinVal * (radius * 1.05);
                    double wpx = cx + rx * cosR105 + ux * sinR105;
                    double wpy = cy + ry * cosR105 + uy * sinR105;
                    double wpz = cz + rz * cosR105 + uz * sinR105;

                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                            wpx, wpy, wpz, 1, 0, 0, 0, 0);

                    if (i % 3 == 0) {
                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                                px, py, pz, 1, 0, 0.01, 0, 0.02);
                    }
                }

                if (isLastPullRing && i % 4 == 0) {
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, px, py, pz, 1, 0, 0, 0, 0.01);
                }
            }
        }
    }

    // --- DİNAMİK DÜŞME HASARI KORUMASI ---
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // PULL veya PUSH fark etmeksizin, alet kullanıldıysa çalışır
        int dashTicks = player.getPersistentData().getInt("AviatorDashTicks");
        if (dashTicks <= 0) return;

        double armor = player.getAttributeValue(Attributes.ARMOR);
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double weight = Mth.clamp(armor + toughness, 0, 60);

        // 0 Ağırlık -> %95 Koruma | 60 Ağırlık -> %55 Koruma
        double weightRatio = weight / 60.0;
        double protectionPercentage = Mth.lerp(weightRatio, 95.0, 55.0);

        // Limit Hız Cezası (PULL veya PUSH fark etmez, hızlı düşüyorsa ceza alır)
        if (player.getDeltaMovement().y < LIMIT_Y) {
            protectionPercentage -= 15.0;
        }

        protectionPercentage = Mth.clamp(protectionPercentage, 0, 100);
        float damageMultiplier = (float) (1.0 - (protectionPercentage / 100.0));

        event.setDamageMultiplier(damageMultiplier);
    }

    // ---  EVENTLER (Tick ve Jump) ---

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Player player && player.level().isClientSide) {
            int shot = player.getPersistentData().getInt("ShotSmokeTicks");
            if (shot > 0) player.getPersistentData().putInt("ShotSmokeTicks", shot - 1);

            int cool = player.getPersistentData().getInt("CooldownSteamTicks");
            if (cool > 0) player.getPersistentData().putInt("CooldownSteamTicks", cool - 1);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // 1. BÖLÜM: CLIENT TARAFI (Duman ve Buhar Efekt Sayaçları)
        if (player.level().isClientSide) {
            int shot = player.getPersistentData().getInt("ShotSmokeTicks");
            if (shot > 0) player.getPersistentData().putInt("ShotSmokeTicks", shot - 1);

            int cool = player.getPersistentData().getInt("CooldownSteamTicks");
            if (cool > 0) player.getPersistentData().putInt("CooldownSteamTicks", cool - 1);
            return; // Client tarafında işlem biter
        }

        // --- BURADAN SONRASI SERVER ---

        CompoundTag pDataReset = player.getPersistentData();
        if (pDataReset.getBoolean("NoPhysicsResetNextTick")) {
            player.noPhysics = false;
            pDataReset.remove("NoPhysicsResetNextTick");
        }

        // 1. BÖLÜM: DASH HASAR MEKANİĞİ
        int dashTicks = player.getPersistentData().getInt("AviatorDashTicks");
        if (dashTicks > 0) {
            player.getPersistentData().putInt("AviatorDashTicks", dashTicks - 1);
            Vec3 playerVel = player.getDeltaMovement();

            //  GÜVENLİK 1: Hızı sadece sunucunun meşru kabul ettiği sınırlarda oku
            double currentSpeed = Math.min(playerVel.length(), 1.5);

            if (currentSpeed > 0.7) {
                AABB dashArea = player.getBoundingBox().inflate(1.0, 0.5, 1.0);
                List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, dashArea, e -> e != player);

                for (LivingEntity target : targets) {
                    if (target.invulnerableTime <= 10) {
                        target.hurt(player.damageSources().playerAttack(player), 3.0f);
                        Vec3 push = target.position().subtract(player.position()).normalize();
                        target.setDeltaMovement(push.x * 1.2, 0.4, push.z * 1.2);
                        target.hurtMarked = true;
                    }
                }
            }
        }


        // --- ÜÇ EKSENLİ VE MİRAS  ---
        CompoundTag pData = player.getPersistentData();

        // Bağımsız eksen verileri
        double momX = pData.getDouble("MomX");
        double momY = pData.getDouble("MomY");
        double momZ = pData.getDouble("MomZ");

        double storedMomentum = pData.getDouble("AviatorMomentum");

         // Herhangi bir eksende veya ana momentumda güç varsa işlemleri başlat
        if (Math.abs(storedMomentum) > 0.05 || Math.abs(momX) > 0.01 || Math.abs(momZ) > 0.01) {

            // 1. Kilitli Yönü ve O Anki Bakış Yönünü Al
            Vec3 lockDir = new Vec3(
                    pData.getDouble("DashDirX"),
                    pData.getDouble("DashDirY"),
                    pData.getDouble("DashDirZ")
            );
            Vec3 currentLook = player.getLookAngle();

            // 2. YÖN HARMANLA (Blending) - %80 Kilitli, %20 Özgür Bakış
            Vec3 blendedDir = lockDir.scale(0.80).add(currentLook.scale(0.20)).normalize();
            Vec3 currentVel = player.getDeltaMovement();

            // Kuvvet Çarpanı
            double force = (storedMomentum + Math.max(Math.abs(momX), Math.abs(momZ))) * 0.14;

            // 3. Kuvveti Uygula (Vektörel Ekleme ve Limit)
            double newY = currentVel.y + (blendedDir.y * force * 0.82);

            player.setDeltaMovement(
                    currentVel.x + (blendedDir.x * force),
                    Math.max(newY, -3.9), // Yere çakılırken sunucu sınırını aşmaması için limit
                    currentVel.z + (blendedDir.z * force)
            );

            int dashTicksActive = pData.getInt("AviatorDashTicks");
            double frictionReduction;

            if (player.onGround()) {
                // --- YERE TEMAS KONTROLÜ (ÇİVİ GİBİ ÇAKILMA) ---
                int onGroundTicks = pData.getInt("AviatorOnGroundTicks");

                if (onGroundTicks == 0) {
                    // Havadan yere ilk değdiğin an: Tüm eksenleri ve ana momentumu SIFIRLA
                    storedMomentum = 0;
                    momX = 0; momY = 0; momZ = 0;
                    pData.remove("AviatorMomentum");
                    pData.remove("MomX"); pData.remove("MomY"); pData.remove("MomZ");
                    pData.remove("AviatorShakeTicks");

                    if (player.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(), player.getZ(), 8, 0.2, 0.05, 0.2, 0.02);
                    }
                }

                pData.putInt("AviatorOnGroundTicks", onGroundTicks + 1);
                frictionReduction = 0.82; // Yerdeyken hızlı duruş

            } else {
                // --- HAVADA SÜZÜLME (X/Z ODAKLI) ---
                pData.putInt("AviatorOnGroundTicks", -3);

                frictionReduction = (dashTicksActive > 0) ? 0.985 : 0.94;
            }

            // 4. MOMENTUM SÖNÜMLENMESİ VE KAYIT
            // Eksenleri ve ana momentumu sönümleyip kaydet
            storedMomentum *= frictionReduction;
            momX *= frictionReduction;
            momY *= (frictionReduction * 0.92);
            momZ *= frictionReduction;

            pData.putDouble("AviatorMomentum", storedMomentum);
            pData.putDouble("MomX", momX);
            pData.putDouble("MomY", momY);
            pData.putDouble("MomZ", momZ);
            player.hurtMarked = true;


            // 5. LANDING SHOCK (Yere Çarpma Etkisi)
            if (player.onGround() && Math.abs(storedMomentum + momX + momZ) > 0.6 && dashTicksActive <= 0) {
                AABB shockArea = player.getBoundingBox().inflate(3.0);
                player.level().getEntitiesOfClass(LivingEntity.class, shockArea, e -> e != player).forEach(target -> {
                    Vec3 push = target.position().subtract(player.position()).normalize().scale(0.8);
                    target.setDeltaMovement(push.x, 0.3, push.z);
                    target.hurtMarked = true;
                });

                pData.remove("AviatorMomentum");
                pData.remove("MomX"); pData.remove("MomY"); pData.remove("MomZ");

                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY(), player.getZ(), 10, 0.5, 0.1, 0.5, 0.05);
                }
            }
        }


        // 3. BÖLÜM: ALTIN KURAL RELOAD VE SENKRONİZASYON
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ZipirAviatorItem)) {
            RECHARGE_TIMER.remove(player.getUUID()); // Silah elde değilse sayacı temizle
            return;
        }

        CompoundTag nbt = stack.getOrCreateTag();
        int uses = nbt.getInt("Uses");
        UUID uuid = player.getUUID();

        // A. SESSİZ COOLDOWN BİTİŞİ (Zıplamayı engelleyen kilit)
        if (nbt.getBoolean("WaitingForOverheat")) {
            if (!player.getCooldowns().isOnCooldown(stack.getItem())) {
                nbt.putBoolean("WaitingForOverheat", false);
                nbt.putBoolean("HasReloadCredit", true);

                nbt.putInt("Uses", 0);
                nbt.remove("LastUseTime");

                syncGauntletNBT(player, stack);
            }
        }

        // B. RELOAD BAŞLATMA (Shift + Sol Tık + Kredi)
        if (player.isCrouching() && player.swingTime > 0 && nbt.getBoolean("HasReloadCredit") && !nbt.getBoolean("IsRecharging")) {
            if (uses > 0 && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                nbt.putBoolean("IsRecharging", true);
                RECHARGE_TIMER.put(uuid, 0);
                syncGauntletNBT(player, stack); // Oyuncu aksiyona girdiği için burada paket atmak güvenli.
            }
        }

        // C. RELOAD SÜRECİ VE İPTAL ŞARTI
        if (nbt.getBoolean("IsRecharging")) {
            if (!player.isCrouching()) {
                nbt.putBoolean("IsRecharging", false);
                RECHARGE_TIMER.put(uuid, 0);
                // ⭐ İPTAL: LED'leri tekrar boş göster (renderer LastUseTime'a bakıyor)
                nbt.putInt("Uses", 6);
                nbt.putLong("LastUseTime", System.currentTimeMillis());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c⚠️ İPTAL EDİLDİ"), true);
                syncGauntletNBT(player, stack);
                return;
            }

            int timer = RECHARGE_TIMER.getOrDefault(uuid, 0) + 1;
            RECHARGE_TIMER.put(uuid, timer);

            // İlerleme Çubuğu (70 tick = 3.5 saniye)
            if (timer % 5 == 0) {
                int progress = (int)((timer / 70.0) * 15);
                String bar = "§6DOLUYOR: " + "█".repeat(progress) + "░".repeat(15 - progress);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(bar), true);
            }

            // D. BAŞARI
            if (timer >= 70) {

                nbt.putInt("Uses", Math.max(0, uses - 3));

                nbt.remove("LastUseTime"); //  Renderer a "reload bitti, normal göster" sinyali
                nbt.putBoolean("HasReloadCredit", false);
                nbt.putBoolean("IsRecharging", false);
                RECHARGE_TIMER.put(uuid, 0);

                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a⚡ 3 MERMİ YÜKLENDİ"), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.2f);

                syncGauntletNBT(player, stack);
            }
        }
    }

    private static void syncGauntletNBT(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = serverPlayer.getInventory().selected;
            if (!serverPlayer.getInventory().getItem(slot).isEmpty() &&
                    serverPlayer.getInventory().getItem(slot).getItem() == stack.getItem()) {
                PacketHandler.sendToPlayer(serverPlayer, new com.zipirhavaci.network.SyncItemNBTPacket(slot, stack.getOrCreateTag().copy()));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (AnimationManager.isSquashed) {
                    Vec3 d = player.getDeltaMovement();
                    player.setDeltaMovement(d.x, d.y * 3.2, d.z);
                    AnimationManager.resetSquash();
                }
            });
        }
    }


    public static void shakeDoors(Player player, Vec3 look, ServerLevel level) {
        // Bakış yönü normalini al (Y bileşeni küçük kap)
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();

        // Oyuncu merkezi
        Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5, 0);

        // Tarama alanı: öne 4.5, yanlara 1.5, Y 1.5
        double forwardReach = 4.5;
        double sideReach    = 1.5;
        double vertReach    = 1.5;

        // Büyük AABB ile önce tüm bloklara bak, sonra yön filtresi uygula
        net.minecraft.world.phys.AABB scanBox = player.getBoundingBox()
                .inflate(forwardReach, vertReach, forwardReach);

        // Yan vektör
        Vec3 side = new Vec3(-flatLook.z, 0, flatLook.x);

        // Bloklara bak
        int minX = (int) Math.floor(scanBox.minX);
        int maxX = (int) Math.ceil(scanBox.maxX);
        int minY = (int) Math.floor(scanBox.minY);
        int maxY = (int) Math.ceil(scanBox.maxY);
        int minZ = (int) Math.floor(scanBox.minZ);
        int maxZ = (int) Math.ceil(scanBox.maxZ);

        java.util.Set<BlockPos> processedDoors = new java.util.HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);

                    // Kapı mı?
                    if (!isDoor(state)) continue;

                    // Sadece alt yarıyı işle
                    if (state.hasProperty(DoorBlock.HALF)
                            && state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;

                    // Zaten işlendi mi?
                    if (processedDoors.contains(pos)) continue;

                    // Yön filtresi
                    Vec3 toBlock = new Vec3(x + 0.5 - origin.x, 0, z + 0.5 - origin.z);
                    double forwardDot = toBlock.dot(flatLook);
                    double sideDot    = Math.abs(toBlock.dot(side));

                    // Öne mi bakıyor ve yanlara dar mı?
                    if (forwardDot < 0.3 || forwardDot > forwardReach) continue;
                    if (sideDot > sideReach) continue;

                    // Tek blok kapılar  — üst yarı yok
                    boolean isSingleBlock = !(state.hasProperty(DoorBlock.HALF));

                    net.minecraft.world.level.block.state.BlockState topState =
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

                    if (!isSingleBlock) {
                        BlockPos topPos = pos.above();
                        topState = level.getBlockState(topPos);
                        if (topState.isAir()) continue;
                    }

                    // Demir kapı kontrolü
                    boolean isIron = isIronLikeDoor(state);

                    // Kapıyı titret
                    triggerDoorShake(level, pos, state, topState, isIron);
                    processedDoors.add(pos);
                }
            }
        }
    }

    public static boolean isDoor(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock;
    }

    public static boolean isIronLikeDoor(net.minecraft.world.level.block.state.BlockState state) {
        net.minecraft.world.level.block.Block block = state.getBlock();

        // Vanilla iron door
        if (block == net.minecraft.world.level.block.Blocks.IRON_DOOR
                || block == net.minecraft.world.level.block.Blocks.IRON_TRAPDOOR) {
            return true;
        }

        // Vanilla ahşap ve doğal kapılar — yumuşak
        if (block instanceof net.minecraft.world.level.block.DoorBlock
                || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                || block instanceof net.minecraft.world.level.block.FenceGateBlock) {
            // ResourceLocation ile vanilla olup olmadığını kontrol et
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
            if (rl != null && rl.getNamespace().equals("minecraft")) {
                return false; // Vanilla ahşap/doğal kapı = yumuşak
            }
            // Mod kapısı = demir gibi
            return true;
        }
        return false;
    }

    /** Kapı titreşimini başlatır — gerçek kapıyı DoorShakeBlock ile değiştirir. */
    public static void triggerDoorShake(ServerLevel level, BlockPos bottomPos,
                                         net.minecraft.world.level.block.state.BlockState bottomState,
                                         net.minecraft.world.level.block.state.BlockState topState,
                                         boolean ironDoor) {

        // Zaten titreşiyor mu?
        if (level.getBlockState(bottomPos).getBlock()
                instanceof com.zipirhavaci.block.DoorShakeBlock) return;

        // Gerçek kapı state lerini sakla
        level.setBlock(bottomPos, com.zipirhavaci.core.ItemRegistry.DOOR_SHAKE_BLOCK.get()
                .defaultBlockState(), 3);

        final net.minecraft.world.level.block.state.BlockState fBottom = bottomState;
        final net.minecraft.world.level.block.state.BlockState fTop = topState;
        final boolean fIron = ironDoor;
        level.getServer().execute(() -> {
            if (level.getBlockEntity(bottomPos) instanceof DoorShakeBlockEntity shake) {
                shake.setup(fBottom, fTop, fIron);
            }
        });
    }

    public static void cleanup(java.util.UUID uuid, Player player) {
        if (uuid != null) {
            RECHARGE_TIMER.remove(uuid);
        }

        if (player != null) {
            player.getPersistentData().remove("AviatorMomentum");
            player.getPersistentData().remove("AviatorDashTicks");
        }
    }

    public static boolean consumeGunpowder(ServerPlayer player) {

        if (player.getAbilities().instabuild) return true;

        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        String lastSlotTag = "LastGunpowderSlot";

        // 1. ÖNCE: Son bilinen slotu kontrol et
        int lastSlot = player.getPersistentData().getInt(lastSlotTag);
        // Slot geçerli mi ve içinde hala barut var mı?
        if (lastSlot >= 0 && lastSlot < inventory.getContainerSize()) {
            ItemStack lastStack = inventory.getItem(lastSlot);
            if (lastStack.is(net.minecraft.world.item.Items.GUNPOWDER)) {
                lastStack.shrink(1);
                return true;
            }
        }

        // 2.YOKSA
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(net.minecraft.world.item.Items.GUNPOWDER)) {
                // Yeni slotu hafızaya al
                player.getPersistentData().putInt(lastSlotTag, i);
                stack.shrink(1);
                return true;
            }
        }

        // 3. HİÇ YOKSA
        player.getPersistentData().remove(lastSlotTag);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cFuel exhausted! §7Gunpowder required."), true);
        return false;
    }
}