package com.zipirhavaci.item;

import com.zipirhavaci.core.capability.SoulBondCapabilityHandler;
import com.zipirhavaci.core.capability.Soulbonddataprovider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SoulBondScrollItem extends Item {

    public SoulBondScrollItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
        );
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        if (player instanceof ServerPlayer sp) {
            var cap = sp.getCapability(Soulbonddataprovider.SOUL_BOND).resolve().orElse(null);

            if (cap != null) {
                if (cap.hasSoulBond()) {
                    sp.sendSystemMessage(Component.literal("§c⚠ SYNC FAILED: SOUL BOND ALREADY INTEGRATED!"), true);
                    return InteractionResultHolder.fail(stack);
                }

                // --- YETENEK ÖĞRENME ---
                cap.learnSoulBond();
                SoulBondCapabilityHandler.syncToClient(sp);

                var adv = sp.getServer().getAdvancements().getAdvancement(new net.minecraft.resources.ResourceLocation("zipirhavaci", "soul_bound"));
                if (adv != null) sp.getAdvancements().award(adv, "manual_trigger");

                sp.sendSystemMessage(Component.literal("§6✦ §eTHE TETHER IS SEALED... SOUL BOND AWAKENED! §6✦"), false);
                level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

                // --- GÜVENLİ VE ZORLA SİLME ---
                if (!sp.getAbilities().instabuild) {
                    // kullandığımız eldeki stack'i hedef al
                    stack.setCount(0);

                    //  Kullandığımız el hangisiyse  boşalt
                    sp.setItemInHand(hand, ItemStack.EMPTY);

                    sp.containerMenu.broadcastChanges();
                    // Alternatif senkronizasyon yöntemi (Hata vermez)
                    sp.sendSystemMessage(Component.empty(), true); // Küçük bir paket tetiklemek için
                }

                return InteractionResultHolder.consume(ItemStack.EMPTY);
            }
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7An ancient spiritual manifestation...").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());

        tooltip.add(Component.literal("§9Right-click to integrate").withStyle(ChatFormatting.BLUE));

        tooltip.add(Component.literal("§7• Establish spiritual tether with the launched shield").withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.literal("§7• Empty hands: Accelerate user toward the shield anchor").withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.literal("§7• Cost: 1 Soul Sand + Vitality + Exhaustion").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());

        tooltip.add(Component.literal("§8\"The spirit departs, drawn by the shield's call...\"").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}