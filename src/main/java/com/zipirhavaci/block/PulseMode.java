package com.zipirhavaci.block;

import net.minecraft.util.StringRepresentable;

public enum PulseMode implements StringRepresentable {
    REPEATER,   // Normal geciktirici
    PULSE,      // Yanıp sönen osilatör
    INVERTER,   // Sinyal gelince söner, kesilince yanar (NOT kapısı)
    LATCH,      // İlk sinyalde açılır, ikincisinde kapanır (toggle)
    COUNTER,    // Her N. sinyalde bir çıkış verir
    AND;        // İki farklı yüzden sinyal gelince çıkış verir

    @Override
    public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}