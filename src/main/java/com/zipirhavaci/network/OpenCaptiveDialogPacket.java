package com.zipirhavaci.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class OpenCaptiveDialogPacket {

    private final int entityId;

    public OpenCaptiveDialogPacket(int entityId) {
        this.entityId = entityId;
    }

    public int getEntityId() { return entityId; }

    public static void encode(OpenCaptiveDialogPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
    }

    public static OpenCaptiveDialogPacket decode(FriendlyByteBuf buf) {
        return new OpenCaptiveDialogPacket(buf.readInt());
    }

    public static void handle(OpenCaptiveDialogPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> handleClient(pkt.entityId));
        ctx.setPacketHandled(true);
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static void handleClient(int entityId) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.zipirhavaci.client.gui.SilentCaptiveScreen(entityId)
        );
    }
}