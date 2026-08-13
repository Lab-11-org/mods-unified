package org.lab_11.modsunified.impl.cookingforblockheads;

import org.lab_11.modsunified.impl.platform.RuntimeBindings;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class CookingPotBridgeCatalog {
    private CookingPotBridgeCatalog() {
    }

    public static List<CookingPotBridgeTarget> resolveActiveTargets(final Logger logger) {
        final List<CookingPotBridgeTarget> activeTargets = new ArrayList<>();
        for (final CookingPotBridgeTarget target : allTargets()) {
            final List<String> missingModIds = target.missingRequiredModIds();
            if (!missingModIds.isEmpty()) {
                logger.info(
                        "Skipping cooking-pot bridge target '{}' because required mods are not loaded: {}.",
                        target.displayName(),
                        String.join(", ", missingModIds)
                );
                continue;
            }

            final List<String> unavailableBindings = target.unavailableBindings();
            if (!unavailableBindings.isEmpty()) {
                logger.warn("Skipping cooking-pot bridge target '{}' because bindings are unavailable: {}.",
                        target.displayName(), String.join(", ", unavailableBindings));
                continue;
            }

            activeTargets.add(target);
            logger.info("Enabled cooking-pot bridge target '{}'.", target.displayName());
        }

        return List.copyOf(activeTargets);
    }

    public static String describeTargets(final List<CookingPotBridgeTarget> targets) {
        return targets.stream().map(CookingPotBridgeTarget::displayName).collect(Collectors.joining(", "));
    }

    static List<CookingPotBridgeTarget> allTargets() {
        final var runtime = RuntimeBindings.active();
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
                        "COOKING_POT",
                        List.of(BridgeKeys.MIRRORED_DD_CUP_RECIPE_ID_PREFIX),
                        List.of()
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT,
                        "DungeonsDelight Monster Pot",
                        List.of(BridgeKeys.MOD_DUNGEONS_DELIGHT, BridgeKeys.MOD_FARMERS_DELIGHT),
                        runtime.dungeonMonsterRecipeClassName(),
                        "net.yirmiri.dungeonsdelight.core.registry.DDRecipeRegistries",
                        "MONSTER_COOKING_RECIPE_TYPE",
                        runtime.dungeonMonsterBlockEntityClassName(),
                        "net.yirmiri.dungeonsdelight.core.registry.DDBlockEntities",
                        "MONSTER_COOKING_POT",
                        List.of(),
                        List.of(BridgeKeys.MARKER_DUNGEON_OVEN)
                ),
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
