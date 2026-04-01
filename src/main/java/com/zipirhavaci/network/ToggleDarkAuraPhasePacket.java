package com.zipirhavaci.network;

import com.zipirhavaci.core.physics.DarkAuraHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class ToggleDarkAuraPhasePacket {
    public ToggleDarkAuraPhasePacket() {}

    public static void encode(ToggleDarkAuraPhasePacket msg, FriendlyByteBuf buf) {}

    public static ToggleDarkAuraPhasePacket decode(FriendlyByteBuf buf) {
        return new ToggleDarkAuraPhasePacket();
    }

    public static void handle(ToggleDarkAuraPhasePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {

                DarkAuraHandler.togglePhase2(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}