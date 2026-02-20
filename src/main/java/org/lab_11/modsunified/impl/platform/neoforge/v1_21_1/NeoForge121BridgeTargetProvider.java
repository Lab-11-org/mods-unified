package org.lab_11.modsunified.impl.platform.neoforge.v1_21_1;

import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotBridgeTarget;
import org.lab_11.modsunified.impl.platform.BridgeTargetProvider;

import java.util.List;

public final class NeoForge121BridgeTargetProvider implements BridgeTargetProvider {
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
