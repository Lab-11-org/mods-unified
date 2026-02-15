package org.lab_11.modsunified.impl;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class CookingPotIndexedRecipe implements Recipe<RecipeInput> {
    private final Recipe<?> delegate;
    private final NonNullList<Ingredient> indexedIngredients;
    private final ItemStack indexedResult;

    public CookingPotIndexedRecipe(final Recipe<?> delegate,
                                   final RegistryAccess registryAccess,
                                   final String markerKey) {
        this.delegate = delegate;
        this.indexedResult = resolveIndexedResult(delegate, registryAccess, markerKey);
        this.indexedIngredients = buildIndexedIngredients(delegate, registryAccess, markerKey);
    }

    public static RecipeHolder<Recipe<?>> toIndexedRecipeHolder(final RecipeHolder<?> recipeHolder,
                                                                final RegistryAccess registryAccess,
                                                                final String markerKey) {
        final CookingPotIndexedRecipe indexedRecipe =
                new CookingPotIndexedRecipe(recipeHolder.value(), registryAccess, markerKey);
        return new RecipeHolder<>(recipeHolder.id(), indexedRecipe);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean matches(final RecipeInput recipeInput, final Level level) {
        return ((Recipe<RecipeInput>) delegate).matches(recipeInput, level);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemStack assemble(final RecipeInput recipeInput, final HolderLookup.Provider provider) {
        return ((Recipe<RecipeInput>) delegate).assemble(recipeInput, provider);
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(final HolderLookup.Provider provider) {
        return indexedResult.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return indexedIngredients;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return delegate.getSerializer();
    }

    @Override
    public RecipeType<?> getType() {
        return delegate.getType();
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public ItemStack getToastSymbol() {
        return delegate.getToastSymbol();
    }

    private static NonNullList<Ingredient> buildIndexedIngredients(final Recipe<?> recipe,
                                                                   final RegistryAccess registryAccess,
                                                                   final String markerKey) {
        final NonNullList<Ingredient> indexedIngredients = NonNullList.create();
        indexedIngredients.addAll(recipe.getIngredients());
        indexedIngredients.add(CookingPotActivationMarkerProvider.markerIngredient(markerKey));

        final ItemStack containerCost = CookingPotContainerCost.resolveForIndexedRecipe(recipe, registryAccess, markerKey);
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

    private static ItemStack resolveIndexedResult(final Recipe<?> recipe,
                                                  final RegistryAccess registryAccess,
                                                  final String markerKey) {
        final ItemStack rawResult = recipe.getResultItem(registryAccess);
        if (MinersDelightCupConversion.COPPER_POT_TARGET_KEY.equals(markerKey)) {
            return MinersDelightCupConversion.convertOutputForCopperPot(rawResult);
        }

        return rawResult.copy();
    }
}
