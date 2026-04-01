package com.zipirhavaci.network;

import com.zipirhavaci.client.KeyInputHandlerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class Syncsoulbondpacket {

    private final boolean success;

    public Syncsoulbondpacket() {
        this.success = true;
    }

    public Syncsoulbondpacket(boolean success) {
        this.success = success;
    }

    // VERİYİ YAZMA (Sunucuda çalışır)
    public static void encode(Syncsoulbondpacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.success);
    }

    // VERİYİ OKUMA (İstemcide çalışır)
    public static Syncsoulbondpacket decode(FriendlyByteBuf buffer) {
        return new Syncsoulbondpacket(buffer.readBoolean());
    }

    // İŞLEME (İstemcide çalışır)
    public static void handle(Syncsoulbondpacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            System.out.println("[ZipirHavaci] Sunucu onayı ulaştı.");

            // SADECE success TRUE İSE ÖĞREN
            if (msg.success) {
                net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) {
                    player.getCapability(com.zipirhavaci.core.capability.Soulbonddataprovider.SOUL_BOND).ifPresent(data -> {
                        data.learnSoulBond();
                    });
                }
            }

            KeyInputHandlerEvents.resetCooldown();
        });
        ctx.get().setPacketHandled(true);
    }
}