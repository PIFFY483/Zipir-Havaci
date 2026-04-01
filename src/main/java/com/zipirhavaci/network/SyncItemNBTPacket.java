package com.zipirhavaci.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class SyncItemNBTPacket {
    private final int slotIndex;
    private final CompoundTag nbt;

    public SyncItemNBTPacket(int slotIndex, CompoundTag nbt) {
        this.slotIndex = slotIndex;
        this.nbt = nbt;
    }

    public static void encode(SyncItemNBTPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.slotIndex);
        buffer.writeNbt(msg.nbt);
    }

    public static SyncItemNBTPacket decode(FriendlyByteBuf buffer) {
        return new SyncItemNBTPacket(buffer.readInt(), buffer.readNbt());
    }

    public static void handle(SyncItemNBTPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                if (player != null && msg.slotIndex >= 0 && msg.slotIndex < player.getInventory().getContainerSize()) {
                    ItemStack stack = player.getInventory().getItem(msg.slotIndex);
                    if (!stack.isEmpty()) {
                        // NBTyi direkt güncelle, yeni itemStack oluşturma
                        stack.setTag(msg.nbt);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}