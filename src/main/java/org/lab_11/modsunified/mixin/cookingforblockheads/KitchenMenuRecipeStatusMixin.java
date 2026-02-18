package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.cookingforblockheads.crafting.CraftingContext;
import net.blay09.mods.cookingforblockheads.crafting.KitchenImpl;
import net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus;
import net.blay09.mods.cookingforblockheads.network.message.SelectionRecipesListMessage;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotIndexedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu")
abstract class KitchenMenuRecipeStatusMixin {
    private record VariantCandidate(String displayKey, RecipeWithStatus status) {
    }

    private record VariantAxis(int ingredientIndex, List<ItemStack> options) {
    }

    private record StackIdentity(Object item, Object components) {
    }

    @Shadow
    private Player player;
    @Shadow
    private KitchenImpl kitchen;
    @Shadow
    private NonNullList<ItemStack> lockedInputs;
    @Shadow
    private Comparator<RecipeWithStatus> currentSorting;
    @Shadow
    private List<RecipeWithStatus> recipesForSelection;

    @Shadow
    private Collection<RecipeHolder<Recipe<?>>> getRecipesFor(ItemStack resultItem) {
        throw new AssertionError();
    }

    @Inject(
            method = "broadcastRecipesForResultItem",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void lab11$indexedVariantsForSelection(final ItemStack resultItem, final CallbackInfo ci) {
        final Collection<RecipeHolder<Recipe<?>>> recipesForResult = getRecipesFor(resultItem);
        if (recipesForResult.stream().noneMatch(KitchenMenuRecipeStatusMixin::isIndexedRecipeHolder)) {
            return;
        }

        final CraftingContext context = new CraftingContext(kitchen, player);
        final Map<String, RecipeWithStatus> deduped = recipesForResult.stream()
                .flatMap(recipeHolder -> buildVariantCandidates(context, recipeHolder))
                .collect(Collectors.toMap(
                        VariantCandidate::displayKey,
                        VariantCandidate::status,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));

        final List<RecipeWithStatus> result = new ArrayList<>(deduped.values());
        result.sort(selectionComparator());

        recipesForSelection = result;
        Balm.getNetworking().sendTo(player, new SelectionRecipesListMessage(result));
        ci.cancel();
    }

    private static boolean isIndexedRecipe(final RecipeWithStatus status) {
        final var recipeId = status.recipeId();
        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    private Comparator<RecipeWithStatus> selectionComparator() {
        return (left, right) -> {
            final boolean leftIndexed = isIndexedRecipe(left);
            final boolean rightIndexed = isIndexedRecipe(right);
            if (leftIndexed != rightIndexed) {
                return leftIndexed ? -1 : 1;
            }

            if (left.canCraft() != right.canCraft()) {
                return left.canCraft() ? -1 : 1;
            }

            final int missingCompare = Integer.compare(left.missingIngredients().size(), right.missingIngredients().size());
            if (missingCompare != 0) {
                return missingCompare;
            }

            return currentSorting.compare(left, right);
        };
    }

    private Stream<VariantCandidate> buildVariantCandidates(final CraftingContext context,
                                                            final RecipeHolder<Recipe<?>> recipeHolder) {
        final Recipe<?> recipe = recipeHolder.value();
        final ItemStack recipeResult = recipe.getResultItem(player.level().registryAccess());
        final NonNullList<ItemStack> baseLocks = recipe instanceof CookingPotIndexedRecipe
                ? emptyLocksForRecipe(recipe)
                : prepareLocksForRecipe(lockedInputs, recipe);

        return Stream.concat(
                        Stream.of(baseLocks),
                        expandTagVariantLocks(recipe, baseLocks)
                )
                .map(variantLocks -> buildVariantCandidate(context, recipeHolder, recipeResult, variantLocks));
    }

    private static VariantCandidate buildVariantCandidate(final CraftingContext context,
                                                          final RecipeHolder<Recipe<?>> recipeHolder,
                                                          final ItemStack recipeResult,
                                                          final List<ItemStack> locks) {
        final Recipe<?> recipe = recipeHolder.value();
        final NonNullList<ItemStack> operationLocks = prepareLocksForRecipe(locks, recipe);
        final var operation = context.createOperation(recipeHolder).withLockedInputs(operationLocks).prepare();
        final NonNullList<ItemStack> displayLocks = copyLocks(operation.getLockedInputs());
        final RecipeWithStatus status = new RecipeWithStatus(
                recipeHolder.id(),
                recipeResult,
                operation.getMissingIngredients(),
                operation.getMissingIngredientsMask(),
                displayLocks
        );
        return new VariantCandidate(buildDisplayKey(recipe, recipeResult, displayLocks), status);
    }

    private static Stream<NonNullList<ItemStack>> expandTagVariantLocks(final Recipe<?> recipe,
                                                                        final NonNullList<ItemStack> baseLocks) {
        final List<VariantAxis> axes = collectVariantAxes(recipe, baseLocks);
        if (axes.isEmpty()) {
            return Stream.empty();
        }

        final List<NonNullList<ItemStack>> expanded = new ArrayList<>();
        final int[] cursor = new int[axes.size()];
        while (advanceCursor(cursor, axes)) {
            expanded.add(applyCursor(baseLocks, axes, cursor));
        }

        return expanded.stream();
    }

    private static List<VariantAxis> collectVariantAxes(final Recipe<?> recipe,
                                                        final List<ItemStack> baseLocks) {
        final List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return List.of();
        }

        final int start = recipe instanceof CookingPotIndexedRecipe indexedRecipe
                ? Math.max(0, indexedRecipe.syntheticIngredientCount())
                : 0;
        final int end = Math.min(ingredients.size(), baseLocks.size());
        final List<VariantAxis> axes = new ArrayList<>();
        for (int index = start; index < end && axes.size() < Integer.SIZE; index++) {
            if (!baseLocks.get(index).isEmpty()) {
                continue;
            }

            final List<ItemStack> options = collectIngredientOptions(ingredients.get(index));
            if (options.size() > 1) {
                axes.add(new VariantAxis(index, options));
            }
        }

        return axes;
    }

    private static NonNullList<ItemStack> applyCursor(final List<ItemStack> baseLocks,
                                                      final List<VariantAxis> axes,
                                                      final int[] cursor) {
        final NonNullList<ItemStack> variantLocks = copyLocks(baseLocks);
        for (int axisIndex = 0; axisIndex < axes.size(); axisIndex++) {
            final VariantAxis axis = axes.get(axisIndex);
            variantLocks.set(axis.ingredientIndex(), axis.options().get(cursor[axisIndex]));
        }
        return variantLocks;
    }

    private static NonNullList<ItemStack> copyLocks(final List<ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return NonNullList.create();
        }

        final NonNullList<ItemStack> copy = NonNullList.withSize(source.size(), ItemStack.EMPTY);
        for (int i = 0; i < source.size(); i++) {
            final ItemStack stack = source.get(i);
            copy.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return copy;
    }

    private static NonNullList<ItemStack> prepareLocksForRecipe(final List<ItemStack> source,
                                                                final Recipe<?> recipe) {
        final NonNullList<ItemStack> operationLocks = copyLocks(source);
        if (operationLocks.isEmpty()) {
            return operationLocks;
        }

        final List<Ingredient> ingredients = recipe.getIngredients();
        final int max = Math.min(operationLocks.size(), ingredients.size());
        for (int i = 0; i < max; i++) {
            final ItemStack locked = operationLocks.get(i);
            if (locked.isEmpty()) {
                continue;
            }

            final Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty() || !ingredient.test(locked)) {
                operationLocks.set(i, ItemStack.EMPTY);
            }
        }

        for (int i = max; i < operationLocks.size(); i++) {
            operationLocks.set(i, ItemStack.EMPTY);
        }
        return operationLocks;
    }

