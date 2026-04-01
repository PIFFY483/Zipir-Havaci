package com.zipirhavaci.core;

public enum AviatorMode {
    PULL("PULL"), // İleri çeker
    PUSH("PUSH");   // Geri iter, önündekini savurur

    private final String label;
    AviatorMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}