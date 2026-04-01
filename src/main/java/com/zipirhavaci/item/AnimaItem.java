package com.zipirhavaci.item;

import com.zipirhavaci.core.capability.StaticProgressionProvider;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.SyncStaticProgressionPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

public class AnimaItem extends Item {
    public AnimaItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Q basıp atma bugı için boş kontrolü
            if (itemstack.isEmpty()) {
                return InteractionResultHolder.fail(itemstack);
            }

            serverPlayer.getCapability(StaticProgressionProvider.STATIC_PROGRESSION).ifPresent(data -> {

                //  LANET KONTROLÜ
                if (data.isCursed()) {
                    serverPlayer.sendSystemMessage(Component.literal("§cYour soul is already tainted by darkness; even the hopes of spirits cannot aid you."));
                    return;
                }

                // SEVİYE KONTROLÜ: Zaten usta mı?
                if (data.getAuraLevel() >= 3.0f) {
                    serverPlayer.sendSystemMessage(Component.literal("§cYou are already a Master. You have already transcended the horizons; there is nothing more to learn from here."));
                    return;
                }

                //  BAŞARIM
                var advancement = serverPlayer.getServer().getAdvancements().getAdvancement(
                        new net.minecraft.resources.ResourceLocation("zipirhavaci", "light_from_crumbs")
                );
                if (advancement != null) {
                    serverPlayer.getAdvancements().award(advancement, "main_criterion");
                }

                // 4. İŞLEMLER
                if (!player.getAbilities().instabuild) {
                    itemstack.shrink(1);
                }

                data.setAuraLevel(3.0f);
                data.setStrikeCount(100);

                // 5. SENKRONİZASYON
                PacketHandler.sendToTracking(serverPlayer, new SyncStaticProgressionPacket(data, serverPlayer.getId()));

                // 6. GÖRSEL EFEKTLER (Purification)
                if (serverPlayer.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 40; i++) {
                        double px = serverPlayer.getX() + (serverLevel.random.nextDouble() - 0.5) * 1.5;
                        double py = serverPlayer.getY() + serverLevel.random.nextDouble() * 2.0;
                        double pz = serverPlayer.getZ() + (serverLevel.random.nextDouble() - 0.5) * 1.5;

                        serverLevel.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0, 0.1, 0, 0.02);
                        serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, 1, 0, 0.05, 0, 0.01);
                    }
                }

                net.minecraft.resources.ResourceLocation advId = new net.minecraft.resources.ResourceLocation("zipirhavaci", "light_from_crumbs");
                net.minecraft.advancements.Advancement targetAdvancement = serverPlayer.getServer().getAdvancements().getAdvancement(advId);

                if (targetAdvancement != null) {
                    serverPlayer.getAdvancements().award(targetAdvancement, "main_criterion");
                }

                // 7. SES VE MESAJ
                serverPlayer.sendSystemMessage(Component.literal("§6§lANIMA’S PRIMAL SYNERGY ACHIEVED... §fTHY ESSENCE IS NOW THE ANCHOR OF THE AETHER!"));
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
            });
        }

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7The ancient guide of souls."));
        tooltip.add(Component.literal("§c[Single Use]"));
        tooltip.add(Component.literal("§eInstantly elevates the user to Master level upon use."));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}