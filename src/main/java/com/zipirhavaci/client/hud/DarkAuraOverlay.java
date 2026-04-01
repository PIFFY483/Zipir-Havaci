package com.zipirhavaci.client.hud;

import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.core.physics.DarkAuraHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class DarkAuraOverlay {

    public static final IGuiOverlay HUD_DEATH_DENY = (gui, guiGraphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        mc.player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            if (!data.isCursed()) return;

            float pct  = DarkAuraHandler.getDeathDenyCooldownPercent(data);
            long  time = System.currentTimeMillis();

            // ─── BOYUTLAR ────────────────────────────────────────────────────
            int barW = 4;
            int barH = 22;
            int x    = width  / 2 - 91 - barW - 5;
            int y    = height - 22 - 2;

            // ─── RENK HESABI ─────────────────────────
            int r = (int) lerp(0x00, 0x55, pct);
            int g = (int) lerp(0x00, 0x00, pct);  // hep 0
            int b = (int) lerp(0x00, 0x99, pct);
            int fillRgb   = (r << 16) | (g << 8) | b;

            int er = Math.min(255, r + 0x22);
            int eb = Math.min(255, b + 0x44);
            int edgeRgb = (er << 16) | eb;

            // ─── ARKAPLAN ────────────────────────────────────────────────────
            guiGraphics.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x55000000);
            guiGraphics.fill(x, y, x + barW, y + barH, 0xFF0A0A0F);

            // ─── DOLU KISIM ─────────────────────────────
            int filled = (int) (barH * pct);
            int fillTop = y + (barH - filled);

            if (pct >= 1.0f) {
                // ── HAZIR ──────────────────
                guiGraphics.fill(x, y, x + barW, y + barH, 0xFF | (fillRgb));

                float flow    = (time % 1200) / 1200.0f;          // 0.0 → 1.0
                int   dotY    = y + barH - (int) (barH * flow);   // alttan yukarıya
                int   dotAlpha = (int) (180 + 75 * Math.abs(Math.sin(time / 200.0)));

                guiGraphics.fill(x, dotY,     x + barW, dotY + 1, (dotAlpha << 24) | 0x00CCAAFF);
                if (dotY - 1 >= y)
                    guiGraphics.fill(x, dotY - 1, x + barW, dotY, (dotAlpha / 3 << 24) | 0x00CCAAFF);

            } else if (filled > 0) {
                // ── DOLUYOR ─────────────
                guiGraphics.fill(x, fillTop, x + barW, y + barH, 0xFF000000 | fillRgb);

                // Üst 1px parlama
                guiGraphics.fill(x, fillTop, x + barW, fillTop + 1, 0xFF000000 | edgeRgb);
            }

            // ─── SEGMENT ÇİZGİLERİ ───────────────────────────

            for (int s = 1; s < 5; s++) {
                float segPct  = s / 5.0f;
                int   segY    = y + barH - (int) (barH * segPct);
                boolean lit   = pct >= segPct;
                int segColor  = lit ? (0xAA000000 | edgeRgb) : 0x44AAAAAA;
                guiGraphics.fill(x, segY, x + barW, segY + 1, segColor);
            }

            // ─── KENAR ───────────────────────────────────────────────────────
            int borderAlpha = (int) (80 + 120 * pct);
            int borderColor = (borderAlpha << 24) | edgeRgb;

            guiGraphics.fill(x - 1,       y - 1,       x + barW + 1, y,           borderColor);
            guiGraphics.fill(x - 1,       y + barH,    x + barW + 1, y + barH + 1, borderColor);
            guiGraphics.fill(x - 1,       y,           x,            y + barH,    borderColor);
            guiGraphics.fill(x + barW,    y,           x + barW + 1, y + barH,    borderColor);
        });
    };

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.min(1.0f, Math.max(0.0f, t));
    }
}