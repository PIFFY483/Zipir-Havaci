package com.zipirhavaci.core;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SoundRegistry {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "zipirhavaci");


    public static final RegistryObject<SoundEvent> COOLDOWN_SOUND = SOUNDS.register("cooldown_clank",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("zipirhavaci", "cooldown_clank")));

    public static final RegistryObject<SoundEvent> AVIATOR_IDLE = SOUNDS.register("aviator_idle",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ZipirHavaci.MOD_ID, "aviator_idle")));


    public static final RegistryObject<SoundEvent> DEPLOY_SOUND = SOUNDS.register("aviator_deploy",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ZipirHavaci.MOD_ID, "aviator_deploy")));

    public static final RegistryObject<SoundEvent> BOND_READY = SOUNDS.register("bond_ready",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ZipirHavaci.MOD_ID, "bond_ready")));



    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}