package com.zipirhavaci.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.ApplyCursePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class CursedBookScreen extends Screen {

    // Sayfa durumu
    private boolean onWarningPage = true;   // true = sayfa 1, false = sayfa 2
    private boolean curseSent     = false;  // paketi bir kez gönder

    // Kitap paneli boyutları
    private static final int BOOK_W = 192;
    private static final int BOOK_H = 220;

    // Renkler
    private static final int COL_BG         = 0xFF0A0A0A;
    private static final int COL_BORDER      = 0xFF3A003A;
    private static final int COL_TITLE       = 0xFFCC00CC;
    private static final int COL_WARNING     = 0xFFFF3333;
    private static final int COL_BODY        = 0xFFBBBBBB;
    private static final int COL_SPELL       = 0xFF8800AA;


    public CursedBookScreen() {
        super(Component.literal("The Deceased's Perspective"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();

        int bx = (width - BOOK_W) / 2;
        int by = (height - BOOK_H) / 2;

        if (onWarningPage) {
            // [ Geri Dön ]
            addRenderableWidget(Button.builder(
                    Component.literal("§7← Reclaim Soul"),
                    btn -> onClose()
            ).pos(bx + 12, by + BOOK_H - 36).size(78, 20).build());

            // [ Okumaya Devam Et ]
            addRenderableWidget(Button.builder(
                    Component.literal("§4Unveil More →"),
                    btn -> {
                        onWarningPage = false;
                        rebuildButtons();
                    }
            ).pos(bx + BOOK_W - 102, by + BOOK_H - 36).size(90, 20).build());

        } else {
            // [ Kitabı Kapat — Ve Laneti Mühürle ]
            addRenderableWidget(Button.builder(
                    Component.literal("§5✦ Bind the Tome and Shroud the Curse ✦"),
                    btn -> {
                        sendCurse();
                        onClose();
                    }
            ).pos(bx + 16, by + BOOK_H - 36).size(BOOK_W - 32, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Karanlık arka plan overlay
        renderBackground(gfx);

        int bx = (width - BOOK_W) / 2;
        int by = (height - BOOK_H) / 2;

        // Kitap arka planı
        gfx.fill(bx, by, bx + BOOK_W, by + BOOK_H, COL_BG);

        // Çerçeve (1px mor kenarlık)
        drawBorder(gfx, bx, by, BOOK_W, BOOK_H, COL_BORDER);

        if (onWarningPage) {
            renderWarningPage(gfx, bx, by);
        } else {
            renderSpellPage(gfx, bx, by);
        }

        // Butonları render et
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderWarningPage(GuiGraphics gfx, int bx, int by) {
        int cx = bx + BOOK_W / 2;

        // Başlık
        gfx.drawCenteredString(font, "§5⚠ THE DECEASED'S PERSPECTIVE ⚠", cx, by + 14, COL_TITLE);

        // İnce ayırıcı çizgi
        gfx.fill(bx + 10, by + 26, bx + BOOK_W - 10, by + 27, COL_BORDER);

        // Uyarı başlığı
        gfx.drawCenteredString(font, "§c§lBEWARE!", cx, by + 34, COL_WARNING);

        // Uyarı metni — sarılmış satırlar
        String[] lines = {
                "§cThis is a forbidden, spectral malison.",
                "",
                "§7Should you venture further, you shall",
                "§7behold the unholy rites of the void.",
                "",
                "§7Once bound, this tome shall vanish,",
                "§7leaving your very soul §4IRREVERSIBLY",
                "§7ensnared.",
                "",
                "§8Static power shall remain",
                "§8veiled from you for all eternity.",
        };

        int lineY = by + 50;
        for (String line : lines) {
            if (line.isEmpty()) {
                lineY += 4;
                continue;
            }
            gfx.drawCenteredString(font, line, cx, lineY, COL_BODY);
            lineY += 11;
        }
    }

    private void renderSpellPage(GuiGraphics gfx, int bx, int by) {
        int cx = bx + BOOK_W / 2;

        // Başlık
        gfx.drawCenteredString(font, "§5✦ THE FORBIDDEN INCANTATIONS ✦", cx, by + 14, COL_TITLE);
        gfx.fill(bx + 10, by + 26, bx + BOOK_W - 10, by + 27, COL_BORDER);


        String[] spellLines = {
                "§5Death cannot evade my hollow grasp,",
                "§5yet from Death’s cold embrace, I flee.",
                "",
                "§8Anima mortis, liga me",
                "§8in tenebris perpetuis.",
                "",
                "§5I abjure the false light,",
                "§5to clasp the eternal shroud.",
                "",
                "§8Fulmen meus hostis,",
                "§8nox mea amica.",
                "",
                "§4Potestas mea — obscura.",
                "§4Fiat lux — numquam.",
                "",
                "§8§o... the malison is sealing...",
        };

        int lineY = by + 36;
        for (String line : spellLines) {
            if (line.isEmpty()) {
                lineY += 4;
                continue;
            }
            gfx.drawCenteredString(font, line, cx, lineY, COL_SPELL);
            lineY += 11;
        }
    }

    // Çerçeve çizici yardımcı
    private void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w,     y + 1,     color); // üst
        gfx.fill(x,         y + h - 1, x + w,     y + h,     color); // alt
        gfx.fill(x,         y,         x + 1,     y + h,     color); // sol
        gfx.fill(x + w - 1, y,         x + w,     y + h,     color); // sağ
    }

    // Lanet paketini sunucuya gönder (bir kez)
    private void sendCurse() {
        if (!curseSent) {
            curseSent = true;
            PacketHandler.sendToServer(new ApplyCursePacket());
        }
    }

    @Override
    public void onClose() {

        if (!onWarningPage) {
            sendCurse();
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}