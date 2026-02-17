package org.lab_11.modsunified.impl.cookingforblockheads;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class CookingPotBridgeCatalog {
    private CookingPotBridgeCatalog() {
    }

    public static List<CookingPotBridgeTarget> resolveActiveTargets(final Logger logger) {
        final List<CookingPotBridgeTarget> activeTargets = new ArrayList<>();
        for (final CookingPotBridgeTarget target : allTargets()) {
            if (!target.isModSetLoaded()) {
                continue;
            }

            if (target.resolveRecipeClass().isEmpty()
                    || target.resolveRecipeType().isEmpty()
                    || target.resolveBlockEntityClass().isEmpty()
                    || target.resolveBlockEntityType().isEmpty()) {
                logger.warn("Skipping cooking-pot bridge target '{}' because one or more reflective bindings are unavailable.",
                        target.displayName());
                continue;
            }

            activeTargets.add(target);
        }

        return List.copyOf(activeTargets);
    }

    public static String describeTargets(final List<CookingPotBridgeTarget> targets) {
        final List<String> names = new ArrayList<>(targets.size());
        for (final CookingPotBridgeTarget target : targets) {
            names.add(target.displayName());
        }
        return String.join(", ", names);
    }

    private static List<CookingPotBridgeTarget> allTargets() {
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
                        "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotRecipe",
                        "net.yirmiri.dungeonsdelight.core.registry.DDRecipeRegistries",
                        "MONSTER_COOKING_RECIPE_TYPE",
                        "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotBlockEntity",
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
                )
        );
    }
}
