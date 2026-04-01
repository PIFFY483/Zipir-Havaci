package com.zipirhavaci.network;

import com.zipirhavaci.physics.MovementHandler;
import com.zipirhavaci.item.ZipirAviatorItem;
import com.zipirhavaci.core.AviatorMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class AviatorSuperSkillPacket {
    private final int chargeLevel; // 1-5
    private final boolean isTPV;   // true = TPV → tpv_fire, false = FPV → fire

    public AviatorSuperSkillPacket(int chargeLevel, boolean isTPV) {
        this.chargeLevel = chargeLevel;
        this.isTPV = isTPV;
    }

    public static void encode(AviatorSuperSkillPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.chargeLevel);
        buf.writeBoolean(msg.isTPV);
    }

    public static AviatorSuperSkillPacket decode(FriendlyByteBuf buf) {
        return new AviatorSuperSkillPacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(AviatorSuperSkillPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof ZipirAviatorItem)) return;

            int modeInt = stack.getOrCreateTag().getInt("AviatorMode");
            AviatorMode mode = modeInt == 0 ? AviatorMode.PULL : AviatorMode.PUSH;

            MovementHandler.executeSuperSkill(player, msg.chargeLevel, mode, stack, msg.isTPV);
        });
        ctx.get().setPacketHandled(true);
    }
}