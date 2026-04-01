package com.zipirhavaci.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayAviatorSoundPacket {
    private final int type; // 0: Mermi Dolum, 1: SuperCharge
    private final int level; // currentlyFilled veya chargeLevel
    private final float volume;
    private final float pitch;

    public PlayAviatorSoundPacket(int type, int level, float volume, float pitch) {
        this.type = type;
        this.level = level;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static void encode(PlayAviatorSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.type);
        buf.writeInt(msg.level);
        buf.writeFloat(msg.volume);
        buf.writeFloat(msg.pitch);
    }

    public static PlayAviatorSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayAviatorSoundPacket(buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(PlayAviatorSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (msg.type == 0) {
                float baseBoost = msg.volume * 1.5f;

                player.level().playSound(null, player,
                        net.minecraft.sounds.SoundEvents.CONDUIT_ACTIVATE,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        baseBoost, msg.pitch);

                player.level().playSound(null, player,
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        baseBoost * 1.1f, msg.pitch + 0.1f);

                player.level().playSound(null, player,
                        net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
                        net.minecraft.sounds.SoundSource.PLAYERS,
                        baseBoost * 0.4f, msg.pitch + 0.3f);
            }
            else if (msg.type == 1) {
                player.level().playSound(null, player, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, msg.volume, msg.pitch);
                player.level().playSound(null, player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.5f, 0.25f);

                if (msg.level == 5) {
                    player.level().playSound(null, player, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.35f, 0.12f);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}