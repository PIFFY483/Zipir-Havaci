package com.zipirhavaci.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import software.bernie.geckolib.animatable.GeoItem;
import com.zipirhavaci.item.ZipirAviatorItem;

import java.util.function.Supplier;


public class TriggerAnimationPacket {
    private final long instanceId;
    private final String controllerName;
    private final String animationName;

    public TriggerAnimationPacket(long instanceId, String controllerName, String animationName) {
        this.instanceId = instanceId;
        this.controllerName = controllerName;
        this.animationName = animationName;
    }

    public static void encode(TriggerAnimationPacket msg, FriendlyByteBuf buffer) {
        buffer.writeLong(msg.instanceId);
        buffer.writeUtf(msg.controllerName);
        buffer.writeUtf(msg.animationName);
    }

    public static TriggerAnimationPacket decode(FriendlyByteBuf buffer) {
        return new TriggerAnimationPacket(
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readUtf()
        );
    }

    public static void handle(TriggerAnimationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (stack.getItem() instanceof ZipirAviatorItem item) {
                            long stackId = GeoItem.getId(stack);
                            if (stackId == msg.instanceId) {
                                // Mevcut animasyon tetikleyici
                                item.triggerAnim(player, msg.instanceId, msg.controllerName, msg.animationName);

                                //  FOV EFEKTİ TETİKLEME
                                if (msg.animationName.contains("fire")) {
                                    int mode = stack.getOrCreateTag().getInt("AviatorMode");
                                    boolean isPush = (mode == 1);

                                    //FOVHandler' a hangi modda olduğumuzu söyle
                                    com.zipirhavaci.client.visuals.FOVHandler.bumpFOV(isPush);
                                }

                                // EFEKT SAYAÇLARI ( Client tarafında NBT)
                                if (msg.animationName.contains("fire")) {
                                    player.getPersistentData().putInt("ShotSmokeTicks", 12); // 0.6 saniye
                                } else if (msg.animationName.equals("cooldown")) {
                                    player.getPersistentData().putInt("CooldownSteamTicks", 120); // 6 saniye
                                }
                                break;
                            }
                        }
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
