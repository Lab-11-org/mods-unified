package org.lab_11.modsunified.compat;

import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FDCookingPotKitchenHandler implements KitchenRecipeHandler<CookingPotRecipe> {

    private record TokenConsumption(IngredientToken token, ItemStack stack) {
    }

    @Override
    public int mapToMatrixSlot(final CookingPotRecipe recipe, final int ingredientIndex) {
        return ingredientIndex < 9 ? ingredientIndex : 8;
    }

    @Override
    public ItemStack assemble(final CraftingContext context,
                              final CookingPotRecipe recipe,
                              final List<IngredientToken> ingredientTokens,
                              final RegistryAccess registryAccess) {
        final ItemStack output = recipe.getResultItem(registryAccess);
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (ingredientTokens.size() < recipe.getIngredients().size()) {
            return ItemStack.EMPTY;
        }

        final List<TokenConsumption> consumed = new ArrayList<>(ingredientTokens.size());

        for (IngredientToken ingredientToken : ingredientTokens) {
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

        final ItemStack containerCost = recipe.getContainerOverride();
        if (!containerCost.isEmpty()) {
            final List<IngredientToken> containerTokens = reserveContainerTokens(context, containerCost);
            if (containerTokens.isEmpty() && containerCost.getCount() > 0) {
                restoreConsumed(context, consumed);
                return ItemStack.EMPTY;
            }

            for (IngredientToken containerToken : containerTokens) {
                final ItemStack consumedStack = containerToken.consume();
                if (consumedStack.isEmpty()) {
                    restoreConsumed(context, consumed);
                    return ItemStack.EMPTY;
                }

                consumed.add(new TokenConsumption(containerToken, consumedStack));
            }
        }

        return output.copy();
    }

    private static List<IngredientToken> reserveContainerTokens(final CraftingContext context, final ItemStack containerCost) {
        final int requiredCount = containerCost.getCount();
        if (requiredCount <= 0) {
            return List.of();
        }

        final ItemStack requestedUnit = containerCost.copy();
        requestedUnit.setCount(1);

        final List<KitchenItemProvider> itemProviders = context.getItemProviders();
        final Map<Integer, List<IngredientToken>> reservedTokensByProvider = new HashMap<>();
        final List<IngredientToken> reservedContainerTokens = new ArrayList<>(requiredCount);

        for (int i = 0; i < requiredCount; i++) {
            IngredientToken foundToken = null;

            for (int providerIndex = 0; providerIndex < itemProviders.size(); providerIndex++) {
                final KitchenItemProvider itemProvider = itemProviders.get(providerIndex);
                final List<IngredientToken> scopedReservations = reservedTokensByProvider.computeIfAbsent(providerIndex, ignored -> new ArrayList<>());
                final IngredientToken candidate = itemProvider.findIngredient(requestedUnit, scopedReservations, CacheHint.NONE);
                if (candidate != null) {
                    scopedReservations.add(candidate);
                    foundToken = candidate;
                    break;
                }
            }

            if (foundToken == null) {
                return List.of();
            }

            reservedContainerTokens.add(foundToken);
        }

        return reservedContainerTokens;
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
