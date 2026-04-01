package com.zipirhavaci.item;

import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.sounds.SoundEvent;
import java.util.function.Supplier;

public enum ModArmorMaterials implements ArmorMaterial {
    STRAINED_GRIEF("strained_grief", 37, new int[]{4, 12, 9, 5}, 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 6.25F, 0.0F, () -> Ingredient.of(ItemRegistry.HEAVY_METAL.get()));

    private final String name;
    private final int durabilityMultiplier;
    private final int[] slotProtections;
    private final int enchantmentValue;
    private final SoundEvent sound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    ModArmorMaterials(String name, int durabilityMultiplier, int[] slotProtections, int enchantmentValue,
                      SoundEvent sound, float toughness, float knockbackResistance, Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.slotProtections = slotProtections;
        this.enchantmentValue = enchantmentValue;
        this.sound = sound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override public int getDurabilityForType(ArmorItem.Type type) { return durabilityMultiplier * new int[]{13, 15, 16, 11}[type.ordinal()]; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return slotProtections[type.ordinal()]; }
    @Override public int getEnchantmentValue() { return enchantmentValue; }
    @Override public SoundEvent getEquipSound() { return sound; }
    @Override public Ingredient getRepairIngredient() { return repairIngredient.get(); }
    @Override public String getName() { return "zipirhavaci:" + name; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
}