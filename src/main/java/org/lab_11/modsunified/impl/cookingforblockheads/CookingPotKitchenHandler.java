package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CookingPotKitchenHandler implements CfbhRuntime.KitchenRecipeHandlerView {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_STOCKPOT_TRANSFER = Boolean.getBoolean("lab11.debug.stockpot_transfer");
    private static final String CFBH_COOKING_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.registry.CookingRegistry";
    private static final String CFBH_GET_WATER_ITEMS_METHOD = "getWaterItems";
    private static final String FEEDBACK_POT_TRANSFER_FAILED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_transfer_failed";
    private static final String FEEDBACK_STOCKPOT_NO_TARGET_PROCESSOR_KEY =
            "lab_11_mods_unified.feedback.cooking_table.stockpot_no_target_processor";

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
        final boolean skipOutputContainerTransferForStockpot =
                BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(recipe.targetKey());
        if (!skipOutputContainerTransferForStockpot
                && !appendRequiredContainerTokens(context, recipe, processingTokens, registryAccess, convertForCopperTarget)) {
            if (DEBUG_STOCKPOT_TRANSFER && BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(recipe.targetKey())) {
                LOGGER.info("Stockpot route aborted: missing required container tokens.");
            }
            notifyFailure(context, FEEDBACK_POT_TRANSFER_FAILED_KEY);
            return ItemStack.EMPTY;
        }
        appendTargetStartupRequirementTokens(context, recipe, processingTokens);

        int matchingProcessors = 0;
        for (final Object itemProcessor : CfbhRuntime.contextItemProcessors(context)) {
            if (!CfbhRuntime.processorCanProcess(itemProcessor, recipe.getType())) {
                continue;
            }
            matchingProcessors++;

            final Object operation = CfbhRuntime.processorProcessRecipe(itemProcessor, recipe, processingTokens);
            if (!CfbhRuntime.isEmptyKitchenOperation(operation)) {
                CfbhRuntime.contextNotify(context, operation);
                return ItemStack.EMPTY;
            }
        }

        if (DEBUG_STOCKPOT_TRANSFER && BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(recipe.targetKey())) {
            LOGGER.info("Stockpot route completed with no processor operation. matchingProcessors={}", matchingProcessors);
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

    private static boolean appendRequiredContainerTokens(final Object context,
                                                         final Recipe<?> recipe,
                                                         final List<Object> processingTokens,
                                                         final RegistryAccess registryAccess,
                                                         final boolean copperPotActive) {
        final ItemStack containerCost = CookingPotContainerCost.resolveForCraft(recipe, registryAccess, copperPotActive);
        if (containerCost.isEmpty()) {
            return true;
        }

        final ItemStack containerUnit = containerCost.copy();
        containerUnit.setCount(1);
        final Ingredient containerIngredient = Ingredient.of(containerUnit);

        final List<Object> allocated = new ArrayList<>(processingTokens);
        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final Object token = findIngredientToken(CfbhRuntime.contextItemProviders(context), containerIngredient, allocated);
            if (token == null) {
                return false;
            }

            allocated.add(token);
            processingTokens.add(token);
            remaining--;
        }

        return true;
    }

    private static void appendTargetStartupRequirementTokens(final Object context,
                                                             final CookingPotIndexedRecipe recipe,
                                                             final List<Object> processingTokens) {
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
        final List<Object> allocated = new ArrayList<>(processingTokens);
        appendStockpotSoupBaseToken(context, processingTokens, allocated);
        final Object lidToken = findIngredientToken(CfbhRuntime.contextItemProviders(context), lidRequirement, allocated);
        if (lidToken != null) {
            processingTokens.add(lidToken);
            return;
        }

        final Object lidItemToken = findItemToken(
                CfbhRuntime.contextItemProviders(context),
                new ItemStack(lidItem),
                allocated
        );
        if (lidItemToken != null) {
            processingTokens.add(lidItemToken);
        }
    }

    private static void appendStockpotSoupBaseToken(final Object context,
                                                    final List<Object> processingTokens,
                                                    final List<Object> allocated) {
        final Object soupToken = findStockpotSoupBaseToken(CfbhRuntime.contextItemProviders(context), allocated);
        if (soupToken == null) {
            return;
        }

        processingTokens.add(soupToken);
        allocated.add(soupToken);
    }

    private static Object findStockpotSoupBaseToken(final List<?> itemProviders, final Collection<?> allocatedTokens) {
        final List<Object> providersWithoutMarkers = new ArrayList<>();
        boolean sinkProviderDetected = false;
        for (final Object itemProvider : itemProviders) {
            if (itemProvider instanceof MarkerProviderView) {
                continue;
            }
            if (StockpotSoupBridge.isSinkItemProvider(itemProvider)) {
                sinkProviderDetected = true;
                continue;
            }
            providersWithoutMarkers.add(itemProvider);
        }

        for (final ItemStack candidate : stockpotSoupBaseCandidates()) {
            final Object exactItemToken = findItemToken(providersWithoutMarkers, candidate, allocatedTokens);
            if (exactItemToken != null) {
                return exactItemToken;
            }

            final Object ingredientToken = findIngredientToken(
                    providersWithoutMarkers,
                    Ingredient.of(candidate),
                    allocatedTokens
            );
            if (ingredientToken != null) {
                return ingredientToken;
            }
        }

        if (sinkProviderDetected) {
            return StockpotSoupBridge.syntheticSinkSoupToken();
        }

        return null;
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
        final Ingredient containerIngredient = Ingredient.of(containerUnit);

        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final List<Object> allocated = new ArrayList<>(consumed.size());
            for (final TokenConsumption tokenConsumption : consumed) {
                allocated.add(tokenConsumption.token());
            }

            final Object token = findIngredientToken(CfbhRuntime.contextItemProviders(context), containerIngredient, allocated);
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

    private static Object findIngredientToken(final List<?> itemProviders,
                                              final Ingredient ingredient,
                                              final Collection<?> allocatedTokens) {
        for (final Object itemProvider : itemProviders) {
            final Object token = CfbhRuntime.findIngredientToken(itemProvider, ingredient, allocatedTokens);
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    private static List<ItemStack> stockpotSoupBaseCandidates() {
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
        return candidates;
    }

    private static void addUniqueSoupBaseCandidate(final List<ItemStack> candidates, final ItemStack candidate) {
        for (final ItemStack existing : candidates) {
            if (MinecraftApiCompat.isSameItemSameData(existing, candidate)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private static Object findItemToken(final List<?> itemProviders,
                                        final ItemStack itemStack,
                                        final Collection<?> allocatedTokens) {
        for (final Object itemProvider : itemProviders) {
            final Object token = CfbhRuntime.findItemToken(itemProvider, itemStack, allocatedTokens);
            if (token != null) {
                return token;
            }
        }
        return null;
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
