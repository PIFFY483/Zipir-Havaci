package com.zipirhavaci.client.gui;

import com.zipirhavaci.client.config.HudConfig;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.ToggleDamageLogPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;


public class HudEditorScreen extends Screen {

    private final HudConfig config;

    // Dragging state
    private DraggableHud dragging = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // Resize state
    private DraggableHud resizing = null;
    private int resizeStartWidth = 0;
    private int resizeStartHeight = 0;
    private int resizeStartMouseX = 0;
    private int resizeStartMouseY = 0;

    int buttonX = 10; // Ekranın sol tarafı
    int buttonY = 10;
    int btnWidth = 100;
    int btnHeight = 20;
    int sliderY = buttonY + (btnHeight + 10) * 2;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
        this.config = HudConfig.getInstance();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {

        gfx.fill(0, 0, this.width, this.height, 0x80000000); // Yarı saydam arka plan

        gfx.drawCenteredString(this.font, "§e§lHUD EDITOR", this.width / 2, 10, 0xFFFFFF);
        gfx.drawCenteredString(this.font, "§7LMB: Drag | RMB: Resize | ESC: Save", this.width / 2, 22, 0xAAAAAA);


        drawGrid(gfx);

        // BUTONLAR VE SLIDER KONTROLLERİ
        int btnX = 10;
        int btnY = 10;
        int btnW = 110;
        int btnH = 22;
        int spacing = 5;


        int statusColor = config.waterBubblesEnabled ? 0xFF4CAF50 : 0xFFF44336;
        boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int bgColor = isHovered ? 0xCC222222 : 0xAA000000;

        gfx.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgColor);
        gfx.fill(btnX, btnY + btnH - 2, btnX + btnW, btnY + btnH, statusColor);
        String waterText = config.waterBubblesEnabled ? "§aON" : "§cOFF";
        gfx.drawString(this.font, "Water Bubble: " + waterText, btnX + 5, btnY + 6, 0xFFFFFFFF);

        // --- DAMAGE LOG TOGGLE BUTONU ---
        int damageLogBtnY = btnY + btnH + spacing;
        renderToggleButton(gfx, mouseX, mouseY, btnX, damageLogBtnY, btnW, btnH,
                "Damage Log: ", config.showDamageLog);

        // --- SÜRE SLIDER (STEPPER) ---
        int sliderX = 10;
        int sliderY = damageLogBtnY + btnH + spacing;

        gfx.fill(sliderX, sliderY, sliderX + 110, sliderY + 20, 0xAA000000); // Arka plan
        gfx.fill(sliderX + 55, sliderY + 2, sliderX + 56, sliderY + 18, 0x44FFFFFF); // Orta ayırıcı çizgi

        String timerText = "Duration: §e" + config.damageLogMaxSeconds + "s";
        gfx.drawString(this.font, timerText, sliderX + 5, sliderY + 6, 0xFFFFFF);


        gfx.drawString(this.font, "§c-", sliderX + 65, sliderY + 6, 0xFFFFFF);
        gfx.drawString(this.font, "§a+", sliderX + 100, sliderY + 6, 0xFFFFFF);

        // =========================================================================

        // 4. HUD Önizlemeleri
        renderActiveAuraPreview(gfx, mouseX, mouseY);
        renderCooldownPreview(gfx, mouseX, mouseY);

