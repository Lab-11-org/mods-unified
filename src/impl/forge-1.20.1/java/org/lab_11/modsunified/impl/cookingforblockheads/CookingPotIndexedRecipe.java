package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;

import java.util.List;
import java.util.ArrayList;

public final class CookingPotIndexedRecipe implements CraftingRecipe {
    private final net.minecraft.resources.ResourceLocation indexedId;
    private final Recipe<?> delegate;
    private final String targetKey;
    private final NonNullList<Ingredient> indexedIngredients;
    private final ItemStack indexedResult;
    private final ItemStack indexedContainerCost;
    private final List<String> requiredMarkerKeys;
    private final int syntheticIngredientCount;

    public CookingPotIndexedRecipe(final net.minecraft.resources.ResourceLocation indexedId,
                                   final Recipe<?> delegate,
                                   final RegistryAccess registryAccess,
                                   final String markerKey,
                                   final List<String> requiredMarkerKeys) {
        this.indexedId = indexedId;
        this.delegate = delegate;
        this.targetKey = markerKey;
        this.indexedResult = resolveIndexedResult(delegate, registryAccess, markerKey);
        this.indexedContainerCost = CookingPotContainerCost.resolveForIndexedRecipe(delegate, registryAccess, markerKey);
        this.requiredMarkerKeys = List.copyOf(requiredMarkerKeys);
        // Legacy 1.20.1 recipe-book path does not include a synthetic marker ingredient.
        this.syntheticIngredientCount = 0;
        this.indexedIngredients = buildIndexedIngredients(delegate, markerKey);
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
                new CookingPotIndexedRecipe(indexedId, recipe, registryAccess, markerKey, requiredMarkerKeys);
        return RecipeRuntimeCompat.recipeEntry(indexedId, indexedRecipe);
    }

    @Override
    public boolean matches(final CraftingContainer container, final Level level) {
        final List<ItemStack> remaining = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            final ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                remaining.add(stack);
            }
        }

        for (final Ingredient ingredient : delegate.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            boolean matched = false;
            for (int i = 0; i < remaining.size(); i++) {
                final ItemStack stack = remaining.get(i);
                if (ingredient.test(stack)) {
                    remaining.remove(i);
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return false;
            }
        }

        return remaining.isEmpty();
    }

    @Override
    public ItemStack assemble(final CraftingContainer container, final RegistryAccess registryAccess) {
        return indexedResult.copy();
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
    public net.minecraft.resources.ResourceLocation getId() {
        return indexedId;
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
        return RecipeType.CRAFTING;
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
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
                                                                   final String markerKey) {
        final NonNullList<Ingredient> indexedIngredients = NonNullList.create();
        indexedIngredients.addAll(recipe.getIngredients());
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
