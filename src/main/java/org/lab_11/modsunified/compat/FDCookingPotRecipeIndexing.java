package org.lab_11.modsunified.compat;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

public final class FDCookingPotRecipeIndexing {
    private FDCookingPotRecipeIndexing() {
    }

    @SuppressWarnings("unchecked")
    public static RecipeHolder<Recipe<?>> toIndexedRecipeHolder(final RecipeHolder<CookingPotRecipe> recipeHolder,
                                                                final RegistryAccess registryAccess) {
        final CookingPotRecipe recipe = recipeHolder.value();
        final ItemStack containerCost = resolveContainerCost(recipe, registryAccess);
        final ContainerAwareCookingPotRecipe indexedRecipe = new ContainerAwareCookingPotRecipe(recipe, registryAccess, containerCost);
        return new RecipeHolder<>(recipeHolder.id(), indexedRecipe);
    }

    public static ItemStack resolveContainerCost(final CookingPotRecipe recipe, final RegistryAccess registryAccess) {
        final ItemStack container = recipe.getOutputContainer();
        if (container.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final ItemStack output = recipe.getResultItem(registryAccess);
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final int outputCount = Math.max(1, output.getCount());
        final int containerCountPerServing = Math.max(1, container.getCount());
        final long totalContainerCount = (long) outputCount * containerCountPerServing;

        final ItemStack totalContainerCost = container.copy();
        totalContainerCost.setCount((int) Math.min(Integer.MAX_VALUE, totalContainerCount));
        return totalContainerCost;
    }

    private static NonNullList<Ingredient> buildIndexedIngredients(final CookingPotRecipe baseRecipe,
                                                                   final ItemStack containerCost) {
        final NonNullList<Ingredient> indexedIngredients = NonNullList.create();
        indexedIngredients.addAll(baseRecipe.getIngredients());
        indexedIngredients.add(FDCookingPotActivationMarkerProvider.markerIngredient());

        if (!containerCost.isEmpty()) {
            final ItemStack containerUnit = containerCost.copy();
            containerUnit.setCount(1);
            final Ingredient containerIngredient = Ingredient.of(containerUnit);
            for (int i = 0; i < containerCost.getCount(); i++) {
                indexedIngredients.add(containerIngredient);
            }
        }

        return indexedIngredients;
    }

    private static final class ContainerAwareCookingPotRecipe extends CookingPotRecipe {
        private ContainerAwareCookingPotRecipe(final CookingPotRecipe baseRecipe,
                                               final RegistryAccess registryAccess,
                                               final ItemStack containerCost) {
            super(
                    baseRecipe.getGroup(),
                    baseRecipe.getRecipeBookTab(),
                    buildIndexedIngredients(baseRecipe, containerCost),
                    baseRecipe.getResultItem(registryAccess).copy(),
                    baseRecipe.getContainerOverride().copy(),
                    baseRecipe.getExperience(),
                    baseRecipe.getCookTime()
            );
        }
    }
}
