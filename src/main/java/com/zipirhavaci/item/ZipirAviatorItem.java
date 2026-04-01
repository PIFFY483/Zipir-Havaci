package com.zipirhavaci.item;

import com.zipirhavaci.client.renderer.ZipirAviatorRenderer;
import com.zipirhavaci.client.sound.ClientSoundManager;
import com.zipirhavaci.core.SoundRegistry;
import com.zipirhavaci.network.DeployItemPacket;
import com.zipirhavaci.network.PacketHandler;
import com.zipirhavaci.network.FireItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class ZipirAviatorItem extends Item implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    //  Passive phase sync
    private long passiveStartTime = System.currentTimeMillis();
    private boolean soundRunning = false;
    private static final long PASSIVE_MS = 4000L;
    private long pauseTimestamp = 0;
    private boolean wasPaused = false;

    public ZipirAviatorItem(Properties properties) {
        super(properties);
    }


    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide && entity instanceof Player player) {
            if (isSelected) {
                CompoundTag tag = stack.getOrCreateTag();

                // GÜVENLİK: Cooldown varsa bayrağı dik ve çık
                if (player.getCooldowns().isOnCooldown(this)) {
                    tag.putBoolean("IsEquipped", true);
                    return;
                }

                if (!tag.getBoolean("IsEquipped")) {
                    tag.putBoolean("IsEquipped", true);

                    // TPV BARAJI
                    boolean isFirstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();

                    // Sadece FPV ise paket gönder ve ses çal
                    if (isFirstPerson) {
                        long lastCooldown = tag.getLong("LastCooldownAnimTime");
                        long currentTime = System.currentTimeMillis();

                        if (currentTime - lastCooldown > 6500) {
                            // Paketi doğrudan 'true' (isFirstPerson) olarak gönder
                            PacketHandler.sendToServer(new DeployItemPacket(true));
                            player.playSound(SoundRegistry.DEPLOY_SOUND.get(), 1.0F, 1.0F);
                        }
                    }
                }
            } else {
                // Silah elden bırakıldığında sıfırla
                stack.getOrCreateTag().putBoolean("IsEquipped", false);
            }
        }
    }

    //  NBT değişiminde reequip animasyonunu engelle
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Sadece slot değiştiğinde animasyon oynat
        // NBT değişimlerinde (Uses, IsEquipped vs.) animasyon OYNATMA
        if (!slotChanged) {
            return false;
        }
        // Item tipinin değişip değişmediğini kontrol et
        return oldStack.getItem() != newStack.getItem();
    }

    // ================= RENDERER =================

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ZipirAviatorRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new ZipirAviatorRenderer();
                return renderer;
            }
        });
    }

    // ================= ANIMATION =================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main_controller", 0, event -> {
            var controller = event.getController();
            String anim = controller.getCurrentAnimation() == null ? "" : controller.getCurrentAnimation().animation().name();
            var mc = Minecraft.getInstance();

            // 1. DURUM KONTROLLERİ
            boolean isHeld = mc.player != null && (mc.player.getMainHandItem().getItem() == this || mc.player.getOffhandItem().getItem() == this);

            if (!isHeld || mc.isPaused()) {
                if (soundRunning) {
                    ClientSoundManager.stopIdle();
                    soundRunning = false;
                }
                wasPaused = true;
                return event.setAndContinue(RawAnimation.begin().thenLoop("animation.gauntlet.passive"));
            }

            // 2. PASİF SENKRONİZASYON
            if (anim.equals("animation.gauntlet.passive")) {
                if (wasPaused || !soundRunning) {
                    controller.forceAnimationReset();
                    ClientSoundManager.startIdle(mc.player, 0f);
                    soundRunning = true;
                    wasPaused = false;
                }
            } else if (soundRunning) {
                ClientSoundManager.stopIdle();
                soundRunning = false;
            }

            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.gauntlet.passive"));
        })
                .triggerableAnim("fire", RawAnimation.begin().thenPlay("animation.gauntlet.fire"))
                .triggerableAnim("tpv_fire", RawAnimation.begin().thenPlay("animation.gauntlet.tpv_fire"))
                .triggerableAnim("cooldown", RawAnimation.begin().thenPlay("animation.gauntlet.cooldown"))
                .triggerableAnim("deploy", RawAnimation.begin().thenPlay("animation.gauntlet.deploy")));
    }

    // ================= FIRE =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand == InteractionHand.MAIN_HAND) {

            if (!player.getAbilities().instabuild) {
                boolean hasGunpowder = player.getInventory().contains(new ItemStack(net.minecraft.world.item.Items.GUNPOWDER));

                if (!hasGunpowder) {
                    if (level.isClientSide) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cFuel exhausted! §7Gunpowder required."));
                    }
                    return InteractionResultHolder.fail(stack);
                }
            }

            // CLIENT Ses durdur ve kamera modunu belirle
            if (level.isClientSide) {
                ClientSoundManager.stopIdle();
                soundRunning = false;
                passiveStartTime = System.currentTimeMillis();

                // Kamera modunu kontrol et ve server'a bildir
                boolean isTPV = !Minecraft.getInstance().options.getCameraType().isFirstPerson();
                PacketHandler.sendToServer(new FireItemPacket(isTPV));
            }

            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    // ================= GEO =================

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}