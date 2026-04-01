package com.zipirhavaci.network;

import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.core.physics.DarkAuraHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ApplyCursePacket {
    public ApplyCursePacket() {}
    public static void encode(ApplyCursePacket msg, FriendlyByteBuf buf) {}
    public static ApplyCursePacket decode(FriendlyByteBuf buf) { return new ApplyCursePacket(); }

    public static void handle(ApplyCursePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {

                if (data.isCursed()) return;

                boolean itemConsumed = false;
                if (player.getAbilities().instabuild) {
                    itemConsumed = true;
                } else {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack.getItem() instanceof com.zipirhavaci.item.CursedBookItem) {
                            stack.shrink(1);
                            itemConsumed = true;
                            break;
                        }
                    }
                }

                if (itemConsumed) {
                    DarkAuraHandler.applyCurse(player);

                    if (player.level() instanceof ServerLevel serverLevel) {
                        for (int i = 0; i < 45; i++) {
                            double px = player.getX() + (serverLevel.random.nextDouble() - 0.5) * 1.8;
                            double py = player.getY() + serverLevel.random.nextDouble() * 2.0;
                            double pz = player.getZ() + (serverLevel.random.nextDouble() - 0.5) * 1.8;

                            serverLevel.sendParticles(ParticleTypes.SOUL, px, py, pz, 1, 0, 0.02, 0, 0.02);

                            serverLevel.sendParticles(ParticleTypes.WITCH, px, py, pz, 1, 0, 0.01, 0, 0.01);
                        }
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}