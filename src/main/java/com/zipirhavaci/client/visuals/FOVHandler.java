package com.zipirhavaci.client.visuals;

import com.zipirhavaci.item.ZipirAviatorItem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FOVHandler {

    private static float fovAddition = 0.0f;
    private static float continuousModifier = 1.0f;

    public static void pulseCharge(float amount) {
        fovAddition = amount;
    }

    public static void bumpFOV(boolean isPushMode) {
        fovAddition = isPushMode ? -0.12f : 0.10f;
    }

    public static void bumpFOVSuper(boolean isPull, int level) {

        float strength = 0.10f + (level * 0.03f);
        fovAddition = isPull ? strength : -strength - 0.05f;
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getMainHandItem();

        if (stack.getItem() instanceof ZipirAviatorItem) {
            int activeTicks = player.getPersistentData().getInt("ShotSmokeTicks");
            int dashTicks   = player.getPersistentData().getInt("AviatorDashTicks");

            boolean superActive = dashTicks > 30;

            if (activeTicks > 0 || dashTicks > 0) {
                int mode = stack.getOrCreateTag().getInt("AviatorMode");
                float targetCont;

                if (superActive) {
                    targetCont = (mode == 1) ? 0.78f : 1.32f;
                } else {
                    targetCont = (mode == 1) ? 0.90f : 1.15f;
                }
                continuousModifier = Mth.lerp(0.2f, continuousModifier, targetCont);
            } else {
                continuousModifier = Mth.lerp(0.1f, continuousModifier, 1.0f);
            }
        } else {
            continuousModifier = 1.0f;
        }

        // --- SARSINTI YÖNETİMİ  ---
        if (Math.abs(fovAddition) > 0.0001f) {
            fovAddition = Mth.lerp(0.08f, fovAddition, 0.0f);
        } else {
            fovAddition = 0.0f;
        }

        event.setNewFovModifier(event.getFovModifier() * continuousModifier + fovAddition);
    }
}