package com.zipirhavaci.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

public class RegularSmoke {

    private static Vec3 lastPipeWorld = null;

    public static void clientTick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof com.zipirhavaci.item.ZipirAviatorItem)) return;

        CompoundTag tag = stack.getOrCreateTag();
        int overloadTimer = tag.getInt("ZipirOverloadTimer");
        int steamTicks = player.getPersistentData().getInt("CooldownSteamTicks");

        // Süperskill şarjını bir kez okuyup alt metotlara taşıyoruz
        int superCharge = player.getPersistentData().getInt("SuperChargeShake");

        boolean isHeavyCooldown = steamTicks > 0;
        boolean isOverloaded = overloadTimer > 0;

        if (overloadTimer > 0) {
            tag.putInt("ZipirOverloadTimer", overloadTimer - 1);
        }

        if (!isHeavyCooldown && !isOverloaded && superCharge <= 0 && player.tickCount % 12 != 0) return;

        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            renderFirstPersonSmoke(player, isHeavyCooldown, isOverloaded, superCharge);
        } else {
            renderThirdPersonSmoke(player, isHeavyCooldown, isOverloaded, superCharge);
        }
    }

    private static void renderFirstPersonSmoke(Player player, boolean isHeavyCooldown, boolean isOverloaded, int superCharge) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        double cosYaw = Math.cos(Math.toRadians(yaw));
        double sinYaw = Math.sin(Math.toRadians(yaw));
        double cosPitch = Math.cos(Math.toRadians(pitch));
        double sinPitch = Math.sin(Math.toRadians(pitch));

        double dirX = -sinYaw * cosPitch;
        double dirY = -sinPitch;
        double dirZ = cosYaw * cosPitch;

        Vec3 eye = player.getEyePosition();
        float chance = player.getRandom().nextFloat();

        boolean isCampfireAction = !isHeavyCooldown && (chance >= 0.14f && chance < 0.22f);

        double vertical;
        double side;
        double forward;
        double camPush = 0.08;

        if (isOverloaded) {
            forward = 0.45;
            vertical = -0.06;
            side = -0.22;
        } else if (isHeavyCooldown) {
            forward = 0.35;
            vertical = -0.12;
            side = -0.32;
        } else if (isCampfireAction) {
            forward = 0.35;
            vertical = -0.11;
            side = -0.30;
        } else {
            forward = 0.35;
            vertical = -0.04;
            side = -0.18;
        }

        double rightX = cosYaw;
        double rightZ = sinYaw;

        Vec3 pos = new Vec3(
                eye.x + dirX * forward + rightX * side,
                eye.y + dirY * forward + vertical,
                eye.z + dirZ * forward + rightZ * side
        ).add(-dirX * camPush, -dirY * camPush, -dirZ * camPush);

        lastPipeWorld = pos;

        // HIZ TELAFİSİ: Oyuncunun 1 tick sonraki konumuna dumanı çivile.
        Vec3 v = player.getDeltaMovement();
        spawnHybridParticles(player, pos.add(v), isHeavyCooldown, isOverloaded, chance, true, superCharge);
    }

    private static void renderThirdPersonSmoke(Player player, boolean isHeavyCooldown, boolean isOverloaded, int superCharge) {
        double yawRad = Math.toRadians(player.yBodyRot);
        double offsetX = -Math.cos(yawRad) * 0.45;
        double offsetZ = -Math.sin(yawRad) * 0.45;
        double offsetY = isHeavyCooldown || isOverloaded ? 1.1 : 1.2;

        Vec3 v = player.getDeltaMovement();
        Vec3 tpvPos = player.position().add(offsetX, offsetY, offsetZ).add(v);

        spawnHybridParticles(player, tpvPos, isHeavyCooldown, isOverloaded, player.getRandom().nextFloat(), false, superCharge);
    }

    private static void spawnHybridParticles(Player player, Vec3 pos, boolean isHeavyCooldown, boolean isOverloaded, float chance, boolean isFPVMode, int superCharge) {

        // SÜPERSKİLL ŞARJ DURUMU: Lav ve Ruh partikülleri seviyeye bağlı olarak çıkar
        if (superCharge > 0) {
            float chargeRatio = Math.min(superCharge / 100.0f, 1.0f);

            if (player.getRandom().nextFloat() < chargeRatio * 0.35f) {
                player.level().addParticle(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 0, 0.01, 0);
            }
            if (player.getRandom().nextFloat() < chargeRatio * 0.50f) {
                player.level().addParticle(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 0, 0.02, 0);
            }
        }

        // 1. ÖNCELİKLİ DURUM: OVERLOAD (Siyah duman ve kül fışkırması)
        if (isOverloaded) {
            for (int i = 0; i < 3; i++) {
                player.level().addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 0, 0, 0);
                if (player.getRandom().nextFloat() < 0.4f) {
                    player.level().addParticle(ParticleTypes.ASH, pos.x, pos.y, pos.z, 0, 0.01, 0);
                }
            }
            return;
        }

        // 2. MEVCUT COOLDOWN MANTIĞI
        if (isHeavyCooldown) {
            if (player.getRandom().nextFloat() < 0.22f) { // Lav %22
                player.level().addParticle(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 0, 0.01, 0);
            }

            float cooldownChance = player.getRandom().nextFloat();
            if (cooldownChance < 0.70f) { // Beyaz Buhar %70
                player.level().addParticle(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 0, 0.02, 0);
            } else if (cooldownChance < 0.85f) { // Siyah Duman %15
                player.level().addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0, 0.01, 0);
            }
        } else {
            // 3. NORMAL İŞLEYİŞ
            if (player.getRandom().nextFloat() < 0.05f) { // Lav %5
                player.level().addParticle(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 0, 0.01, 0);
            }

            if (chance < 0.14f) { // Kül %14
                for (int i = 0; i < 4; i++) {
                    player.level().addParticle(ParticleTypes.ASH, pos.x, pos.y, pos.z, 0, 0.005, 0);
                }
            } else if (chance < 0.22f) { // Campfire %8
                player.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 0, 0.004, 0);
            } else if (chance < 0.53f) { // Siyah Duman %31
                player.level().addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0, isFPVMode ? 0.006 : 0.01, 0);
            } else if (chance < 0.55f) { // Kirli Beyaz %4
                player.level().addParticle(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 0, 0.005, 0);
            } else if (chance < 0.57f) { // Ateş %2
                player.level().addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 0, 0.005, 0);
            }
        }
    }


    public static Vec3 getPipeWorldPos() {
        return lastPipeWorld;
    }
}