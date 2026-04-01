package com.zipirhavaci.client.gui;

import com.zipirhavaci.core.physics.ImpactReactionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeteorCooldownOverlay {

    private static final Set<UUID> NOTIFIED_PLAYERS = new HashSet<>();

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) {

            if (!ImpactReactionHandler.knowsMeteorSkill(Minecraft.getInstance().player)) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;

            long remaining = ImpactReactionHandler.getRemainingCooldown(mc.player.getUUID());

            if (remaining > 0) {
                NOTIFIED_PLAYERS.remove(mc.player.getUUID());

                float total = 25000.0f; // LONG_COOLDOWN_MS
                float progress = 1.0f - (remaining / total);

                int screenWidth = event.getWindow().getGuiScaledWidth();
                int screenHeight = event.getWindow().getGuiScaledHeight();


                int x = screenWidth / 2 - 91; // XP barı genişliğiyle aynı (182 / 2)
                int y = screenHeight - 29;    // XP Barının tam altı, Hotbar ın üst sınırı

                GuiGraphics graphics = event.getGuiGraphics();


                graphics.fill(x, y, x + 182, y + 1, 0x66000000);

                // (Kırmızı -> Yeşil)
                int red = (progress < 0.5f) ? 255 : (int)(255 * (1.0f - progress) * 2);
                int green = (progress > 0.5f) ? 255 : (int)(255 * progress * 2);
                int color = (0xFF << 24) | (Math.min(255, red) << 16) | (Math.min(255, green) << 8);

                graphics.fill(x, y, x + (int)(182 * progress), y + 1, color);

            } else {
                if (!NOTIFIED_PLAYERS.contains(mc.player.getUUID())) {
                    // Action Bar uyarısı (Hotbar üstü)
                    mc.player.displayClientMessage(Component.literal("§6☄ §eIGNITE THE BURNING SHATTERS! §6☄"), true);
                    mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 0.6f);
                    NOTIFIED_PLAYERS.add(mc.player.getUUID());
                }
            }
        }
    }
}