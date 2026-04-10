package com.zipirhavaci.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class SyncCraterPacket {
    private final ChunkPos chunkPos;
    private final List<Long> validCraterIds;

    public SyncCraterPacket(ChunkPos chunkPos, List<Long> validCraterIds) {
        this.chunkPos = chunkPos;
        this.validCraterIds = validCraterIds;
    }

    public static void encode(SyncCraterPacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.chunkPos.x);
        buffer.writeInt(msg.chunkPos.z);
        buffer.writeInt(msg.validCraterIds.size());
        for (long id : msg.validCraterIds) {
            buffer.writeLong(id);
        }
    }

    public static SyncCraterPacket decode(FriendlyByteBuf buffer) {
        ChunkPos cp = new ChunkPos(buffer.readInt(), buffer.readInt());
        int size = buffer.readInt();
        List<Long> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buffer.readLong());
        }
        return new SyncCraterPacket(cp, ids);
    }

    public static void handle(SyncCraterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {

            Set<Long> validSet = new HashSet<>(msg.validCraterIds);

            var level = Minecraft.getInstance().level;
            if (level == null) return;

            AABB area = new AABB(
                    msg.chunkPos.getMinBlockX(), level.getMinBuildHeight(), msg.chunkPos.getMinBlockZ(),
                    msg.chunkPos.getMaxBlockX() + 1, level.getMaxBuildHeight(), msg.chunkPos.getMaxBlockZ() + 1
            );

            level.getEntitiesOfClass(Display.BlockDisplay.class, area,
                    e -> e.getTags().contains("zipir_krater_fx")
            ).forEach(display -> {

                long currentId = display.getTags().stream()
                        .filter(tag -> tag.startsWith("zipir_id_"))
                        .map(tag -> {
                            try {
                                return Long.parseLong(tag.replace("zipir_id_", ""));
                            } catch (NumberFormatException ex) {
                                return -1L;
                            }
                        })
                        .findFirst()
                        .orElse(-1L);

                if (!validSet.contains(currentId)) {
                    display.discard();
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
