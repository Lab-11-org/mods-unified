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

@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu")
abstract class KitchenMenuRecipeStatusMixin {

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
        if (!containsIndexedRecipe(recipesForResult)) {
            return;
        }

        final Map<String, RecipeWithStatus> deduped = new LinkedHashMap<>();
        final CraftingContext context = new CraftingContext(kitchen, player);

        for (final RecipeHolder<Recipe<?>> recipeHolder : recipesForResult) {
            final ItemStack recipeResult = recipeHolder.value().getResultItem(player.level().registryAccess());
            final NonNullList<ItemStack> baseLocks = copyLocks(lockedInputs);
            sanitizeLocksForRecipe(baseLocks, recipeHolder.value());

            appendVariant(deduped, context, recipeHolder, recipeResult, baseLocks);
            appendGeneratedTagVariants(deduped, context, recipeHolder, recipeResult, baseLocks);
        }

        final List<RecipeWithStatus> result = new ArrayList<>(deduped.values());
        result.sort((left, right) -> {
            final boolean leftIndexed = isIndexedRecipe(left);
            final boolean rightIndexed = isIndexedRecipe(right);
            if (leftIndexed != rightIndexed) {
                return leftIndexed ? -1 : 1;
            }

            if (left.canCraft() && !right.canCraft()) {
                return -1;
            }
            if (!left.canCraft() && right.canCraft()) {
                return 1;
            }

            final int missingCompare = Integer.compare(left.missingIngredients().size(), right.missingIngredients().size());
            if (missingCompare != 0) {
                return missingCompare;
            }

            return currentSorting.compare(left, right);
        });

        recipesForSelection = result;
        Balm.getNetworking().sendTo(player, new SelectionRecipesListMessage(result));
        ci.cancel();
    }

    private static boolean isIndexedRecipe(final RecipeWithStatus status) {
        final var recipeId = status.recipeId();
        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    private static boolean containsIndexedRecipe(final Collection<RecipeHolder<Recipe<?>>> recipesForResult) {
        for (final RecipeHolder<Recipe<?>> recipeHolder : recipesForResult) {
            if (recipeHolder.value() instanceof CookingPotIndexedRecipe) {
                return true;
            }
        }
        return false;
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

    private static void sanitizeLocksForRecipe(final NonNullList<ItemStack> operationLocks,
                                               final Recipe<?> recipe) {
        if (operationLocks == null || operationLocks.isEmpty()) {
            return;
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
    }

    private static void appendVariant(final Map<String, RecipeWithStatus> deduped,
                                      final CraftingContext context,
                                      final RecipeHolder<Recipe<?>> recipeHolder,
                                      final ItemStack recipeResult,
                                      final NonNullList<ItemStack> locks) {
        final NonNullList<ItemStack> attemptLocks = copyLocks(locks);
        sanitizeLocksForRecipe(attemptLocks, recipeHolder.value());
        final var operation = context.createOperation(recipeHolder).withLockedInputs(attemptLocks).prepare();

        final NonNullList<ItemStack> displayLocks = copyLocks(operation.getLockedInputs());
        final RecipeWithStatus candidate = new RecipeWithStatus(
                recipeHolder.id(),
                recipeResult,
                operation.getMissingIngredients(),
                operation.getMissingIngredientsMask(),
                displayLocks
        );

        final String displayKey = buildDisplayKey(recipeHolder.value(), recipeResult, displayLocks);
        deduped.putIfAbsent(displayKey, candidate);
    }

    private static void appendGeneratedTagVariants(final Map<String, RecipeWithStatus> deduped,
                                                   final CraftingContext context,
                                                   final RecipeHolder<Recipe<?>> recipeHolder,
                                                   final ItemStack recipeResult,
                                                   final NonNullList<ItemStack> baseLocks) {
        final Recipe<?> recipe = recipeHolder.value();
        final List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return;
        }

        final int ingredientStart = recipe instanceof CookingPotIndexedRecipe indexedRecipe
                ? Math.max(0, indexedRecipe.syntheticIngredientCount())
                : 0;

        final List<Integer> variantIndices = new ArrayList<>();
        final List<List<ItemStack>> variantOptions = new ArrayList<>();

        for (int i = ingredientStart; i < ingredients.size() && variantIndices.size() < Integer.SIZE; i++) {
            if (i >= baseLocks.size()) {
                break;
            }

            if (!baseLocks.get(i).isEmpty()) {
                continue;
            }

            final List<ItemStack> options = collectIngredientOptions(ingredients.get(i));
            if (options.size() <= 1) {
                continue;
            }

            variantIndices.add(i);
            variantOptions.add(options);
        }

        if (variantIndices.isEmpty()) {
            return;
        }

        final int[] cursor = new int[variantIndices.size()];
        while (true) {
            final NonNullList<ItemStack> candidateLocks = copyLocks(baseLocks);
            boolean changed = false;

            for (int i = 0; i < variantIndices.size(); i++) {
                final int ingredientIndex = variantIndices.get(i);
                final ItemStack selected = variantOptions.get(i).get(cursor[i]);
                candidateLocks.set(ingredientIndex, selected);
                changed |= cursor[i] != 0;
            }

            if (changed) {
                appendVariant(deduped, context, recipeHolder, recipeResult, candidateLocks);
            }

            if (!advanceCursor(cursor, variantOptions)) {
                break;
            }
        }
    }

    private static List<ItemStack> collectIngredientOptions(final Ingredient ingredient) {
        final ItemStack[] rawOptions = ingredient.getItems();
        if (rawOptions.length == 0) {
            return List.of();
        }

        final List<ItemStack> options = new ArrayList<>(rawOptions.length);
        for (final ItemStack raw : rawOptions) {
            if (raw.isEmpty()) {
                continue;
            }

            final ItemStack option = raw.copyWithCount(1);
            boolean duplicate = false;
            for (final ItemStack existing : options) {
                if (ItemStack.isSameItemSameComponents(existing, option)) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate) {
                options.add(option);
            }
        }

        return options;
    }

    private static boolean advanceCursor(final int[] cursor, final List<List<ItemStack>> options) {
        for (int i = cursor.length - 1; i >= 0; i--) {
            final int next = cursor[i] + 1;
            if (next < options.get(i).size()) {
                cursor[i] = next;
                for (int j = i + 1; j < cursor.length; j++) {
                    cursor[j] = 0;
                }
                return true;
            }
        }
        return false;
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
                    key.append('@').append(BuiltInRegistries.ITEM.getKey(locked.getItem()));
                }
            }
        }

        return key.toString();
    }
}
