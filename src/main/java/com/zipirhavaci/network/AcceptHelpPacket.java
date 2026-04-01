package com.zipirhavaci.network;

import com.zipirhavaci.entity.SilentCaptiveEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class AcceptHelpPacket {

    private final int entityId;

    public AcceptHelpPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(AcceptHelpPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
    }

    public static AcceptHelpPacket decode(FriendlyByteBuf buf) {
        return new AcceptHelpPacket(buf.readInt());
    }

    public static void handle(AcceptHelpPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;

            ServerLevel level = sender.serverLevel();
            Entity entity = level.getEntity(pkt.entityId);

            if (entity instanceof SilentCaptiveEntity captive) {
                captive.onPlayerAcceptedHelp();
            }
        });
        ctx.setPacketHandled(true);
    }
}