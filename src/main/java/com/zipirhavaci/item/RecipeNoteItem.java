package com.zipirhavaci.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;


public class RecipeNoteItem extends Item {

    public RecipeNoteItem(Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // Client: swing animasyonu
            player.swing(hand, true);
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }


        String recipeId = stack.hasTag() ? stack.getTag().getString("RecipeId") : "";
        if (recipeId.isBlank()) {
            serverPlayer.sendSystemMessage(
                    Component.literal("§c⚠ This note is blank — no recipe inscribed. ⚠"), true);
            return InteractionResultHolder.fail(stack);
        }

        ResourceLocation rl = new ResourceLocation(recipeId);


        var recipeManager = serverPlayer.getServer().getRecipeManager();
        var optRecipe = recipeManager.byKey(rl);

        if (optRecipe.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.literal("§c⚠ Recipe not found: " + recipeId + " ⚠"), true);
            return InteractionResultHolder.fail(stack);
        }


        boolean alreadyKnown = serverPlayer.getRecipeBook().contains(optRecipe.get());
        if (alreadyKnown) {
            serverPlayer.sendSystemMessage(
                    Component.literal("§e✦ You already know this recipe. ✦"), true);
            return InteractionResultHolder.fail(stack);
        }


        serverPlayer.awardRecipes(List.of(optRecipe.get()));


        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1.4f);


        String friendlyName = formatRecipeName(rl.getPath());
        serverPlayer.sendSystemMessage(
                Component.literal("§6✦ §eRecipe Learned: §f" + friendlyName + " §6✦"), false);


        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) {
                serverPlayer.setItemInHand(hand, ItemStack.EMPTY);
            }
            serverPlayer.containerMenu.broadcastChanges();
        }

        return InteractionResultHolder.consume(stack);
    }

    private static String formatRecipeName(String path) {
        String[] parts = path.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0)))
                        .append(p.substring(1))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        // Tarif adını tooltip e ekle
        if (stack.hasTag()) {
            String recipeId = stack.getTag().getString("RecipeId");
            if (!recipeId.isBlank()) {
                ResourceLocation rl = new ResourceLocation(recipeId);
                String name = formatRecipeName(rl.getPath());
                tooltip.add(Component.literal("§7Recipe: §f" + name));
                tooltip.add(Component.literal("§8Right-click to learn"));
            }
        } else {
            tooltip.add(Component.literal("§8(No recipe inscribed)"));
        }
    }
}