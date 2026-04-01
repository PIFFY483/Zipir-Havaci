package com.zipirhavaci.network;

import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.core.capability.StaticProgressionData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncStaticProgressionPacket {
    private final int strikeCount;
    private final float auraLevel;
    private final boolean auraActive;
    private final int auraTicksLeft;
    private final boolean isCursed;
    private final long lastDeathDenyTime;
    private final float darkAuraLevel;
    private final boolean darkAuraActive;
    private final int darkAuraTicksLeft;
    private final boolean darkAuraPhase2;
    private final boolean smokeVisible;
    private final int ritualStep;
    private final int playerId;

    public SyncStaticProgressionPacket(StaticProgressionData data, int playerId) {
        this.strikeCount       = data.getStrikeCount();
        this.auraLevel         = data.getAuraLevel();
        this.auraActive        = data.isAuraActive();
        this.auraTicksLeft     = data.getAuraTicksLeft();
        this.isCursed          = data.isCursed();
        this.lastDeathDenyTime = data.getLastDeathDenyTime();
        this.darkAuraLevel     = data.getDarkAuraLevel();
        this.darkAuraActive    = data.isDarkAuraActive();
        this.darkAuraTicksLeft = data.getDarkAuraTicksLeft();
        this.darkAuraPhase2    = data.isDarkAuraPhase2();
        this.smokeVisible      = data.isSmokeVisible();
        this.ritualStep        = data.getRitualStep();
        this.playerId          = playerId;
    }

    public SyncStaticProgressionPacket(int strikeCount, float auraLevel, boolean auraActive,
                                       int auraTicksLeft, boolean isCursed, long lastDeathDenyTime,
                                       float darkAuraLevel, boolean darkAuraActive,
                                       int darkAuraTicksLeft, boolean darkAuraPhase2,
                                       boolean smokeVisible, int ritualStep, int playerId) {
        this.strikeCount       = strikeCount;
        this.auraLevel         = auraLevel;
        this.auraActive        = auraActive;
        this.auraTicksLeft     = auraTicksLeft;
        this.isCursed          = isCursed;
        this.lastDeathDenyTime = lastDeathDenyTime;
        this.darkAuraLevel     = darkAuraLevel;
        this.darkAuraActive    = darkAuraActive;
        this.darkAuraTicksLeft = darkAuraTicksLeft;
        this.darkAuraPhase2    = darkAuraPhase2;
        this.smokeVisible      = smokeVisible;
        this.ritualStep        = ritualStep;
        this.playerId          = playerId;
    }

    public static void encode(SyncStaticProgressionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.strikeCount);
        buf.writeFloat(msg.auraLevel);
        buf.writeBoolean(msg.auraActive);
        buf.writeInt(msg.auraTicksLeft);
        buf.writeBoolean(msg.isCursed);
        buf.writeLong(msg.lastDeathDenyTime);
        buf.writeFloat(msg.darkAuraLevel);
        buf.writeBoolean(msg.darkAuraActive);
        buf.writeInt(msg.darkAuraTicksLeft);
        buf.writeBoolean(msg.darkAuraPhase2);
        buf.writeBoolean(msg.smokeVisible);
        buf.writeInt(msg.ritualStep);
        buf.writeInt(msg.playerId);
    }

    public static SyncStaticProgressionPacket decode(FriendlyByteBuf buf) {
        return new SyncStaticProgressionPacket(
                buf.readInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readLong(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt()
        );
    }

    public static void handle(SyncStaticProgressionPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(msg.playerId);
                if (entity instanceof Player targetPlayer) {
                    targetPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {

                        boolean wasActive     = data.isAuraActive();
                        boolean wasDarkActive = data.isDarkAuraActive();
                        float prevLevel       = data.getAuraLevel();

                        data.setStrikeCount(msg.strikeCount);
                        data.setAuraLevel(msg.auraLevel);
                        data.setAuraActive(msg.auraActive);
                        data.setAuraTicksLeft(msg.auraTicksLeft);
                        data.setCursed(msg.isCursed);
                        data.setLastDeathDenyTime(msg.lastDeathDenyTime);
                        data.setDarkAuraLevel(msg.darkAuraLevel);
                        data.setDarkAuraActive(msg.darkAuraActive);
                        data.setDarkAuraTicksLeft(msg.darkAuraTicksLeft);
                        data.setDarkAuraPhase2(msg.darkAuraPhase2);
                        data.setSmokeVisible(msg.smokeVisible);
                        data.setRitualStep(msg.ritualStep);

                        if (wasActive && !msg.auraActive) {
                            data.setLastAuraUseTime(System.currentTimeMillis());
                            com.zipirhavaci.client.visuals.AuraHudOverlay.triggerCooldownStart();
                            if (targetPlayer == Minecraft.getInstance().player) {
                                com.zipirhavaci.client.visuals.AuraVisualEffects.spawnShockwaveBurst(targetPlayer, prevLevel);
                            }
                        }

                        if (wasDarkActive && !msg.darkAuraActive) {
                            data.setLastDarkAuraUseTime(System.currentTimeMillis());
                        }
                    });
                }
            }
        });
        context.setPacketHandled(true);
    }
}