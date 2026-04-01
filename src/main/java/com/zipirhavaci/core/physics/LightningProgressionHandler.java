package com.zipirhavaci.core.physics;

import com.zipirhavaci.core.ZipirHavaci;
import com.zipirhavaci.core.capability.StaticProgressionData;
import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.SyncStaticProgressionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LightningProgressionHandler {


    public static void handleLevelUpLogic(ServerPlayer player, StaticProgressionData data) {

        if (data.getAuraLevel() >= 3.0f) {
            return;
        }

        // 2. STRIKE EKLEME (Sadece usta değilse çalışır)
        data.addStrike();
        int currentCount = data.getStrikeCount();
        float currentLevel = data.getAuraLevel();

        // 3. SEVİYE ATLATMA MANTIĞI
        if (currentLevel == 0.0f && currentCount >= 5) {
            applyLevelUp(player, data, 0.5f, 0.06f, "§bDIVINE AWAKENING... §fThe light finds its vessel.");
        } else if (currentLevel == 0.5f && currentCount >= 10) {
            applyLevelUp(player, data, 1.0f, 0.12f, "§6AURIC SOVEREIGNTY ACHIEVED... §fThou art crowned in solar radiance.");
        } else if (currentLevel == 1.0f && currentCount >= 40) {
            applyLevelUp(player, data, 2.0f, 0.18f, "§6SAGE’S TRANSCENDENCE ASCENDED... §fThe static harmony is absolute.");
        } else if (currentLevel == 2.0f && currentCount >= 80) {
            // USTA SEVİYESİNE GEÇİŞ
            applyLevelUp(player, data, 3.0f, 0.24f, "§b§lDOMAIN OF THE ARCHON: §fTHOU HAST TRANSCENDED THE MORTAL VEIL!");

            // Gereksiz veriyi temizle/sabitle
            data.setStrikeCount(80);
        }
    }

    private static void applyLevelUp(ServerPlayer player, StaticProgressionData data, float level, float resist, String msg) {
        data.setAuraLevel(level);
        data.setStrikeCount(0); // Her seviye geçişinde sayaç sıfırlanır
        data.setLightningResist(resist);
        player.sendSystemMessage(Component.literal(msg));

    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && !event.getLevel().isClientSide()) {
            if (event.getPlacedBlock().getBlock() == Blocks.LIGHTNING_ROD) {
                player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
                    // Paratoner koordinatlarını ve zamanı kaydet
                    data.setLastRodPos(event.getPos());
                    data.setTrainingStartTime(System.currentTimeMillis());
                    player.sendSystemMessage(Component.literal("§eCONCENTRATION INITIATED... §fENDURE THE CELESTIAL WRATH FOR TWO CYCLES!"));
                });
            }
        }
    }

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {

                // KONTROL: Lanetliyse yıldırım progression ı tamamen durdur.
                if (data.isCursed()) {
                    return;
                }

                //  KONTROL: Eğer oyuncu Usta ise direkt metodu bitir. Sessiz mod.
                if (data.getAuraLevel() >= 3.0f) {
                    return;
                }

                long currentTime = System.currentTimeMillis();
                long lastStrikeTime = data.getTrainingStartTime();
                BlockPos rodPos = data.getLastRodPos();

                //  KONTROL: Aktif bir eğitim seansı var mı?

                boolean isTrainingActive = rodPos != null && (currentTime - lastStrikeTime) <= 7200000;

                if (!isTrainingActive) {
                    return;
                }

                //  KONTROL: Yakınlık kontrolü
                if (player.blockPosition().closerThan(rodPos, 10)) {

                    //  COOLDOWN KONTROLÜ: Saniyede birden fazla yıldırım sayma (Spam engelleme)
                    if (currentTime - lastStrikeTime < 1000) {
                        return;
                    }

                    // --- HER ŞEY TAMAM ---

                    data.setTrainingStartTime(currentTime);

                    handleLevelUpLogic(player, data);

                    PacketHandler.sendToTracking(player, new SyncStaticProgressionPacket(data, player.getId()));

                    if (data.getAuraLevel() < 3.0f) {
                        player.sendSystemMessage(Component.literal("§bAETHERIC ASSIMILATION COMPLETE... §fCurrent Attunement: §b" + data.getStrikeCount()));
                    }
                }
            });
        }
    }

    public static void summonLightningOnPlayer(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.entity.LightningBolt lightningbolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(player.level());
        if (lightningbolt != null) {
            lightningbolt.moveTo(player.getX(), player.getY(), player.getZ());
            lightningbolt.setVisualOnly(true);
            player.level().addFreshEntity(lightningbolt);
        }
    }

}