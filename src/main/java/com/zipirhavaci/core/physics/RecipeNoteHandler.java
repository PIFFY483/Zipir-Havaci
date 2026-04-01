package com.zipirhavaci.core.physics;

import com.zipirhavaci.core.ItemRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RecipeNoteHandler {


    private static final String[] RECIPE_IDS = {
            "blaze_core",
            "generator",
            "heavy_metal",
            "heavy_piston",
            "redemptions_light",
            "scorched_impeller_arrow_recipe",
            "singularity_remnant_arrow_recipe",
            "smelt_remnant_to_mass",
            "strained_grief_boots",
            "strained_grief_chestplate",
            "strained_grief_helmet",
            "strained_grief_leggings",
            "zipir_aviator",
            "energy_carrier",
            "core_rod",
            "blaze_core_to_remnant",
            "gunpowder_craft"
    };

    /** Hangi loot tablolarına enjekte edileceği */
    private static final List<String> TARGET_LOOT_TABLES = List.of(
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/village/village_weaponsmith",
            "minecraft:chests/village/village_toolsmith",
            "minecraft:chests/village/village_armorer",
            "minecraft:chests/village/village_temple",
            "minecraft:chests/village/village_cartographer",
            "minecraft:chests/jungle_temple",
            "minecraft:chests/desert_pyramid",
            "minecraft:chests/pillager_outpost",
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/nether_bridge",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/bastion_other",
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/ancient_city",
            "minecraft:chests/shipwreck_supply",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/ruined_portal",
            "minecraft:chests/buried_treasure"
    );

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        String tableName = event.getName().toString();
        if (!TARGET_LOOT_TABLES.contains(tableName)) return;

        for (String recipeId : RECIPE_IDS) {
            String fullId = "zipirhavaci:" + recipeId;

            CompoundTag nbt = new CompoundTag();
            nbt.putString("RecipeId", fullId);

            LootPool pool = LootPool.lootPool()
                    .name("recipe_note_" + recipeId)
                    .setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(0.08f))
                    .add(
                            LootItem.lootTableItem(ItemRegistry.RECIPE_NOTE.get())
                                    .apply(SetNbtFunction.setTag(nbt))
                    )
                    .build();

            event.getTable().addPool(pool);
        }
    }
}