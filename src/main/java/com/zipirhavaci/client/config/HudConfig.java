package com.zipirhavaci.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * HUD pozisyonlarını ve ayarlarını yöneten config sistemi
 */
public class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/zipirhavaci-hud.json");

    private static HudConfig INSTANCE;

    // ACTIVE AURA HUD (Ortada olan)
    public HudPosition activeAuraHud = new HudPosition(0, -50, true, "center"); // center-bottom default
    public int activeAuraWidth = 100;
    public int activeAuraHeight = 20;

    // COOLDOWN HUD (Sağda olan)
    public HudPosition cooldownHud = new HudPosition(-90, -30, true, "right"); // right-bottom default
    public int cooldownWidth = 100;
    public int cooldownHeight = 20;
    public boolean waterBubblesEnabled = true;
    public boolean showDamageLog = true;

    public HudPosition damageLogHud = new HudPosition(10, 50, true, "left"); // Sol üst default
    public int damageLogWidth = 150;
    public int damageLogHeight = 20;
    public float damageLogScale = 1.0f; // Boyut ayarı için


    public transient String lastDamageLog = "";
    public transient int logDisplayTicks = 0;
    public int damageLogMaxSeconds = 5;
    public transient long logEndTime = 0;

    public static class HudPosition {
        public int x;
        public int y;
        public boolean enabled;
        public String anchor; // "left", "center", "right", "custom"


        public HudPosition(int x, int y, boolean enabled, String anchor) {
            this.x = x;
            this.y = y;
            this.enabled = enabled;
            this.anchor = anchor;
        }


        public int getActualX(int screenWidth, int elementWidth) {
            switch (anchor) {
                case "left":
                    return x;
                case "center":
                    return (screenWidth - elementWidth) / 2 + x;
                case "right":
                    return screenWidth + x - elementWidth;
                default: // "custom"
                    return x;
            }
        }


        public int getActualY(int screenHeight) {
            if (y < 0) {
                return screenHeight + y; // Alttan mesafe
            }
            return y; // Üstten mesafe
        }
    }

    public static HudConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, HudConfig.class);
                System.out.println("[ZipirHavaci] HUD config loaded from file");
                return;
            } catch (IOException e) {
                System.err.println("[ZipirHavaci] Failed to load HUD config: " + e.getMessage());
            }
        }

        // Default config
        INSTANCE = new HudConfig();
        save();
        System.out.println("[ZipirHavaci] Created default HUD config");
    }

    public static void save() {
        try {
            // Eğer INSTANCE null ise (henüz load edilmemişse) kaydetmeye çalışma
            if (INSTANCE == null) {
                System.err.println("[ZipirHavaci] Cannot save: HudConfig INSTANCE is null!");
                return;
            }

            // Klasörün var olduğundan emin ol
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }

            // FileWriter ile dosyayı aç
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
                System.out.println("[ZipirHavaci] HUD config successfully saved to: " + CONFIG_FILE.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("[ZipirHavaci] Failed to save HUD config: " + e.getMessage());
            e.printStackTrace();
        }
    }
}