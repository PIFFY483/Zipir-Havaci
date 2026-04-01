package com.zipirhavaci.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.zipirhavaci.client.gui.HudEditorScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "zipirhavaci", value = Dist.CLIENT)
public class HudEditorKeybind {

    private static final String CATEGORY = "key.categories.zipirhavaci";

    public static KeyMapping OPEN_HUD_EDITOR = new KeyMapping(
            "key.zipirhavaci.hud_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    @Mod.EventBusSubscriber(modid = "zipirhavaci", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_HUD_EDITOR);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // H basıldı mı?
        if (OPEN_HUD_EDITOR.consumeClick()) {
            mc.setScreen(new HudEditorScreen());
        }

    }
}