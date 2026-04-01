package com.zipirhavaci.client.renderer;

import com.zipirhavaci.client.ClientShakeHandler;
import com.zipirhavaci.core.ZipirHavaci;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZipirHavaci.MOD_ID, value = Dist.CLIENT)
public class CameraShakeRenderHandler {

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ClientShakeHandler.isShaking()) {
            event.setPitch(event.getPitch() + ClientShakeHandler.getShakeAmount());
            event.setYaw(event.getYaw() + ClientShakeHandler.getShakeAmount());
        }
    }
}