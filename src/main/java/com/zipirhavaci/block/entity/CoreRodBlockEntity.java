package com.zipirhavaci.block.entity;

import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CoreRodBlockEntity extends BlockEntity {

    // LATCH modu: önceki input durumu
    private boolean wasInputHigh = false;
    // COUNTER modu: mevcut sayaç değeri
    private int counter = 0;

    public CoreRodBlockEntity(BlockPos pos, BlockState state) {
        super(ItemRegistry.CORE_ROD_BE.get(), pos, state);
    }

    public boolean wasInputHigh() { return wasInputHigh; }
    public void setInputHigh(boolean val) {
        if (wasInputHigh != val) {
            wasInputHigh = val;
            setChanged();
        }
    }

    public int incrementCounter() {
        counter++;
        setChanged();
        return counter;
    }

    public void resetCounter() {
        counter = 0;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("wasInputHigh", wasInputHigh);
        tag.putInt("counter", counter);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        wasInputHigh = tag.getBoolean("wasInputHigh");
        counter = tag.getInt("counter");
    }
}