        renderDamageLogPreview(gfx, mouseX, mouseY);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawGrid(GuiGraphics gfx) {
        int gridSize = 20;
        int color = 0x20FFFFFF;

        // Vertical lines
        for (int x = 0; x < this.width; x += gridSize) {
            gfx.fill(x, 0, x + 1, this.height, color);
        }

        // Horizontal lines
        for (int y = 0; y < this.height; y += gridSize) {
            gfx.fill(0, y, this.width, y + 1, color);
        }

        gfx.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x40FFFF00);
        gfx.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x40FFFF00);
    }

    private void renderActiveAuraPreview(GuiGraphics gfx, int mouseX, int mouseY) {
        int width = config.activeAuraWidth;
        int height = config.activeAuraHeight;

        int x = config.activeAuraHud.getActualX(this.width, width);
        int y = config.activeAuraHud.getActualY(this.height);

        boolean isHovered = isMouseOver(mouseX, mouseY, x, y, width, height);
        boolean isDragging = dragging == DraggableHud.ACTIVE_AURA;
        boolean isResizing = resizing == DraggableHud.ACTIVE_AURA;

        if (isDragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }

        if (isResizing) {
            int deltaX = mouseX - resizeStartMouseX;
            int deltaY = mouseY - resizeStartMouseY;
            width = Math.max(30, resizeStartWidth + deltaX);
            height = Math.max(2, resizeStartHeight + deltaY);
        }

        // HUD kutusu
        int borderColor = isResizing ? 0xFFFF00FF : (isDragging ? 0xFF00FF00 : (isHovered ? 0xFFFFFF00 : 0xFFFFFFFF));
        int bgColor = 0x80000000;

        gfx.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        gfx.fill(x, y, x + width, y + height, bgColor);

        // Simüle edilmiş bar
        int barWidth = width - 10;
        int barHeight = Math.max(3, height / 5);
        int barX = x + 5;
        int barY = y + height - barHeight - 3;

        gfx.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF00FF00);

        // Label
        gfx.drawString(this.font, "§bACTIVE AURA", x + 5, y + 2, 0xFFFFFF);

        // Boyut göstergesi
        if (isResizing) {
            String sizeText = String.format("%dx%d", width, height);
            gfx.drawString(this.font, sizeText, x + 5, y + 12, 0xFFFF00);
        }
        // Pozisyon bilgisi
        else if (isDragging) {
            String pos = String.format("X:%d Y:%d", x, y);
            gfx.drawString(this.font, pos, x + 5, y + 12, 0xFFFF00);
        }

        if (isHovered || isResizing) {
            int cornerSize = 6;
            gfx.fill(x + width - cornerSize, y + height - cornerSize, x + width, y + height, 0xFFFFFF00);
        }
    }

    private void renderCooldownPreview(GuiGraphics gfx, int mouseX, int mouseY) {
        int width = config.cooldownWidth;
        int height = config.cooldownHeight;

        int x = config.cooldownHud.getActualX(this.width, width);
        int y = config.cooldownHud.getActualY(this.height);

        boolean isHovered = isMouseOver(mouseX, mouseY, x, y, width, height);
        boolean isDragging = dragging == DraggableHud.COOLDOWN;
        boolean isResizing = resizing == DraggableHud.COOLDOWN;

        // Dragging ise mouse pozisyonuna taşı
        if (isDragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
        }

        // Resizing ise boyutu değiştir
        if (isResizing) {
            int deltaX = mouseX - resizeStartMouseX;
            int deltaY = mouseY - resizeStartMouseY;
            width = Math.max(30, resizeStartWidth + deltaX);
            height = Math.max(2, resizeStartHeight + deltaY);
        }

        // HUD kutusu
        int borderColor = isResizing ? 0xFFFF00FF : (isDragging ? 0xFF00FF00 : (isHovered ? 0xFFFFFF00 : 0xFFFFFFFF));
        int bgColor = 0x80000000;

        gfx.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        gfx.fill(x, y, x + width, y + height, bgColor);

        int barWidth = Math.max(20, width - 20);
        int barHeight = Math.max(2, height / 7);
        int barX = x + 10;
        int barY = y + height - barHeight - 3;

        float fakeProgress = (System.currentTimeMillis() % 3000) / 3000f;
        int fillWidth = (int)(barWidth * fakeProgress);

        int r = (int) Mth.lerp(fakeProgress, 255, 80);
        int g = (int) Mth.lerp(fakeProgress, 50, 255);
        int fillColor = 0xFF000000 | (r << 16) | (g << 8) | 50;

        gfx.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

        gfx.drawString(this.font, "§7COOLDOWN", x + 5, y + 2, 0xFFFFFF);

        int remainingSeconds = (int)((1f - fakeProgress) * 130); // 130 saniye max
        String timeText = formatTime(remainingSeconds);
        gfx.drawString(this.font, "§f" + timeText, x + 5, y + 11, 0xFFFFFF);

        if (isResizing) {
            String sizeText = String.format("%dx%d", width, height);
            gfx.drawString(this.font, sizeText, x + 5, y + 11, 0xFFFF00);
        }

        else if (isDragging) {
            String pos = String.format("X:%d Y:%d", x, y);
            gfx.drawString(this.font, pos, x + 5, y + 11, 0xFFFF00);
        }

        if (isHovered || isResizing) {
            int cornerSize = 6;
            gfx.fill(x + width - cornerSize, y + height - cornerSize, x + width, y + height, 0xFFFFFF00);
        }
    }

    private void renderToggleButton(GuiGraphics gfx, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean state) {
        boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        // Tasarım
        int bgColor = isHovered ? 0xCC222222 : 0xAA000000;
        int statusColor = state ? 0xFF4CAF50 : 0xFFF44336;

        gfx.fill(x, y, x + w, y + h, bgColor);
        gfx.fill(x, y + h - 2, x + w, y + h, statusColor);

        String statusText = state ? "§aON" : "§cOFF";
        gfx.drawString(this.font, label + statusText, x + 5, y + 6, 0xFFFFFFFF);
    }

    private void renderDamageLogPreview(GuiGraphics gfx, int mouseX, int mouseY) {
        int width = config.damageLogWidth;
        int height = config.damageLogHeight;
        int x = config.damageLogHud.getActualX(this.width, width);
        int y = config.damageLogHud.getActualY(this.height);

        // Kutu çizimi
        gfx.fill(x, y, x + width, y + height, 0x80000000);

        gfx.pose().pushPose();

        gfx.pose().translate(x + 3, y + 3, 0);

        float scale = 0.9f;
        gfx.pose().scale(scale, scale, 1.0f);

        int wrapWidth = (int) ((width - 6) / scale);

        gfx.drawWordWrap(this.font,
                Component.literal("§6Damage Log Preview: Damage messages will automatically wrap to the next line as the box narrows."),
                0, 0, wrapWidth, 0xFFFFFF);

        gfx.pose().popPose();
    }


    private String formatTime(int totalSeconds) {
        if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return totalSeconds + "s";
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int btnX = 10;
        int btnY = 10;
        int btnW = 110;
        int btnH = 22;
        int spacing = 5;
        resizeStartMouseX = (int) mouseX;
        resizeStartMouseY = (int) mouseY;
        resizeStartWidth = config.damageLogWidth;

        // ===================== GLOBAL SU FIZIGI BUTONU TIKLAMA (YENI) =====================
        // Render kısmında belirlediğimiz koordinatlar: x:10, y:10, w:110, h:22
        if (mx >= 10 && mx <= 120 && my >= 10 && my <= 32) {
            config.waterBubblesEnabled = !config.waterBubblesEnabled;
            HudConfig.save(); // Ayarı anında dosyaya kaydet

            // Kullanıcıya geri bildirim için tıklama sesi
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                    )
            );
            return true;
        }
        // =================================================================================

        // Active Aura HUD kontrol
        int ax = config.activeAuraHud.getActualX(this.width, config.activeAuraWidth);
        int ay = config.activeAuraHud.getActualY(this.height);
        if (isMouseOver(mx, my, ax, ay, config.activeAuraWidth, config.activeAuraHeight)) {
            if (button == 0) { // Sol tık - taşı
                dragging = DraggableHud.ACTIVE_AURA;
                dragOffsetX = mx - ax;
                dragOffsetY = my - ay;
                return true;
            } else if (button == 1) { // Sağ tık - boyutlandır
                resizing = DraggableHud.ACTIVE_AURA;
                resizeStartWidth = config.activeAuraWidth;
                resizeStartHeight = config.activeAuraHeight;
                resizeStartMouseX = mx;
                resizeStartMouseY = my;
                return true;
            }
        }

        int dx = config.damageLogHud.getActualX(this.width, config.damageLogWidth);
        int dy = config.damageLogHud.getActualY(this.height);
        if (isMouseOver((int)mouseX, (int)mouseY, dx, dy, config.damageLogWidth, config.damageLogHeight)) {
            if (button == 0) {
                this.dragging = DraggableHud.DAMAGE_LOG;
                this.dragOffsetX = (int)mouseX - dx;
                this.dragOffsetY = (int)mouseY - dy;
            } else if (button == 1) {
                this.resizing = DraggableHud.DAMAGE_LOG;
                this.resizeStartWidth = config.damageLogWidth; // Mevcut genişliği başlangıç olarak al
                this.resizeStartHeight = config.damageLogHeight;
            }
            return true;
        }

        if (isMouseOver((int) mouseX, (int) mouseY, buttonX, buttonY + btnHeight + 10, btnWidth, btnHeight)) {
            config.showDamageLog = !config.showDamageLog;
            HudConfig.save();

            // 3. Sunucuya "Yeni durum budur, NBT ni buna göre güncelle"
            PacketHandler.sendToServer(new ToggleDamageLogPacket(config.showDamageLog));
            return true;
        }

        // Cooldown HUD kontrol
        int cx = config.cooldownHud.getActualX(this.width, config.cooldownWidth);
        int cy = config.cooldownHud.getActualY(this.height);
        if (isMouseOver(mx, my, cx, cy, config.cooldownWidth, config.cooldownHeight)) {
            if (button == 0) { // Sol tık - taşı
                dragging = DraggableHud.COOLDOWN;
                dragOffsetX = mx - cx;
                dragOffsetY = my - cy;
                return true;
            } else if (button == 1) { // Sağ tık - boyutlandır
                resizing = DraggableHud.COOLDOWN;
                resizeStartWidth = config.cooldownWidth;
                resizeStartHeight = config.cooldownHeight;
                resizeStartMouseX = mx;
                resizeStartMouseY = my;
                return true;
            }
        }

        // HudEditorScreen.java içindeki mouseClicked metodu
        if (mx >= 10 && mx <= 120 && my >= sliderY && my <= sliderY + 20) {

            if (mx < 65) {
                config.damageLogMaxSeconds = Math.max(1, config.damageLogMaxSeconds - 1);
            } else {
                config.damageLogMaxSeconds = Math.min(15, config.damageLogMaxSeconds + 1);
            }

            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                    )
            );
            HudConfig.save();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 1. BOYUTLANDIRMA (RESIZING) MANTIĞI
        if (resizing != null) {
            int deltaX = (int) (mouseX - resizeStartMouseX);
            int deltaY = (int) (mouseY - resizeStartMouseY);

            int newWidth = Math.max(30, resizeStartWidth + deltaX);
            int newHeight = Math.max(10, resizeStartHeight + deltaY);

            switch (resizing) {
                case ACTIVE_AURA -> {
                    config.activeAuraWidth = newWidth;
                    config.activeAuraHeight = newHeight;
                }
                case COOLDOWN -> {
                    config.cooldownWidth = newWidth;
                    config.cooldownHeight = newHeight;
                }
                case DAMAGE_LOG -> {
                    config.damageLogWidth = newWidth;
                    config.damageLogHeight = newHeight;
                }
            }
            return true;
        }

        // 2. SÜRÜKLEME (DRAGGING) MANTIĞI
        else if (dragging != null) {
            // Sürükleme işlemi sırasında RAM üzerindeki koordinatları güncelle
            switch (dragging) {
                case ACTIVE_AURA -> {
                    config.activeAuraHud.x += (int) dragX;
                    config.activeAuraHud.y += (int) dragY;
                }
                case COOLDOWN -> {
                    config.cooldownHud.x += (int) dragX;
                    config.cooldownHud.y += (int) dragY;
                }
                case DAMAGE_LOG -> {
                    config.damageLogHud.x += (int) dragX;
                    config.damageLogHud.y += (int) dragY;
                }
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Dragging bitir
        if (dragging != null && button == 0) {
            int finalX = (int) mouseX - dragOffsetX;
            int finalY = (int) mouseY - dragOffsetY;

            // Hangi elementi sürüklüyorsak onun anchor (çapa) ve pozisyonunu güncelle
            if (dragging == DraggableHud.ACTIVE_AURA) {
                updateAnchor(config.activeAuraHud, finalX, finalY, config.activeAuraWidth);
            } else if (dragging == DraggableHud.COOLDOWN) {
                updateAnchor(config.cooldownHud, finalX, finalY, config.cooldownWidth);
            } else if (dragging == DraggableHud.DAMAGE_LOG) {
                updateAnchor(config.damageLogHud, finalX, finalY, config.damageLogWidth);
            }

            dragging = null;
            HudConfig.save();
            return true;
        }

        if (resizing != null && button == 1) {
            int deltaX = (int) mouseX - resizeStartMouseX;
            int deltaY = (int) mouseY - resizeStartMouseY;

            if (resizing == DraggableHud.DAMAGE_LOG) {
                config.damageLogWidth = Math.max(30, resizeStartWidth + deltaX);
                config.damageLogHeight = Math.max(10, resizeStartHeight + deltaY);
            }

            resizing = null;
            HudConfig.save(); // Sadece bırakınca kaydet
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateAnchor(HudConfig.HudPosition hudPos, int x, int y, int elementWidth) {
        // En yakın anchor'u bul
        int centerX = (this.width - elementWidth) / 2;
        int distToLeft = Math.abs(x);
        int distToCenter = Math.abs(x - centerX);
        int distToRight = Math.abs(x - (this.width - elementWidth));

        if (distToLeft < distToCenter && distToLeft < distToRight) {
            // Sol
            hudPos.anchor = "left";
            hudPos.x = x;
        } else if (distToCenter < distToRight) {
            // Merkez
            hudPos.anchor = "center";
            hudPos.x = x - centerX;
        } else {
            // Sağ
            hudPos.anchor = "right";
            hudPos.x = x - (this.width - elementWidth);
        }

        // Y pozisyonu (alttan mı üstten mi yakın?)
        if (y > this.height / 2) {

            hudPos.y = y - this.height;
        } else {

            hudPos.y = y;
        }
    }


    private boolean isMouseOver(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public void removed() {
        // Ekran kapandığında kaydet
        HudConfig.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    private enum DraggableHud {
        ACTIVE_AURA,
        COOLDOWN,
        DAMAGE_LOG
    }
}