    private static NonNullList<ItemStack> emptyLocksForRecipe(final Recipe<?> recipe) {
        return NonNullList.withSize(recipe.getIngredients().size(), ItemStack.EMPTY);
    }

    private static List<ItemStack> collectIngredientOptions(final Ingredient ingredient) {
        final ItemStack[] rawOptions = ingredient.getItems();
        if (rawOptions.length == 0) {
            return List.of();
        }

        final LinkedHashMap<StackIdentity, ItemStack> options = new LinkedHashMap<>(rawOptions.length);
        for (final ItemStack raw : rawOptions) {
            if (raw.isEmpty()) {
                continue;
            }

            final ItemStack option = raw.copyWithCount(1);
            options.putIfAbsent(new StackIdentity(option.getItem(), option.getComponents()), option);
        }

        final List<ItemStack> sorted = new ArrayList<>(options.values());
        sorted.sort(Comparator
                .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .thenComparing(stack -> stack.getComponents().toString()));
        return List.copyOf(sorted);
    }

    private static boolean advanceCursor(final int[] cursor, final List<VariantAxis> axes) {
        for (int i = cursor.length - 1; i >= 0; i--) {
            final int next = cursor[i] + 1;
            if (next < axes.get(i).options().size()) {
                cursor[i] = next;
                for (int j = i + 1; j < cursor.length; j++) {
                    cursor[j] = 0;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isIndexedRecipeHolder(final RecipeHolder<Recipe<?>> recipeHolder) {
        return recipeHolder.value() instanceof CookingPotIndexedRecipe;
    }

    private static String buildDisplayKey(final Recipe<?> recipe,
                                          final ItemStack result,
                                          final List<ItemStack> lockedInputs) {
        final StringBuilder key = new StringBuilder(128);
        key.append(BuiltInRegistries.ITEM.getKey(result.getItem()))
                .append('#')
                .append(result.getCount());

        final int ingredientStart = recipe instanceof CookingPotIndexedRecipe indexedRecipe
                ? Math.max(0, indexedRecipe.syntheticIngredientCount())
                : 0;
        final List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = ingredientStart; i < ingredients.size(); i++) {
            key.append('|').append(ingredients.get(i).getStackingIds());
            if (lockedInputs != null && i < lockedInputs.size()) {
                final ItemStack locked = lockedInputs.get(i);
                if (!locked.isEmpty()) {
                    key.append('@')
                            .append(BuiltInRegistries.ITEM.getKey(locked.getItem()))
                            .append('#')
                            .append(locked.getComponents());
                }
            }
        }

        return key.toString();
    }
}
