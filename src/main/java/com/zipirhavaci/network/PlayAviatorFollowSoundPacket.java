package com.zipirhavaci.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import com.zipirhavaci.core.SoundRegistry;
import java.util.function.Supplier;

public class PlayAviatorFollowSoundPacket {
    private final int entityId;
    private final float pitch;

    public PlayAviatorFollowSoundPacket(int entityId, float pitch) {
        this.entityId = entityId;
        this.pitch = pitch;
    }

    public static void encode(PlayAviatorFollowSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeFloat(msg.pitch);
    }

    public static PlayAviatorFollowSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayAviatorFollowSoundPacket(buf.readInt(), buf.readFloat());
    }

    public static void handle(PlayAviatorFollowSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                net.minecraft.world.entity.Entity e = mc.level.getEntity(msg.entityId);
                if (e instanceof net.minecraft.world.entity.player.Player player) {
                    // Pitch 1.0f'den küçükse (0.75f gibi) bu bir arıza sesidir ve 2 saniye sonra kesilir.
                    boolean shouldLimit = msg.pitch < 1.0F;
                    mc.getSoundManager().play(new com.zipirhavaci.client.sound.AviatorFollowSound(player, com.zipirhavaci.core.SoundRegistry.COOLDOWN_SOUND.get(), msg.pitch, shouldLimit));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}