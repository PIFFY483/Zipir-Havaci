package com.zipirhavaci.network;

import com.zipirhavaci.client.visuals.AuraVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public class ShieldBreakVisualPacket {

    private final int targetId;
    private final float level;

    public ShieldBreakVisualPacket(int targetId, float level) {
        this.targetId = targetId;
        this.level = level;
    }

    public static void encode(ShieldBreakVisualPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetId);
        buf.writeFloat(msg.level);
    }

    public static ShieldBreakVisualPacket decode(FriendlyByteBuf buf) {
        return new ShieldBreakVisualPacket(buf.readInt(), buf.readFloat());
    }

    public static void handle(ShieldBreakVisualPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level == null) return;

            net.minecraft.world.entity.Entity entity =
                    Minecraft.getInstance().level.getEntity(msg.targetId);

            if (entity instanceof Player target) {
                AuraVisualEffects.spawnShieldBreakBurst(target, msg.level);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}