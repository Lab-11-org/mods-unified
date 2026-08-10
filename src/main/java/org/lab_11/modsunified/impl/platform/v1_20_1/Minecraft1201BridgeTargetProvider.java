package org.lab_11.modsunified.impl.platform.v1_20_1;

import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeTarget;
import org.lab_11.modsunified.impl.platform.BridgeTargetProvider;

import java.util.List;

public final class Minecraft1201BridgeTargetProvider implements BridgeTargetProvider {
    @Override
    public List<CookingPotBridgeTarget> allTargets() {
        return List.of(
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT,
                        "FarmersDelight Cooking Pot",
                        List.of(BridgeKeys.MOD_FARMERS_DELIGHT),
                        "vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
                        "vectorwing.farmersdelight.common.registry.ModRecipeTypes",
                        "COOKING",
                        "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity",
                        "vectorwing.farmersdelight.common.registry.ModBlockEntityTypes",
                        "COOKING_POT"
                ).withDeniedRecipeIdPrefixes(List.of(BridgeKeys.MIRRORED_DD_CUP_RECIPE_ID_PREFIX)),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT,
                        "DungeonsDelight Monster Pot",
                        List.of(BridgeKeys.MOD_DUNGEONS_DELIGHT, BridgeKeys.MOD_FARMERS_DELIGHT),
                        "net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotRecipe",
                        "net.yirmiri.dungeonsdelight.core.registry.DDRecipeRegistries",
                        "MONSTER_COOKING_RECIPE_TYPE",
                        "net.yirmiri.dungeonsdelight.common.block.entity.MonsterPotBlockEntity",
                        "net.yirmiri.dungeonsdelight.core.registry.DDBlockEntities",
                        "MONSTER_COOKING_POT"
                ).withRequiredMarkerKeys(List.of(BridgeKeys.MARKER_DUNGEON_OVEN)),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT,
                        "MinersDelight Copper Pot",
                        List.of(BridgeKeys.MOD_MINERS_DELIGHT, BridgeKeys.MOD_FARMERS_DELIGHT),
                        "vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
                        "vectorwing.farmersdelight.common.registry.ModRecipeTypes",
                        "COOKING",
                        "com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity",
                        "com.sammy.minersdelight.setup.MDBlockEntities",
                        "COPPER_POT"
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT,
                        "KaleidoscopeCookery Pot",
                        List.of(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY),
                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                        "POT_RECIPE",
                        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks",
                        "POT_BE"
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT,
                        "KaleidoscopeCookery Flex Pot",
                        List.of(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY),
                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexPotRecipe",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                        "FLEX_POT_RECIPE",
                        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks",
                        "POT_BE"
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT,
                        "KaleidoscopeCookery Stockpot",
                        List.of(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY),
                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                        "STOCKPOT_RECIPE",
                        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks",
                        "STOCKPOT_BE"
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT,
                        "KaleidoscopeCookery Flex Stockpot",
                        List.of(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY),
                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                        "FLEX_STOCKPOT_RECIPE",
                        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks",
                        "STOCKPOT_BE"
                )
        );
    }
}
