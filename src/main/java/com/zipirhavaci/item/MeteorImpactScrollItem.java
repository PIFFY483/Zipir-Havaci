package com.zipirhavaci.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class MeteorImpactScrollItem extends Item {
    public MeteorImpactScrollItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7THE CELESTIAL DECREE: §fA technique descending from the highest firmament. For when the stars align to strike, no mortal bastion shall remain unshaken.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§6Right-click to integrate").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("§7• Attack during descent to initiate meteor impact").withStyle(ChatFormatting.GRAY));
    }
}