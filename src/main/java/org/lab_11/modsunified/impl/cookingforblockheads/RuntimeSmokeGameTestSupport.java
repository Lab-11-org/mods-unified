package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RuntimeSmokeGameTestSupport {
    private RuntimeSmokeGameTestSupport() {
    }

    static void verify(final GameTestHelper helper,
                       final Logger logger,
                       final List<String> fullRuntimeTargetKeys,
                       final boolean verifyRecipeManagerLookup,
                       final boolean verifyLegacyRegistry) {
        final List<String> expected = Boolean.getBoolean("lab11.fullRuntime")
                ? fullRuntimeTargetKeys
                : CookingPotBridgeCatalog.allTargets().stream()
                .filter(target -> target.missingRequiredModIds().isEmpty())
                .map(CookingPotBridgeTarget::targetKey)
                .toList();
        final List<String> active = CookingPotBridgeCatalog.resolveActiveTargets(logger).stream()
                .map(CookingPotBridgeTarget::targetKey)
                .toList();
        if (!active.equals(expected)) {
            helper.fail("Loaded bridge targets did not resolve: expected=" + expected + ", active=" + active);
        }

        final var recipeManager = helper.getLevel().getServer().getRecipeManager();
        final List<Recipe<?>> indexedRecipes = CookingPotRecipeIndexer.indexedRecipes(recipeManager);
        verifyIndexedCoverage(helper, expected, indexedRecipes);
        verifyPrefilledContainerStatusCredit(helper);
        verifyStoveTopology(helper, BridgeKeys.MOD_FARMERS_DELIGHT);
        verifyStoveTopology(helper, BridgeKeys.MOD_KALEIDOSCOPE_COOKERY);

        if (verifyLegacyRegistry && LegacyCookingRegistryBridge.countInjectedRecipes() != indexedRecipes.size()) {
            helper.fail("Legacy CFBH registry does not contain every indexed recipe");
        }
        if (verifyRecipeManagerLookup) {
            for (final var recipeId : CookingPotRecipeIndexer.indexedRecipeIds(recipeManager)) {
                if (recipeManager.byKey(recipeId).isEmpty()) {
                    helper.fail("RecipeManager.byKey did not resolve indexed recipe " + recipeId);
                }
            }
        }
    }

    private static void verifyIndexedCoverage(final GameTestHelper helper,
                                              final List<String> expectedTargetKeys,
                                              final List<Recipe<?>> indexedRecipes) {
        for (final String targetKey : expectedTargetKeys) {
            final CookingPotBridgeTarget target = CookingPotBridgeCatalog.findByKey(targetKey).orElse(null);
            if (target == null || !target.missingRequiredModIds().isEmpty()
                    || !target.unavailableContainerBindings().isEmpty()) {
                helper.fail("Expected bridge target is unavailable: " + targetKey);
                continue;
            }

            final List<CookingPotIndexedRecipe> targetRecipes = indexedRecipes.stream()
                    .filter(CookingPotIndexedRecipe.class::isInstance)
                    .map(CookingPotIndexedRecipe.class::cast)
                    .filter(recipe -> targetKey.equals(recipe.targetKey()))
                    .toList();
            if (targetRecipes.isEmpty()) {
                helper.fail("Bridge target produced no indexed recipes: " + targetKey);
            }

            for (final CookingPotBridgeTarget.RecipeBinding binding : target.recipeBindings()) {
                final Class<?> recipeClass = binding.resolveRecipeClass().orElse(null);
                if (!binding.unavailableBindings().isEmpty() || recipeClass == null) {
                    helper.fail("Recipe binding is unavailable: " + binding.recipeClassName());
                    continue;
                }
                final boolean indexed = targetRecipes.stream().anyMatch(recipe ->
                        recipe.delegateRecipe() != null && recipe.delegateRecipe().getClass().equals(recipeClass));
                if (!indexed) {
                    helper.fail("Recipe binding produced no indexed recipes: " + binding.recipeClassName());
                }
            }
        }
    }

    private static void verifyPrefilledContainerStatusCredit(final GameTestHelper helper) {
        final MarkerProviderView creditedPot = new MarkerProviderView() {
            @Override
            public boolean isMarkerKey(final String markerKey) {
                return BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT.equals(markerKey);
            }

            @Override
            public boolean isActiveForCurrentTableMarker() {
                return true;
            }

            @Override
            public boolean isTargetPotConnectedForCurrentTableMarker() {
                return true;
            }

            @Override
            public ItemStack netContainerRequired(final Recipe<?> recipe, final ItemStack fullContainerCost) {
                return fullContainerCost.copyWithCount(1);
            }
        };
        final ItemStack fullCost = new ItemStack(Items.BOWL, 2);
        final ItemStack netCost = CookingPotContainerCost.netContainerCostForStatus(
                List.of(creditedPot),
                BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT,
                null,
                fullCost
        );
        if (netCost.getCount() != 1 || fullCost.getCount() != 2) {
            helper.fail("Recipe status did not credit prefilled serving containers");
        }
    }

    private static void verifyStoveTopology(final GameTestHelper helper, final String stoveModId) {
        final ResourceLocation tableId = MinecraftApiCompat.resourceLocation(
                BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS, "white_cooking_table");
        final ResourceLocation stoveId = MinecraftApiCompat.resourceLocation(
                stoveModId, "stove");
        final Block table = BuiltInRegistries.BLOCK.getOptional(tableId).orElseGet(() ->
                BuiltInRegistries.BLOCK.getOptional(MinecraftApiCompat.resourceLocation(
                        BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS, "cooking_table")).orElse(null));
        final Block stove = BuiltInRegistries.BLOCK.getOptional(stoveId).orElse(null);
        if (table == null || stove == null) {
            return;
        }

        final BlockPos tableRelative = new BlockPos(1, 1, 1);
        final BlockPos stoveRelative = tableRelative.east();
        final BlockPos potRelative = stoveRelative.above();
        helper.setBlock(tableRelative, table.defaultBlockState());
        helper.setBlock(stoveRelative, stove.defaultBlockState().setValue(BlockStateProperties.LIT, true));

        final Level level = helper.getLevel();
        final BlockPos tablePos = helper.absolutePos(tableRelative);
        final BlockPos potPos = helper.absolutePos(potRelative);
        final Map<ResourceLocation, String> stoveTargets = Map.of(
                MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_FARMERS_DELIGHT, "cooking_pot"),
                BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT,
                MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_MINERS_DELIGHT, "copper_pot"),
                BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT,
                MinecraftApiCompat.resourceLocation("miners_delight", "copper_pot"),
                BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT,
                MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "pot"),
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT,
                MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "stockpot"),
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT
        );
        for (final var target : stoveTargets.entrySet()) {
            final Block pot = BuiltInRegistries.BLOCK.getOptional(target.getKey()).orElse(null);
            if (pot == null) {
                continue;
            }
            helper.setBlock(potRelative, pot.defaultBlockState());
            final BlockEntity potBlockEntity = level.getBlockEntity(potPos);
            if (potBlockEntity == null
                    || !CookingPotHeatBridge.isTargetPotConnectedForCookingTable(
                    potBlockEntity, target.getValue())
                    || !CookingPotHeatBridge.tryIgniteManagedOvenForPot(potBlockEntity)) {
                helper.fail(stoveId + " cookware is not an active move target: " + target.getKey());
                return;
            }
            verifyKitchenTraversal(helper, level, tablePos, potPos, stoveId, target.getKey());
        }
    }

    private static void verifyKitchenTraversal(final GameTestHelper helper,
                                               final Level level,
                                               final BlockPos tablePos,
                                               final BlockPos potPos,
                                               final ResourceLocation stoveId,
                                               final ResourceLocation potId) {
        try {
            final Object kitchen = buildKitchen(level, tablePos);
            final Field checkedPosField = CfbhRuntime.findField(kitchen, "checkedPos");
            if (checkedPosField == null) {
                helper.fail("Cooking for Blockheads kitchen topology exposes no checked positions");
                return;
            }
            checkedPosField.setAccessible(true);
            final Object checkedPos = checkedPosField.get(kitchen);
            if (!(checkedPos instanceof Set<?> positions) || !positions.contains(potPos)) {
                helper.fail("Cooking for Blockheads did not traverse " + stoveId + " to " + potId);
            }
        } catch (ReflectiveOperationException e) {
            helper.fail("Failed to inspect Cooking for Blockheads stove topology: " + e);
        }
    }

    private static Object buildKitchen(final Level level, final BlockPos tablePos) throws ReflectiveOperationException {
        try {
            return Class.forName("net.blay09.mods.cookingforblockheads.crafting.KitchenImpl")
                    .getConstructor(Level.class, BlockPos.class)
                    .newInstance(level, tablePos);
        } catch (ClassNotFoundException ignored) {
            return Class.forName("net.blay09.mods.cookingforblockheads.KitchenMultiBlock")
                    .getMethod("buildFromLocation", Level.class, BlockPos.class)
                    .invoke(null, level, tablePos);
        }
    }

}
