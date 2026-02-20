package org.lab_11.modsunified.mixin.cookingforblockheads;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.cookingforblockheads.CookingPotIndexedRecipe;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private record VariantCandidate(String displayKey, Object status) {
    }

    private record VariantAxis(int ingredientIndex, List<ItemStack> options) {
    }

    private record StackIdentity(Object item, Object components) {
    }

    @Unique
    private static final String CRAFTING_CONTEXT_CLASS = "net.blay09.mods.cookingforblockheads.crafting.CraftingContext";
    @Unique
    private static final String RECIPE_WITH_STATUS_CLASS = "net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus";
    @Unique
    private static final String SELECTION_RECIPES_LIST_MESSAGE_CLASS =
            "net.blay09.mods.cookingforblockheads.network.message.SelectionRecipesListMessage";
    @Unique
    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";

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

        final Player player = player();
        if (player == null) {
            return;
        }

        final Object context = newCraftingContext(kitchen(), player);
        if (context == null) {
            return;
        }

        final Map<String, Object> deduped = recipesForResult.stream()
                .flatMap(recipeHolder -> buildVariantCandidates(context, recipeHolder))
                .collect(Collectors.toMap(
                        VariantCandidate::displayKey,
                        VariantCandidate::status,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));

        final List<Object> result = new ArrayList<>(deduped.values());
        result.sort(selectionComparator());

        setRecipesForSelection(result);
        sendSelectionRecipes(player, result);
        ci.cancel();
    }

    @Unique
    private Comparator<Object> selectionComparator() {
        return (left, right) -> {
            final boolean leftIndexed = isIndexedRecipe(left);
            final boolean rightIndexed = isIndexedRecipe(right);
            if (leftIndexed != rightIndexed) {
                return leftIndexed ? -1 : 1;
            }

            if (canCraft(left) != canCraft(right)) {
                return canCraft(left) ? -1 : 1;
            }

            final int missingCompare = Integer.compare(missingIngredients(left).size(), missingIngredients(right).size());
            if (missingCompare != 0) {
                return missingCompare;
            }

            final Comparator<Object> currentSorting = currentSorting();
            return currentSorting != null ? currentSorting.compare(left, right) : 0;
        };
    }

    @Unique
    private Stream<VariantCandidate> buildVariantCandidates(final Object context,
                                                            final RecipeHolder<Recipe<?>> recipeHolder) {
        final Player player = player();
        if (player == null) {
            return Stream.empty();
        }

        final Recipe<?> recipe = recipeHolder.value();
        final ItemStack recipeResult = recipe.getResultItem(player.level().registryAccess());
        final NonNullList<ItemStack> baseLocks = recipe instanceof CookingPotIndexedRecipe
                ? emptyLocksForRecipe(recipe)
                : prepareLocksForRecipe(lockedInputs(), recipe);

        return Stream.concat(
                        Stream.of(baseLocks),
                        expandTagVariantLocks(recipe, baseLocks)
                )
                .map(variantLocks -> buildVariantCandidate(context, recipeHolder, recipeResult, variantLocks));
    }

    @Unique
    private static VariantCandidate buildVariantCandidate(final Object context,
                                                          final RecipeHolder<Recipe<?>> recipeHolder,
                                                          final ItemStack recipeResult,
                                                          final List<ItemStack> locks) {
        final Recipe<?> recipe = recipeHolder.value();
        final NonNullList<ItemStack> operationLocks = prepareLocksForRecipe(locks, recipe);
        final Object operationWithLocks = invoke(
                invoke(context, "createOperation", new Class<?>[]{RecipeHolder.class}, recipeHolder),
                "withLockedInputs",
                new Class<?>[]{NonNullList.class},
                operationLocks
        );
        final Object operation = invokeNoArg(operationWithLocks, "prepare");

        final NonNullList<ItemStack> displayLocks = copyLocks(lockedInputs(operation));
        final Object status = newRecipeWithStatus(
                recipeHolder.id(),
                recipeResult,
                missingIngredients(operation),
                missingIngredientsMask(operation),
                displayLocks
        );
        return new VariantCandidate(buildDisplayKey(recipe, recipeResult, displayLocks), status);
    }

    @Unique
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

    @Unique
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

    @Unique
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

    @Unique
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

    @Unique
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

    @Unique
    private static NonNullList<ItemStack> emptyLocksForRecipe(final Recipe<?> recipe) {
        return NonNullList.withSize(recipe.getIngredients().size(), ItemStack.EMPTY);
    }

    @Unique
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
            options.putIfAbsent(new StackIdentity(option.getItem(), MinecraftApiCompat.stackDataKey(option)), option);
        }

        final List<ItemStack> sorted = new ArrayList<>(options.values());
        sorted.sort(Comparator
                .comparing((ItemStack stack) -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .thenComparing(MinecraftApiCompat::stackDataKey));
        return List.copyOf(sorted);
    }

    @Unique
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

    @Unique
    private static boolean isIndexedRecipeHolder(final RecipeHolder<Recipe<?>> recipeHolder) {
        return recipeHolder.value() instanceof CookingPotIndexedRecipe;
    }

    @Unique
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
                            .append(MinecraftApiCompat.stackDataKey(locked));
                }
            }
        }

        return key.toString();
    }

    @Unique
    private static boolean isIndexedRecipe(final Object status) {
        final ResourceLocation recipeId = recipeId(status);
        return recipeId != null
                && BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    @Unique
    private Player player() {
        final Object value = readFieldValue(this, "player");
        return value instanceof Player player ? player : null;
    }

    @Unique
    private Object kitchen() {
        return readFieldValue(this, "kitchen");
    }

    @Unique
    private NonNullList<ItemStack> lockedInputs() {
        final Object value = readFieldValue(this, "lockedInputs");
        if (value instanceof NonNullList<?> list) {
            @SuppressWarnings("unchecked")
            final NonNullList<ItemStack> cast = (NonNullList<ItemStack>) list;
            return cast;
        }
        return NonNullList.create();
    }

    @Unique
    private Comparator<Object> currentSorting() {
        final Object value = readFieldValue(this, "currentSorting");
        if (value instanceof Comparator<?> comparator) {
            @SuppressWarnings("unchecked")
            final Comparator<Object> cast = (Comparator<Object>) comparator;
            return cast;
        }
        return null;
    }

    @Unique
    private Collection<RecipeHolder<Recipe<?>>> getRecipesFor(final ItemStack resultItem) {
        final Object value = invokeDeclared(this, "getRecipesFor", new Class<?>[]{ItemStack.class}, resultItem);
        if (value instanceof Collection<?> collection) {
            @SuppressWarnings("unchecked")
            final Collection<RecipeHolder<Recipe<?>>> cast = (Collection<RecipeHolder<Recipe<?>>>) collection;
            return cast;
        }
        return List.of();
    }

    @Unique
    private void setRecipesForSelection(final List<Object> statuses) {
        writeFieldValue(this, "recipesForSelection", statuses);
    }

    @Unique
    private static boolean canCraft(final Object status) {
        final Object value = invokeNoArg(status, "canCraft");
        return value instanceof Boolean b && b;
    }

    @Unique
    private static List<Ingredient> missingIngredients(final Object statusOrOperation) {
        Object value = invokeNoArg(statusOrOperation, "missingIngredients");
        if (!(value instanceof List<?>)) {
            value = invokeNoArg(statusOrOperation, "getMissingIngredients");
        }
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            final List<Ingredient> cast = (List<Ingredient>) list;
            return cast;
        }
        return List.of();
    }

    @Unique
    private static int missingIngredientsMask(final Object statusOrOperation) {
        Object value = invokeNoArg(statusOrOperation, "missingIngredientsMask");
        if (!(value instanceof Integer)) {
            value = invokeNoArg(statusOrOperation, "getMissingIngredientsMask");
        }
        return value instanceof Integer mask ? mask : 0;
    }

    @Unique
    private static NonNullList<ItemStack> lockedInputs(final Object statusOrOperation) {
        Object value = invokeNoArg(statusOrOperation, "lockedInputs");
        if (!(value instanceof NonNullList<?>)) {
            value = invokeNoArg(statusOrOperation, "getLockedInputs");
        }
        if (value instanceof NonNullList<?> list) {
            @SuppressWarnings("unchecked")
            final NonNullList<ItemStack> cast = (NonNullList<ItemStack>) list;
            return cast;
        }
        return NonNullList.create();
    }

    @Unique
    private static ResourceLocation recipeId(final Object status) {
        final Object value = invokeNoArg(status, "recipeId");
        return value instanceof ResourceLocation id ? id : null;
    }

    @Unique
    private static Object newCraftingContext(final Object kitchen, final Player player) {
        if (kitchen == null || player == null) {
            return null;
        }
        try {
            final Class<?> contextClass = Class.forName(CRAFTING_CONTEXT_CLASS);
            for (final Constructor<?> constructor : contextClass.getConstructors()) {
                if (constructor.getParameterCount() == 2) {
                    return constructor.newInstance(kitchen, player);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return null;
    }

    @Unique
    private static Object newRecipeWithStatus(final ResourceLocation recipeId,
                                              final ItemStack resultItem,
                                              final List<Ingredient> missingIngredients,
                                              final int missingIngredientsMask,
                                              final NonNullList<ItemStack> lockedInputs) {
        try {
            final Class<?> statusClass = Class.forName(RECIPE_WITH_STATUS_CLASS);
            final Constructor<?> constructor = statusClass.getConstructor(
                    ResourceLocation.class,
                    ItemStack.class,
                    List.class,
                    int.class,
                    NonNullList.class
            );
            return constructor.newInstance(
                    recipeId,
                    resultItem,
                    missingIngredients,
                    missingIngredientsMask,
                    lockedInputs
            );
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void sendSelectionRecipes(final Player player, final List<Object> statuses) {
        if (player == null) {
            return;
        }

        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Object networking = balmClass.getMethod("getNetworking").invoke(null);

            final Class<?> messageClass = Class.forName(SELECTION_RECIPES_LIST_MESSAGE_CLASS);
            final Object message = messageClass.getConstructor(List.class).newInstance(statuses);

            Method sendToMethod = null;
            for (final Method method : networking.getClass().getMethods()) {
                if ("sendTo".equals(method.getName()) && method.getParameterCount() == 2) {
                    final Class<?>[] parameterTypes = method.getParameterTypes();
                    if (parameterTypes[0].isAssignableFrom(Player.class) || Player.class.isAssignableFrom(parameterTypes[0])) {
                        sendToMethod = method;
                        break;
                    }
                }
            }
            if (sendToMethod != null) {
                sendToMethod.invoke(networking, player, message);
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    @Unique
    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object invoke(final Object target, final String methodName, final Class<?>[] parameterTypes, final Object... args) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static Object invokeDeclared(final Object target,
                                         final String methodName,
                                         final Class<?>[] parameterTypes,
                                         final Object... args) {
        if (target == null) {
            return null;
        }

        Class<?> current = target.getClass();
        while (current != null) {
            try {
                final Method method = current.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    @Unique
    private static Object readFieldValue(final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }

        final Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return null;
        }

        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Unique
    private static void writeFieldValue(final Object target, final String fieldName, final Object value) {
        if (target == null) {
            return;
        }

        final Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return;
        }

        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    @Unique
    private static Field findField(final Class<?> ownerClass, final String fieldName) {
        Class<?> current = ownerClass;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
