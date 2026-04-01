package com.zipirhavaci.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.UUID;

public class StrainedGriefArmorItem extends ArmorItem {

    private static final UUID KB_RES_UUID = UUID.fromString("6a1f16f5-e698-4835-903c-235d97e882e3");

    private static final UUID DAMAGE_RED_UUID = UUID.fromString("f4702951-6677-44f2-959c-6a759600a96a");

    public StrainedGriefArmorItem(Type type, Properties properties) {
        super(ModArmorMaterials.STRAINED_GRIEF, type, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof Player player) {
            // Sadece zırh slotundaysa özellikleri işle
            if (player.getItemBySlot(this.getType().getSlot()) == stack) {
                // Parça başı yavaşlık
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, true));
                updateDynamicAttributes(player);
            } else {
                // Zırh slotundan çıktıysa temizle
                clearAttributes(player);
            }
        }
    }

    private void updateDynamicAttributes(Player player) {
        int count = 0;
        for (ItemStack armorStack : player.getArmorSlots()) {
            if (armorStack.getItem() instanceof StrainedGriefArmorItem) count++;
        }

        // ---  Geri Tepme Direnci (Parça başı 0.25 - Total 1.0) ---
        var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) {
            double expectedKb = count * 0.25;
            if (kbAttr.getModifier(KB_RES_UUID) == null || kbAttr.getModifier(KB_RES_UUID).getAmount() != expectedKb) {
                kbAttr.removeModifier(KB_RES_UUID);
                kbAttr.addTransientModifier(new AttributeModifier(KB_RES_UUID, "Heavy Metal Weight", expectedKb, AttributeModifier.Operation.ADDITION));
            }
        }

        // ---  Hasar Direnci (Parça başı %2.5 - Total %10) ---
        var armorToughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armorToughness != null) {
            double toughnessBonus = count * 2.0; // Her parça için ekstra toughness koruması
            if (armorToughness.getModifier(DAMAGE_RED_UUID) == null || armorToughness.getModifier(DAMAGE_RED_UUID).getAmount() != toughnessBonus) {
                armorToughness.removeModifier(DAMAGE_RED_UUID);
                armorToughness.addTransientModifier(new AttributeModifier(DAMAGE_RED_UUID, "Reinforced Density", toughnessBonus, AttributeModifier.Operation.ADDITION));
            }
        }

        // ---  Set Bonusları (2 ve 4 Parça) ---
        if (count >= 2) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20, 0, false, false, true));
        }
        if (count == 4) {
            player.causeFoodExhaustion(0.005F); // Ağır yük acıktırır
        }
    }

    private void clearAttributes(Player player) {
        var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) kbAttr.removeModifier(KB_RES_UUID);

        var armorToughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armorToughness != null) armorToughness.removeModifier(DAMAGE_RED_UUID);
    }
}