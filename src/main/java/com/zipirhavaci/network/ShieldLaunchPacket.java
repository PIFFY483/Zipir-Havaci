package com.zipirhavaci.network;

import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.entity.ThrownShieldEntity;
import com.zipirhavaci.item.ZipirAviatorItem;
import com.zipirhavaci.core.AviatorConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

public class ShieldLaunchPacket {
    private final float charge;

    public ShieldLaunchPacket(float charge) {
        this.charge = charge;
    }

    public static void encode(ShieldLaunchPacket msg, FriendlyByteBuf buffer) {
        buffer.writeFloat(msg.charge);
    }

    public static ShieldLaunchPacket decode(FriendlyByteBuf buffer) {
        return new ShieldLaunchPacket(buffer.readFloat());
    }

    public static void handle(ShieldLaunchPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            boolean isHoldingShield = player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShieldItem ||
                    player.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem;

            if (!isHoldingShield) {
                // Sunucudaki kalkanı bul ve çekilme işlemini başlat
                com.zipirhavaci.core.physics.SoulBondHandler.tryExecuteBond(player);
                return;
            }


            ItemStack aviator = player.getInventory().items.stream()
                    .filter(s -> s.getItem() instanceof ZipirAviatorItem)
                    .findFirst().orElse(ItemStack.EMPTY);

            InteractionHand shieldHand = player.getMainHandItem().is(net.minecraft.world.item.Items.SHIELD) ? InteractionHand.MAIN_HAND :
                    player.getOffhandItem().is(net.minecraft.world.item.Items.SHIELD) ? InteractionHand.OFF_HAND : null;

            if (shieldHand != null && !aviator.isEmpty()) {
                if (player.isCreative() || !player.getCooldowns().isOnCooldown(aviator.getItem())) {
                    float safeCharge = net.minecraft.util.Mth.clamp(msg.charge, 0.0f, 1.0f);
                    performLaunch(player, aviator, shieldHand, safeCharge, player.isCreative());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void performLaunch(ServerPlayer player, ItemStack aviator, InteractionHand hand, float charge, boolean creative) {
        Level level = player.level();

        ItemStack firlatilanKalkan = player.getItemInHand(hand).copy();


        if (!creative) {
            player.getItemInHand(hand).shrink(1);
        }

        // Kalkanı fırlat
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.01) right = player.getForward().cross(new Vec3(0, 1, 0)).normalize();

        Vec3 spawnPos = player.position().add(0, player.getEyeHeight() - 0.4, 0)
                .add(look.scale(1.2)).add(right.scale(0.5));

        ThrownShieldEntity shield = new ThrownShieldEntity(level, player, charge);
        shield.setShieldItem(firlatilanKalkan);
        shield.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        shield.setOwner(player);
        shield.setInitialTarget(eyePos.add(look.scale(50.0)));


        float finalSpeed = (float) (AviatorConstants.SHIELD_BASE_SPEED + (charge * AviatorConstants.SHIELD_MAX_CHARGE_MULT));
        shield.shoot(look.x, look.y, look.z, finalSpeed, 0.1F);
        level.addFreshEntity(shield);

        player.swing(hand, true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.PISTON_EXTEND, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 2.0F);

        // === ANİMASYON VE NBT ===
        if (!creative && aviator.getItem() instanceof ZipirAviatorItem item) {
            CompoundTag nbt = aviator.getOrCreateTag();
            int currentUses = nbt.getInt("Uses");
            long id = GeoItem.getOrAssignId(aviator, player.serverLevel());

            String animName;
            int newUses;

            if (currentUses >= AviatorConstants.MAX_USES_BEFORE_RELOAD - 1) {
                player.getCooldowns().addCooldown(aviator.getItem(), 120);
                newUses = 0;
                animName = "cooldown";

                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        com.zipirhavaci.core.SoundRegistry.COOLDOWN_SOUND.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

            } else {
                newUses = currentUses + 1;
                animName = "fire";
            }

            System.out.println("SERVER (Shield): Sending animation: " + animName + " with ID: " + id);

            // Animasyonu istemciye gönder
            PacketHandler.sendToPlayer(player,
                    new TriggerAnimationPacket(id, "main_controller", animName));

            // NBT'yi güncelle
            int finalNewUses = newUses;
            ZipirHavaci.SCHEDULER.schedule(() -> {
                if (!player.isRemoved()) {
                    level.getServer().execute(() -> {
                        nbt.putInt("Uses", finalNewUses);
                        int slotIndex = player.getInventory().findSlotMatchingItem(aviator);
                        if (slotIndex >= 0) {
                            PacketHandler.sendToPlayer(player, new SyncItemNBTPacket(slotIndex, nbt.copy()));
                        }
                    });
                }
            }, 100, TimeUnit.MILLISECONDS);
        }
    }
}