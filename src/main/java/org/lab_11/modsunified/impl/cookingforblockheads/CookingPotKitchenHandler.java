package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CookingPotKitchenHandler implements CfbhRuntime.KitchenRecipeHandlerView {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_STOCKPOT_TRANSFER = Boolean.getBoolean("lab11.debug.stockpot_transfer");
    private static final String CFBH_COOKING_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.registry.CookingRegistry";
    private static final String CFBH_GET_WATER_ITEMS_METHOD = "getWaterItems";
    private static final String FEEDBACK_POT_TRANSFER_FAILED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_transfer_failed";
    private static final String FEEDBACK_STOCKPOT_NO_TARGET_PROCESSOR_KEY =
            "lab_11_mods_unified.feedback.cooking_table.stockpot_no_target_processor";
    private static volatile List<ItemStack> cachedWaterSoupBaseCandidates;

    private record TokenConsumption(Object token, ItemStack stack) {
    }

    public static Object createRuntimeHandlerProxy() {
        return CfbhRuntime.newKitchenRecipeHandlerProxy(new CookingPotKitchenHandler());
    }

    @Override
    public int mapToMatrixSlot(final Recipe<?> recipe, final int ingredientIndex) {
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
            final int syntheticIngredientCount = indexedRecipe.syntheticIngredientCount();
            if (ingredientIndex < syntheticIngredientCount) {
                // Keep synthetic activation/container ingredients off the displayed matrix.
                return 0;
            }

            final int visibleIngredientIndex = ingredientIndex - syntheticIngredientCount;
            return visibleIngredientIndex < 9 ? visibleIngredientIndex : 8;
        }

        return ingredientIndex < 9 ? ingredientIndex : 8;
    }

    @Override
    public ItemStack assemble(final Object context,
                              final Recipe<?> recipe,
                              final List<?> ingredientTokens,
                              final RegistryAccess registryAccess) {
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
            return routeIndexedRecipeToPot(context, indexedRecipe, ingredientTokens, registryAccess);
        }

        return assembleToOutput(context, recipe, ingredientTokens, registryAccess);
    }

    private static ItemStack routeIndexedRecipeToPot(final Object context,
                                                     final CookingPotIndexedRecipe recipe,
                                                     final List<?> ingredientTokens,
                                                     final RegistryAccess registryAccess) {
        if (DEBUG_STOCKPOT_TRANSFER && BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(recipe.targetKey())) {
            LOGGER.info(
                    "Stockpot route start: recipeType={}, tokenCount={}, ingredientCount={}",
                    recipe.getType(),
                    ingredientTokens.size(),
                    recipe.getIngredients().size()
            );
        }
        final boolean convertForCopperTarget =
                MinersDelightCupConversion.COPPER_POT_TARGET_KEY.equals(recipe.targetKey());
        final List<Object> processingTokens = new ArrayList<>(ingredientTokens);
        final Map<Object, List<Object>> allocatedByProvider = new IdentityHashMap<>();
        if (!BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(recipe.targetKey())) {
            appendRequiredContainerTokens(
                    context, recipe, processingTokens, registryAccess, convertForCopperTarget, allocatedByProvider);
        }
        appendTargetStartupRequirementTokens(context, recipe, processingTokens, allocatedByProvider);

        Object deferredFailureOperation = null;
        for (final Object itemProcessor : CfbhRuntime.contextItemProcessors(context)) {
            if (!CfbhRuntime.processorCanProcess(itemProcessor, recipe.getType())) {
                continue;
            }
            final Object operation = CfbhRuntime.processorProcessRecipe(itemProcessor, recipe, processingTokens);
            if (!CfbhRuntime.isEmptyKitchenOperation(operation)) {
                if (CfbhRuntime.isRetryableKitchenOperation(operation)) {
                    if (deferredFailureOperation == null) {
                        deferredFailureOperation = operation;
                    }
                    continue;
                }
                CfbhRuntime.contextNotify(context, operation);
                return ItemStack.EMPTY;
            }
        }

        if (deferredFailureOperation != null) {
            CfbhRuntime.contextNotify(context, deferredFailureOperation);
            return ItemStack.EMPTY;
        }

        notifyFailure(context, FEEDBACK_STOCKPOT_NO_TARGET_PROCESSOR_KEY);
        return ItemStack.EMPTY;
    }

    private static ItemStack assembleToOutput(final Object context,
                                              final Recipe<?> recipe,
                                              final List<?> ingredientTokens,
                                              final RegistryAccess registryAccess) {
        ItemStack output = recipe.getResultItem(registryAccess).copy();
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (ingredientTokens.size() < recipe.getIngredients().size()) {
            return ItemStack.EMPTY;
        }

        final List<TokenConsumption> consumed = new ArrayList<>(ingredientTokens.size());

        for (final Object ingredientToken : ingredientTokens) {
            if (CfbhRuntime.isEmptyIngredientToken(ingredientToken)) {
                continue;
            }

            final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(ingredientToken);
            if (consumedStack.isEmpty()) {
                restoreConsumed(context, consumed);
                return ItemStack.EMPTY;
            }

            consumed.add(new TokenConsumption(ingredientToken, consumedStack));
        }

        if (!consumeRequiredContainers(context, recipe, registryAccess, false, consumed)) {
            restoreConsumed(context, consumed);
            return ItemStack.EMPTY;
        }

        return output;
    }

    private static void appendRequiredContainerTokens(final Object context,
                                                      final Recipe<?> recipe,
                                                      final List<Object> processingTokens,
                                                      final RegistryAccess registryAccess,
                                                      final boolean copperPotActive,
                                                      final Map<Object, List<Object>> allocatedByProvider) {
        final ItemStack containerCost = CookingPotContainerCost.resolveForCraft(recipe, registryAccess, copperPotActive);
        if (containerCost.isEmpty()) {
            return;
        }

        final ItemStack containerUnit = containerCost.copy();
        containerUnit.setCount(1);

        final List<?> itemProviders = CfbhRuntime.contextItemProviders(context);
        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final Object token = CfbhRuntime.findAndReserveItemToken(
                    itemProviders, containerUnit, allocatedByProvider);
            if (token == null) {
                break;
            }

            processingTokens.add(token);
            remaining--;
        }

    }

    private static void appendTargetStartupRequirementTokens(final Object context,
                                                             final CookingPotIndexedRecipe recipe,
                                                             final List<Object> processingTokens,
                                                             final Map<Object, List<Object>> allocatedByProvider) {
        if (BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(recipe.targetKey())) {
            appendKaleidoscopePotOilToken(context, processingTokens, allocatedByProvider);
            return;
        }

        if (!BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(recipe.targetKey())) {
            return;
        }

        final var lidItem = BuiltInRegistries.ITEM.getOptional(
                MinecraftApiCompat.resourceLocation(
                        BridgeKeys.MOD_KALEIDOSCOPE_COOKERY,
                        BridgeKeys.ITEM_KALEIDOSCOPE_STOCKPOT_LID
                )
        ).orElse(null);
        if (lidItem == null) {
            return;
        }

        final Ingredient lidRequirement = Ingredient.of(lidItem);
        final List<?> itemProviders = CfbhRuntime.contextItemProviders(context);
        final ResourceLocation requiredSoupBaseId = StockpotSoupBridge.resolveRequiredSoupBaseId(recipe);
        appendStockpotSoupBaseTokens(processingTokens, itemProviders, allocatedByProvider, requiredSoupBaseId);
        final Object lidToken = CfbhRuntime.findAndReserveIngredientToken(
                itemProviders, lidRequirement, allocatedByProvider);
        if (lidToken != null) {
            processingTokens.add(lidToken);
            return;
        }

        final Object lidItemToken = CfbhRuntime.findAndReserveItemToken(
                itemProviders, new ItemStack(lidItem), allocatedByProvider);
        if (lidItemToken != null) {
            processingTokens.add(lidItemToken);
        }
    }

    private static void appendKaleidoscopePotOilToken(final Object context,
                                                      final List<Object> processingTokens,
                                                      final Map<Object, List<Object>> allocatedByProvider) {
        final List<?> itemProviders = CfbhRuntime.contextItemProviders(context);

        final var oilItem = BuiltInRegistries.ITEM.getOptional(
                MinecraftApiCompat.resourceLocation(
                        BridgeKeys.MOD_KALEIDOSCOPE_COOKERY,
                        BridgeKeys.ITEM_KALEIDOSCOPE_OIL
                )
        ).orElse(null);
        if (oilItem != null) {
            final Object oilToken = findTokenForAnyCandidate(
                    itemProviders,
                    List.of(new ItemStack(oilItem)),
                    allocatedByProvider
            );
            if (oilToken != null) {
                processingTokens.add(oilToken);
                return;
            }
        }

        final var oilPotItem = BuiltInRegistries.ITEM.getOptional(
                MinecraftApiCompat.resourceLocation(
                        BridgeKeys.MOD_KALEIDOSCOPE_COOKERY,
                        BridgeKeys.ITEM_KALEIDOSCOPE_OIL_POT
                )
        ).orElse(null);
        if (oilPotItem == null) {
            return;
        }

        while (true) {
            final Object oilPotToken = findTokenForAnyCandidate(
                    itemProviders,
                    List.of(new ItemStack(oilPotItem)),
                    allocatedByProvider
            );
            if (oilPotToken == null) {
                return;
            }

            final ItemStack oilPotStack = CfbhRuntime.peekIngredientToken(oilPotToken);
            if (KaleidoscopeOilBridge.isKaleidoscopeOilPotWithOil(oilPotStack)) {
                processingTokens.add(oilPotToken);
                return;
            }
        }
    }

    private static void appendStockpotSoupBaseTokens(final List<Object> processingTokens,
                                                     final List<?> itemProviders,
                                                     final Map<Object, List<Object>> allocatedByProvider,
                                                     final ResourceLocation requiredSoupBaseId) {
        final List<Object> soupTokens = findStockpotSoupBaseTokens(
                itemProviders,
                allocatedByProvider,
                requiredSoupBaseId
        );
        if (soupTokens.isEmpty()) {
            return;
        }

        processingTokens.addAll(soupTokens);
    }

    private static List<Object> findStockpotSoupBaseTokens(final List<?> itemProviders,
                                                           final Map<Object, List<Object>> allocatedByProvider,
                                                           final ResourceLocation requiredSoupBaseId) {
        final List<Object> providersWithoutMarkers = new ArrayList<>();
        boolean waterSinkProviderDetected = false;
        boolean lavaSinkMarkerDetected = false;
        for (final Object itemProvider : itemProviders) {
            if (itemProvider instanceof MarkerProviderView markerProvider) {
                if (markerProvider.isMarkerKey(BridgeKeys.MARKER_LAVA_SINK)
                        && markerProvider.isActiveForCurrentTableMarker()) {
                    lavaSinkMarkerDetected = true;
                }
                continue;
            }
            if (StockpotSoupBridge.isSinkItemProvider(itemProvider)) {
                waterSinkProviderDetected = true;
                continue;
            }
            providersWithoutMarkers.add(itemProvider);
        }

        if (StockpotSoupBridge.isWaterSoupBase(requiredSoupBaseId)) {
            if (waterSinkProviderDetected) {
                return List.of(StockpotSoupBridge.syntheticWaterSinkSoupToken());
            }
            final Object token = findTokenForAnyCandidate(
                    providersWithoutMarkers,
                    stockpotWaterSoupBaseCandidates(),
                    allocatedByProvider
            );
            if (token != null) {
                return List.of(token);
            }
            return List.of();
        }

        if (StockpotSoupBridge.isLavaSoupBase(requiredSoupBaseId)) {
            if (lavaSinkMarkerDetected) {
                return List.of(StockpotSoupBridge.syntheticLavaSinkSoupToken());
            }
            final ItemStack lavaBucket = new ItemStack(Items.LAVA_BUCKET);
            final Object bucketToken = findTokenForAnyCandidate(
                    providersWithoutMarkers,
                    List.of(lavaBucket),
                    allocatedByProvider
            );
            if (bucketToken != null) {
                return List.of(bucketToken);
            }
            return List.of();
        }

        if (StockpotSoupBridge.isFishBucketSoupBase(requiredSoupBaseId)) {
            final ItemStack fishBucket = StockpotSoupBridge.soupBaseBucketStack(requiredSoupBaseId);
            if (!fishBucket.isEmpty()) {
                final Object bucketToken = findTokenForAnyCandidate(
                        providersWithoutMarkers,
                        List.of(fishBucket),
                        allocatedByProvider
                );
                if (bucketToken != null) {
                    return List.of(bucketToken);
                }
            }

            final ItemStack fishIngredient = StockpotSoupBridge.fishSoupBaseIngredientStack(requiredSoupBaseId);
            if (fishIngredient.isEmpty()) {
                return List.of();
            }

            final Object fishToken = findTokenForAnyCandidate(
                    providersWithoutMarkers,
                    List.of(fishIngredient),
                    allocatedByProvider
            );
            if (fishToken == null) {
                return List.of();
            }
            if (waterSinkProviderDetected) {
                return List.of(fishToken, StockpotSoupBridge.syntheticWaterSinkSoupToken());
            }

            final Object waterToken = findTokenForAnyCandidate(
                    providersWithoutMarkers,
                    stockpotWaterSoupBaseCandidates(),
                    allocatedByProvider
            );
            if (waterToken != null) {
                return List.of(fishToken, waterToken);
            }
            return List.of();
        }

        final ItemStack exactSoupBaseItem = StockpotSoupBridge.soupBaseBucketStack(requiredSoupBaseId);
        if (!exactSoupBaseItem.isEmpty()) {
            final Object soupToken = findTokenForAnyCandidate(
                    providersWithoutMarkers,
                    List.of(exactSoupBaseItem),
                    allocatedByProvider
            );
            if (soupToken != null) {
                return List.of(soupToken);
            }
        }

        return List.of();
    }

    private static boolean consumeRequiredContainers(final Object context,
                                                     final Recipe<?> recipe,
                                                     final RegistryAccess registryAccess,
                                                     final boolean copperPotActive,
                                                     final List<TokenConsumption> consumed) {
        final ItemStack containerCost = CookingPotContainerCost.resolveForCraft(recipe, registryAccess, copperPotActive);
        if (containerCost.isEmpty()) {
            return true;
        }

        final ItemStack containerUnit = containerCost.copy();
        containerUnit.setCount(1);

        final List<?> itemProviders = CfbhRuntime.contextItemProviders(context);
        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final Object token = CfbhRuntime.findAndReserveItemToken(
                    itemProviders, containerUnit, new IdentityHashMap<>());
            if (token == null) {
                return false;
            }

            final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
            if (consumedStack.isEmpty()) {
                return false;
            }

            consumed.add(new TokenConsumption(token, consumedStack));
            remaining -= Math.max(1, consumedStack.getCount());
        }

        return true;
    }

    private static Object findTokenForAnyCandidate(final List<?> itemProviders,
                                                   final List<ItemStack> candidates,
                                                   final Map<Object, List<Object>> allocatedByProvider) {
        for (final ItemStack candidate : candidates) {
            final Object exactItemToken = CfbhRuntime.findAndReserveItemToken(
                    itemProviders, candidate, allocatedByProvider);
            if (exactItemToken != null) {
                return exactItemToken;
            }

            final Object ingredientToken = CfbhRuntime.findAndReserveIngredientToken(
                    itemProviders,
                    Ingredient.of(candidate),
                    allocatedByProvider
            );
            if (ingredientToken != null) {
                return ingredientToken;
            }
        }
        return null;
    }

    private static List<ItemStack> stockpotWaterSoupBaseCandidates() {
        final List<ItemStack> cached = cachedWaterSoupBaseCandidates;
        if (cached != null) {
            return cached;
        }

        final List<ItemStack> candidates = new ArrayList<>();
        try {
            final Class<?> registryClass = Class.forName(CFBH_COOKING_REGISTRY_CLASS);
            final Method getWaterItems = registryClass.getMethod(CFBH_GET_WATER_ITEMS_METHOD);
            final Object value = getWaterItems.invoke(null);
            if (value instanceof Iterable<?> iterable) {
                for (final Object entry : iterable) {
                    if (entry instanceof ItemStack stack && !stack.isEmpty()) {
                        addUniqueSoupBaseCandidate(candidates, stack.copyWithCount(1));
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to vanilla water bucket.
        }
        addUniqueSoupBaseCandidate(candidates, new ItemStack(Items.WATER_BUCKET));
        final List<ItemStack> immutable = List.copyOf(candidates);
        cachedWaterSoupBaseCandidates = immutable;
        return immutable;
    }

    private static void addUniqueSoupBaseCandidate(final List<ItemStack> candidates, final ItemStack candidate) {
        for (final ItemStack existing : candidates) {
            if (MinecraftApiCompat.isSameItemSameData(existing, candidate)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private static void notifyFailure(final Object context, final String translationKey) {
        CfbhRuntime.contextNotify(
                context,
                CfbhRuntime.newKitchenOperationWithFeedback(
                        Component.translatable(translationKey).withStyle(ChatFormatting.RED)
                )
        );
    }

    private static void restoreConsumed(final Object context, final List<TokenConsumption> consumed) {
        for (int i = consumed.size() - 1; i >= 0; i--) {
            final TokenConsumption tokenConsumption = consumed.get(i);
            final ItemStack rest = CfbhRuntime.restoreIngredientToken(tokenConsumption.token(), tokenConsumption.stack);
            if (!rest.isEmpty()) {
                CfbhRuntime.contextRestore(context, rest);
            }
        }
    }
}
