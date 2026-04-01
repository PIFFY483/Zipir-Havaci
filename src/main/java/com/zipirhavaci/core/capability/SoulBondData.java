package com.zipirhavaci.core.capability;

import net.minecraft.nbt.CompoundTag;

public class SoulBondData {
    private boolean hasSoulBond = false;

    public boolean hasSoulBond() {
        return hasSoulBond;
    }

    public void learnSoulBond() {
        this.hasSoulBond = true;
    }

    public void copyFrom(SoulBondData source) {
        this.hasSoulBond = source.hasSoulBond;
    }

    public void saveNBTData(CompoundTag nbt) {
        nbt.putBoolean("HasSoulBond", hasSoulBond);
    }

    public void loadNBTData(CompoundTag nbt) {
        hasSoulBond = nbt.getBoolean("HasSoulBond");
    }
}