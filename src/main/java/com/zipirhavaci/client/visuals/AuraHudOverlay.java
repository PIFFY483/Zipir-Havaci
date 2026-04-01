package com.zipirhavaci.client.visuals;

import com.zipirhavaci.client.config.HudConfig;
import com.zipirhavaci.core.capability.StaticProgressionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class AuraHudOverlay {

    private static boolean cachedActive = false;
    private static float   cachedLevel  = 0f;
    private static int     cachedTicks  = 0;
    private static float   cachedRatio  = 0f;
    private static long    cachedLastUseTime = 0L;

    private static boolean cachedDarkActive = false;
    private static float   cachedDarkLevel  = 0f;
    private static int     cachedDarkTicks  = 0;
    private static float   cachedDarkRatio  = 0f;

    private static float darkCooldownAlpha = 0f;
    private static long cachedDarkLastUseTime = 0L;

    private static boolean isCooldownTriggered = false;
    private static boolean cachedDarkPhase2 = false;

    public static void triggerCooldownStart() {
        isCooldownTriggered = true;
    }

    private static float smoothRatio     = 0f;
    private static float smoothAlpha     = 0f;
    private static float cooldownAlpha   = 0f;
    private static float darkSmoothRatio = 0f;
    private static float darkSmoothAlpha = 0f;

    public static void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {
            // --- Normal Aura Verileri ---
            cachedActive      = data.isAuraActive();
            cachedLevel       = data.getAuraLevel();
            cachedTicks       = data.getAuraTicksLeft();
            cachedLastUseTime = data.getLastAuraUseTime();

            if (cachedActive && cachedTicks <= 0) {
                cachedActive = false;
            }

            if (cachedActive || (System.currentTimeMillis() - cachedLastUseTime >= getCooldownForLevel(cachedLevel))) {
                isCooldownTriggered = false;
            }

            float max;
            if (cachedLevel >= 3.0f)      max = 600f;
            else if (cachedLevel >= 2.0f) max = 520f;
            else if (cachedLevel >= 1.0f) max = 480f;
            else                          max = 360f;
            cachedRatio = Math.max(0f, Math.min(1f, (float)cachedTicks / max));

            cachedDarkActive      = data.isDarkAuraActive();
            cachedDarkLevel       = data.getDarkAuraLevel();
            cachedDarkTicks       = data.getDarkAuraTicksLeft();
            cachedDarkLastUseTime  = data.getLastDarkAuraUseTime();
            cachedDarkPhase2      = data.isDarkAuraPhase2();

            float maxDarkDuration = com.zipirhavaci.core.physics.DarkAuraHandler.getDarkDuration(cachedDarkLevel);
            cachedDarkRatio = Math.max(0f, Math.min(1f, (float)cachedDarkTicks / maxDarkDuration));
        });
    }



    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) return;

        HudConfig config = HudConfig.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        long now = System.currentTimeMillis();

        // ==========================================
        // 1. AKTİF SÜRE BARLARI (ORTA HUD)
        // ==========================================

        if (config.activeAuraHud.enabled && cachedActive && cachedTicks > 0) {
            smoothAlpha = Math.min(1f, smoothAlpha + 0.1f);
        } else {
            smoothAlpha = Math.max(0f, smoothAlpha - 0.05f);
        }
        if (smoothAlpha > 0) {
            smoothRatio += (cachedRatio - smoothRatio) * 0.15f;
            float finalAlpha = smoothAlpha;
            if (cachedActive && cachedRatio < 0.3f) {
                float pulse = Mth.sin((float)mc.level.getGameTime() * 0.8f) * 0.4f + 0.6f;
                finalAlpha *= pulse;
            }
            int color = getDynamicColor(cachedLevel, smoothRatio, (int)(finalAlpha * 255));
            drawActiveDuration(graphics, mc, finalAlpha, color);
        }

        if (cachedDarkActive && cachedDarkTicks > 0) {
            darkSmoothAlpha = Math.min(1f, darkSmoothAlpha + 0.1f);
        } else {
            darkSmoothAlpha = Math.max(0f, darkSmoothAlpha - 0.05f);
        }

        if (darkSmoothAlpha > 0) {

            float followSpeed = cachedDarkPhase2 ? 0.45f : 0.15f;
            darkSmoothRatio += (cachedDarkRatio - darkSmoothRatio) * followSpeed;

            float finalDarkAlpha = darkSmoothAlpha;
            if (cachedDarkPhase2) {
                float flicker = Mth.sin((float)mc.level.getGameTime() * 1.2f) * 0.15f + 0.85f;
                finalDarkAlpha *= flicker;
            }

            int darkColor = getDarkAuraColor(cachedDarkLevel, darkSmoothRatio, (int)(finalDarkAlpha * 255));
            drawDarkAuraDuration(graphics, mc, finalDarkAlpha, darkColor);
        }

        // ==========================================
        // 2. ENTEGRE COOLDOWN HUD (SAĞ HUD)
        // ==========================================

        long normCdMs = Math.max(1L, getCooldownForLevel(cachedLevel));
        long normElapsed = now - cachedLastUseTime;
        boolean isNormCdActive = (!cachedActive && cachedLastUseTime > 0 && normElapsed < normCdMs);

        long darkCdMs = Math.max(1L, com.zipirhavaci.core.physics.DarkAuraHandler.getDarkCooldownTicks(cachedDarkLevel) * 50L);
        long darkElapsed = now - cachedDarkLastUseTime;
        boolean isDarkCdActive = (!cachedDarkActive && cachedDarkLastUseTime > 0 && darkElapsed < darkCdMs);

        if (config.cooldownHud.enabled) {
            int yOffset = 0;

            // --- Normal Aura Cooldown ---
            if (isNormCdActive || isCooldownTriggered) {
                cooldownAlpha = Math.min(0.8f, cooldownAlpha + 0.15f);
            } else {
                cooldownAlpha = Math.max(0f, cooldownAlpha - 0.05f);
            }

            if (cooldownAlpha > 0.01f) {
                long currentRemaining = Math.max(0, normCdMs - normElapsed);
                float progress = (float) (normCdMs - currentRemaining) / normCdMs;
                int color = getCooldownDynamicColor(cachedLevel, progress, (int)(cooldownAlpha * 255));
                drawCooldown(graphics, mc, currentRemaining, progress, color, cooldownAlpha, yOffset, false);

                yOffset += (int)((config.cooldownHeight + 8) * (cooldownAlpha / 0.8f));
            }

            // --- Karanlık Aura Cooldown ---
            if (isDarkCdActive) {
                darkCooldownAlpha = Math.min(0.8f, darkCooldownAlpha + 0.15f);
            } else {
                darkCooldownAlpha = Math.max(0f, darkCooldownAlpha - 0.05f);
            }

            if (darkCooldownAlpha > 0.01f) {
                long currentRemaining = Math.max(0, darkCdMs - darkElapsed);
                float progress = (float) (darkCdMs - currentRemaining) / darkCdMs;
                int color = getDarkAuraColor(cachedDarkLevel, progress, (int)(darkCooldownAlpha * 255));
                drawCooldown(graphics, mc, currentRemaining, progress, color, darkCooldownAlpha, yOffset, true);
            }
        }

        if (cachedDarkActive && cachedDarkPhase2 && cachedDarkTicks > 0) {
            renderHealthPreview(graphics, mc);
        }

    }

    private static void drawActiveDuration(GuiGraphics gfx, Minecraft mc, float alpha, int color) {
        HudConfig config = HudConfig.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int barW = config.activeAuraWidth;
        int barH = config.activeAuraHeight;
        int barX = config.activeAuraHud.getActualX(sw, barW);
        int barY = config.activeAuraHud.getActualY(sh);
        int aVal = (int)(alpha * 255);

        // Işık Saçan Çerçeve
        int borderColor = (cachedRatio < 0.3f)
                ? (aVal << 24) | (200 << 16) | (50 << 8) | 50
                : (aVal / 2 << 24) | 0x000000;

        gfx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, borderColor);

        // Yanıp Sönme Efekti (Pulse)
        int finalColor = color;
        if (cachedRatio < 0.3f) {
            float pulse = Mth.sin((float)mc.level.getGameTime() * 0.8f) * 0.4f + 0.6f;
            int r = (int)(((color >> 16) & 0xFF) * pulse);
            int g = (int)(((color >> 8) & 0xFF) * pulse);
            int b = (int)((color & 0xFF) * pulse);
            finalColor = (aVal << 24) | (r << 16) | (g << 8) | b;
        }

        // Bar Çizimi
        int fillW = (int)(barW * smoothRatio);
        if (fillW > 0) {
            gfx.fill(barX, barY, barX + fillW, barY + barH, finalColor);
        }

        // Yazı
        if (barW >= 40) {
            String txt = getLevelName(cachedLevel);
            float textScale = Math.min(0.8f, barW / 140.0f);
            gfx.pose().pushPose();
            gfx.pose().translate(barX, barY - (10 * textScale), 0);
            gfx.pose().scale(textScale, textScale, 1.0f);
            gfx.drawString(mc.font, txt, 0, 0, (aVal << 24) | 0xFFFFFF, true);
            gfx.pose().popPose();
        }
    }

    private static void drawCooldown(GuiGraphics gfx, Minecraft mc, long remainingMs, float progress, int color, float alpha, int yOffset, boolean isDark) {
        if (alpha <= 0.01f) return;
        HudConfig config = HudConfig.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int barW = config.cooldownWidth;
        int barH = isDark ? (config.cooldownHeight / 2) : config.cooldownHeight;

        int barX = config.cooldownHud.getActualX(sw, barW);
        int barY = config.cooldownHud.getActualY(sh) + yOffset;
        int aVal = (int)(alpha * 255);

        // Arka Plan
        int bgColor = isDark ? ((aVal / 2 << 24) | 0x100010) : ((aVal / 3 << 24) | 0x000000);
        gfx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, bgColor);

        // Çizim
        int fillW = (int)(barW * progress);
        if (fillW > 0) {
            gfx.fill(barX, barY, barX + fillW, barY + barH, color);
        }

        // Yazı
        if (barW >= 40) {
            String label = (isDark ? "Empowering: " : "") + formatTime((int)Math.ceil(remainingMs / 1000.0));
            float textScale = isDark ? 0.6f : Math.min(0.8f, barW / 140.0f);
            int textYOffset = isDark ? -7 : (int)-(10 * textScale);
            int textColor = isDark ? ((aVal << 24) | 0xAAAAAA) : ((aVal << 24) | 0xFFFFFF);

            gfx.pose().pushPose();
            gfx.pose().translate(barX, barY + textYOffset, 0);
            gfx.pose().scale(textScale, textScale, 1.0f);
            gfx.drawString(mc.font, label, 0, 0, textColor, true);
            gfx.pose().popPose();
        }
    }

    private static String formatTime(int totalSeconds) {
        if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return totalSeconds + "s";
        }
    }

    private static long getCooldownForLevel(float level) {
        if (level >= 3.0f) return 130000; // 2 dakika 10 saniye
        if (level >= 2.0f) return 130000; // 2 dakika 10 saniye
        if (level >= 1.0f) return 115000; // 1 dakika 55 saniye
        return 90000; // 1 dakika 30 saniye (0.5 seviye)
    }

    private static int getDynamicColor(float lvl, float ratio, int a) {
        int targetR, targetG, targetB;

        if (lvl >= 3.0f) {
            targetR = 0; targetG = 0; targetB = 139;
        } else if (lvl >= 2.0f) {
            targetR = 0; targetG = 255; targetB = 255;
        } else if (lvl >= 1.0f) {
            targetR = 255; targetG = 215; targetB = 0;
        } else {
            targetR = 0; targetG = 100; targetB = 255;
        }

        int r = (int) Mth.lerp(ratio, 255, targetR);
        int g = (int) Mth.lerp(ratio, 0, targetG);
        int b = (int) Mth.lerp(ratio, 0, targetB);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int getCooldownDynamicColor(float lvl, float ratio, int a) {
        int targetR, targetG, targetB;

        if (lvl >= 3.0f) { targetR = 0; targetG = 0; targetB = 139; }
        else if (lvl >= 2.0f) { targetR = 0; targetG = 255; targetB = 255; }
        else if (lvl >= 1.0f) { targetR = 255; targetG = 215; targetB = 0; }
        else { targetR = 0; targetG = 100; targetB = 255; }

        int r = (int) Mth.lerp(ratio, 255, targetR);
        int g = (int) Mth.lerp(ratio, 0, targetG);
        int b = (int) Mth.lerp(ratio, 0, targetB);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String getLevelName(float lvl) {
        if (lvl >= 3.0f) return "§dGRANDMASTER PRESENCE";
        if (lvl >= 2.0f) return "§6ASCENDED TRANCE";
        return "§bPRIMAL STATIC";
    }

    // ─── KARANLIK AURA HUD ÇİZİMİ ─────────────────────────────────────────────
    private static void drawDarkAuraDuration(GuiGraphics gfx, Minecraft mc, float alpha, int color) {
        HudConfig config = HudConfig.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int barW = config.activeAuraWidth;
        int barH = config.activeAuraHeight;
        int barX = config.activeAuraHud.getActualX(sw, barW);
        int barY = config.activeAuraHud.getActualY(sh) + barH + 4;
        int aVal = (int)(alpha * 255);

        // Çerçeve
        gfx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1,
                (aVal / 2 << 24) | 0x1A001A);

        // Bar
        int fillW = (int)(barW * darkSmoothRatio);
        if (fillW > 0) {
            gfx.fill(barX, barY, barX + fillW, barY + barH, color);
        }

        // Yazı
        if (barW >= 40) {
            String txt = getDarkLevelName(cachedDarkLevel);
            float textScale = Math.min(0.8f, barW / 140.0f);
            gfx.pose().pushPose();
            gfx.pose().translate(barX, barY - (10 * textScale), 0);
            gfx.pose().scale(textScale, textScale, 1.0f);
            gfx.drawString(mc.font, txt, 0, 0, (aVal << 24) | 0xFFFFFF, true);
            gfx.pose().popPose();
        }
    }

    // Karanlık aura rengi: mor-siyah nabız
    private static int getDarkAuraColor(float lvl, float ratio, int a) {

        float levelRatio = Mth.clamp((lvl - 0.5f) / 2.5f, 0f, 1f);

        // Seviye arttıkça ana renkleri (80 ve 120) 10 ve 20'ye kadar düşürürüz
        int baseR = (int)Mth.lerp(levelRatio, 80, 10);
        int baseB = (int)Mth.lerp(levelRatio, 120, 20);

        int r = (int)(ratio * baseR);
        int g = (int)(ratio * 5);
        int b = (int)(ratio * baseB);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String getDarkLevelName(float lvl) {
        if (lvl >= 3.0f) return "§5VOIDMASTER’S TYRANNY";
        if (lvl >= 2.0f) return "§8ASCENDED ANATHEMA";
        if (lvl >= 1.0f) return "§8DORMANT CORRUPTION";
        return "§8VESTIGIAL MALEDICTION";
    }

    private static void renderHealthPreview(GuiGraphics gfx, Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        // --- MANTIK HESAPLAMA ---
        float seconds = cachedDarkTicks / 20f;
        float modifier = Mth.lerp((cachedDarkLevel - 0.5f) / 2.5f, 0.15f, 0.25f);
        float previewHeal = seconds * 0.5f * modifier;

        if (previewHeal <= 0.1f) return;

        // --- KOORDİNATLAR ---
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int left = sw / 2 - 91;
        int top = sh - 39;

        float currentHealth = mc.player.getHealth();
        float totalPreviewHealth = currentHealth + previewHeal;
        float maxHealth = mc.player.getMaxHealth();

        // Pulse efekti
        float pulseAlpha = Mth.sin((float)mc.level.getGameTime() * 0.4f) * 0.25f + 0.5f;

        net.minecraft.resources.ResourceLocation ICONS = new net.minecraft.resources.ResourceLocation("minecraft", "textures/gui/icons.png");

        gfx.pose().pushPose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(0.4f, 1.0f, 0.5f, pulseAlpha);

        for (int i = 0; i < Math.ceil(maxHealth / 2f); i++) {
            int heartX = left + i * 8;
            int heartY = top;

            if (i >= 10) {
                heartX = left + (i % 10) * 8;
                heartY -= 10;
            }

            float heartThreshold = (i + 1) * 2;

            // Mevcut canın bittiği yerden itibaren hayalet kalpleri çiz
            if (heartThreshold > currentHealth && heartThreshold <= Math.ceil(totalPreviewHealth)) {
                if (totalPreviewHealth >= heartThreshold) {
                    // Tam Ghost Kalp (Dolu kalp dokusu: x=16, y=0, w=9, h=9)
                    gfx.blit(ICONS, heartX, heartY, 16, 0, 9, 9);
                } else if (totalPreviewHealth > heartThreshold - 1) {
                    // Yarım Ghost Kalp (Yarım kalp dokusu: x=25, y=0, w=9, h=9)
                    gfx.blit(ICONS, heartX, heartY, 25, 0, 9, 9);
                }
            }
        }

        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        gfx.pose().popPose();
    }

}