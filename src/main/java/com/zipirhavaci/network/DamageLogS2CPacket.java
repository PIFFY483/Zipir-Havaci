package com.zipirhavaci.network;

import com.zipirhavaci.client.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class DamageLogS2CPacket {
    private final String message;

    public DamageLogS2CPacket(String message) {
        this.message = message;
    }

    public static void encode(DamageLogS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.message);
    }

    public static DamageLogS2CPacket decode(FriendlyByteBuf buf) {
        return new DamageLogS2CPacket(buf.readUtf());
    }

    public static void handle(DamageLogS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            HudConfig config = HudConfig.getInstance();

            // Sadece açıkken değil, mesaj boş değilse de kontrol et
            if (!config.showDamageLog || msg.message == null) return;

            config.lastDamageLog = msg.message;

            // Max 15s sınırı
            long durationMs = (long) Math.min(config.damageLogMaxSeconds, 15) * 1000L;

            // Bitiş
            config.logEndTime = System.currentTimeMillis() + durationMs;
        });
        ctx.get().setPacketHandled(true);
    }
}