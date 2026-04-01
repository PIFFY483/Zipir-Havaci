package com.zipirhavaci.item;

import com.zipirhavaci.entity.BlazeCoreEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class BlazeCoreItem extends Item {
    public BlazeCoreItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public boolean isFoil(ItemStack pStack) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack pStack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pHand) {
        pPlayer.startUsingItem(pHand);
        return InteractionResultHolder.consume(pPlayer.getItemInHand(pHand));
    }

    @Override
    public void onUseTick(Level pLevel, LivingEntity pLivingEntity, ItemStack pStack, int pCount) {
        if (!pLevel.isClientSide && pLivingEntity instanceof Player player) {
            int duration = this.getUseDuration(pStack) - pCount;

            if (duration > 15) {
                if (pLevel.random.nextFloat() < 0.25f) {
                    ((ServerLevel) pLevel).sendParticles(ParticleTypes.PORTAL,
                            player.getX(), player.getY() + 1.2, player.getZ(),
                            3, 0.1, 0.1, 0.1, 0.05);
                }
            }

            if (duration >= 20 && duration % 20 == 0) {
                if (pLevel.random.nextFloat() < 0.10f) {
                    if (pLevel instanceof ServerLevel serverLevel) {
                        Vec3 pos = pLivingEntity.position()
                                .add(0, pLivingEntity.getEyeHeight() * 0.5, 0);

                        serverLevel.sendParticles(ParticleTypes.FLASH,
                                pos.x, pos.y, pos.z, 1, 0, 0, 0, 0.0);
                        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                                net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 0.8F);

                        for (int i = 0; i < 3; i++) {
                            net.minecraft.world.entity.item.ItemEntity remnant =
                                    new net.minecraft.world.entity.item.ItemEntity(
                                            serverLevel, pos.x, pos.y, pos.z,
                                            new ItemStack(com.zipirhavaci.core.ItemRegistry.SCORCHED_REMNANT.get()));
                            remnant.setDeltaMovement(
                                    pLevel.random.nextGaussian() * 0.25,
                                    0.45 + pLevel.random.nextDouble() * 0.15,
                                    pLevel.random.nextGaussian() * 0.25);
                            remnant.lifespan = 120;
                            serverLevel.addFreshEntity(remnant);
                        }

                        com.zipirhavaci.entity.BlazeCoreEffectEntity effect =
                                new com.zipirhavaci.entity.BlazeCoreEffectEntity(serverLevel, pos.x, pos.y, pos.z);
                        serverLevel.addFreshEntity(effect);
                        effect.spawnFakeSoulFire(serverLevel, pos);
                    }

                    pStack.shrink(1);
                    pLivingEntity.stopUsingItem();
                }
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack pStack, Level pLevel, LivingEntity pLivingEntity, int pTimeLeft) {
        if (!(pLivingEntity instanceof Player player)) return;

        int duration = this.getUseDuration(pStack) - pTimeLeft;
        if (duration < 10) return;

        if (!pLevel.isClientSide) {
            float power = getPowerForTime(duration) * 1.2F;

            // Oyuncunun baktığı yönden velocity hesapla
            // xRot = pitch (yukarı/aşağı), yRot = yaw (sağ/sol)
            float yawRad   = (float) Math.toRadians(player.getYRot());
            float pitchRad = (float) Math.toRadians(player.getXRot());

            double vx = -Math.sin(yawRad) * Math.cos(pitchRad);
            double vy = -Math.sin(pitchRad);
            double vz =  Math.cos(yawRad) * Math.cos(pitchRad);

            Vec3 velocity = new Vec3(vx, vy, vz).normalize().scale(power);

            BlazeCoreEntity core = new BlazeCoreEntity(pLevel, player, velocity);
            pLevel.addFreshEntity(core);
        }

        pStack.shrink(1);
    }

    private float getPowerForTime(int duration) {
        float f = (float) duration / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }
}