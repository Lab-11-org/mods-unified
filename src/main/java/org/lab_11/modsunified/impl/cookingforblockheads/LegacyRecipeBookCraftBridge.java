package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.lab_11.modsunified.impl.platform.RuntimeBindings;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class LegacyRecipeBookCraftBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String FEEDBACK_MOVED_TO_POT_KEY = "lab_11_mods_unified.feedback.cooking_table.moved_to_pot";
    private static final String FEEDBACK_POT_NOT_CONNECTED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_not_connected";
    private static final String FEEDBACK_POT_TRANSFER_FAILED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_transfer_failed";
    private static volatile boolean hookObserved;
    private static volatile boolean diagnosticsLogged;

    private LegacyRecipeBookCraftBridge() {
    }

    public static void logKitchenDiagnosticsOnce(final Object recipeBookMenu) {
        if (diagnosticsLogged) {
            return;
        }
        diagnosticsLogged = true;

        final Player player = readFieldValue(recipeBookMenu, "player", Player.class);
        final Object multiBlock = readFieldValue(recipeBookMenu, "multiBlock", Object.class);
        if (player == null || multiBlock == null) {
            LOGGER.warn("Legacy CFBH diagnostics: menu has no player or kitchen multiblock.");
            return;
        }

        final List<String> connectedBlocks = new ArrayList<>();
        final Object checked = readFieldValue(multiBlock, "checkedPos", Object.class);
        if (checked instanceof Iterable<?> positions) {
            for (final Object value : positions) {
                if (!(value instanceof BlockPos pos)) {
                    continue;
                }
                final BlockEntity blockEntity = player.level().getBlockEntity(pos);
                connectedBlocks.add(pos + "=" + (blockEntity == null
                        ? player.level().getBlockState(pos).getBlock().getClass().getName()
                        : blockEntity.getClass().getName()));
            }
        }

        int kaleidoscopeRecipes = 0;
        for (final Recipe<?> recipe : CookingPotRecipeIndexer.indexedRecipes()) {
            if (recipe instanceof CookingPotIndexedRecipe indexed
                    && (BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(indexed.targetKey())
                    || BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexed.targetKey()))) {
                kaleidoscopeRecipes++;
            }
        }
        LOGGER.info("Legacy CFBH diagnostics: indexedKaleidoscopeRecipes={}, connectedBlocks={}",
                kaleidoscopeRecipes, connectedBlocks);
        final List<?> providers = resolveItemProviders(multiBlock, player);
        final Object hasOvenValue = invokeNoArg(multiBlock, "hasSmeltingProvider");
        LegacyCookingRegistryBridge.logKaleidoscopeStatusSummary(
                providers,
                hasOvenValue instanceof Boolean hasOven && hasOven
        );
    }

    public static void restoreLegacyRecipes(final Object recipeBookMenu) {
        final Player player = readFieldValue(recipeBookMenu, "player", Player.class);
        if (player == null || player.level().isClientSide) {
            return;
        }
        final int restored = LegacyCookingRegistryBridge.ensureIndexedRecipesPresent(
                player.level().registryAccess());
        if (restored > 0) {
            LOGGER.info("Restored {} cooking-pot recipes after the legacy CFBH registry reset.", restored);
        }
    }

    public static boolean tryHandleRecipeBookCraft(final Object recipeBookMenu) {
        final Object selectedRecipe = invokeNoArg(recipeBookMenu, "getSelection");
        final ItemStack selectedOutputItem = readOutputItem(selectedRecipe);
        final Object selectedRecipeType = readRecipeType(selectedRecipe);
        final List<ItemStack> displayedMatrixStacks = readDisplayedMatrixStacks(recipeBookMenu);
        return tryHandleRecipeBookCraft(recipeBookMenu, selectedOutputItem, selectedRecipeType, displayedMatrixStacks);
    }

    public static boolean tryHandleRecipeBookCraft(final Object recipeBookMenu,
                                                   final ItemStack requestedOutputItem,
                                                   final Object requestedRecipeType,
                                                   final List<ItemStack> requestedMatrixStacks) {
        if (!hookObserved) {
            hookObserved = true;
            LOGGER.info(
                    "Observed legacy RecipeBookMenu.tryCraft hook for output='{}', recipeType='{}', matrixSize={}.",
                    requestedOutputItem == null ? "<null>" : requestedOutputItem.getItem(),
                    requestedRecipeType == null ? "<null>" : requestedRecipeType,
                    requestedMatrixStacks == null ? 0 : requestedMatrixStacks.size()
            );
        }

        final Player player = readFieldValue(recipeBookMenu, "player", Player.class);
        if (player == null || player.level().isClientSide) {
            return false;
        }

        if (!isCraftingRecipeType(requestedRecipeType)) {
            return false;
        }

        final ItemStack outputItem = requestedOutputItem == null ? ItemStack.EMPTY : requestedOutputItem.copy();
        if (outputItem.isEmpty()) {
            return false;
        }

        final List<ItemStack> matrixStacks = sanitizeMatrixStacks(requestedMatrixStacks);
        if (matrixStacks.isEmpty()) {
            return false;
        }

        final Object multiBlock = readFieldValue(recipeBookMenu, "multiBlock", Object.class);
        if (multiBlock == null) {
            return false;
        }

        final List<CookingPotIndexedRecipe> indexedCandidates = findIndexedCandidates(player, outputItem, matrixStacks);
        if (indexedCandidates.isEmpty()) {
            return false;
        }

        final List<CookingPotBridgeTarget> allTargets = RuntimeBindings.active().bridgeTargetProvider().allTargets();
        for (final CookingPotIndexedRecipe indexedRecipe : indexedCandidates) {
            final CookingPotBridgeTarget target = findTargetByKey(allTargets, indexedRecipe.targetKey());
            if (target == null || !target.isModSetLoaded()) {
                continue;
            }

            final List<BlockEntity> targetPots = findConnectedPots(
                    level(player), multiBlock, target, indexedRecipe.targetKey());
            if (targetPots.isEmpty()) {
                continue;
            }

            if (!routeIngredientsToPot(recipeBookMenu, player, multiBlock, targetPots, indexedRecipe, matrixStacks, outputItem)) {
                sendFeedback(player, FEEDBACK_POT_TRANSFER_FAILED_KEY, ChatFormatting.RED);
                return true;
            }

            sendFeedback(player, FEEDBACK_MOVED_TO_POT_KEY, ChatFormatting.YELLOW);
            invokeNoArg(recipeBookMenu, "broadcastChanges");
            return true;
        }

        sendFeedback(player, FEEDBACK_POT_NOT_CONNECTED_KEY, ChatFormatting.RED);
        return true;
    }

    private static boolean routeIngredientsToPot(final Object recipeBookMenu,
                                                 final Player player,
                                                 final Object multiBlock,
                                                 final List<BlockEntity> targetPots,
                                                 final CookingPotIndexedRecipe indexedRecipe,
                                                 final List<ItemStack> matrixStacks,
                                                 final ItemStack outputItem) {
        final List<?> providers = resolveItemProviders(multiBlock, player);
        if (providers.isEmpty()) {
            logTransferFailure(indexedRecipe, "providers", "no item providers");
            return false;
        }

        resetSimulation(providers);
        final boolean requireBucket = doesItemRequireBucketForCrafting(outputItem);
        final List<ItemStack> requestedIngredientStacks = resolveIngredientStacksForLegacyTransfer(indexedRecipe, matrixStacks);
        if (requestedIngredientStacks == null || requestedIngredientStacks.isEmpty()) {
            logTransferFailure(indexedRecipe, "matrix", "displayed ingredients did not match indexed recipe");
            return false;
        }

        final List<SourceConsumption> ingredientSources = new ArrayList<>();
        for (final ItemStack requestedIngredient : requestedIngredientStacks) {
            final SourceConsumption source = findSourceForStack(requestedIngredient, providers, requireBucket, true);
            if (source == null) {
                logTransferFailure(indexedRecipe, "ingredients", "missing " + requestedIngredient.getItem());
                return false;
            }
            ingredientSources.add(source);
        }

        final ItemStack containerCost = BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexedRecipe.targetKey())
                ? ItemStack.EMPTY
                : resolveContainerCostForLegacyCraft(indexedRecipe, player, outputItem);
        final List<SourceConsumption> containerSources = new ArrayList<>();
        if (!containerCost.isEmpty()) {
            final ItemStack unit = containerCost.copy();
            unit.setCount(1);
            for (int i = 0; i < containerCost.getCount(); i++) {
                final SourceConsumption source = findSourceForStack(unit, providers, false, true);
                if (source == null) {
                    logTransferFailure(indexedRecipe, "container", "missing " + unit.getItem());
                    return false;
                }
                containerSources.add(source);
            }
        }

        final List<ItemStack> ingredientUnits = new ArrayList<>(ingredientSources.size());
        for (final SourceConsumption source : ingredientSources) {
            ingredientUnits.add(source.sourceStack().copyWithCount(1));
        }

        final StartupPlan[] selectedStartupPlan = {null};
        final BlockEntity targetPot = FirstReady.find(targetPots, candidate -> {
            if (!CookingPotProcessorCapability.canTransferResolvedStacksToPot(
                    candidate, indexedRecipe, ingredientUnits, containerCost)) {
                return false;
            }
            selectedStartupPlan[0] = resolveStartupPlan(
                    player.level(), multiBlock, candidate, indexedRecipe, providers);
            return selectedStartupPlan[0] != null;
        });
        final StartupPlan startupPlan = selectedStartupPlan[0];
        if (targetPot == null) {
            logTransferFailure(indexedRecipe, "target", "no connected cookware is ready for this recipe");
            return false;
        }

        final List<ConsumedSource> consumedSources = new ArrayList<>();
        for (final SourceConsumption source : ingredientSources) {
            if (!consumeSource(source, providers, requireBucket, consumedSources)) {
                restoreConsumedSources(player, consumedSources);
                logTransferFailure(indexedRecipe, "consume_ingredients", "source changed during transfer");
                return false;
            }
        }
        if (!BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(indexedRecipe.targetKey())
                && !BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexedRecipe.targetKey())) {
            for (final SourceConsumption source : containerSources) {
                if (!consumeSource(source, providers, false, consumedSources)) {
                    restoreConsumedSources(player, consumedSources);
                    logTransferFailure(indexedRecipe, "consume_container", "source changed during transfer");
                    return false;
                }
            }
        }

        ConsumedSource consumedStartup = null;
        if (startupPlan.requiredSource() != null) {
            consumedStartup = consumeSource(startupPlan.requiredSource(), providers, false);
            if (consumedStartup == null) {
                restoreConsumedSources(player, consumedSources);
                logTransferFailure(indexedRecipe, "consume_startup", "oil or soup source changed during transfer");
                return false;
            }
        } else if (startupPlan.connectedOilSource() != null
                && !changeConnectedOil(startupPlan.connectedOilSource(), -1)) {
            restoreConsumedSources(player, consumedSources);
            logTransferFailure(indexedRecipe, "consume_startup", "connected oil source changed during transfer");
            return false;
        }

        if (!CookingPotProcessorCapability.transferResolvedStacksToPot(
                targetPot, indexedRecipe, ingredientUnits, containerCost)) {
            if (startupPlan.connectedOilSource() != null) {
                changeConnectedOil(startupPlan.connectedOilSource(), 1);
            }
            if (consumedStartup != null) {
                restoreSourceStack(player, consumedStartup.source(), consumedStartup.stack());
            }
            restoreConsumedSources(player, consumedSources);
            logTransferFailure(indexedRecipe, "apply", "cookware rejected resolved ingredients");
            return false;
        }

        finishStartupConsumption(player, startupPlan, consumedStartup);
        applyOptionalStockpotLid(player, targetPot, startupPlan.lidSource(), providers);
        return true;
    }

    private static StartupPlan resolveStartupPlan(final Level level,
                                                  final Object multiBlock,
                                                  final BlockEntity targetPot,
                                                  final CookingPotIndexedRecipe indexedRecipe,
                                                  final List<?> providers) {
        if (BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(indexedRecipe.targetKey())) {
            if (hasBooleanBlockProperty(targetPot, "has_oil")) {
                return StartupPlan.none();
            }
            final var oilItem = BuiltInRegistries.ITEM.getOptional(
                    MinecraftApiCompat.resourceLocation(
                            BridgeKeys.MOD_KALEIDOSCOPE_COOKERY,
                            BridgeKeys.ITEM_KALEIDOSCOPE_OIL
                    )
            ).orElse(null);
            if (oilItem != null) {
                final SourceConsumption rawOil = findSourceForStack(
                        new ItemStack(oilItem), providers, false, true);
                if (rawOil != null) {
                    return new StartupPlan(StartupKind.RAW_OIL, rawOil, null, null);
                }
            }

            final SourceConsumption filledOilPot = findSource(
                    providers,
                    false,
                    true,
                    KaleidoscopeOilBridge::isKaleidoscopeOilPotWithOil
            );
            if (filledOilPot != null) {
                return new StartupPlan(StartupKind.FILLED_OIL_POT, filledOilPot, null, null);
            }

            final ConnectedOilSource connectedOil = findConnectedOilSource(level, multiBlock);
            return connectedOil == null
                    ? null
                    : new StartupPlan(StartupKind.CONNECTED_OIL, null, connectedOil, null);
        }

        if (!BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexedRecipe.targetKey())) {
            return StartupPlan.none();
        }

        final int status = stockpotStatus(targetPot);
        if (status != 0 && status != 1) {
            return null;
        }
        final ResourceLocation requiredSoupBaseId = StockpotSoupBridge.resolveRequiredSoupBaseId(indexedRecipe);
        StartupKind startupKind = StartupKind.NONE;
        SourceConsumption soupSource = null;
        if (status == 0) {
            startupKind = StartupKind.SOUP_BASE;
            if (!StockpotSoupBridge.isLavaSoupBase(requiredSoupBaseId)
                    || !hasConnectedLavaSink(level, multiBlock)) {
                final ItemStack soupBase = StockpotSoupBridge.soupBaseBucketStack(requiredSoupBaseId);
                if (soupBase.isEmpty()) {
                    return null;
                }
                soupSource = findSourceForStack(soupBase, providers, false, true);
                if (soupSource == null) {
                    return null;
                }
            }
        } else if (!requiredSoupBaseId.equals(invokeNoArg(targetPot, "getSoupBaseId"))) {
            return null;
        }

        SourceConsumption lidSource = null;
        final var lidItem = BuiltInRegistries.ITEM.getOptional(
                MinecraftApiCompat.resourceLocation(
                        BridgeKeys.MOD_KALEIDOSCOPE_COOKERY,
                        BridgeKeys.ITEM_KALEIDOSCOPE_STOCKPOT_LID
                )
        ).orElse(null);
        if (lidItem != null && !hasBooleanBlockProperty(targetPot, "has_lid")) {
            lidSource = findSourceForStack(new ItemStack(lidItem), providers, false, true);
        }
        return new StartupPlan(startupKind, soupSource, null, lidSource);
    }

    private static List<ItemStack> resolveIngredientStacksForLegacyTransfer(final CookingPotIndexedRecipe indexedRecipe,
                                                                            final List<ItemStack> matrixStacks) {
        if (indexedRecipe == null || matrixStacks == null || matrixStacks.isEmpty()) {
            return null;
        }

        final List<ItemStack> remainingMatrixUnits = new ArrayList<>();
        for (final ItemStack matrixStack : matrixStacks) {
            if (matrixStack == null || matrixStack.isEmpty()) {
                continue;
            }

            final int count = Math.max(1, matrixStack.getCount());
            for (int i = 0; i < count; i++) {
                remainingMatrixUnits.add(matrixStack.copyWithCount(1));
            }
        }

        final List<ItemStack> resolvedIngredients = new ArrayList<>();
        final int start = Math.max(0, indexedRecipe.syntheticIngredientCount());
        final List<Ingredient> ingredients = indexedRecipe.getIngredients();
        for (int ingredientIndex = start; ingredientIndex < ingredients.size(); ingredientIndex++) {
            final Ingredient ingredient = ingredients.get(ingredientIndex);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            boolean matched = false;
            for (int matrixIndex = 0; matrixIndex < remainingMatrixUnits.size(); matrixIndex++) {
                final ItemStack matrixUnit = remainingMatrixUnits.get(matrixIndex);
                if (!ingredient.test(matrixUnit)) {
                    continue;
                }

                remainingMatrixUnits.remove(matrixIndex);
                resolvedIngredients.add(matrixUnit);
                matched = true;
                break;
            }

            if (!matched) {
                return null;
            }
        }

        return resolvedIngredients;
    }

    private static List<CookingPotIndexedRecipe> findIndexedCandidates(final Player player,
                                                                       final ItemStack outputItem,
                                                                       final List<ItemStack> matrixStacks) {
        final List<CookingPotIndexedRecipe> strictMatches = new ArrayList<>();
        final List<CookingPotIndexedRecipe> outputMatches = new ArrayList<>();
        final var registryAccess = player.level().registryAccess();
        for (final Recipe<?> recipe : CookingPotRecipeIndexer.indexedRecipes()) {
            if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
                continue;
            }

            final ItemStack result = indexedRecipe.getResultItem(registryAccess);
            if (!MinecraftApiCompat.isSameItemSameData(result, outputItem)) {
                continue;
            }

            outputMatches.add(indexedRecipe);
            if (matchesDisplayedMatrix(indexedRecipe, matrixStacks, registryAccess)) {
                strictMatches.add(indexedRecipe);
            }
        }

        // 1.20.1 CFBH matrix payload can differ from 1.21.1 (e.g. serving-container visibility),
        // so fall back to output-based indexed recipes to keep pot/container transfer working.
        if (!strictMatches.isEmpty()) {
            return strictMatches;
        }

        outputMatches.sort((left, right) ->
                Integer.compare(
                        fallbackMatchScore(right, matrixStacks, player.level().registryAccess()),
                        fallbackMatchScore(left, matrixStacks, player.level().registryAccess())
                )
        );
        return outputMatches;
    }

    private static int fallbackMatchScore(final CookingPotIndexedRecipe indexedRecipe,
                                          final List<ItemStack> matrixStacks,
                                          final net.minecraft.core.RegistryAccess registryAccess) {
        int score = 0;
        final List<ItemStack> remaining = new ArrayList<>();
        for (final ItemStack stack : matrixStacks) {
            if (stack != null && !stack.isEmpty()) {
                remaining.add(stack.copy());
            }
        }

        final int start = Math.max(0, indexedRecipe.syntheticIngredientCount());
        final List<Ingredient> ingredients = indexedRecipe.getIngredients();
        for (int i = start; i < ingredients.size(); i++) {
            final Ingredient ingredient = ingredients.get(i);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            boolean matched = false;
            for (int stackIndex = 0; stackIndex < remaining.size(); stackIndex++) {
                final ItemStack candidate = remaining.get(stackIndex);
                if (ingredient.test(candidate)) {
                    remaining.remove(stackIndex);
                    matched = true;
                    break;
                }
            }

            score += matched ? 10 : -25;
        }

        ItemStack containerCost = indexedRecipe.indexedContainerCost();
        if (containerCost.isEmpty()) {
            containerCost = CookingPotContainerCost.resolveForIndexedRecipe(
                    indexedRecipe.delegateRecipe(),
                    registryAccess,
                    indexedRecipe.targetKey()
            );
        }
        if (!containerCost.isEmpty()) {
            score += 5;
            final ItemStack containerUnit = containerCost.copy();
            containerUnit.setCount(1);
            for (final ItemStack extra : remaining) {
                if (!extra.isEmpty() && MinecraftApiCompat.isSameItemSameData(extra, containerUnit)) {
                    score += 3;
                } else if (!extra.isEmpty()) {
                    score -= 4;
                }
            }
        } else {
            for (final ItemStack extra : remaining) {
                if (!extra.isEmpty()) {
                    score -= 4;
                }
            }
        }

        return score;
    }

    private static ItemStack resolveContainerCostForLegacyCraft(final CookingPotIndexedRecipe indexedRecipe,
                                                                final Player player,
                                                                final ItemStack outputItem) {
        ItemStack containerCost = indexedRecipe.indexedContainerCost();
        if (containerCost.isEmpty()) {
            containerCost = CookingPotContainerCost.resolveForIndexedRecipe(
                    indexedRecipe.delegateRecipe(),
                    player.level().registryAccess(),
                    indexedRecipe.targetKey()
            );
        }
        if (!containerCost.isEmpty()) {
            return containerCost;
        }

        final ItemStack outputContainer = outputItem.getCraftingRemainingItem();
        if (outputContainer.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final int outputCount = Math.max(1, outputItem.getCount());
        final int containerCountPerServing = Math.max(1, outputContainer.getCount());
        final long totalContainerCount = (long) outputCount * containerCountPerServing;
        final ItemStack fallback = outputContainer.copy();
        fallback.setCount((int) Math.min(Integer.MAX_VALUE, totalContainerCount));
        LOGGER.info(
                "Using output-container fallback for legacy recipe-book craft: output='{}', container='{}', count={}.",
                outputItem.getItem(),
                fallback.getItem(),
                fallback.getCount()
        );
        return fallback;
    }

    private static boolean matchesDisplayedMatrix(final CookingPotIndexedRecipe indexedRecipe,
                                                  final List<ItemStack> matrixStacks,
                                                  final net.minecraft.core.RegistryAccess registryAccess) {
        final List<ItemStack> remainingStacks = new ArrayList<>();
        for (final ItemStack matrixStack : matrixStacks) {
            if (!matrixStack.isEmpty()) {
                remainingStacks.add(matrixStack);
            }
        }

        final int start = Math.max(0, indexedRecipe.syntheticIngredientCount());
        final List<Ingredient> ingredients = indexedRecipe.getIngredients();
        for (int i = start; i < ingredients.size(); i++) {
            final Ingredient ingredient = ingredients.get(i);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            boolean matched = false;
            for (int stackIndex = 0; stackIndex < remainingStacks.size(); stackIndex++) {
                final ItemStack candidate = remainingStacks.get(stackIndex);
                if (ingredient.test(candidate)) {
                    remainingStacks.remove(stackIndex);
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return false;
            }
        }

        if (remainingStacks.isEmpty()) {
            return true;
        }

        ItemStack containerCost = indexedRecipe.indexedContainerCost();
        if (containerCost.isEmpty()) {
            containerCost = CookingPotContainerCost.resolveForIndexedRecipe(
                    indexedRecipe.delegateRecipe(),
                    registryAccess,
                    indexedRecipe.targetKey()
            );
        }
        if (containerCost.isEmpty()) {
            return false;
        }

        int remainingContainerCount = containerCost.getCount();
        final ItemStack containerUnit = containerCost.copy();
        containerUnit.setCount(1);
        for (final ItemStack extraStack : remainingStacks) {
            if (remainingContainerCount <= 0) {
                break;
            }
            if (extraStack.isEmpty() || !MinecraftApiCompat.isSameItemSameData(extraStack, containerUnit)) {
                continue;
            }

            final int used = Math.min(remainingContainerCount, extraStack.getCount());
            remainingContainerCount -= used;
            extraStack.shrink(used);
        }

        if (remainingContainerCount > 0) {
            return false;
        }

        for (final ItemStack extraStack : remainingStacks) {
            if (!extraStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<BlockEntity> findConnectedPots(final Level level,
                                                       final Object multiBlock,
                                                       final CookingPotBridgeTarget target,
                                                       final String targetKey) {
        final Object checkedPosValue = readFieldValue(multiBlock, "checkedPos", Object.class);
        if (!(checkedPosValue instanceof Iterable<?> checkedPosIterable)) {
            return List.of();
        }

        final List<BlockEntity> connectedPots = new ArrayList<>();
        for (final Object posObj : checkedPosIterable) {
            if (!(posObj instanceof BlockPos blockPos)) {
                continue;
            }

            final BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (blockEntity == null || !target.matchesBlockEntity(blockEntity)) {
                continue;
            }
            if (!CookingPotHeatBridge.isTargetPotConnectedForCookingTable(blockEntity, targetKey)) {
                continue;
            }
            connectedPots.add(blockEntity);
        }

        return connectedPots;
    }

    private static List<?> resolveItemProviders(final Object multiBlock, final Player player) {
        final Object value = invoke(
                multiBlock,
                "getItemProviders",
                new Class<?>[]{player.getInventory().getClass()},
                player.getInventory()
        );
        if (value instanceof List<?> providers) {
            return providers;
        }

        // Fallback to interface signature when runtime class erasure lookup differs.
        final Object fallback = invoke(multiBlock, "getItemProviders", new Class<?>[]{net.minecraft.world.entity.player.Inventory.class}, player.getInventory());
        if (fallback instanceof List<?> providers) {
            return providers;
        }
        return List.of();
    }

    private static void resetSimulation(final List<?> providers) {
        for (final Object provider : providers) {
            invokeNoArg(provider, "resetSimulation");
        }
    }

    private static SourceConsumption findSourceForStack(final ItemStack expectedStack,
                                                        final List<?> providers,
                                                        final boolean requireBucket,
                                                        final boolean simulate) {
        if (expectedStack.isEmpty()) {
            return null;
        }

        return findSource(
                providers,
                requireBucket,
                simulate,
                candidate -> MinecraftApiCompat.isSameItemSameData(candidate, expectedStack)
        );
    }

    private static SourceConsumption findSource(final List<?> providers,
                                                final boolean requireBucket,
                                                final boolean simulate,
                                                final Predicate<ItemStack> predicate) {

        final Class<?> ingredientPredicateClass = resolveClass("net.blay09.mods.cookingforblockheads.api.capability.IngredientPredicate");
        if (ingredientPredicateClass == null) {
            return null;
        }

        final Object ingredientPredicate = newIngredientPredicate(ingredientPredicateClass, predicate);
        if (ingredientPredicate == null) {
            return null;
        }

        for (final Object provider : providers) {
            final Object sourceItem = invokeByName(
                    provider,
                    "findSourceAndMarkAsUsed",
                    ingredientPredicate,
                    1,
                    providers,
                    requireBucket,
                    simulate
            );
            if (sourceItem == null) {
                continue;
            }

            final ItemStack sourceStack = readSourceStack(sourceItem);
            final Object sourceProvider = invokeNoArg(sourceItem, "getSourceProvider");
            if (sourceStack.isEmpty() || sourceProvider == null) {
                continue;
            }

            return new SourceConsumption(sourceProvider, sourceItem, sourceStack);
        }

        return null;
    }

    private static Object newIngredientPredicate(final Class<?> ingredientPredicateClass,
                                                 final Predicate<ItemStack> predicate) {
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("test".equals(method.getName())
                    && args != null
                    && args.length == 2
                    && args[0] instanceof ItemStack candidate
                    && args[1] instanceof Integer count) {
                return count > 0 && predicate.test(candidate);
            }

            return switch (method.getName()) {
                case "toString" -> "Lab11IngredientPredicate";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                default -> false;
            };
        };

        return Proxy.newProxyInstance(
                ingredientPredicateClass.getClassLoader(),
                new Class<?>[]{ingredientPredicateClass},
                handler
        );
    }

    private static ItemStack readOutputItem(final Object selectedRecipe) {
        if (selectedRecipe == null) {
            return ItemStack.EMPTY;
        }
        final Object value = invokeNoArg(selectedRecipe, "getOutputItem");
        return value instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
    }

    private static Object readRecipeType(final Object selectedRecipe) {
        if (selectedRecipe == null) {
            return null;
        }
        return invokeNoArg(selectedRecipe, "getRecipeType");
    }

    private static List<ItemStack> readDisplayedMatrixStacks(final Object recipeBookMenu) {
        final Object slotsValue = invokeNoArg(recipeBookMenu, "getCraftingMatrixSlots");
        if (!(slotsValue instanceof List<?> slots)) {
            return List.of();
        }

        final List<ItemStack> stacks = new ArrayList<>(slots.size());
        for (final Object slot : slots) {
            final Object value = invokeNoArg(slot, "getItem");
            if (value instanceof ItemStack itemStack) {
                stacks.add(itemStack);
            } else {
                stacks.add(ItemStack.EMPTY);
            }
        }
        return List.copyOf(stacks);
    }

    private static boolean isCraftingRecipeType(final Object recipeType) {
        return recipeType != null && "CRAFTING".equals(recipeType.toString());
    }

    private static List<ItemStack> sanitizeMatrixStacks(final List<ItemStack> rawMatrixStacks) {
        if (rawMatrixStacks == null || rawMatrixStacks.isEmpty()) {
            return List.of();
        }

        final List<ItemStack> sanitized = new ArrayList<>(rawMatrixStacks.size());
        for (final ItemStack stack : rawMatrixStacks) {
            if (stack == null || stack.isEmpty()) {
                sanitized.add(ItemStack.EMPTY);
            } else {
                sanitized.add(stack.copy());
            }
        }
        return List.copyOf(sanitized);
    }

    private static boolean doesItemRequireBucketForCrafting(final ItemStack outputItem) {
        try {
            final Class<?> cookingRegistryClass = Class.forName("net.blay09.mods.cookingforblockheads.registry.CookingRegistry");
            final Method method = cookingRegistryClass.getMethod("doesItemRequireBucketForCrafting", ItemStack.class);
            final Object value = method.invoke(null, outputItem);
            return value instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean consumeSource(final SourceConsumption source,
                                         final List<?> providers,
                                         final boolean requireContainer,
                                         final List<ConsumedSource> consumedSources) {
        final ConsumedSource consumed = consumeSource(source, providers, requireContainer);
        if (consumed == null) {
            return false;
        }
        consumedSources.add(consumed);
        return true;
    }

    private static ConsumedSource consumeSource(final SourceConsumption source,
                                                final List<?> providers,
                                                final boolean requireContainer) {
        final Object slotValue = invokeNoArg(source.sourceItem(), "getSourceSlot");
        if (!(slotValue instanceof Number slot)) {
            return null;
        }
        final Object value = invokeByName(
                source.provider(),
                "useItemStack",
                slot.intValue(),
                1,
                false,
                providers,
                requireContainer
        );
        if (!(value instanceof ItemStack consumed) || consumed.isEmpty()) {
            return null;
        }
        return new ConsumedSource(source, consumed.copy());
    }

    private static void restoreConsumedSources(final Player player, final List<ConsumedSource> consumedSources) {
        for (int i = consumedSources.size() - 1; i >= 0; i--) {
            final ConsumedSource consumed = consumedSources.get(i);
            restoreSourceStack(player, consumed.source(), consumed.stack());
        }
    }

    private static void restoreSourceStack(final Player player,
                                           final SourceConsumption source,
                                           final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final Object value = invokeByName(
                source.provider(), "returnItemStack", stack.copy(), source.sourceItem());
        final ItemStack remainder = value instanceof ItemStack returned ? returned : stack.copy();
        giveToPlayer(player, remainder);
    }

    private static void giveToPlayer(final Player player, final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        final ItemStack remainder = stack.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static void finishStartupConsumption(final Player player,
                                                 final StartupPlan plan,
                                                 final ConsumedSource consumedStartup) {
        if (consumedStartup == null) {
            return;
        }
        if (plan.kind() == StartupKind.FILLED_OIL_POT) {
            restoreSourceStack(
                    player,
                    consumedStartup.source(),
                    KaleidoscopeOilBridge.decrementOilPotCount(consumedStartup.stack().copyWithCount(1))
            );
            return;
        }
        if (plan.kind() == StartupKind.SOUP_BASE
                && !StockpotSoupBridge.isSinkItemProvider(consumedStartup.source().provider())) {
            final ItemStack remainder = consumedStartup.stack().getCraftingRemainingItem();
            if (!remainder.isEmpty()) {
                restoreSourceStack(player, consumedStartup.source(), remainder);
            }
        }
    }

    private static void applyOptionalStockpotLid(final Player player,
                                                 final BlockEntity targetPot,
                                                 final SourceConsumption lidSource,
                                                 final List<?> providers) {
        if (lidSource == null || hasBooleanBlockProperty(targetPot, "has_lid")) {
            return;
        }
        final ConsumedSource consumedLid = consumeSource(lidSource, providers, false);
        if (consumedLid == null) {
            LOGGER.warn("Legacy cooking-pot transfer skipped optional stockpot lid because its source changed.");
            return;
        }
        if (!applyStockpotLid(targetPot, consumedLid.stack().copyWithCount(1))) {
            restoreSourceStack(player, consumedLid.source(), consumedLid.stack());
            LOGGER.warn("Legacy cooking-pot transfer restored optional stockpot lid after apply failed.");
        }
    }

    private static boolean applyStockpotLid(final BlockEntity targetPot, final ItemStack lid) {
        final Level level = targetPot.getLevel();
        if (level == null || lid.isEmpty()) {
            return false;
        }
        final BlockState state = targetPot.getBlockState();
        final BooleanProperty hasLid = booleanProperty(state, "has_lid");
        if (hasLid == null) {
            return false;
        }
        try {
            targetPot.getClass().getMethod("setLidItem", ItemStack.class)
                    .invoke(targetPot, lid.copyWithCount(1));
            level.setBlockAndUpdate(targetPot.getBlockPos(), state.setValue(hasLid, true));
            targetPot.setChanged();
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int stockpotStatus(final BlockEntity targetPot) {
        final Object status = invokeNoArg(targetPot, "getStatus");
        if (status instanceof Number number) {
            return number.intValue();
        }
        final Field field = findField(targetPot.getClass(), "status");
        if (field == null) {
            return Integer.MIN_VALUE;
        }
        try {
            field.setAccessible(true);
            return field.getInt(targetPot);
        } catch (ReflectiveOperationException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean hasBooleanBlockProperty(final BlockEntity blockEntity, final String name) {
        if (blockEntity == null) {
            return false;
        }
        final BlockState state = blockEntity.getBlockState();
        final BooleanProperty property = booleanProperty(state, name);
        return property != null && state.getValue(property);
    }

    private static BooleanProperty booleanProperty(final BlockState state, final String name) {
        final var property = state.getBlock().getStateDefinition().getProperty(name);
        return property instanceof BooleanProperty booleanProperty ? booleanProperty : null;
    }

    private static ConnectedOilSource findConnectedOilSource(final Level level, final Object multiBlock) {
        final Object checkedValue = readFieldValue(multiBlock, "checkedPos", Object.class);
        if (!(checkedValue instanceof Iterable<?> checkedPositions)) {
            return null;
        }
        for (final Object value : checkedPositions) {
            if (!(value instanceof BlockPos pos)) {
                continue;
            }
            final BlockState state = level.getBlockState(pos);
            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null || !BridgeKeys.MOD_KALEIDOSCOPE_COOKERY.equals(blockId.getNamespace())) {
                continue;
            }
            if (BridgeKeys.ITEM_KALEIDOSCOPE_OIL_POT.equals(blockId.getPath())) {
                final ConnectedOilSource source = new ConnectedOilSource(level, pos, ConnectedOilKind.OIL_POT);
                if (connectedOilCount(source) > 0) {
                    return source;
                }
            } else if ("enamel_basin".equals(blockId.getPath())) {
                final ConnectedOilSource source = new ConnectedOilSource(level, pos, ConnectedOilKind.ENAMEL_BASIN);
                if (connectedOilCount(source) > 0) {
                    return source;
                }
            }
        }
        return null;
    }

    private static boolean hasConnectedLavaSink(final Level level, final Object multiBlock) {
        final Object checkedValue = readFieldValue(multiBlock, "checkedPos", Object.class);
        if (!(checkedValue instanceof Iterable<?> checkedPositions)) {
            return false;
        }
        for (final Object value : checkedPositions) {
            if (!(value instanceof BlockPos pos)) {
                continue;
            }
            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (blockId != null
                    && BridgeKeys.MOD_LAB11_UNIFIED.equals(blockId.getNamespace())
                    && (BridgeKeys.BLOCK_LAVA_SINK.equals(blockId.getPath())
                    || blockId.getPath().endsWith("_" + BridgeKeys.BLOCK_LAVA_SINK))) {
                return true;
            }
        }
        return false;
    }

    private static int connectedOilCount(final ConnectedOilSource source) {
        if (source.kind() == ConnectedOilKind.OIL_POT) {
            final BlockEntity blockEntity = source.level().getBlockEntity(source.pos());
            final Object value = invokeNoArg(blockEntity, "getOilCount");
            return value instanceof Number number ? number.intValue() : 0;
        }
        final BlockState state = source.level().getBlockState(source.pos());
        final var property = state.getBlock().getStateDefinition().getProperty("oil_count");
        return property instanceof IntegerProperty oilCount ? state.getValue(oilCount) : 0;
    }

    private static boolean changeConnectedOil(final ConnectedOilSource source, final int delta) {
        final int current = connectedOilCount(source);
        final int updated = current + delta;
        if (updated < 0) {
            return false;
        }
        if (source.kind() == ConnectedOilKind.OIL_POT) {
            final BlockEntity blockEntity = source.level().getBlockEntity(source.pos());
            if (blockEntity == null) {
                return false;
            }
            try {
                blockEntity.getClass().getMethod("setOilCount", int.class).invoke(blockEntity, updated);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        final BlockState state = source.level().getBlockState(source.pos());
        final var property = state.getBlock().getStateDefinition().getProperty("oil_count");
        if (!(property instanceof IntegerProperty oilCount) || !oilCount.getPossibleValues().contains(updated)) {
            return false;
        }
        return source.level().setBlockAndUpdate(source.pos(), state.setValue(oilCount, updated));
    }

    private static void logTransferFailure(final CookingPotIndexedRecipe recipe,
                                           final String stage,
                                           final String detail) {
        LOGGER.warn(
                "Legacy cooking-pot transfer failed: recipe='{}', target='{}', stage='{}', detail='{}'.",
                recipe.delegateRecipe().getClass().getName(), recipe.targetKey(), stage, detail
        );
    }

    private static ItemStack readSourceStack(final Object sourceItem) {
        final Object value = invokeNoArg(sourceItem, "getSourceStack");
        return value instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
    }

    private static Level level(final Player player) {
        return player.level();
    }

    private static CookingPotBridgeTarget findTargetByKey(final List<CookingPotBridgeTarget> targets, final String targetKey) {
        for (final CookingPotBridgeTarget target : targets) {
            if (target.targetKey().equals(targetKey)) {
                return target;
            }
        }
        return null;
    }

    private static void sendFeedback(final Player player, final String key, final ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invoke(final Object target, final String methodName, final Class<?>[] parameterTypes, final Object... args) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeByName(final Object target, final String methodName, final Object... args) {
        if (target == null) {
            return null;
        }

        final Method method = findCompatibleMethod(target.getClass(), methodName, args);
        if (method == null) {
            return null;
        }

        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findCompatibleMethod(final Class<?> ownerClass, final String methodName, final Object[] args) {
        Class<?> current = ownerClass;
        while (current != null) {
            for (final Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) {
                    continue;
                }
                if (areArgumentsCompatible(parameterTypes, args)) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean areArgumentsCompatible(final Class<?>[] parameterTypes, final Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            final Object arg = args[i];
            final Class<?> parameterType = parameterTypes[i];
            if (arg == null) {
                if (parameterType.isPrimitive()) {
                    return false;
                }
                continue;
            }

            final Class<?> wrapped = wrapPrimitive(parameterType);
            if (!wrapped.isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrapPrimitive(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Class<?> resolveClass(final String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static <T> T readFieldValue(final Object target, final String fieldName, final Class<T> fieldType) {
        if (target == null) {
            return null;
        }

        final Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            field.setAccessible(true);
            final Object value = field.get(target);
            if (fieldType.isInstance(value)) {
                return fieldType.cast(value);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to read field {} from {}", fieldName, target.getClass().getName(), e);
        }
        return null;
    }

    private static Field findField(final Class<?> ownerClass, final String fieldName) {
        Class<?> current = ownerClass;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private record SourceConsumption(Object provider, Object sourceItem, ItemStack sourceStack) {
    }

    private record ConsumedSource(SourceConsumption source, ItemStack stack) {
    }

    private enum StartupKind {
        NONE,
        RAW_OIL,
        FILLED_OIL_POT,
        CONNECTED_OIL,
        SOUP_BASE
    }

    private record StartupPlan(StartupKind kind,
                               SourceConsumption requiredSource,
                               ConnectedOilSource connectedOilSource,
                               SourceConsumption lidSource) {
        private static StartupPlan none() {
            return new StartupPlan(StartupKind.NONE, null, null, null);
        }
    }

    private enum ConnectedOilKind {
        OIL_POT,
        ENAMEL_BASIN
    }

    private record ConnectedOilSource(Level level, BlockPos pos, ConnectedOilKind kind) {
    }
}
