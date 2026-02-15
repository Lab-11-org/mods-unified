package org.lab_11.modsunified.impl;

import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CookingPotKitchenHandler implements KitchenRecipeHandler<Recipe<?>> {

    private record TokenConsumption(IngredientToken token, ItemStack stack) {
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
    public ItemStack assemble(final CraftingContext context,
                              final Recipe<?> recipe,
                              final List<IngredientToken> ingredientTokens,
                              final RegistryAccess registryAccess) {
        final boolean copperPotActive = MinersDelightCupConversion.isCopperPotActive(context);
        ItemStack output = recipe.getResultItem(registryAccess).copy();
        if (copperPotActive) {
            output = MinersDelightCupConversion.convertOutputForCopperPot(output);
        }
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (ingredientTokens.size() < recipe.getIngredients().size()) {
            return ItemStack.EMPTY;
        }

        final List<TokenConsumption> consumed = new ArrayList<>(ingredientTokens.size());

        for (final IngredientToken ingredientToken : ingredientTokens) {
            if (ingredientToken == IngredientToken.EMPTY) {
                continue;
            }

            final ItemStack consumedStack = ingredientToken.consume();
            if (consumedStack.isEmpty()) {
                restoreConsumed(context, consumed);
                return ItemStack.EMPTY;
            }

            consumed.add(new TokenConsumption(ingredientToken, consumedStack));
        }

        if (!consumeRequiredContainers(context, recipe, registryAccess, copperPotActive, consumed)) {
            restoreConsumed(context, consumed);
            return ItemStack.EMPTY;
        }

        return output;
    }

    private static boolean consumeRequiredContainers(final CraftingContext context,
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

        final List<IngredientToken> allocatedTokens = new ArrayList<>(consumed.size());
        for (final TokenConsumption tokenConsumption : consumed) {
            allocatedTokens.add(tokenConsumption.token());
        }

        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final IngredientToken token = findIngredientToken(context.getItemProviders(), containerIngredient, allocatedTokens);
            if (token == null || token == IngredientToken.EMPTY) {
                return false;
            }

            final ItemStack consumedStack = token.consume();
            if (consumedStack.isEmpty()) {
                return false;
            }

            consumed.add(new TokenConsumption(token, consumedStack));
            allocatedTokens.add(token);
            remaining -= Math.max(1, consumedStack.getCount());
        }

        return true;
    }

    private static IngredientToken findIngredientToken(final List<KitchenItemProvider> itemProviders,
                                                       final Ingredient ingredient,
                                                       final Collection<IngredientToken> allocatedTokens) {
        for (final KitchenItemProvider itemProvider : itemProviders) {
            final IngredientToken token = itemProvider.findIngredient(ingredient, allocatedTokens, CacheHint.NONE);
            if (token != null && token != IngredientToken.EMPTY) {
                return token;
            }
        }
        return null;
    }

    private static void restoreConsumed(final CraftingContext context, final List<TokenConsumption> consumed) {
        for (int i = consumed.size() - 1; i >= 0; i--) {
            final TokenConsumption tokenConsumption = consumed.get(i);
            final ItemStack rest = tokenConsumption.token.restore(tokenConsumption.stack);
            if (!rest.isEmpty()) {
                context.restore(rest);
            }
        }
    }
}
