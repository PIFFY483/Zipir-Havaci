package com.zipirhavaci.core.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class StaticProgressionData {
    // ─── STATIC PROGRESSION ───────────────────────────────────────────────────
    private int strikeCount = 0;
    private float auraLevel = 0.0f;
    private float lightningResist = 0.0f;
    private BlockPos lastRodPos;
    private long trainingStartTime;
    private boolean auraActive = false;
    private int auraTicksLeft = 0;
    private long lastAuraUseTime = 0;

    // ─── DARK AURA ────────────────────────────────────────────────────────────
    private boolean isCursed = false;
    private long lastDeathDenyTime = 0L;
    private float darkAuraLevel = 0.0f;
    private boolean darkAuraActive = false;
    private int darkAuraTicksLeft = 0;
    private long lastDarkAuraUseTime = 0L;
    private boolean darkAuraPhase2 = false;

    private boolean smokeVisible = true;

    private int ritualStep = 0;

    // ─── STATIC GET/SET ───────────────────────────────────────────────────────
    public int getStrikeCount()                 { return strikeCount; }
    public void setStrikeCount(int count)       { this.strikeCount = count; }
    public void addStrike()                     { this.strikeCount++; }

    public float getAuraLevel()                 { return auraLevel; }
    public void setAuraLevel(float level)       { this.auraLevel = level; }

    public float getLightningResist()           { return lightningResist; }
    public void setLightningResist(float r)     { this.lightningResist = r; }

    public BlockPos getLastRodPos()             { return lastRodPos; }
    public void setLastRodPos(BlockPos pos)     { this.lastRodPos = pos; }

    public long getTrainingStartTime()          { return trainingStartTime; }
    public void setTrainingStartTime(long t)    { this.trainingStartTime = t; }

    public boolean isAuraActive()               { return auraActive; }
    public void setAuraActive(boolean active)   { this.auraActive = active; }

    public int getAuraTicksLeft()               { return auraTicksLeft; }
    public void setAuraTicksLeft(int ticks)     { this.auraTicksLeft = ticks; }

    public long getLastAuraUseTime()            { return lastAuraUseTime; }
    public void setLastAuraUseTime(long t)      { this.lastAuraUseTime = t; }

    // ─── DARK AURA GET/SET ────────────────────────────────────────────────────
    public boolean isCursed()                   { return isCursed; }
    public void setCursed(boolean cursed)       { this.isCursed = cursed; }

    public long getLastDeathDenyTime()          { return lastDeathDenyTime; }
    public void setLastDeathDenyTime(long t)    { this.lastDeathDenyTime = t; }

    public float getDarkAuraLevel()             { return darkAuraLevel; }
    public void setDarkAuraLevel(float level)   { this.darkAuraLevel = level; }

    public boolean isDarkAuraActive()           { return darkAuraActive; }
    public void setDarkAuraActive(boolean b)    { this.darkAuraActive = b; }

    public int getDarkAuraTicksLeft()           { return darkAuraTicksLeft; }
    public void setDarkAuraTicksLeft(int t)     { this.darkAuraTicksLeft = t; }

    public long getLastDarkAuraUseTime()        { return lastDarkAuraUseTime; }
    public void setLastDarkAuraUseTime(long t)  { this.lastDarkAuraUseTime = t; }

    public boolean isDarkAuraPhase2()           { return darkAuraPhase2; }
    public void setDarkAuraPhase2(boolean b)    { this.darkAuraPhase2 = b; }

    // ─── RITUAL / DUMAN ───────────────────────────────────────────────────────
    public boolean isSmokeVisible()             { return smokeVisible; }
    public void setSmokeVisible(boolean b)      { this.smokeVisible = b; }

    public int getRitualStep()                  { return ritualStep; }
    public void setRitualStep(int step)         { this.ritualStep = step; }

    // ─── NBT ──────────────────────────────────────────────────────────────────
    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt("strikeCount", strikeCount);
        nbt.putFloat("auraLevel", auraLevel);
        nbt.putFloat("lightningResist", lightningResist);
        nbt.putBoolean("auraActive", auraActive);
        nbt.putInt("auraTicksLeft", auraTicksLeft);
        nbt.putLong("lastAuraUseTime", lastAuraUseTime);
        nbt.putLong("trainingStartTime", trainingStartTime);
        if (lastRodPos != null) {
            nbt.putLong("lastRodPos", lastRodPos.asLong());
        }
        nbt.putBoolean("isCursed", isCursed);
        nbt.putLong("lastDeathDenyTime", lastDeathDenyTime);
        nbt.putFloat("darkAuraLevel", darkAuraLevel);
        nbt.putBoolean("darkAuraActive", darkAuraActive);
        nbt.putInt("darkAuraTicksLeft", darkAuraTicksLeft);
        nbt.putLong("lastDarkAuraUseTime", lastDarkAuraUseTime);
        nbt.putBoolean("darkAuraPhase2", darkAuraPhase2);
        nbt.putBoolean("smokeVisible", smokeVisible);
        nbt.putInt("ritualStep", ritualStep);
    }

    public void loadNBTData(CompoundTag nbt) {
        strikeCount          = nbt.getInt("strikeCount");
        auraLevel            = nbt.getFloat("auraLevel");
        lightningResist      = nbt.getFloat("lightningResist");
        auraActive           = nbt.getBoolean("auraActive");
        auraTicksLeft        = nbt.getInt("auraTicksLeft");
        lastAuraUseTime      = nbt.getLong("lastAuraUseTime");
        trainingStartTime    = nbt.getLong("trainingStartTime");
        if (nbt.contains("lastRodPos")) {
            lastRodPos = BlockPos.of(nbt.getLong("lastRodPos"));
        }
        isCursed             = nbt.getBoolean("isCursed");
        lastDeathDenyTime    = nbt.getLong("lastDeathDenyTime");
        darkAuraLevel        = nbt.getFloat("darkAuraLevel");
        darkAuraActive       = nbt.getBoolean("darkAuraActive");
        darkAuraTicksLeft    = nbt.getInt("darkAuraTicksLeft");
        lastDarkAuraUseTime  = nbt.getLong("lastDarkAuraUseTime");
        darkAuraPhase2       = nbt.getBoolean("darkAuraPhase2");
        smokeVisible         = !nbt.contains("smokeVisible") || nbt.getBoolean("smokeVisible");
        ritualStep           = nbt.getInt("ritualStep");
    }
}