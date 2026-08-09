package org.lab_11.modsunified.impl.cookingforblockheads;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LegacyCookingRegistryBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LEGACY_COOKING_REGISTRY_CLASS =
            "net.blay09.mods.cookingforblockheads.registry.CookingRegistry";
    private static final String LEGACY_GENERAL_FOOD_RECIPE_CLASS =
            "net.blay09.mods.cookingforblockheads.registry.recipe.GeneralFoodRecipe";
    private static final Set<Object> INJECTED_FOOD_RECIPES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private LegacyCookingRegistryBridge() {
    }

    public static synchronized int injectIndexedRecipes(final Map<ResourceLocation, Object> indexedRecipesById,
                                                        final RegistryAccess registryAccess) {
        final Multimap<ResourceLocation, Object> foodRecipes = resolveLegacyFoodRecipes();
        if (foodRecipes == null) {
            INJECTED_FOOD_RECIPES.clear();
            return 0;
        }

        removePreviouslyInjectedRecipes(foodRecipes);
        if (indexedRecipesById.isEmpty()) {
            return 0;
        }

        final List<Map.Entry<ResourceLocation, Object>> orderedEntries = new ArrayList<>(indexedRecipesById.entrySet());
        orderedEntries.sort(Comparator.comparing(entry -> entry.getKey().toString()));

        int injected = 0;
        for (final Map.Entry<ResourceLocation, Object> entry : orderedEntries) {
            final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(entry.getValue());
            if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
                continue;
            }

            final ItemStack output = indexedRecipe.getResultItem(registryAccess);
            if (output.isEmpty()) {
                continue;
            }

            final Object legacyFoodRecipe = createLegacyFoodRecipe(entry.getKey(), indexedRecipe, output);
            if (legacyFoodRecipe == null) {
                continue;
            }

            final ResourceLocation outputItemId = BuiltInRegistries.ITEM.getKey(output.getItem());
            if (outputItemId == null) {
                continue;
            }

            foodRecipes.put(outputItemId, legacyFoodRecipe);
            INJECTED_FOOD_RECIPES.add(legacyFoodRecipe);
            injected++;
        }

        return injected;
    }

    public static synchronized int countInjectedRecipes() {
        if (INJECTED_FOOD_RECIPES.isEmpty()) {
            return 0;
        }

        final Multimap<ResourceLocation, Object> foodRecipes = resolveLegacyFoodRecipes();
        if (foodRecipes == null) {
            return 0;
        }

        int count = 0;
        for (final Object recipe : foodRecipes.values()) {
            if (INJECTED_FOOD_RECIPES.contains(recipe)) {
                count++;
            }
        }
        return count;
    }

    private static void removePreviouslyInjectedRecipes(final Multimap<ResourceLocation, Object> foodRecipes) {
        if (INJECTED_FOOD_RECIPES.isEmpty()) {
            return;
        }

        final Collection<Object> values = foodRecipes.values();
        values.removeIf(INJECTED_FOOD_RECIPES::contains);
        INJECTED_FOOD_RECIPES.clear();
    }

    private static Object createLegacyFoodRecipe(final ResourceLocation indexedRecipeId,
                                                 final CookingPotIndexedRecipe indexedRecipe,
                                                 final ItemStack output) {
        try {
            final Class<?> foodRecipeClass = Class.forName(LEGACY_GENERAL_FOOD_RECIPE_CLASS);
            final Constructor<?> constructor = foodRecipeClass.getConstructor(Recipe.class, ItemStack.class);
            final NonNullList<Ingredient> visibleIngredients = visibleIngredients(indexedRecipe);
            final Recipe<?> recipeProxy = legacyRecipeProxy(indexedRecipeId, visibleIngredients, output);
            return constructor.newInstance(recipeProxy, output.copy());
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to create legacy Cooking for Blockheads food recipe for {}.", indexedRecipeId, e);
            return null;
        }
    }

    private static NonNullList<Ingredient> visibleIngredients(final CookingPotIndexedRecipe indexedRecipe) {
        final List<Ingredient> ingredients = indexedRecipe.getIngredients();
        if (ingredients.isEmpty()) {
            return NonNullList.create();
        }

        final int start = Math.max(0, Math.min(indexedRecipe.syntheticIngredientCount(), ingredients.size()));
        final NonNullList<Ingredient> visible = NonNullList.create();
        for (int i = start; i < ingredients.size(); i++) {
            final Ingredient ingredient = ingredients.get(i);
            visible.add(ingredient == null ? Ingredient.EMPTY : ingredient);
        }
        return visible;
    }

    @SuppressWarnings("unchecked")
    private static Recipe<?> legacyRecipeProxy(final ResourceLocation indexedRecipeId,
                                               final NonNullList<Ingredient> visibleIngredients,
                                               final ItemStack output) {
        final InvocationHandler handler = (proxy, method, args) -> {
            final String name = method.getName();
            if ("getIngredients".equals(name)) {
                return visibleIngredients;
            }
            if ("canCraftInDimensions".equals(name)) {
                return false;
            }
            if ("getResultItem".equals(name) || "assemble".equals(name)) {
                return output.copy();
            }
            if ("matches".equals(name) || "isSpecial".equals(name) || "showNotification".equals(name)) {
                return false;
            }
            if ("getType".equals(name)) {
                return RecipeType.CRAFTING;
            }
            if ("getId".equals(name)) {
                return indexedRecipeId;
            }
            if ("toString".equals(name)) {
                return "LegacyIndexedFoodRecipeProxy[" + indexedRecipeId + "]";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == (args != null && args.length == 1 ? args[0] : null);
            }
            final Object bySignature = handleObfuscatedRecipeMethod(method, args, indexedRecipeId, visibleIngredients, output);
            if (bySignature != UnhandledCall.INSTANCE) {
                return bySignature;
            }
            return defaultReturnValue(method);
        };

        return (Recipe<?>) Proxy.newProxyInstance(
                Recipe.class.getClassLoader(),
                new Class<?>[]{Recipe.class},
                handler
        );
    }

    private static Object handleObfuscatedRecipeMethod(final Method method,
                                                       final Object[] args,
                                                       final ResourceLocation recipeId,
                                                       final NonNullList<Ingredient> visibleIngredients,
                                                       final ItemStack output) {
        final int argCount = args == null ? 0 : args.length;
        final Class<?> returnType = method.getReturnType();

        if (argCount == 0) {
            if (ResourceLocation.class.isAssignableFrom(returnType)) {
                return recipeId;
            }
            if (RecipeType.class.isAssignableFrom(returnType)) {
                return RecipeType.CRAFTING;
            }
            if (NonNullList.class.isAssignableFrom(returnType)) {
                return visibleIngredients;
            }
            if (List.class.isAssignableFrom(returnType)) {
                return visibleIngredients;
            }
        }

        if (argCount == 1 && ItemStack.class.isAssignableFrom(returnType)) {
            return output.copy();
        }

        if (argCount == 2) {
            final Class<?>[] parameterTypes = method.getParameterTypes();
            if (boolean.class.equals(returnType)
                    && int.class.equals(parameterTypes[0])
                    && int.class.equals(parameterTypes[1])) {
                return false;
            }
            if (ItemStack.class.isAssignableFrom(returnType)) {
                return output.copy();
            }
            if (boolean.class.equals(returnType)) {
                return false;
            }
        }

        return UnhandledCall.INSTANCE;
    }

    private enum UnhandledCall {
        INSTANCE
    }

    private static Object defaultReturnValue(final Method method) {
        final Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Multimap<ResourceLocation, Object> resolveLegacyFoodRecipes() {
        try {
            final Class<?> registryClass = Class.forName(LEGACY_COOKING_REGISTRY_CLASS);
            final Method method = registryClass.getMethod("getFoodRecipes");
            final Object value = method.invoke(null);
            if (value instanceof Multimap<?, ?> multimap) {
                return (Multimap<ResourceLocation, Object>) multimap;
            }
        } catch (ReflectiveOperationException ignored) {
            // Legacy registry is unavailable on non-legacy CFBH runtimes.
        }
        return null;
    }
}
