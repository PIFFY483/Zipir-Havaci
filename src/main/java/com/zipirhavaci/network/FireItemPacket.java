package com.zipirhavaci.network;

import com.zipirhavaci.physics.MovementHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class FireItemPacket {
    private final boolean isTPV;

    public FireItemPacket(boolean isTPV) {
        this.isTPV = isTPV;
    }

    public static void encode(FireItemPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.isTPV);
    }

    public static FireItemPacket decode(FriendlyByteBuf buffer) {
        return new FireItemPacket(buffer.readBoolean());
    }

    public static void handle(FireItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();

                if (!MovementHandler.consumeGunpowder(player)) {

                    return;
                }

                // Kamera moduna göre  metodu çağır
                if (msg.isTPV) {
                    MovementHandler.executeLaunchTPV(player, stack);
                } else {
                    MovementHandler.executeLaunchFPV(player, stack);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}