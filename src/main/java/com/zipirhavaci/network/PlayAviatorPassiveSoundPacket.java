package com.zipirhavaci.network;

import com.zipirhavaci.client.sound.AviatorPassiveSound;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayAviatorPassiveSoundPacket {

    private final int entityId;

    public PlayAviatorPassiveSoundPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(PlayAviatorPassiveSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static PlayAviatorPassiveSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayAviatorPassiveSoundPacket(buf.readInt());
    }

    public static void handle(PlayAviatorPassiveSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            Entity e = mc.level.getEntity(msg.entityId);
            if (e instanceof Player player) {
                mc.getSoundManager().play(new AviatorPassiveSound(player));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
