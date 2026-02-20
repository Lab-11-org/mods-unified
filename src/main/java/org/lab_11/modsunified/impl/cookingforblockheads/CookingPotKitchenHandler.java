package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CookingPotKitchenHandler implements CfbhRuntime.KitchenRecipeHandlerView {
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
        final boolean convertForCopperTarget =
                MinersDelightCupConversion.COPPER_POT_TARGET_KEY.equals(recipe.targetKey());
        final List<Object> processingTokens = new ArrayList<>(ingredientTokens);
        if (!appendRequiredContainerTokens(context, recipe, processingTokens, registryAccess, convertForCopperTarget)) {
            return ItemStack.EMPTY;
        }

        for (final Object itemProcessor : CfbhRuntime.contextItemProcessors(context)) {
            if (!CfbhRuntime.processorCanProcess(itemProcessor, recipe.getType())) {
                continue;
            }

            final Object operation = CfbhRuntime.processorProcessRecipe(itemProcessor, recipe, processingTokens);
            if (!CfbhRuntime.isEmptyKitchenOperation(operation)) {
                CfbhRuntime.contextNotify(context, operation);
                return ItemStack.EMPTY;
            }
        }

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
