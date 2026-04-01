package com.zipirhavaci.network;

import com.zipirhavaci.item.ZipirAviatorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.function.Supplier;

public class DeployItemPacket {

    private final boolean isFirstPerson;

    public DeployItemPacket(boolean isFirstPerson) {
        this.isFirstPerson = isFirstPerson;
    }

    public static void encode(DeployItemPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.isFirstPerson);
    }

    public static DeployItemPacket decode(FriendlyByteBuf buffer) {
        return new DeployItemPacket(buffer.readBoolean());
    }

    public static void handle(DeployItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            //TPV BARAJI
            if (!msg.isFirstPerson) {
                return;
            }

            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof ZipirAviatorItem) {
                CompoundTag tag = stack.getOrCreateTag();

                // GÜVENLİK BARAJI: Zaman Damgası Kontrolü
                long lastCooldown = tag.getLong("LastCooldownAnimTime");
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastCooldown < 6500) {
                    return;
                }

                ServerLevel level = player.serverLevel();
                long instanceId = GeoItem.getOrAssignId(stack, level);

                player.getCooldowns().addCooldown(stack.getItem(), 3);

                PacketHandler.sendToPlayer(player, new TriggerAnimationPacket(instanceId, "main_controller", "deploy"));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}