package com.zipirhavaci.client.gui;

import com.zipirhavaci.client.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

@Mod.EventBusSubscriber(modid = "zipirhavaci", value = Dist.CLIENT)
public class DamageLogOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        HudConfig cfg = HudConfig.getInstance();

        // 1. Güvenlik ve Durum Kontrolleri
        if (mc.player == null || mc.options.hideGui) return;

        // Editor açıksa gerçek logu çizme
        if (mc.screen instanceof HudEditorScreen) return;

        // 2. Zaman Kontrolü
        if (cfg.showDamageLog && System.currentTimeMillis() < cfg.logEndTime) {
            GuiGraphics gfx = event.getGuiGraphics();

            int x = cfg.damageLogHud.getActualX(gfx.guiWidth(), cfg.damageLogWidth);
            int y = cfg.damageLogHud.getActualY(gfx.guiHeight());

            gfx.pose().pushPose();
            gfx.pose().translate(x + 3, y + 3, 0);

            float scale = 1.0f;
            gfx.pose().scale(scale, scale, 1.0f);

            // Kutu genişliğine göre yazıyı alt satıra kaydır (Word Wrap)
            int wrapWidth = (int) ((cfg.damageLogWidth - 6) / scale);

            gfx.drawWordWrap(mc.font, Component.literal(cfg.lastDamageLog), 0, 0, wrapWidth, 0xFFFFFF);

            gfx.pose().popPose();
        }
    }
}