package com.zipirhavaci.core.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticProgressionProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static Capability<StaticProgressionData> STATIC_PROGRESSION = CapabilityManager.get(new CapabilityToken<StaticProgressionData>() { });

    private StaticProgressionData backend = null;
    private final LazyOptional<StaticProgressionData> optional = LazyOptional.of(this::createStaticProgression);

    private StaticProgressionData createStaticProgression() {
        if (this.backend == null) {
            this.backend = new StaticProgressionData();
        }
        return this.backend;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == STATIC_PROGRESSION) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createStaticProgression().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createStaticProgression().loadNBTData(nbt);
    }
}