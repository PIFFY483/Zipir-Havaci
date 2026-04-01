package com.zipirhavaci.core.physics;

import com.zipirhavaci.core.ItemRegistry;
import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.core.capability.StaticProgressionData;
import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.SyncStaticProgressionPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;


@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DarkAuraHandler {

    // ─── KARANLIK YİYECEKLER ─────────────────────────────────────────────────
    private static final Set<Item> CURSED_FOODS = Set.of(
            Items.POISONOUS_POTATO,
            Items.SPIDER_EYE,
            Items.ROTTEN_FLESH,
            Items.PUFFERFISH,
            Items.CHORUS_FRUIT,
            Items.SUSPICIOUS_STEW,
            Items.MUSHROOM_STEW,
            Items.BEETROOT_SOUP,
            Items.CHICKEN
    );

    private static final long DEATH_DENY_COOLDOWN_MS = 300000L;

    // ─── TICK: Pasif Efektler (SERVER SIDE) ──────────────────────────────────
    public static void onCursedPlayerTick(ServerPlayer player, StaticProgressionData data) {
        if (!data.isCursed()) return;

        Level level = player.level();
        boolean isNether = level.dimension() == Level.NETHER;
        boolean isUnderDirectSun = !isNether && level.isDay() && level.canSeeSky(player.blockPosition());

        // 1. GÜNEŞ CEZASI
        if (isUnderDirectSun) {
            if (player.tickCount % 10 == 0) {
                player.causeFoodExhaustion(0.15f);
            }
            player.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, 40, 0, false, false, false));

            if (data.isDarkAuraActive()) {
                int currentTicks = data.getDarkAuraTicksLeft();
                if (currentTicks > 1) {
                    data.setDarkAuraTicksLeft(currentTicks - 2);
                }
            }
        } else {
            // 2. PASİF REGENERATION I (gece veya nether)
            if (player.tickCount % 40 == 0) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.REGENERATION, 60, 0, false, false, false));
            }
        }

        // 3. NETHER UYUMU
        if (isNether) {
            net.minecraft.core.BlockPos below = player.blockPosition().below();
            boolean onSoulBlock = level.getBlockState(below)
                    .is(net.minecraft.tags.BlockTags.SOUL_SPEED_BLOCKS);
            if (onSoulBlock) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 30, 1, false, false, false));
            }
        }

        // 4. RUH ALEVİ GÜCÜ
        net.minecraft.core.BlockPos feetPos = player.blockPosition();
        boolean inSoulFire = level.getBlockState(feetPos)
                .getBlock() == net.minecraft.world.level.block.Blocks.SOUL_FIRE;
        if (inSoulFire) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false, false));
            player.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 40, 1, false, false, false));
        }
    }

    // ─── DUMAN PARTİKÜLLERİ (CLIENT SIDE) ───────────────────────────────────
    public static void tickCursedSmoke(net.minecraft.world.entity.player.Player player) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        // smokeVisible false ise duman çıkarma
        boolean[] smokeCheck = {true};
        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            smokeCheck[0] = data.isSmokeVisible();
        });
        if (!smokeCheck[0]) return;

        Level clientLevel = mc.level;
        long timeOfDay = clientLevel.getDayTime() % 24000;
        boolean isDay = timeOfDay < 13000;

        int interval = isDay ? 2 : 5;
        if (player.tickCount % interval != 0) return;

        net.minecraft.core.particles.ParticleOptions dustType = isDay
                ? new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(0.75f, 0.75f, 0.75f), 1.8f)
                : ParticleTypes.LARGE_SMOKE;

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isCursed()) return;

            Level level = player.level();
            if (level.dimension() == Level.NETHER) return;

            boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();

            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            float yawRad = (float) Math.toRadians(player.getYRot());
            double rx = Math.cos(yawRad);
            double rz = Math.sin(yawRad);

            double vy = 0.015;

            if (isFirstPerson) {
                level.addParticle(dustType, px - rx * 0.45, py + 0.8, pz - rz * 0.45, 0, vy, 0);
                level.addParticle(dustType, px + rx * 0.45, py + 0.8, pz + rz * 0.45, 0, vy, 0);
                level.addParticle(dustType, px, py + 0.15, pz, 0, vy * 0.5, 0);
            } else {
                level.addParticle(dustType, px - rx * 0.4, py + 1.25, pz - rz * 0.4, 0, vy, 0);
                level.addParticle(dustType, px + rx * 0.4, py + 1.25, pz + rz * 0.4, 0, vy, 0);
                level.addParticle(dustType, px - rx * 0.2, py + 0.15, pz - rz * 0.2, 0, vy * 0.5, 0);
                level.addParticle(dustType, px + rx * 0.2, py + 0.15, pz + rz * 0.2, 0, vy * 0.5, 0);
                level.addParticle(dustType, px, py + 0.7, pz, 0, vy, 0);
            }
        });
    }

    // ─── ÖLÜMÜ REDDETME ───────────────────────────────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeathDefy(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
                if (!data.isCursed() || getDeathDenyCooldownPercent(data) < 1.0f) return;

                event.setCanceled(true);
                player.getInventory().setChanged();
                player.setHealth(5.0f);
                player.removeAllEffects();

                data.setLastDeathDenyTime(System.currentTimeMillis());

                player.getServer().execute(() -> {
                    if (player.isAlive()) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 1));
                        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 1));
                        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80, 0));
                    }
                });

                player.sendSystemMessage(Component.literal(
                        "§4§lDEATH DENIED §8— §7The malediction binds thee to this realm..."));

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.WITHER_HURT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);

                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SCULK_SOUL,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            20, 0.5, 0.5, 0.5, 0.1);
                }

                PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
            });
        }
    }

    public static float getDeathDenyCooldownPercent(StaticProgressionData data) {
        long lastUse = data.getLastDeathDenyTime();
        if (lastUse <= 0) return 1.0f;
        long diff = System.currentTimeMillis() - lastUse;
        if (diff >= DEATH_DENY_COOLDOWN_MS) return 1.0f;
        return (float) diff / DEATH_DENY_COOLDOWN_MS;
    }

    // ─── AÇLIK SİSTEMİ — YİYECEK BLOKLAMA ───────────────────────────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Item item0 = event.getItem().getItem();
        // Yiyecek olmayan ama rituale dahil olanlar da kontrol edilmeli
        boolean isConsumable = event.getItem().isEdible()
                || item0 == Items.MILK_BUCKET
                || item0 == Items.WATER_BUCKET
                || item0 == Items.POTION;
        if (!isConsumable) return;

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isCursed()) return;

            Item item = event.getItem().getItem();

            // İzin verilen yiyecekler: CURSED_FOODS + redemptions_light + ritual maddeleri
            boolean isAllowed = CURSED_FOODS.contains(item)
                    || item == ItemRegistry.REDEMPTIONS_LIGHT.get()
                    || item == Items.ENCHANTED_GOLDEN_APPLE
                    || item == Items.MILK_BUCKET
                    || item == Items.WATER_BUCKET
                    || item == Items.POTION;

            if (!isAllowed) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(
                        "§8AShen Sustenance §7— Thy curse rejects all flavor..."), true);
            }
        });
    }

    // ─── YİYECEK BİTİŞİ: CURSED_FOODS bonusu + ritual takibi ────────────────
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Item item = event.getItem().getItem();

        // Filtreye POTION eklendi
        if (!event.getItem().isEdible() && item != Items.MILK_BUCKET && item != Items.POTION) return;

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isCursed()) return;

            // ── 1. ALTIN ELMA CEZASI (Wither Etkisi) ──────────────────────────
            if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {

                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                player.sendSystemMessage(Component.literal("§4The sacred fruit burns thy corrupted gullet..."), true);
            }

            // ── 2. CURSED_FOODS bonusu ──────────────────────────────────────
            if (CURSED_FOODS.contains(item)) {
                player.heal(2.0f);
                player.removeEffect(MobEffects.POISON);
                player.removeEffect(MobEffects.HUNGER);
                if (player.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
                }
            }

            // ── 3. RİTÜEL TAKİBİ ─────────────────────────────────────────────
            handleRitualStep(player, data, item, event.getItem());

            PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
        });
    }

    /**
     * Ritual sırası: redemptions_light → enchanted_golden_apple → milk_bucket
     *              → redemptions_light → potion
     */
    private static void handleRitualStep(ServerPlayer player, StaticProgressionData data, Item item, net.minecraft.world.item.ItemStack stack) {

        if (!data.isSmokeVisible()) return;

        Item redemptionsLight = ItemRegistry.REDEMPTIONS_LIGHT.get();
        int step = data.getRitualStep();

        // Beklenen item
        Item expected = switch (step) {
            case 0 -> redemptionsLight;
            case 1 -> Items.ENCHANTED_GOLDEN_APPLE;
            case 2 -> Items.MILK_BUCKET;
            case 3 -> redemptionsLight;
            case 4 -> Items.POTION;
            default -> null;
        };

        if (expected == null) return;

        if (item == expected) {

            if (item == Items.POTION) {
                net.minecraft.world.item.alchemy.Potion p = net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack);
                if (p != net.minecraft.world.item.alchemy.Potions.WATER_BREATHING &&
                        p != net.minecraft.world.item.alchemy.Potions.LONG_WATER_BREATHING) {

                    if (step > 0) {
                        data.setRitualStep(0);
                        player.sendSystemMessage(Component.literal("§8§o...the thread unravels."), true);
                    }
                    return;
                }
            }

            int nextStep = step + 1;
            if (nextStep >= 5) {
                // ── RİTUAL TAMAMLANDI ─────────────────────────────────────
                data.setRitualStep(0);
                data.setSmokeVisible(false);

                // Max HP +1 kalp (+2.0f)
                net.minecraft.world.entity.ai.attributes.AttributeInstance maxHp =
                        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (maxHp != null) {
                    maxHp.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                            "dark_aura_ritual_hp",
                            2.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION
                    ));
                    // Mevcut canı da max'a eşitle
                    player.setHealth(player.getMaxHealth());
                }

                player.sendSystemMessage(Component.literal(
                        "§8§lTHE ASHES SETTLE... §7The internal fire no longer spills into the world."));
                player.sendSystemMessage(Component.literal(
                        "§dThe curse is bound within. §7Thy vessel has finally hardened against the light."));

                if (player.level() instanceof ServerLevel sl) {

                    sl.sendParticles(ParticleTypes.END_ROD,
                            player.getX(), player.getY() + 1.0, player.getZ(),
                            30, 0.5, 0.7, 0.5, 0.05);
                }

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.2f);

            } else {
                // ── ADIM İLERLEDİ ─────────────────────────────────────────
                data.setRitualStep(nextStep);

                String hint = switch (nextStep) {
                    case 1 -> "§8...The heat within begins to coalesce.";
                    case 2 -> "§8...The internal fire stops seeking an exit.";
                    case 3 -> "§8...Thy vessel is learning to contain the dark.";
                    case 4 -> "§8...The lungs adapt to the crushing depths.";
                    default -> "";
                };
                if (!hint.isEmpty()) {
                    player.sendSystemMessage(Component.literal(hint), true);
                }
            }
        } else {
            // ── YANLIŞ SIRA: Sıfırla ──────────────────────────────────────
            if (step > 0) {
                data.setRitualStep(0);
                player.sendSystemMessage(Component.literal(
                        "§8§o...the thread unravels."), true);
            }
        }
    }

    // ─── HASAR FİLTRESİ: Nether'da ateş hasarı ───────────────────────────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isCursed()) return;

            boolean isNether = player.level().dimension() == Level.NETHER;
            if (isNether && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
                event.setCanceled(true);
            }
        });
    }

    // ─── LANET UYGULAMA ───────────────────────────────────────────────────────
    public static void applyCurse(ServerPlayer player) {
        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (data.isCursed()) {
                player.sendSystemMessage(Component.literal("§8THY SOUL IS ALREADY BOUND TO THE ANATHEMA."));
                return;
            }

            data.setAuraActive(false);
            data.setAuraTicksLeft(0);
            data.setCursed(true);
            data.setDarkAuraLevel(data.getAuraLevel());
            data.setSmokeVisible(true);
            data.setRitualStep(0);

            player.hurt(player.damageSources().magic(), 2.0f);

            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SCULK_SOUL,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        30, 0.6, 0.6, 0.6, 0.15);
            }

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.WITHER_AMBIENT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);

            player.sendSystemMessage(Component.literal(
                    "§4§l⚠ MALEDICTION SEALED §8— §cThe primal static is now forbidden to thee!"));
            player.sendSystemMessage(Component.literal(
                    "§8NOT EVEN DEATH SHALL ABSOLVE THEE OF THIS BURDEN..."));

            PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
        });
    }

    // ─── YARDIMCI ─────────────────────────────────────────────────────────────
    public static boolean isCursedFood(Item item) {
        return CURSED_FOODS.contains(item);
    }

    // ─── KARANLIK AURA — AKTİVASYON ──────────────────────────────────────────
    public static void handleDarkAuraSkill(ServerPlayer player) {
        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isCursed()) return;

            float lvl = data.getDarkAuraLevel();
            long now = System.currentTimeMillis();
            long cd  = getDarkCooldownTicks(lvl) * 50L;

            if (data.isDarkAuraActive()) {
                if (!data.isDarkAuraPhase2()) {
                    activatePhase2(player, data);
                } else {
                    deactivateDarkAura(player, data);
                }
                return;
            }

            long timeLeft = (data.getLastDarkAuraUseTime() + cd) - now;
            if (timeLeft > 0) {
                player.sendSystemMessage(Component.literal(
                        "§8THE MALEDICTION CALCIFIES... §7Patience for the next eruption." + (timeLeft / 1000) + "sn"));
                return;
            }

            data.setDarkAuraActive(true);
            data.setDarkAuraTicksLeft(getDarkDuration(lvl));

            String msg = switch ((int)(lvl * 2)) {
                case 1  -> "§8THE SHADOWS STIR... §7The abyss awakens.";
                case 2  -> "§8§oDARK ANATHEMA — TIER I";
                case 4  -> "§8§lDARK ANATHEMA — TIER II";
                case 6  -> "§8§lVOIDMASTER'S TYRANNY — ASCENDED";
                default -> "§8THE MALEDICTION IS MANIFEST.";
            };
            player.sendSystemMessage(Component.literal(msg));

            spawnDarkLightning(player);
            pushNearbyPlayers(player, 3.5, 0.5f);

            PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
        });
    }

    private static void spawnDarkLightning(ServerPlayer player) {
        net.minecraft.world.entity.LightningBolt bolt =
                net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(player.level());
        if (bolt != null) {
            bolt.moveTo(player.getX(), player.getY(), player.getZ());
            bolt.setVisualOnly(true);
            player.level().addFreshEntity(bolt);
        }
    }

    private static void pushNearbyPlayers(ServerPlayer player, double radius, float power) {
        player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player
        ).forEach(target -> {
            net.minecraft.world.phys.Vec3 dir =
                    target.position().subtract(player.position()).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(
                    dir.x * power, 0.2, dir.z * power));
            if (target instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
            }
            target.hurtMarked = true;
        });
    }

    private static void deactivateDarkAura(ServerPlayer player, StaticProgressionData data) {
        data.setDarkAuraActive(false);
        data.setDarkAuraTicksLeft(0);
        data.setDarkAuraPhase2(false);
        data.setLastDarkAuraUseTime(System.currentTimeMillis());
        removeDarkAuraEffects(player);
        PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
    }

    // ─── PHASE 2: KARANLIK ÇEMBER ─────────────────────────────────────────────
    public static void activatePhase2(ServerPlayer player, StaticProgressionData data) {
        if (data.getDarkAuraTicksLeft() <= 0) return;

        data.setDarkAuraPhase2(true);

        player.sendSystemMessage(Component.literal(
                "§4§l⬡ THE ABYSSAL HEX IS CAST — §8Harvesting the essence..."));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.WITHER_AMBIENT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);

        PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
    }

    public static double getPhase2Radius(float lvl) {
        if (lvl >= 3.0f) return 6.0;
        if (lvl >= 2.0f) return 5.0;
        if (lvl >= 1.0f) return 4.0;
        return 3.0;
    }

    private static float getPhase2DrainPerSecond(float lvl) {
        if (lvl >= 3.0f) return 2.0f;
        if (lvl >= 2.0f) return 1.25f;
        if (lvl >= 1.0f) return 0.75f;
        return 0.5f;
    }

    public static void tickPhase2Drain(ServerPlayer player, StaticProgressionData data) {
        if (!data.isDarkAuraPhase2()) return;
        if (data.getDarkAuraTicksLeft() % 20 != 0) return;

        float lvl    = data.getDarkAuraLevel();
        double radius = getPhase2Radius(lvl);
        float drain  = getPhase2DrainPerSecond(lvl);

        player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive()
        ).forEach(target -> {
            target.hurt(player.damageSources().indirectMagic(player, player), drain);

            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        2, 0.2, 0.2, 0.2, 0.05);
            }
        });
    }

    public static void togglePhase2(ServerPlayer player) {
        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isDarkAuraActive()) return;

            Level levelWorld = player.level();

            if (!data.isDarkAuraPhase2()) {
                data.setDarkAuraPhase2(true);
                player.sendSystemMessage(Component.literal("§5THE SINGULARITY CONDENSES... §8Suffocating the light."));

                levelWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.8f);
                levelWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.WITHER_AMBIENT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.5f);

            } else {
                int remainingTicks = data.getDarkAuraTicksLeft();
                if (remainingTicks > 0) {
                    float level = data.getDarkAuraLevel();
                    float modifier = net.minecraft.util.Mth.lerp((level - 0.5f) / 2.5f, 0.15f, 0.25f);
                    float seconds = remainingTicks / 20f;
                    float healAmount = seconds * 0.5f * modifier;

                    if (healAmount > 0) {
                        player.heal(healAmount);
                        player.sendSystemMessage(Component.literal(
                                "§dSOUL-STITCH COMPLETE — §aCorruption heals: +" + String.format("%.1f", healAmount)));

                        float dynamicPitch = net.minecraft.util.Mth.clamp(0.8f + (seconds / 5.0f), 0.8f, 2.0f);
                        levelWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sounds.SoundEvents.WITHER_SHOOT,
                                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, dynamicPitch);
                    }
                }

                data.setDarkAuraPhase2(false);
                data.setDarkAuraActive(false);
                data.setDarkAuraTicksLeft(0);
                data.setLastDarkAuraUseTime(System.currentTimeMillis());

                player.sendSystemMessage(Component.literal(
                        "§8THE MALEDICTION RECOILS... §7Shadows return to the deep."));

                levelWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.5f);
            }

            PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
        });
    }

    private static void removeDarkAuraEffects(ServerPlayer player) {
        player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
        player.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
    }

    // ─── KARANLIK AURA — TICK (SERVER) ───────────────────────────────────────
    public static int getDarkDuration(float lvl) {
        if (lvl >= 3.0f) return 900;
        if (lvl >= 2.0f) return 900;
        if (lvl >= 1.0f) return 800;
        return 700;
    }

    public static int getDarkCooldownTicks(float lvl) {
        if (lvl >= 3.0f) return 2000;
        if (lvl >= 2.0f) return 2200;
        if (lvl >= 1.0f) return 1800;
        return 1600;
    }

    public static void tickDarkAura(ServerPlayer player, StaticProgressionData data) {
        if (!data.isDarkAuraActive()) return;

        int ticks = data.getDarkAuraTicksLeft() - 1;
        data.setDarkAuraTicksLeft(ticks);

        if (ticks <= 0) {
            data.setDarkAuraActive(false);
            data.setDarkAuraPhase2(false);
            data.setLastDarkAuraUseTime(System.currentTimeMillis());

            player.sendSystemMessage(Component.literal(
                    "§8THE CORE SEEKS REFUGE... §7Let the shadows settle within."));
            PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
            return;
        }

        float lvl = data.getDarkAuraLevel();

        if (data.isDarkAuraPhase2()) {
            tickPhase2Drain(player, data);
        } else {
            applyDarkAuraEffects(player, lvl);
            if (ticks % 10 == 0) {
                applyDarkAuraPressure(player, lvl);
            }
        }

        if (ticks % 5 == 0) {
            PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));
        }
    }

    private static void applyDarkAuraEffects(ServerPlayer player, float lvl) {
        player.addEffect(new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 30, 1, false, false, false));

        if (lvl >= 1.0f) {
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 30, 0, false, false, false));
        }

        if (lvl >= 2.0f) {
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 30, 1, false, false, false));
            boolean isNether = player.level().dimension() == Level.NETHER;
            if (!isNether) {
                player.addEffect(new MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 30, 0, false, false, false));
            }
        }

        if (lvl >= 3.0f) {
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 30, 2, false, false, false));
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 30, 1, false, false, false));
            player.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 30, 0, false, false, false));
            if (player.tickCount % 40 == 0) {
                player.addEffect(new MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.ABSORPTION, 100, 1, false, false, false));
            }
        }
    }

    private static void applyDarkAuraPressure(ServerPlayer player, float lvl) {
        double pressureRadius = 2.5;
        player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(pressureRadius),
                e -> e != player
        ).forEach(target -> {
            target.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0, false, true, true));
            target.addEffect(new MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WITHER, 40, 0, false, true, true));
        });
    }
}