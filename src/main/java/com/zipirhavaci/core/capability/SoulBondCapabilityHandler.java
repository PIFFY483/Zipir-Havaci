package com.zipirhavaci.core.capability;

import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.network.Syncsoulbondpacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID)
public class SoulBondCapabilityHandler {

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SoulBondData.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(Soulbonddataprovider.SOUL_BOND).isPresent()) {
                event.addCapability(new ResourceLocation(ZipirHavaci.MOD_ID, "soul_bond"),
                        new Soulbonddataprovider());
            }
            if (!event.getObject().getCapability(StaticProgressionProvider.STATIC_PROGRESSION).isPresent()) {
                event.addCapability(new ResourceLocation(ZipirHavaci.MOD_ID, "static_progression"),
                        new StaticProgressionProvider());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerCloned(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            // reviveCaps() olmadan getOriginal()ın capabilities i boş döner
            event.getOriginal().reviveCaps();

            event.getOriginal().getCapability(Soulbonddataprovider.SOUL_BOND).ifPresent(oldStore -> {
                event.getEntity().getCapability(Soulbonddataprovider.SOUL_BOND).ifPresent(newStore -> {
                    newStore.copyFrom(oldStore);
                });
            });


            event.getOriginal().invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToClient(player);
        }
    }

    public static void syncToClient(ServerPlayer player) {
        player.getCapability(Soulbonddataprovider.SOUL_BOND).ifPresent(data -> {
            com.zipirhavaci.network.PacketHandler.sendToPlayer(player,
                    new Syncsoulbondpacket(data.hasSoulBond()));
        });
    }
}