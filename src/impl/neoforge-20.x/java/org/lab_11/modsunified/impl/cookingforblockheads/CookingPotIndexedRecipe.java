package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;

import java.util.List;

public final class CookingPotIndexedRecipe implements Recipe<Container> {
    private final Recipe<?> delegate;
    private final String targetKey;
    private final NonNullList<Ingredient> indexedIngredients;
    private final ItemStack indexedResult;
    private final ItemStack indexedContainerCost;
    private final List<String> requiredMarkerKeys;
    private final int syntheticIngredientCount;

    public CookingPotIndexedRecipe(final Recipe<?> delegate,
                                   final RegistryAccess registryAccess,
                                   final String markerKey,
                                   final List<String> requiredMarkerKeys) {
        this.delegate = delegate;
        this.targetKey = markerKey;
        this.indexedResult = resolveIndexedResult(delegate, registryAccess, markerKey);
        this.indexedContainerCost = CookingPotContainerCost.resolveForIndexedRecipe(delegate, registryAccess, markerKey);
        this.requiredMarkerKeys = List.copyOf(requiredMarkerKeys);
        this.syntheticIngredientCount = resolveSyntheticIngredientCount(delegate);
        this.indexedIngredients = buildIndexedIngredients(delegate, markerKey, syntheticIngredientCount);
    }

    public static Object toIndexedRecipeHolder(final Object recipeEntry,
                                               final net.minecraft.resources.ResourceLocation indexedId,
                                               final RegistryAccess registryAccess,
                                               final String markerKey,
                                               final List<String> requiredMarkerKeys) {
        final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(recipeEntry);
        if (recipe == null) {
            return null;
        }
        final CookingPotIndexedRecipe indexedRecipe =
                new CookingPotIndexedRecipe(recipe, registryAccess, markerKey, requiredMarkerKeys);
        return RecipeRuntimeCompat.recipeEntry(indexedId, indexedRecipe);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean matches(final Container container, final Level level) {
        return ((Recipe<Container>) delegate).matches(container, level);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ItemStack assemble(final Container container, final RegistryAccess registryAccess) {
        return ((Recipe<Container>) delegate).assemble(container, registryAccess);
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(final RegistryAccess registryAccess) {
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

    public int syntheticIngredientCount() {
        return syntheticIngredientCount;
    }

    public String targetKey() {
        return targetKey;
    }

    public ItemStack indexedContainerCost() {
        return indexedContainerCost.copy();
    }

    Recipe<?> delegateRecipe() {
        return delegate;
    }

    public List<String> requiredMarkerKeys() {
        return requiredMarkerKeys;
    }

    private static NonNullList<Ingredient> buildIndexedIngredients(final Recipe<?> recipe,
                                                                   final String markerKey,
                                                                   final int syntheticIngredientCount) {
        final NonNullList<Ingredient> indexedIngredients = NonNullList.create();
        if (syntheticIngredientCount > 0) {
            indexedIngredients.add(CookingPotActivationMarkerProvider.markerIngredient(markerKey));
        }

        indexedIngredients.addAll(recipe.getIngredients());
        return indexedIngredients;
    }

    private static int resolveSyntheticIngredientCount(final Recipe<?> recipe) {
        return recipe.getIngredients().size() < 9 ? 1 : 0;
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
