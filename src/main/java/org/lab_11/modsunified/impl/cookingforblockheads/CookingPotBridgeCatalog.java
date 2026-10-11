package org.lab_11.modsunified.impl.cookingforblockheads;

import org.lab_11.modsunified.impl.platform.RuntimeBindings;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CookingPotBridgeCatalog {
    private static final List<CookingPotBridgeTarget> ALL_TARGETS = createTargets();
    private static final Map<String, CookingPotBridgeTarget> TARGETS_BY_KEY = ALL_TARGETS.stream()
            .collect(Collectors.toUnmodifiableMap(CookingPotBridgeTarget::targetKey, target -> target));

    private CookingPotBridgeCatalog() {
    }

    public static List<CookingPotBridgeTarget> resolveActiveTargets(final Logger logger) {
        final List<CookingPotBridgeTarget> activeTargets = new ArrayList<>();
        for (final CookingPotBridgeTarget target : ALL_TARGETS) {
            final List<String> missingModIds = target.missingRequiredModIds();
            if (!missingModIds.isEmpty()) {
                logger.info(
                        "Skipping cooking-pot bridge target '{}' because required mods are not loaded: {}.",
                        target.displayName(),
                        String.join(", ", missingModIds)
                );
                continue;
            }

            final List<String> unavailableContainerBindings = target.unavailableContainerBindings();
            if (!unavailableContainerBindings.isEmpty()) {
                logger.warn("Skipping cooking-pot bridge target '{}' because container bindings are unavailable: {}.",
                        target.displayName(), String.join(", ", unavailableContainerBindings));
                continue;
            }

            int availableRecipeBindings = 0;
            for (final CookingPotBridgeTarget.RecipeBinding binding : target.recipeBindings()) {
                final List<String> unavailableBindings = binding.unavailableBindings();
                if (unavailableBindings.isEmpty()) {
                    availableRecipeBindings++;
                } else {
                    logger.warn("Skipping recipe binding '{}' for cooking-pot bridge target '{}' because bindings are unavailable: {}.",
                            binding.recipeClassName(), target.displayName(), String.join(", ", unavailableBindings));
                }
            }
            if (availableRecipeBindings == 0) {
                logger.warn("Skipping cooking-pot bridge target '{}' because no recipe bindings are available.",
                        target.displayName());
                continue;
            }

            activeTargets.add(target);
            logger.info("Enabled cooking-pot bridge target '{}' with {} recipe binding(s).",
                    target.displayName(), availableRecipeBindings);
        }

        return List.copyOf(activeTargets);
    }

    public static String describeTargets(final List<CookingPotBridgeTarget> targets) {
        return targets.stream().map(CookingPotBridgeTarget::displayName).collect(Collectors.joining(", "));
    }

    public static Optional<CookingPotBridgeTarget> findByKey(final String targetKey) {
        return Optional.ofNullable(TARGETS_BY_KEY.get(targetKey));
    }

    public static boolean isTargetKey(final String targetKey) {
        return TARGETS_BY_KEY.containsKey(targetKey);
    }

    static List<CookingPotBridgeTarget> allTargets() {
        return ALL_TARGETS;
    }

    private static List<CookingPotBridgeTarget> createTargets() {
        final var runtime = RuntimeBindings.active();
        return List.of(
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT,
                        "FarmersDelight Cooking Pot",
                        List.of(BridgeKeys.MOD_FARMERS_DELIGHT),
                        "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity",
                        "vectorwing.farmersdelight.common.registry.ModBlockEntityTypes",
                        "COOKING_POT",
                        List.of(),
                        List.of(new CookingPotBridgeTarget.RecipeBinding(
                                "vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
                                "vectorwing.farmersdelight.common.registry.ModRecipeTypes",
                                "COOKING",
                                List.of(BridgeKeys.MIRRORED_DD_CUP_RECIPE_ID_PREFIX)
                        ))
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT,
                        "DungeonsDelight Monster Pot",
                        List.of(BridgeKeys.MOD_DUNGEONS_DELIGHT, BridgeKeys.MOD_FARMERS_DELIGHT),
                        runtime.dungeonMonsterBlockEntityClassName(),
                        "net.yirmiri.dungeonsdelight.core.registry.DDBlockEntities",
                        "MONSTER_COOKING_POT",
                        List.of(BridgeKeys.MARKER_DUNGEON_OVEN),
                        List.of(new CookingPotBridgeTarget.RecipeBinding(
                                runtime.dungeonMonsterRecipeClassName(),
                                "net.yirmiri.dungeonsdelight.core.registry.DDRecipeRegistries",
                                "MONSTER_COOKING_RECIPE_TYPE"
                        ))
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT,
                        "MinersDelight Copper Pot",
                        List.of(BridgeKeys.MOD_MINERS_DELIGHT, BridgeKeys.MOD_FARMERS_DELIGHT),
                        "com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity",
                        "com.sammy.minersdelight.setup.MDBlockEntities",
                        "COPPER_POT",
                        List.of(),
                        List.of(new CookingPotBridgeTarget.RecipeBinding(
                                "vectorwing.farmersdelight.common.crafting.CookingPotRecipe",
                                "vectorwing.farmersdelight.common.registry.ModRecipeTypes",
                                "COOKING"
                        ))
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT,
                        "KaleidoscopeCookery Pot",
                        List.of(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY),
                        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks",
                        "POT_BE",
                        List.of(),
                        List.of(
                                new CookingPotBridgeTarget.RecipeBinding(
                                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe",
                                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                                        "POT_RECIPE"
                                ),
                                new CookingPotBridgeTarget.RecipeBinding(
                                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexPotRecipe",
                                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                                        "FLEX_POT_RECIPE"
                                )
                        )
                ),
                new CookingPotBridgeTarget(
                        BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT,
                        "KaleidoscopeCookery Stockpot",
                        List.of(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY),
                        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity",
                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks",
                        "STOCKPOT_BE",
                        List.of(),
                        List.of(
                                new CookingPotBridgeTarget.RecipeBinding(
                                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe",
                                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                                        "STOCKPOT_RECIPE"
                                ),
                                new CookingPotBridgeTarget.RecipeBinding(
                                        "com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe",
                                        "com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes",
                                        "FLEX_STOCKPOT_RECIPE"
                                )
                        )
                )
        );
    }
}
