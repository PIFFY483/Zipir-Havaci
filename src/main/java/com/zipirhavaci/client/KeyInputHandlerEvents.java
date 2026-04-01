package com.zipirhavaci.client;

import com.zipirhavaci.core.AviatorMode;
import com.zipirhavaci.entity.ThrownShieldEntity;
import com.zipirhavaci.item.ZipirAviatorItem;
import com.zipirhavaci.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.zipirhavaci.core.SoundRegistry;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KeyInputHandlerEvents {
    private static int chargeTicks = 0;

    // --- TAKİP DEĞİŞKENLERİ ---
    public static ThrownShieldEntity lastThrownShield = null;
    private static long lastExecutionTime = 0;
    private static int lastSoundLevel = -1;

    public static void resetCooldown() {
        lastExecutionTime = System.currentTimeMillis();
    }

    private static float appearanceAlpha = 0.0f;
    private static boolean soundPlayed = false;

    // --- SUPER SKILL ŞARJ DEĞİŞKENLERİ ---
    private static int superChargeTicks = 0;
    private static int holdTicks = 0; // YENİ: Oyuncunun tuşa basılı tutma süresi (Titremeler için)
    private static int autoFireCountdown = -1;
    private static boolean superFired = false;

    private static final int TICKS_PER_LEVEL = 20; // 1 saniye
    private static final int MAX_LEVEL       = 5;
    private static final int AUTO_FIRE_DELAY = 10; // yarım saniye (tick)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        while (KeyInputHandler.AURA_SKILL_KEY.consumeClick()) {
            final net.minecraft.client.player.LocalPlayer clientPlayer = player; // final referans
            clientPlayer.getCapability(com.zipirhavaci.core.capability.StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
                if (data.isCursed()) {
                    // Eğer Lanetliyse (Dark Aura):
                    if (data.isDarkAuraActive()) {

                        PacketHandler.sendToServer(new com.zipirhavaci.network.ToggleDarkAuraPhasePacket());
                    } else {

                        PacketHandler.sendToServer(new com.zipirhavaci.network.AuraSkillPacket());
                    }
                } else {

                    PacketHandler.sendToServer(new com.zipirhavaci.network.AuraSkillPacket());
                }
            });
        }


        // --- MERMİ YENİLEME ENERJİ AKIŞI  ---
        ItemStack handStack = player.getMainHandItem();
        if (handStack.getItem() instanceof ZipirAviatorItem && handStack.hasTag()) {
            CompoundTag tag = handStack.getTag();

            if (tag.contains("LastUseTime")) {

                long lastUseTime = tag.getLong("LastUseTime");
                long elapsed = System.currentTimeMillis() - lastUseTime;

                int currentlyFilled = (int) (elapsed / 1000);

                if (lastSoundLevel == -1) {
                    lastSoundLevel = currentlyFilled;
                }

                if (currentlyFilled > lastSoundLevel && currentlyFilled < 6) {
                    float pitchBase = 1.1f + (currentlyFilled * 0.16f);
                    float vol = 0.06f + (currentlyFilled * 0.02f);

                    // Sunucuya gönder, o herkese yayınlasın
                    PacketHandler.sendToServer(new PlayAviatorSoundPacket(0, currentlyFilled, vol, pitchBase));

                    lastSoundLevel = currentlyFilled;
                }

                if (currentlyFilled >= 6) {
                    lastSoundLevel = 5;
                }
            } else {
                lastSoundLevel = -1;
            }
        }

        // 1. MOD DEĞİŞTİRME (R)
        while (KeyInputHandler.MODE_CHANGE_KEY.consumeClick()) {
            //MÜDAHALE: Mevcut kamera modunu al (First Person ise true, değilse/TPV ise false)
            boolean isFirstPerson = net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();

            PacketHandler.sendToServer(new ModChangePacket(isFirstPerson));
        }

        // 2. RUH BAĞI GÖRSELİ
        if (lastThrownShield != null) {
            if (lastThrownShield.isRemoved()) {
                lastThrownShield = null;
                appearanceAlpha = 0.0f;
            } else {
                double dist = player.distanceTo(lastThrownShield);
                if (dist <= 25.0) {
                    appearanceAlpha = Math.min(1.0f, appearanceAlpha + 0.05f);
                    renderStableLink(player, lastThrownShield, appearanceAlpha);
                } else {
                    appearanceAlpha = Math.max(0.0f, appearanceAlpha - 0.05f);
                    if (appearanceAlpha > 0) {
                        renderStableLink(player, lastThrownShield, appearanceAlpha);
                    }
                }
            }
        }

        // 3. FIRLATMA VE ŞARJ (V TUŞU)
        if (KeyInputHandler.SHIELD_LAUNCH_KEY.isDown()) {
            chargeTicks++;
            if (chargeTicks > 5) {
                player.setDeltaMovement(player.getDeltaMovement().multiply(0.8, 1.0, 0.8));
            }
        } else {
            if (chargeTicks > 0) {
                float chargeRatio = Math.min((float) chargeTicks / 16.0f, 1.5f);
                PacketHandler.sendToServer(new ShieldLaunchPacket(chargeRatio));
                chargeTicks = 0;
            }
        }

        // 4. SÜPER YETENEK (X TUŞU)
        handleSuperSkill(player);
    }

    private static void handleSuperSkill(net.minecraft.client.player.LocalPlayer player) {
        boolean hasGauntlet = player.getMainHandItem().getItem() instanceof ZipirAviatorItem;
        if (!hasGauntlet) {
            superChargeTicks = 0;
            holdTicks = 0;
            autoFireCountdown = -1;
            superFired = false;
            return;
        }

        CompoundTag nbt = player.getMainHandItem().getOrCreateTag();
        int currentUses = nbt.getInt("Uses");

        boolean hasSufficientAmmo = currentUses <= 4;
        boolean onCooldown = player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
        boolean keyDown = KeyInputHandler.AVIATOR_SUPER_KEY.isDown();

        int availableAmmo = 6 - currentUses;

        int maxChargeTicks;
        int autoFireThreshold; // Şarj dolduktan sonraki bekleme süresi (tick)

        switch (availableAmmo) {
            case 6:
                maxChargeTicks = 120;
                autoFireThreshold = 15;  // 0.75 saniye (Hızlı ve riskli)
                break;
            case 5:
                maxChargeTicks = 95;
                autoFireThreshold = 30;  // 1.5 saniye (Baz değer)
                break;
            case 4:
                maxChargeTicks = 79;
                autoFireThreshold = 40;  // 2 saniye
                break;
            case 3:
                maxChargeTicks = 63;
                autoFireThreshold = 60;  // 3 saniye
                break;
            case 2:
                maxChargeTicks = 47;
                autoFireThreshold = 80;  // 4 saniye
                break;
            case 1:
                maxChargeTicks = 31;
                autoFireThreshold = 100; // 5 saniye
                break;
            default:
                maxChargeTicks = 0;
                autoFireThreshold = 200;
                break;
        }

        if (autoFireCountdown >= 0) {
            autoFireCountdown--;
            if (autoFireCountdown <= 0) {
                fireSuperSkill(player, Math.min(5, availableAmmo));
                autoFireCountdown = -1;
                superChargeTicks = 0;
                holdTicks = 0;
                superFired = true;
            }
            return;
        }

        if (keyDown && !superFired && !onCooldown && hasSufficientAmmo) {
            holdTicks++;

            if (superChargeTicks < maxChargeTicks) {
                superChargeTicks++;

                if (superChargeTicks % 16 == 0) {
                    player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_ON,
                            net.minecraft.sounds.SoundSource.PLAYERS,
                            0.6f, 1.8f, false);
                }
            }

            // --- DİNAMİK AUTO-FIRE TETİKLEME ---
            if (superChargeTicks >= maxChargeTicks && autoFireCountdown < 0) {
                int ticksSinceFull = holdTicks - maxChargeTicks;
                if (ticksSinceFull >= autoFireThreshold) {
                    autoFireCountdown = AUTO_FIRE_DELAY;
                }
            }

            // --- FİZİKSEL SARSINTI  ---
            if (holdTicks % 4 == 0) {
                int chargeLevel = Math.min(5, 1 + (superChargeTicks / TICKS_PER_LEVEL));

                if (holdTicks % 20 == 0) {
                    float progress = (chargeLevel - 1) / 4.0f;
                    float energyPitch = 0.4f + (progress * 1.1f);
                    float energyVolume = 0.4f + (progress * 0.3f);

                    // Sunucuya gönder
                    PacketHandler.sendToServer(new PlayAviatorSoundPacket(1, chargeLevel, energyVolume, energyPitch));
                }

                boolean pulseDir = (holdTicks / 4) % 2 == 0;
                float pulseIntensity = 0.03f + (chargeLevel * 0.015f);

                com.zipirhavaci.client.visuals.FOVHandler.pulseCharge(pulseDir ? pulseIntensity : -pulseIntensity);
                player.getPersistentData().putInt("SuperChargeShake", superChargeTicks);
                applyPhysicalShock(player, player.getMainHandItem(), chargeLevel);

                float shakePower = pulseIntensity * 0.5f;
                double nX = (player.getRandom().nextDouble() - 0.5) * shakePower;
                double nZ = (player.getRandom().nextDouble() - 0.5) * shakePower;
                player.setDeltaMovement(player.getDeltaMovement().add(nX, 0, nZ));
            }

            // Auto-Fire sadece Max level'a ulaşıldığında (veya mermi yetiyorsa)
            if (superChargeTicks >= TICKS_PER_LEVEL * MAX_LEVEL && autoFireCountdown < 0) {
                autoFireCountdown = AUTO_FIRE_DELAY;
            }

        } else if (!keyDown) {
            if (superChargeTicks > 0 && !superFired && !onCooldown && hasSufficientAmmo) {
                int level = Math.min(MAX_LEVEL, 1 + (superChargeTicks / TICKS_PER_LEVEL));
                fireSuperSkill(player, level);
            }
            superChargeTicks = 0;
            holdTicks = 0;
            superFired = false;
        }
    }

    private static void fireSuperSkill(net.minecraft.client.player.LocalPlayer player, int level) {
        boolean isTPV = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
        int modeInt = player.getMainHandItem().getOrCreateTag().getInt("AviatorMode");
        boolean isPull = (modeInt == 0);

        PacketHandler.sendToServer(new AviatorSuperSkillPacket(level, isTPV));
        com.zipirhavaci.client.visuals.FOVHandler.bumpFOVSuper(isPull, level);

        player.getPersistentData().remove("SuperChargeShake");
        chargeTicks = 0;
    }

    private static void renderStableLink(net.minecraft.client.player.LocalPlayer player, ThrownShieldEntity shield, float alpha) {
        boolean hasLearned = player.getCapability(com.zipirhavaci.core.capability.Soulbonddataprovider.SOUL_BOND)
                .map(data -> data.hasSoulBond())
                .orElse(false);

        if (!hasLearned) return;

        Vec3 start = player.position().add(0, 1.2, 0);
        Vec3 end = shield.position().add(0, 0.3, 0);
        Vec3 line = end.subtract(start);
        double dist = line.length();

        long timePassed = System.currentTimeMillis() - lastExecutionTime;
        float skillReady = Math.min(1.0f, (float) timePassed / 8000.0f);

        if (skillReady >= 1.0f) {
            if (!soundPlayed) {
                player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                        SoundRegistry.BOND_READY.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.0F, false);
                soundPlayed = true;
            }
        } else {
            soundPlayed = false;
        }

        if (skillReady < 0.05f || alpha <= 0.01f) return;

        float r = 0.0f;
        float g = 0.3f * skillReady;
        float b = 0.6f * skillReady;
        float size = (0.25f + (0.1f * skillReady)) * alpha;

        net.minecraft.core.particles.DustParticleOptions soulDust =
                new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(r, g, b), size);

        int points = (int) (dist * 1.2);
        for (int i = 0; i < points; i++) {
            double t = (double) i / Math.max(1, points);
            double sineWave = Math.sin((player.tickCount + i) * 0.15) * 0.025;
            double jitter = (player.level().random.nextDouble() - 0.5) * 0.03;

            Vec3 pos = start.add(line.scale(t)).add(jitter, sineWave + jitter, jitter);
            player.level().addParticle(soulDust, pos.x, pos.y, pos.z, 0, 0, 0);

            if (skillReady >= 0.999f && player.level().random.nextInt(100) < 1) {
                player.level().addParticle(net.minecraft.core.particles.ParticleTypes.SOUL,
                        pos.x, pos.y, pos.z, 0, 0.01, 0);
            }
        }
    }

    private static void applyPhysicalShock(Player player, ItemStack stack, int level) {
        int modeInt = stack.getOrCreateTag().getInt("AviatorMode");
        Vec3 look = player.getLookAngle();

        float intensity = 0.04f + (level * 0.03f);
        double nX = (player.getRandom().nextDouble() - 0.5) * 0.06;
        double nZ = (player.getRandom().nextDouble() - 0.5) * 0.06;

        if (modeInt == 1) {
            player.setDeltaMovement(player.getDeltaMovement().add(
                    -look.x * intensity + nX,
                    0.02,
                    -look.z * intensity + nZ
            ));
            com.zipirhavaci.client.visuals.FOVHandler.pulseCharge(-0.12f);
        } else {
            player.setDeltaMovement(player.getDeltaMovement().add(
                    look.x * (intensity * 1.1) + nX,
                    0.04,
                    look.z * (intensity * 1.1) + nZ
            ));
            com.zipirhavaci.client.visuals.FOVHandler.pulseCharge(0.12f);
        }
    }
}