package com.zipirhavaci.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ToggleDamageLogPacket {
    private final boolean enabled;

    public ToggleDamageLogPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(ToggleDamageLogPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.enabled);
    }

    public static ToggleDamageLogPacket decode(FriendlyByteBuf buffer) {
        return new ToggleDamageLogPacket(buffer.readBoolean());
    }

    public static void handle(ToggleDamageLogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getPersistentData().putBoolean("ZipirDamageLog", msg.enabled);

                String status = msg.enabled ? "§aON" : "§cOFF";
                player.displayClientMessage(Component.literal("§6Damage Log: " + status), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}