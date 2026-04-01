package com.zipirhavaci.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class KeyInputHandler {
    public static final KeyMapping SHIELD_LAUNCH_KEY = new KeyMapping("key.zipirhavaci.launch_shield", GLFW.GLFW_KEY_V, "category.zipirhavaci");
    public static final KeyMapping MODE_CHANGE_KEY = new KeyMapping("key.zipirhavaci.change_mode", GLFW.GLFW_KEY_R, "category.zipirhavaci");
    public static final KeyMapping AURA_SKILL_KEY = new KeyMapping("key.zipirhavaci.aura_skill", GLFW.GLFW_KEY_G, "category.zipirhavaci");
    public static final KeyMapping AVIATOR_SUPER_KEY = new KeyMapping("key.zipirhavaci.aviator_super", GLFW.GLFW_KEY_X, "category.zipirhavaci");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SHIELD_LAUNCH_KEY);
        event.register(MODE_CHANGE_KEY);
        event.register(AURA_SKILL_KEY);
    }

}