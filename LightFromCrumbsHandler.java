package com.zipirhavaci.core.handler;

import com.zipirhavaci.core.ZipirHavaci;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LightFromCrumbsHandler {

    private static final UUID ANIMA_HP_BONUS_ID = UUID.fromString("f470298a-211c-4f71-a9f8-4f243e887f86");
    private static final ResourceLocation ANIMA_ADV = new ResourceLocation(ZipirHavaci.MOD_ID, "light_from_crumbs");
    private static final String ANIMA_TAG = "has_anima_light"; // Performanslı kontrol etiketi

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getAdvancement().getId().equals(ANIMA_ADV)) {

                // 1. Oyuncuya etiketi yapıştır her tickte başarım aramaya gerek yok)
                if (!player.getTags().contains(ANIMA_TAG)) {
                    player.addTag(ANIMA_TAG);
                }

                // 2. Maksimum HP Artışı
                AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth != null && maxHealth.getModifier(ANIMA_HP_BONUS_ID) == null) {
                    maxHealth.addPermanentModifier(new AttributeModifier(
                            ANIMA_HP_BONUS_ID,
                            "Anima Light Bonus",
                            8.0,
                            AttributeModifier.Operation.ADDITION));
                    player.heal(8.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide() && event.player.tickCount % 400 == 0) {
            ServerPlayer player = (ServerPlayer) event.player;

            // vanilla Tag listesinde bizim etiketimiz var mı?
            if (player.getTags().contains(ANIMA_TAG)) {

                float currentExhaustion = player.getFoodData().getExhaustionLevel();

                if (currentExhaustion > 0) {

                    float reduction = Math.min(currentExhaustion, 1.2f);
                    player.getFoodData().addExhaustion(-reduction);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getTags().contains(ANIMA_TAG)) {
                AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth != null && maxHealth.getModifier(ANIMA_HP_BONUS_ID) == null) {
                    maxHealth.addPermanentModifier(new AttributeModifier(
                            ANIMA_HP_BONUS_ID, "Anima Light Bonus", 8.0, AttributeModifier.Operation.ADDITION));
                    player.setHealth(player.getHealth() + 8.0f);
                }
            }
        }
    }

}