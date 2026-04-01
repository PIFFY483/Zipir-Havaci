package com.zipirhavaci.core;

import com.zipirhavaci.block.DoorShakeBlock;
import com.zipirhavaci.block.DoorShakePlaceholderBlock;
import com.zipirhavaci.block.FakeMovingBlock;
import com.zipirhavaci.block.entity.DoorShakeBlockEntity;
import com.zipirhavaci.block.entity.DoorShakePlaceholderEntity;
import com.zipirhavaci.block.HeavyPistonBlock;
import com.zipirhavaci.block.CoreRodBlock;
import com.zipirhavaci.block.entity.FakeMovingBlockEntity;
import com.zipirhavaci.block.entity.HeavyPistonBlockEntity;
import com.zipirhavaci.energy.EnergyCarrierBlock;
import com.zipirhavaci.energy.FlagGroup;
import com.zipirhavaci.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.zipirhavaci.block.entity.CoreRodBlockEntity;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "zipirhavaci");
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "zipirhavaci");
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "zipirhavaci");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "zipirhavaci");

    // --- HEAVY PISTON ---
    public static final RegistryObject<Block> HEAVY_PISTON_BLOCK = BLOCKS.register("heavy_piston",
            () -> new HeavyPistonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final RegistryObject<Item> HEAVY_PISTON = ITEMS.register("heavy_piston",
            () -> new BlockItem(HEAVY_PISTON_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<HeavyPistonBlockEntity>> HEAVY_PISTON_BE = BLOCK_ENTITIES.register("heavy_piston_be",
            () -> BlockEntityType.Builder.of(HeavyPistonBlockEntity::new, HEAVY_PISTON_BLOCK.get()).build(null));

    // --- CORE ROD ---
    public static final RegistryObject<Block> CORE_ROD_BLOCK = BLOCKS.register("core_rod",
            () -> new CoreRodBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(CoreRodBlock.POWERED) ? 15 : 0)));

    public static final RegistryObject<Item> CORE_ROD = ITEMS.register("core_rod",
            () -> new BlockItem(CORE_ROD_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<CoreRodBlockEntity>> CORE_ROD_BE = BLOCK_ENTITIES.register("core_rod_be",
            () -> BlockEntityType.Builder.of(CoreRodBlockEntity::new, CORE_ROD_BLOCK.get()).build(null));

    // --- ENERGY CARRIER ---
    public static final RegistryObject<Block> ENERGY_CARRIER = BLOCKS.register("energy_carrier",
            () -> new EnergyCarrierBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .noOcclusion()));

    public static final RegistryObject<Item> ENERGY_CARRIER_ITEM = ITEMS.register("energy_carrier",
            () -> new BlockItem(ENERGY_CARRIER.get(), new Item.Properties()));

    // --- FAKE MOVING BLOCK ---
    public static final RegistryObject<Block> FAKE_MOVING_BLOCK = BLOCKS.register("fake_moving_block",
            () -> new FakeMovingBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()
                    .strength(-1.0f)));

    public static final RegistryObject<BlockEntityType<FakeMovingBlockEntity>> FAKE_MOVING_BLOCK_BE = BLOCK_ENTITIES.register("fake_moving_block_be",
            () -> BlockEntityType.Builder.of(FakeMovingBlockEntity::new, FAKE_MOVING_BLOCK.get()).build(null));

    // --- DOOR SHAKE ---
    public static final RegistryObject<Block> DOOR_SHAKE_BLOCK = BLOCKS.register("door_shake_block",
            () -> new DoorShakeBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(-1.0f)
                    .noLootTable()));

    public static final RegistryObject<BlockEntityType<DoorShakeBlockEntity>> DOOR_SHAKE_BE = BLOCK_ENTITIES.register("door_shake_be",
            () -> BlockEntityType.Builder.of(DoorShakeBlockEntity::new, DOOR_SHAKE_BLOCK.get()).build(null));

    public static final RegistryObject<Block> DOOR_SHAKE_PLACEHOLDER = BLOCKS.register("door_shake_placeholder",
            () -> new DoorShakePlaceholderBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(-1.0f)
                    .noLootTable()));

    public static final RegistryObject<Item> REDEMPTIONS_LIGHT = ITEMS.register("redemptions_light",
            () -> new Item(new Item.Properties()
                    .stacksTo(16) // Özel iksir 16'lı istiflenir
                    .rarity(Rarity.RARE) // Nadir eşya rengi (Mavi)
                    .food(new FoodProperties.Builder()
                            .alwaysEat()
                            .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.JUMP, 600, 1), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 600, 0), 1.0F)
                            .build())) {

                @Override
                public UseAnim getUseAnimation(ItemStack stack) {
                    return UseAnim.DRINK;
                }

                @Override
                public SoundEvent getDrinkingSound() {
                    return SoundEvents.GENERIC_DRINK;
                }

                @Override
                public boolean isFoil(ItemStack pStack) {
                    return true;
                }
                // ------------------------
            });

    public static final RegistryObject<BlockEntityType<DoorShakePlaceholderEntity>> DOOR_SHAKE_PLACEHOLDER_BE = BLOCK_ENTITIES.register("door_shake_placeholder_be",
            () -> BlockEntityType.Builder.of(DoorShakePlaceholderEntity::new, DOOR_SHAKE_PLACEHOLDER.get()).build(null));

    // --- EŞYA KAYITLARI ---
    public static final RegistryObject<Item> ZIPIR_AVIATOR = ITEMS.register("zipir_aviator",
            () -> new ZipirAviatorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> METEOR_IMPACT_SCROLL = ITEMS.register("meteor_impact_scroll",
            () -> new MeteorImpactScrollItem());

    public static final RegistryObject<Item> SOUL_BOND_SCROLL = ITEMS.register("soul_bond_scroll",
            () -> new SoulBondScrollItem());

    public static final RegistryObject<Item> HEAVY_METAL = ITEMS.register("heavy_metal",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> BLAZE_CORE = ITEMS.register("blaze_core",
            () -> new BlazeCoreItem(new Item.Properties().rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> GENERATOR = ITEMS.register("generator",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC)) {
                @Override public boolean isFoil(ItemStack pStack) { return true; }
            });

    public static final RegistryObject<Item> SCORCHED_REMNANT = ITEMS.register("scorched_remnant",
            () -> new Item(new Item.Properties()
                    .rarity(Rarity.EPIC)) {
                @Override
                public boolean isFoil(ItemStack pStack) {
                    return true;
                }
            });

    public static final RegistryObject<Item> BLAZING_MASS = ITEMS.register("blazing_mass",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SCORCHED_IMPELLER_ARROW = ITEMS.register("scorched_impeller_arrow",
            () -> new AnomalousProjectileItem(new Item.Properties()));

    public static final RegistryObject<Item> SINGULARITY_REMNANT_ARROW = ITEMS.register("singularity_remnant_arrow",
            () -> new AnomalousProjectileItem(new Item.Properties()));

    public static final RegistryObject<Item> ANIMA_BOOK = ITEMS.register("anima",
            () -> new AnimaItem());

    // --- DARK AURA ---
    public static final RegistryObject<Item> CURSED_BOOK = ITEMS.register("cursed_book",
            () -> new CursedBookItem());

    // --- STRAINED GRIEF ARMOR ---
    public static final RegistryObject<Item> STRAINED_GRIEF_HELMET = ITEMS.register("strained_grief_helmet",
            () -> new StrainedGriefArmorItem(ArmorItem.Type.HELMET, new Item.Properties()));

    public static final RegistryObject<Item> STRAINED_GRIEF_CHESTPLATE = ITEMS.register("strained_grief_chestplate",
            () -> new StrainedGriefArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Properties()));

    public static final RegistryObject<Item> STRAINED_GRIEF_LEGGINGS = ITEMS.register("strained_grief_leggings",
            () -> new StrainedGriefArmorItem(ArmorItem.Type.LEGGINGS, new Item.Properties()));

    public static final RegistryObject<Item> STRAINED_GRIEF_BOOTS = ITEMS.register("strained_grief_boots",
            () -> new StrainedGriefArmorItem(ArmorItem.Type.BOOTS, new Item.Properties()));

    public static final RegistryObject<Item> RECIPE_NOTE =
            ITEMS.register("recipe_note",
                    () -> new RecipeNoteItem(new Item.Properties()));

    // --- KREATİF TAB ---
    public static final RegistryObject<CreativeModeTab> ZIPIR_TAB = CREATIVE_MODE_TABS.register("zipir_tab", () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(ZIPIR_AVIATOR.get()))
            .title(Component.translatable("creativetab.zipir_tab"))
            .displayItems((parameters, output) -> {
                output.accept(ZIPIR_AVIATOR.get());
                output.accept(HEAVY_PISTON.get());
                output.accept(CORE_ROD.get());
                output.accept(ENERGY_CARRIER_ITEM.get());
                output.accept(BLAZE_CORE.get());
                output.accept(GENERATOR.get());
                output.accept(HEAVY_METAL.get());
                output.accept(SOUL_BOND_SCROLL.get());
                output.accept(METEOR_IMPACT_SCROLL.get());
                output.accept(BLAZING_MASS.get());
                output.accept(SCORCHED_REMNANT.get());
                output.accept(SCORCHED_IMPELLER_ARROW.get());
                output.accept(SINGULARITY_REMNANT_ARROW.get());
                output.accept(REDEMPTIONS_LIGHT.get());
                output.accept(STRAINED_GRIEF_BOOTS.get());
                output.accept(STRAINED_GRIEF_LEGGINGS.get());
                output.accept(STRAINED_GRIEF_CHESTPLATE.get());
                output.accept(STRAINED_GRIEF_HELMET.get());
                output.accept(RECIPE_NOTE.get());
            }).build());
}