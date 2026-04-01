package com.zipirhavaci.network;

import com.zipirhavaci.core.ZipirHavaci;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ZipirHavaci.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        // ===== CLIENTE GİDEN =====
        CHANNEL.registerMessage(id++,
                PlayAviatorPassiveSoundPacket.class,
                PlayAviatorPassiveSoundPacket::encode,
                PlayAviatorPassiveSoundPacket::decode,
                PlayAviatorPassiveSoundPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                TriggerAnimationPacket.class,
                TriggerAnimationPacket::encode,
                TriggerAnimationPacket::decode,
                TriggerAnimationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                SyncItemNBTPacket.class,
                SyncItemNBTPacket::encode,
                SyncItemNBTPacket::decode,
                SyncItemNBTPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // ===== SERVERA GİDEN =====
        CHANNEL.registerMessage(id++,
                FireItemPacket.class,
                FireItemPacket::encode,
                FireItemPacket::decode,
                FireItemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                ShieldLaunchPacket.class,
                ShieldLaunchPacket::encode,
                ShieldLaunchPacket::decode,
                ShieldLaunchPacket::handle);

        CHANNEL.registerMessage(id++,
                ModChangePacket.class,
                ModChangePacket::encode,
                ModChangePacket::decode,
                ModChangePacket::handle);

        CHANNEL.registerMessage(id++,
                DeployItemPacket.class,
                DeployItemPacket::encode,
                DeployItemPacket::decode,
                DeployItemPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                PlayAviatorFollowSoundPacket.class,
                PlayAviatorFollowSoundPacket::encode,
                PlayAviatorFollowSoundPacket::decode,
                PlayAviatorFollowSoundPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                Syncsoulbondpacket.class,
                Syncsoulbondpacket::encode,
                Syncsoulbondpacket::decode,
                Syncsoulbondpacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(id++,
                AuraSkillPacket.class,
                AuraSkillPacket::encode,
                AuraSkillPacket::decode,
                AuraSkillPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++,
                SyncStaticProgressionPacket.class,
                SyncStaticProgressionPacket::encode,
                SyncStaticProgressionPacket::decode,
                SyncStaticProgressionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++,
                ShieldBreakVisualPacket.class,
                ShieldBreakVisualPacket::encode,
                ShieldBreakVisualPacket::decode,
                ShieldBreakVisualPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(id++,
                AviatorSuperSkillPacket.class,
                AviatorSuperSkillPacket::encode,
                AviatorSuperSkillPacket::decode,
                AviatorSuperSkillPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(id++,
                TriggerCameraShakePacket.class,
                TriggerCameraShakePacket::encode,
                TriggerCameraShakePacket::decode,
                TriggerCameraShakePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(id++,
                PlayAviatorSoundPacket.class,
                PlayAviatorSoundPacket::encode,
                PlayAviatorSoundPacket::decode,
                PlayAviatorSoundPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // ===== DARK AURA =====
        CHANNEL.registerMessage(id++,
                ApplyCursePacket.class,
                ApplyCursePacket::encode,
                ApplyCursePacket::decode,
                ApplyCursePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(id++,
                ToggleDarkAuraPhasePacket.class,
                ToggleDarkAuraPhasePacket::encode,
                ToggleDarkAuraPhasePacket::decode,
                ToggleDarkAuraPhasePacket::handle,
                Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
        );

        // ===== SILENT CAPTIVE NPC =====
        CHANNEL.registerMessage(id++,
                OpenCaptiveDialogPacket.class,
                OpenCaptiveDialogPacket::encode,
                OpenCaptiveDialogPacket::decode,
                OpenCaptiveDialogPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(id++,
                AcceptHelpPacket.class,
                AcceptHelpPacket::encode,
                AcceptHelpPacket::decode,
                AcceptHelpPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(id++,
                ToggleDamageLogPacket.class,
                ToggleDamageLogPacket::encode,
                ToggleDamageLogPacket::decode,
                ToggleDamageLogPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(id++,
                DamageLogS2CPacket.class,
                DamageLogS2CPacket::encode,
                DamageLogS2CPacket::decode,
                DamageLogS2CPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

    }


    // ===== SEND HELPERS =====

    public static void sendToTracking(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}