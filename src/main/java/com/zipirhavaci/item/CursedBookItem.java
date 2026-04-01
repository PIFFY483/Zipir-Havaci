package com.zipirhavaci.item;

import com.zipirhavaci.core.capability.StaticProgressionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CursedBookItem extends Item {

    public CursedBookItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        // Sunucu tarafı kontrol: Oyuncu zaten lanetli mi?
        if (!level.isClientSide()) {
            var dataOpt = player.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).resolve();
            if (dataOpt.isPresent() && dataOpt.get().isCursed()) {
                player.sendSystemMessage(Component.literal("§dThy soul is but a hollow vessel for the abyss... §fThou hast outlived even the amusement of thy master."));
                return InteractionResultHolder.fail(itemstack);
            }
        }

        if (level.isClientSide) {
            openScreen();
        }

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private void openScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.zipirhavaci.client.gui.CursedBookScreen()
        );
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§8Perspective of the Dead..."));
        tooltip.add(Component.literal("§5The price of truth is your very soul."));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}