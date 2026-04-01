package com.zipirhavaci.network;

import com.zipirhavaci.item.ZipirAviatorItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.function.Supplier;

public class ModChangePacket {
    private final boolean isFirstPerson; // Kamera durumunu tutan değişken

    // Constructor güncellendi
    public ModChangePacket(boolean isFirstPerson) {
        this.isFirstPerson = isFirstPerson;
    }

    // Veriyi buffer'a yaz
    public static void encode(ModChangePacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.isFirstPerson);
    }

    // Veriyi buffer'dan oku
    public static ModChangePacket decode(FriendlyByteBuf buffer) {
        return new ModChangePacket(buffer.readBoolean());
    }

    public static void handle(ModChangePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack aviator = player.getInventory().items.stream()
                    .filter(s -> s.getItem() instanceof ZipirAviatorItem)
                    .findFirst().orElse(ItemStack.EMPTY);

            if (!aviator.isEmpty()) {
                CompoundTag nbt = aviator.getOrCreateTag();
                int currentMode = nbt.getInt("AviatorMode");
                int newMode = (currentMode == 0) ? 1 : 0; // PULL -> PUSH veya tersi

                // 1. Modu Güncelle ve Arıza Dumanını SIFIRLA
                nbt.putInt("AviatorMode", newMode);
                nbt.putInt("ZipirOverloadTimer", 0);

                // 2.  Mesaj Gönder (ActionBar)
                String modeKey = newMode == 0 ? "§bPULL (ÇEKME)" : "§6PUSH (İTME)";
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Mod: " + modeKey), true);

                // 3. TPV BARAJI
                if (msg.isFirstPerson) {
                    long instanceId = GeoItem.getOrAssignId(aviator, player.serverLevel());
                    PacketHandler.sendToTracking(player, new TriggerAnimationPacket(instanceId, "main_controller", "deploy"));
                }

                // 4. Clientı zorla senkronize et (Dumanın anında kesilmesi için)
                int slot = player.getInventory().findSlotMatchingItem(aviator);
                if (slot != -1) {
                    PacketHandler.sendToPlayer(player, new SyncItemNBTPacket(slot, nbt.copy()));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}