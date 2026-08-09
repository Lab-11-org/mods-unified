package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;
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

    private static final String[] MONSTER_POT_RECIPE_CLASS_CANDIDATES = {
            "net.yirmiri.dungeonsdelight.common.block.monster_pot.MonsterPotRecipe",
            "net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotRecipe"
    };
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
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_MINERS_DELIGHT, "copper_cup");

    private static volatile Class<?> cachedMonsterPotRecipeClass;
    private static volatile Constructor<?> cachedFdRecipeConstructor;
    private static volatile Method cachedFdRecipeTabFindByNameMethod;
    private static volatile boolean reflectionLookupFailed;

    private DungeonsDelightCupRecipeMirror() {
    }

    public static boolean shouldRouteToCopperPotOnly(final Object recipeEntry) {
        if (!isMirrorSupported()) {
            return false;
        }

        final Class<?> monsterRecipeClass = resolveMonsterRecipeClass();
        if (monsterRecipeClass == null) {
            return false;
        }

        final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(recipeEntry);
        if (recipe == null) {
            return false;
        }
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

        final List<Object> allRecipes = RecipeRuntimeCompat.getAllRecipes(recipeManager);
        final List<Object> mergedRecipes = new ArrayList<>(allRecipes.size() + 32);
        final Set<ResourceLocation> recipeIds = new HashSet<>(allRecipes.size() * 2);

        int removed = 0;
        for (final Object recipeEntry : allRecipes) {
            final ResourceLocation entryId = RecipeRuntimeCompat.recipeId(recipeEntry);
            if (entryId == null || isMirroredRecipeId(entryId) || isIndexedRecipeId(entryId)) {
                removed++;
                continue;
            }

            mergedRecipes.add(recipeEntry);
            recipeIds.add(entryId);
        }

        int added = 0;
        for (final Object recipeEntry : allRecipes) {
            final ResourceLocation entryId = RecipeRuntimeCompat.recipeId(recipeEntry);
            if (entryId == null || isMirroredRecipeId(entryId) || isIndexedRecipeId(entryId)) {
                continue;
            }

            final Recipe<?> sourceRecipe = RecipeRuntimeCompat.recipeValue(recipeEntry);
            if (sourceRecipe == null) {
                continue;
            }
            if (!monsterRecipeClass.isInstance(sourceRecipe)) {
                continue;
            }

            if (!shouldRouteToCopperPotOnly(recipeEntry)) {
                continue;
            }

            final ResourceLocation mirroredRecipeId = mirroredRecipeId(entryId);
            if (!recipeIds.add(mirroredRecipeId)) {
                continue;
            }

            final Object mirroredRecipeEntry = createMirroredFdRecipe(
                    sourceRecipe,
                    mirroredRecipeId,
                    registryAccess,
                    fdRecipeConstructor,
                    fdRecipeTabFindByName
            );
            if (mirroredRecipeEntry == null) {
                continue;
            }

            mergedRecipes.add(mirroredRecipeEntry);
            added++;
        }

        if (added == 0 && removed == 0) {
            return;
        }

        RecipeRuntimeCompat.replaceRecipes(recipeManager, mergedRecipes);
        LOGGER.info("Mirrored {} DungeonsDelight copper-cup monster recipes into FarmersDelight cooking recipes (removed {}) via {}.",
                added, removed, source);
    }

    private static Object createMirroredFdRecipe(final Recipe<?> sourceRecipe,
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
            return RecipeRuntimeCompat.recipeEntry(mirroredRecipeId, recipe);
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
        return MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_LAB11_UNIFIED, mirroredPath);
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
        return LoaderApiCompat.isModLoaded(BridgeKeys.MOD_DUNGEONS_DELIGHT)
                && LoaderApiCompat.isModLoaded(BridgeKeys.MOD_MINERS_DELIGHT)
                && LoaderApiCompat.isModLoaded(BridgeKeys.MOD_FARMERS_DELIGHT);
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

            for (final String candidateClassName : MONSTER_POT_RECIPE_CLASS_CANDIDATES) {
                try {
                    cachedMonsterPotRecipeClass = Class.forName(candidateClassName);
                    return cachedMonsterPotRecipeClass;
                } catch (ClassNotFoundException ignored) {
                    // Try the next known DD class layout.
                }
            }
            reflectionLookupFailed = true;
            return null;
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
