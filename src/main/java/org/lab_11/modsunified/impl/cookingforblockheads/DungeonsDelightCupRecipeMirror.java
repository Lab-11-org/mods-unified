package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DungeonsDelightCupRecipeMirror {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MONSTER_POT_RECIPE_CLASS =
            "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotRecipe";
    private static final String FD_COOKING_POT_RECIPE_CLASS =
            "vectorwing.farmersdelight.common.crafting.CookingPotRecipe";
    private static final String FD_COOKING_POT_RECIPE_TAB_CLASS =
            "vectorwing.farmersdelight.client.recipebook.CookingPotRecipeBookTab";

    private static final String RECIPE_METHOD_GET_OUTPUT_CONTAINER = "getOutputContainer";
    private static final String RECIPE_METHOD_GET_EXPERIENCE = "getExperience";
    private static final String RECIPE_METHOD_GET_COOK_TIME = "getCookTime";
    private static final String RECIPE_METHOD_GET_RECIPE_BOOK_TAB = "getRecipeBookTab";
    private static final String TAB_FIND_BY_NAME_METHOD = "findByName";

    private static final ResourceLocation COPPER_CUP_ID =
            ResourceLocation.fromNamespaceAndPath(BridgeKeys.MOD_MINERS_DELIGHT, "copper_cup");

    private static volatile Class<?> cachedMonsterPotRecipeClass;
    private static volatile Constructor<?> cachedFdRecipeConstructor;
    private static volatile Method cachedFdRecipeTabFindByNameMethod;
    private static volatile boolean reflectionLookupFailed;

    private DungeonsDelightCupRecipeMirror() {
    }

    public static boolean shouldRouteToCopperPotOnly(final RecipeHolder<?> recipeHolder) {
        if (!isMirrorSupported()) {
            return false;
        }

        final Class<?> monsterRecipeClass = resolveMonsterRecipeClass();
        if (monsterRecipeClass == null) {
            return false;
        }

        final Recipe<?> recipe = recipeHolder.value();
        if (!monsterRecipeClass.isInstance(recipe)) {
            return false;
        }

        final ItemStack outputContainer = invokeItemStackGetter(recipe, RECIPE_METHOD_GET_OUTPUT_CONTAINER);
        return isCopperCupContainer(outputContainer);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void injectMirroredRecipes(final RecipeManager recipeManager,
                                             final RegistryAccess registryAccess,
                                             final String source) {
        if (!isMirrorSupported()) {
            return;
        }

        final Class<?> monsterRecipeClass = resolveMonsterRecipeClass();
        final Constructor<?> fdRecipeConstructor = resolveFdRecipeConstructor();
        final Method fdRecipeTabFindByName = resolveFdRecipeTabFindByNameMethod();
        if (monsterRecipeClass == null || fdRecipeConstructor == null || fdRecipeTabFindByName == null) {
            return;
        }

        final Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();
        final List<RecipeHolder<?>> mergedRecipes = new ArrayList<>(allRecipes.size() + 32);
        final Set<ResourceLocation> recipeIds = new HashSet<>(allRecipes.size() * 2);

        int removed = 0;
        for (final RecipeHolder<?> recipeHolder : allRecipes) {
            if (isMirroredRecipeId(recipeHolder.id()) || isIndexedRecipeId(recipeHolder.id())) {
                removed++;
                continue;
            }

            mergedRecipes.add(recipeHolder);
            recipeIds.add(recipeHolder.id());
        }

        int added = 0;
        for (final RecipeHolder<?> recipeHolder : allRecipes) {
            if (isMirroredRecipeId(recipeHolder.id()) || isIndexedRecipeId(recipeHolder.id())) {
                continue;
            }

            final Recipe<?> sourceRecipe = recipeHolder.value();
            if (!monsterRecipeClass.isInstance(sourceRecipe)) {
                continue;
            }

            if (!shouldRouteToCopperPotOnly(recipeHolder)) {
                continue;
            }

            final ResourceLocation mirroredRecipeId = mirroredRecipeId(recipeHolder.id());
            if (!recipeIds.add(mirroredRecipeId)) {
                continue;
            }

            final RecipeHolder<?> mirroredRecipeHolder = createMirroredFdRecipe(
                    sourceRecipe,
                    mirroredRecipeId,
                    registryAccess,
                    fdRecipeConstructor,
                    fdRecipeTabFindByName
            );
            if (mirroredRecipeHolder == null) {
                continue;
            }

            mergedRecipes.add(mirroredRecipeHolder);
            added++;
        }

        if (added == 0 && removed == 0) {
            return;
        }

        recipeManager.replaceRecipes((Iterable) mergedRecipes);
        LOGGER.info("Mirrored {} DungeonsDelight copper-cup monster recipes into FarmersDelight cooking recipes (removed {}) via {}.",
                added, removed, source);
    }

    private static RecipeHolder<?> createMirroredFdRecipe(final Recipe<?> sourceRecipe,
                                                          final ResourceLocation mirroredRecipeId,
                                                          final RegistryAccess registryAccess,
                                                          final Constructor<?> fdRecipeConstructor,
                                                          final Method fdRecipeTabFindByName) {
        final ItemStack output = sourceRecipe.getResultItem(registryAccess).copy();
        if (output.isEmpty()) {
            return null;
        }

        final ItemStack outputContainer = invokeItemStackGetter(sourceRecipe, RECIPE_METHOD_GET_OUTPUT_CONTAINER);
        if (!isCopperCupContainer(outputContainer)) {
            return null;
        }

        final String recipeBookTabName = resolveFdRecipeBookTabName(invokeNoArg(sourceRecipe, RECIPE_METHOD_GET_RECIPE_BOOK_TAB));
        final Object fdRecipeBookTab = invokeStatic(fdRecipeTabFindByName, recipeBookTabName);
        if (fdRecipeBookTab == null) {
            return null;
        }

        final NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.addAll(sourceRecipe.getIngredients());

        final String group = sourceRecipe.getGroup();
        final float experience = invokeFloatGetter(sourceRecipe, RECIPE_METHOD_GET_EXPERIENCE, 0.0F);
        final int cookTime = invokeIntGetter(sourceRecipe, RECIPE_METHOD_GET_COOK_TIME, 200);

        try {
            final Object fdRecipe = fdRecipeConstructor.newInstance(
                    group,
                    fdRecipeBookTab,
                    ingredients,
                    output,
                    outputContainer.copy(),
                    experience,
                    cookTime
            );
            if (!(fdRecipe instanceof Recipe<?> recipe)) {
                return null;
            }
            return new RecipeHolder<>(mirroredRecipeId, recipe);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String resolveFdRecipeBookTabName(final Object sourceTab) {
        if (sourceTab == null) {
            return "misc";
        }

        final String sourceName = sourceTab.toString().toLowerCase();
        if (sourceName.contains("drink")) {
            return "drinks";
        }
        if (sourceName.contains("meal")) {
            return "meals";
        }
        return "misc";
    }

    private static boolean isCopperCupContainer(final ItemStack container) {
        if (container.isEmpty()) {
            return false;
        }
        return COPPER_CUP_ID.equals(BuiltInRegistries.ITEM.getKey(container.getItem()));
    }

    private static ResourceLocation mirroredRecipeId(final ResourceLocation originalRecipeId) {
        final String mirroredPath = BridgeKeys.MIRRORED_DD_CUP_RECIPE_PATH_PREFIX
                + originalRecipeId.getNamespace() + "/"
                + originalRecipeId.getPath();
        return ResourceLocation.fromNamespaceAndPath(BridgeKeys.MOD_LAB11_UNIFIED, mirroredPath);
    }

    private static boolean isMirroredRecipeId(final ResourceLocation recipeId) {
        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.MIRRORED_DD_CUP_RECIPE_PATH_PREFIX);
    }

    private static boolean isIndexedRecipeId(final ResourceLocation recipeId) {
        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    private static boolean isMirrorSupported() {
        return ModList.get().isLoaded(BridgeKeys.MOD_DUNGEONS_DELIGHT)
                && ModList.get().isLoaded(BridgeKeys.MOD_MINERS_DELIGHT)
                && ModList.get().isLoaded(BridgeKeys.MOD_FARMERS_DELIGHT);
    }

    private static Class<?> resolveMonsterRecipeClass() {
        final Class<?> cached = cachedMonsterPotRecipeClass;
        if (cached != null) {
            return cached;
        }
        if (reflectionLookupFailed) {
            return null;
        }

        synchronized (DungeonsDelightCupRecipeMirror.class) {
            if (cachedMonsterPotRecipeClass != null) {
                return cachedMonsterPotRecipeClass;
            }
            if (reflectionLookupFailed) {
                return null;
            }

            try {
                cachedMonsterPotRecipeClass = Class.forName(MONSTER_POT_RECIPE_CLASS);
                return cachedMonsterPotRecipeClass;
            } catch (ClassNotFoundException ignored) {
                reflectionLookupFailed = true;
                return null;
            }
        }
    }

    private static Constructor<?> resolveFdRecipeConstructor() {
        final Constructor<?> cached = cachedFdRecipeConstructor;
        if (cached != null) {
            return cached;
        }
        if (reflectionLookupFailed) {
            return null;
        }

        synchronized (DungeonsDelightCupRecipeMirror.class) {
            if (cachedFdRecipeConstructor != null) {
                return cachedFdRecipeConstructor;
            }
            if (reflectionLookupFailed) {
                return null;
            }

            try {
                final Class<?> recipeClass = Class.forName(FD_COOKING_POT_RECIPE_CLASS);
                final Class<?> recipeTabClass = Class.forName(FD_COOKING_POT_RECIPE_TAB_CLASS);
                cachedFdRecipeConstructor = recipeClass.getConstructor(
                        String.class,
                        recipeTabClass,
                        NonNullList.class,
                        ItemStack.class,
                        ItemStack.class,
                        float.class,
                        int.class
                );
                return cachedFdRecipeConstructor;
            } catch (ReflectiveOperationException ignored) {
                reflectionLookupFailed = true;
                return null;
            }
        }
    }

    private static Method resolveFdRecipeTabFindByNameMethod() {
        final Method cached = cachedFdRecipeTabFindByNameMethod;
        if (cached != null) {
            return cached;
        }
        if (reflectionLookupFailed) {
            return null;
        }

        synchronized (DungeonsDelightCupRecipeMirror.class) {
            if (cachedFdRecipeTabFindByNameMethod != null) {
                return cachedFdRecipeTabFindByNameMethod;
            }
            if (reflectionLookupFailed) {
                return null;
            }

            try {
                final Class<?> recipeTabClass = Class.forName(FD_COOKING_POT_RECIPE_TAB_CLASS);
                cachedFdRecipeTabFindByNameMethod = recipeTabClass.getMethod(TAB_FIND_BY_NAME_METHOD, String.class);
                return cachedFdRecipeTabFindByNameMethod;
            } catch (ReflectiveOperationException ignored) {
                reflectionLookupFailed = true;
                return null;
            }
        }
    }

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

    private static Object invokeStatic(final Method method, final Object argument) {
        try {
            return method.invoke(null, argument);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static ItemStack invokeItemStackGetter(final Object target, final String methodName) {
        final Object value = invokeNoArg(target, methodName);
        return value instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
    }

    private static float invokeFloatGetter(final Object target, final String methodName, final float fallback) {
        final Object value = invokeNoArg(target, methodName);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private static int invokeIntGetter(final Object target, final String methodName, final int fallback) {
        final Object value = invokeNoArg(target, methodName);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }
}
