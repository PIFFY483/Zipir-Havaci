package com.zipirhavaci.item;

import com.zipirhavaci.core.ItemRegistry;
import com.zipirhavaci.entity.ScorchedImpellerArrowEntity;
import com.zipirhavaci.entity.SingularityRemnantArrowEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

public class AnomalousProjectileItem extends ArrowItem {
    public AnomalousProjectileItem(Properties properties) {

        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
        if (stack.is(ItemRegistry.SCORCHED_IMPELLER_ARROW.get())) {
            return new ScorchedImpellerArrowEntity(level, shooter);
        } else if (stack.is(ItemRegistry.SINGULARITY_REMNANT_ARROW.get())) {
            return new SingularityRemnantArrowEntity(level, shooter);
        }
        return super.createArrow(level, stack, shooter);
    }

    @Override
    public boolean isInfinite(ItemStack stack, ItemStack bow, net.minecraft.world.entity.player.Player player) {

        return false;
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.EPIC;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}