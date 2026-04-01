package com.zipirhavaci.network;

import com.zipirhavaci.client.ClientShakeHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.Optional;
import java.util.function.Supplier;

public class TriggerCameraShakePacket {
    private final int duration;
    private final float intensity;

    public TriggerCameraShakePacket(int duration, float intensity) {
        this.duration = duration;
        this.intensity = intensity;
    }

    public static void encode(TriggerCameraShakePacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.duration);
        buffer.writeFloat(msg.intensity);
    }

    public static TriggerCameraShakePacket decode(FriendlyByteBuf buffer) {
        return new TriggerCameraShakePacket(buffer.readInt(), buffer.readFloat());
    }

    public static void handle(TriggerCameraShakePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // İstemci tarafında sarsıntıyı başlat
            ClientShakeHandler.startShake(msg.duration, msg.intensity);
        });
        ctx.get().setPacketHandled(true);
    }
}