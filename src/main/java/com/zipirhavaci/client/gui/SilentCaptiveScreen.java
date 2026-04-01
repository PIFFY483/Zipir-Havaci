package com.zipirhavaci.client.gui;

import com.zipirhavaci.network.AcceptHelpPacket;
import com.zipirhavaci.network.PacketHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;


@OnlyIn(Dist.CLIENT)
public class SilentCaptiveScreen extends Screen {

    private final int entityId;

    // Panel boyutları
    private static final int DLG_W = 220;
    private static final int DLG_H = 200;

    // Renkler
    private static final int COL_BG      = 0xFF080810;
    private static final int COL_BORDER  = 0xFF4B0082;
    private static final int COL_TITLE   = 0xFFAA00FF;
    private static final int COL_GIBBER  = 0xFF6A006A;
    private static final int COL_REQUEST = 0xFFDDDDDD;

    private static final String[] GIBBERISH = {
            "§8Kethûl va'ryn... môrthas elun...",
            "§8Vel'shara nox... umbrath kyel...",
            "§8Ashen'dra... fael koth umbraes...",
            "§8Zyr'vael... nethum drossa keel...",
            "§8...vel'thas... aen'kûr moreth...",
    };

    private static final String[] REQUEST_LINES = {
            "",
            "§7...§r",
            "§7I... crave... but one",
            "§7thing from thee...",
            "",
            "§b§lA bottled dragon’s breath§r§7...",
            "§b§lThe tear of a Ghast§r§7...",
            "",
            "§7I beseech thee...",
            "§d§oGrant me thy aid.",
    };

    public SilentCaptiveScreen(int entityId) {
        super(Component.literal("§dThe Unhallowed Vagrant"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        super.init();

        int bx = (width  - DLG_W) / 2;
        int by = (height - DLG_H) / 2;

        // Tamam butonu — AcceptHelpPacket gönder, ekranı kapat
        addRenderableWidget(Button.builder(
                Component.literal("§7If it must be..."),
                btn -> {
                    PacketHandler.sendToServer(new AcceptHelpPacket(entityId));
                    onClose();
                }
        ).pos(bx + DLG_W / 2 - 40, by + DLG_H - 30).size(80, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        int bx = (width  - DLG_W) / 2;
        int by = (height - DLG_H) / 2;

        gfx.fill(bx, by, bx + DLG_W, by + DLG_H, COL_BG);
        drawBorder(gfx, bx, by, DLG_W, DLG_H, COL_BORDER);

        int cx = bx + DLG_W / 2;

        gfx.drawCenteredString(font, "§5✦ The Unhallowed Vagrant ✦", cx, by + 10, COL_TITLE);
        gfx.fill(bx + 8, by + 22, bx + DLG_W - 8, by + 23, COL_BORDER);

        int lineY = by + 30;
        for (String gibber : GIBBERISH) {
            gfx.drawCenteredString(font, gibber, cx, lineY, COL_GIBBER);
            lineY += 10;
        }

        lineY += 2;
        gfx.fill(bx + 20, lineY, bx + DLG_W - 20, lineY + 1, 0xFF330033);
        lineY += 6;

        for (String line : REQUEST_LINES) {
            if (line.isEmpty()) { lineY += 4; continue; }
            gfx.drawCenteredString(font, line, cx, lineY, COL_REQUEST);
            lineY += 10;
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w,     y + 1,     color);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     color);
        gfx.fill(x,         y,         x + 1,     y + h,     color);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     color);
    }

    @Override
    public boolean isPauseScreen() { return true; }
}