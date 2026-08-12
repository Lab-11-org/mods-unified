package org.lab_11.modsunified.impl.cookingforblockheads;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotRecipeIndexer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INDEXED_RECIPE_NAMESPACE = "lab_11_mods_unified";
    private static final String INDEXED_RECIPE_PATH_PREFIX = "cfbh_indexed/";
    private static final String NON_FOOD_VARIANT_PATH_PREFIX = "cfbh_nonfood_variant/";
    private static final int MAX_EXPANDED_VARIANTS = 48;
    private static final String RECIPE_MANAGER_BY_NAME_FIELD = "byName";
    private static final String CFBH_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.registry.CookingForBlockheadsRegistry";
    private static final Map<ResourceLocation, Recipe<?>> INDEXED_RECIPES_BY_ID = new ConcurrentHashMap<>();

    private CookingPotRecipeIndexer() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void injectRecipes(final RecipeManager recipeManager,
                                     final RegistryAccess registryAccess,
                                     final String source,
                                     final List<CookingPotBridgeTarget> targets) {
        final Set<RecipeType<?>> indexedRecipeTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final CookingPotBridgeTarget target : targets) {
            target.resolveRecipeType().ifPresent(indexedRecipeTypes::add);
        }

        if (indexedRecipeTypes.isEmpty()) {
            INDEXED_RECIPES_BY_ID.clear();
            return;
        }

        final Map<ResourceLocation, Object> indexedRecipesById = new HashMap<>();
        int added = 0;
        final Set<String> addedRecipeKeys = new HashSet<>();
        for (final CookingPotBridgeTarget target : targets) {
            final RecipeType<?> recipeType = target.resolveRecipeType().orElse(null);
            if (recipeType == null) {
                continue;
            }

            final List<Object> recipesForType = RecipeRuntimeCompat.getAllRecipesFor(recipeManager, recipeType);

            for (final Object rawRecipeEntry : recipesForType) {
                final ResourceLocation rawRecipeId = RecipeRuntimeCompat.recipeId(rawRecipeEntry);
                final Recipe<?> rawRecipeValue = RecipeRuntimeCompat.recipeValue(rawRecipeEntry);
                if (rawRecipeId == null || rawRecipeValue == null) {
                    continue;
                }
                if (!target.acceptsRecipe(rawRecipeEntry)) {
                    continue;
                }
                if (BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(target.targetKey())
                        && DungeonsDelightCupRecipeMirror.shouldRouteToCopperPotOnly(rawRecipeEntry)) {
                    continue;
                }

                final String recipeKey = target.targetKey() + "|" + rawRecipeId;
                if (!addedRecipeKeys.add(recipeKey)) {
                    continue;
                }

                final ItemStack rawResult = rawRecipeValue.getResultItem(registryAccess);
                if (rawResult.isEmpty()) {
                    continue;
                }

                final ResourceLocation indexedRecipeId = indexedRecipeId(rawRecipeId, target.targetKey());
                final Object indexedRecipeEntry =
                        CookingPotIndexedRecipe.toIndexedRecipeHolder(
                                rawRecipeEntry,
                                indexedRecipeId,
                                registryAccess,
                                target.targetKey(),
                                target.requiredMarkerKeys()
                        );
                final Recipe<?> indexedRecipe = RecipeRuntimeCompat.recipeValue(indexedRecipeEntry);
                if (indexedRecipe == null) {
                    continue;
                }
                final ItemStack indexedResult = indexedRecipe.getResultItem(registryAccess);
                if (indexedResult.isEmpty()) {
                    continue;
                }
                indexedRecipesById.put(indexedRecipeId, indexedRecipeEntry);
                added++;
            }
        }

        cacheIndexedRecipes(indexedRecipesById);

        final Multimap<ResourceLocation, Object> recipesByItemId = resolveRecipesByItemId();
        int removed = 0;
        if (recipesByItemId != null) {
            final var iterator = recipesByItemId.entries().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<ResourceLocation, Object> entry = iterator.next();
                final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(entry.getValue());
                if (recipe != null && indexedRecipeTypes.contains(recipe.getType())) {
                    iterator.remove();
                    removed++;
                }
            }

            for (final Object indexedRecipeEntry : indexedRecipesById.values()) {
                final Recipe<?> indexedRecipe = RecipeRuntimeCompat.recipeValue(indexedRecipeEntry);
                if (indexedRecipe == null) {
                    continue;
                }
                final ItemStack indexedResult = indexedRecipe.getResultItem(registryAccess);
                if (indexedResult.isEmpty()) {
                    continue;
                }
                final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(indexedResult.getItem());
                recipesByItemId.put(itemId, indexedRecipeEntry);
            }

            if (!installIndexedRecipesByName(recipeManager, indexedRecipesById)) {
                LOGGER.warn("Injected {} indexed recipes into Cooking for Blockheads index via {}, but failed to expose IDs in RecipeManager byName lookup.",
                        added, source);
            } else {
                LOGGER.info("Injected {} cooking-pot recipes into Cooking for Blockheads recipe index (removed {}) and updated RecipeManager byName lookup via {}.",
                        added, removed, source);
            }
            return;
        }

        final boolean refreshedLegacyRegistry = refreshLegacyCookingForBlockheadsRegistry(recipeManager, registryAccess);
        if (!refreshedLegacyRegistry) {
            LOGGER.warn("Indexed {} cooking-pot recipes, but could not refresh Cooking for Blockheads registry via {}.",
                    added, source);
            return;
        }

        final int legacyInjectedRecipes = LegacyCookingRegistryBridge.injectIndexedRecipes(indexedRecipesById, registryAccess);
        final int indexedRecipesInLegacyRegistry = countIndexedRecipesInLegacyRegistry();
        if (indexedRecipesInLegacyRegistry != added) {
            LOGGER.warn(
                    "Indexed recipe visibility is out of sync via {} (expected={}, legacyIndexed={}).",
                    source,
                    added,
                    indexedRecipesInLegacyRegistry
            );
        } else {
            LOGGER.info(
                    "Injected {} cooking-pot recipes into legacy Cooking for Blockheads registry via {} (legacyInjected={}).",
                    added,
                    source,
                    legacyInjectedRecipes
            );
        }
    }

    public static void injectNonFoodCraftingRecipes(final RecipeManager recipeManager,
                                                     final RegistryAccess registryAccess,
                                                     final List<ResourceLocation> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }

        final Set<ResourceLocation> targetItemIds = new HashSet<>(itemIds);

        final Multimap<ResourceLocation, Object> recipesByItemId = resolveRecipesByItemId();
        if (recipesByItemId == null) {
            LOGGER.debug("Skipping non-food crafting recipe injection: modern CFBH recipe index unavailable.");
            return;
        }

        // Remove previously injected non-food crafting entries for target items to ensure idempotency.
        // This covers both original crafting recipes and our synthetic non-food variants.
        for (final ResourceLocation itemId : targetItemIds) {
            recipesByItemId.get(itemId).removeIf(entry -> {
                final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(entry);
                return recipe != null && recipe.getType() == RecipeType.CRAFTING;
            });
        }

        // Find and inject crafting recipes whose output matches target item IDs,
        // expanding tag ingredients into per-item variants so CFBH's variant arrows appear.
        final Map<ResourceLocation, Object> allVariantsById = new HashMap<>();
        int injected = 0;
        for (final Object recipeEntry : RecipeRuntimeCompat.getAllRecipesFor(recipeManager, RecipeType.CRAFTING)) {
            final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(recipeEntry);
            if (recipe == null) {
                continue;
            }
            final ItemStack result = recipe.getResultItem(registryAccess);
            if (result.isEmpty()) {
                continue;
            }
            final ResourceLocation resultItemId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (!targetItemIds.contains(resultItemId)) {
                continue;
            }

            final List<Map.Entry<ResourceLocation, Object>> variants = expandTagVariants(recipeEntry, registryAccess);
            if (variants.isEmpty()) {
                // No tag ingredients to expand; inject original recipe as-is.
                recipesByItemId.put(resultItemId, recipeEntry);
                injected++;
            } else {
                for (final Map.Entry<ResourceLocation, Object> variant : variants) {
                    recipesByItemId.put(resultItemId, variant.getValue());
                    allVariantsById.put(variant.getKey(), variant.getValue());
                    injected++;
                }
            }
        }

        if (injected > 0) {
            if (!allVariantsById.isEmpty()) {
                final boolean installed = installNonFoodVariantsByName(recipeManager, allVariantsById);
                if (!installed) {
                    LOGGER.warn("Injected {} non-food crafting variants into CFBH index but failed to install in RecipeManager byName.",
                            injected);
                } else {
                    LOGGER.info("Injected {} non-food crafting variants (including {} synthetic) into Cooking for Blockheads recipe index for {} item(s).",
                            injected, allVariantsById.size(), targetItemIds.size());
                }
            } else {
                LOGGER.info("Injected {} non-food crafting recipes into Cooking for Blockheads recipe index for {} item(s).",
                        injected, targetItemIds.size());
            }
        }
    }

    private static List<Map.Entry<ResourceLocation, Object>> expandTagVariants(
            final Object recipeEntry, final RegistryAccess registryAccess) {
        final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(recipeEntry);
        final ResourceLocation originalId = RecipeRuntimeCompat.recipeId(recipeEntry);
        if (recipe == null || originalId == null) {
            return List.of();
        }

        final NonNullList<Ingredient> ingredients = recipe.getIngredients();

        // Find the first ingredient with multiple items (the "primary tag").
        int expandSlot = -1;
        ItemStack[] expandItems = null;
        for (int i = 0; i < ingredients.size(); i++) {
            final ItemStack[] items = ingredients.get(i).getItems();
            if (items.length > 1) {
                expandSlot = i;
                expandItems = items;
                break;
            }
        }

        if (expandSlot < 0 || expandItems == null) {
            return List.of();
        }

        final ItemStack result = recipe.getResultItem(registryAccess);
        final String group = recipe.getGroup();
        final Object category = resolveCraftingCategory(recipe);

        if (category == null) {
            LOGGER.debug("Cannot expand tag variants for {}: unable to resolve CraftingBookCategory.", originalId);
            return List.of();
        }

        final int variantCount = Math.min(expandItems.length, MAX_EXPANDED_VARIANTS);
        final List<Map.Entry<ResourceLocation, Object>> variants = new ArrayList<>(variantCount);

        for (int v = 0; v < variantCount; v++) {
            final NonNullList<Ingredient> variantIngredients = NonNullList.create();
            for (int i = 0; i < ingredients.size(); i++) {
                if (i == expandSlot) {
                    variantIngredients.add(Ingredient.of(expandItems[v]));
                } else {
                    variantIngredients.add(ingredients.get(i));
                }
            }

            final Recipe<?> variantRecipe = createShapelessRecipe(group, category, result.copy(), variantIngredients);
            if (variantRecipe == null) {
                LOGGER.debug("Cannot create ShapelessRecipe variant {} for {}.", v, originalId);
                return List.of();
            }

            final ResourceLocation variantId = nonFoodVariantId(originalId, v);
            final Object variantEntry = RecipeRuntimeCompat.recipeEntry(variantId, variantRecipe);
            variants.add(Map.entry(variantId, variantEntry));
        }

        return variants;
    }

    @SuppressWarnings("unchecked")
    private static Recipe<?> createShapelessRecipe(final String group, final Object category,
                                                    final ItemStack result,
                                                    final NonNullList<Ingredient> ingredients) {
        try {
            final Class<?> shapelessRecipeClass = Class.forName("net.minecraft.world.item.crafting.ShapelessRecipe");

            // 1.21.1: ShapelessRecipe(String, CraftingBookCategory, ItemStack, NonNullList)
            for (final Constructor<?> constructor : shapelessRecipeClass.getConstructors()) {
                final Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 4
                        && params[0] == String.class
                        && params[1].isEnum()
                        && params[2] == ItemStack.class
                        && NonNullList.class.isAssignableFrom(params[3])) {
                    return (Recipe<?>) constructor.newInstance(group, category, result, ingredients);
                }
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to create ShapelessRecipe via reflection.", e);
        }
        return null;
    }

    private static Object resolveCraftingCategory(final Recipe<?> recipe) {
        try {
            final Method categoryMethod = recipe.getClass().getMethod("category");
            return categoryMethod.invoke(recipe);
        } catch (ReflectiveOperationException ignored) {
            // Fall back to structural match.
        }

        for (final Method method : recipe.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            final Class<?> returnType = method.getReturnType();
            if (returnType.isEnum() && returnType.getSimpleName().contains("CraftingBookCategory")) {
                try {
                    return method.invoke(recipe);
                } catch (ReflectiveOperationException ignored) {
                    // Continue.
                }
            }
        }
        return null;
    }

    private static ResourceLocation nonFoodVariantId(final ResourceLocation originalId, final int variantIndex) {
        final String path = NON_FOOD_VARIANT_PATH_PREFIX
                + originalId.getNamespace() + "/"
                + originalId.getPath() + "/"
                + variantIndex;
        return MinecraftApiCompat.resourceLocation(INDEXED_RECIPE_NAMESPACE, path);
    }

    private static boolean isNonFoodVariantId(final ResourceLocation recipeId) {
        return INDEXED_RECIPE_NAMESPACE.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(NON_FOOD_VARIANT_PATH_PREFIX);
    }

    @SuppressWarnings("unchecked")
    private static boolean installNonFoodVariantsByName(final RecipeManager recipeManager,
                                                         final Map<ResourceLocation, Object> variantsById) {
        try {
            final Field byNameField = resolveRecipeByIdMapField(recipeManager);
            if (byNameField == null) {
                return false;
            }

            byNameField.setAccessible(true);
            final Object fieldValue = byNameField.get(recipeManager);
            if (!(fieldValue instanceof Map<?, ?> rawMap)) {
                return false;
            }

            final Map<ResourceLocation, Object> byIdMap = new HashMap<>((Map<ResourceLocation, Object>) rawMap);
            byIdMap.entrySet().removeIf(entry -> isNonFoodVariantId(entry.getKey()));
            byIdMap.putAll(variantsById);
            byNameField.set(recipeManager, byIdMap);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to install non-food variant recipes into RecipeManager byName map.", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean installIndexedRecipesByName(final RecipeManager recipeManager,
                                                        final Map<ResourceLocation, Object> indexedRecipesById) {
        try {
            final Field byNameField = resolveRecipeByIdMapField(recipeManager);
            if (byNameField == null) {
                return false;
            }

            byNameField.setAccessible(true);
            final Object fieldValue = byNameField.get(recipeManager);
            if (!(fieldValue instanceof Map<?, ?> rawMap)) {
                return false;
            }

            final Map<ResourceLocation, Object> byIdMap = mergeIndexedRecipesByName(
                    (Map<ResourceLocation, Object>) rawMap,
                    indexedRecipesById
            );
            byNameField.set(recipeManager, byIdMap);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map.", e);
            return false;
        }
    }

    static Map<ResourceLocation, Object> mergeIndexedRecipesByName(
            final Map<ResourceLocation, Object> currentRecipes,
            final Map<ResourceLocation, Object> indexedRecipes) {
        final Map<ResourceLocation, Object> merged = new HashMap<>(currentRecipes);
        merged.entrySet().removeIf(entry -> isIndexedRecipeId(entry.getKey()));
        merged.putAll(indexedRecipes);
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static Multimap<ResourceLocation, Object> resolveRecipesByItemId() {
        try {
            final Class<?> registryClass = Class.forName(CFBH_REGISTRY_CLASS);
            final Method getRecipesByItemIdMethod = registryClass.getMethod("getRecipesByItemId");
            final Object value = getRecipesByItemIdMethod.invoke(null);
            if (value instanceof Multimap<?, ?> multimap) {
                return (Multimap<ResourceLocation, Object>) multimap;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return null;
    }

    public static Recipe<?> findIndexedRecipe(final ResourceLocation recipeId) {
        if (recipeId == null) {
            return null;
        }
        return INDEXED_RECIPES_BY_ID.get(recipeId);
    }

    public static List<Recipe<?>> indexedRecipes() {
        return List.copyOf(INDEXED_RECIPES_BY_ID.values());
    }

    private static void cacheIndexedRecipes(final Map<ResourceLocation, Object> indexedRecipesById) {
        INDEXED_RECIPES_BY_ID.clear();
        for (final Map.Entry<ResourceLocation, Object> entry : indexedRecipesById.entrySet()) {
            final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(entry.getValue());
            if (recipe != null) {
                INDEXED_RECIPES_BY_ID.put(entry.getKey(), recipe);
            }
        }
    }

    private static boolean refreshLegacyCookingForBlockheadsRegistry(final RecipeManager recipeManager,
                                                                     final RegistryAccess registryAccess) {
        try {
            final Class<?> legacyRegistryClass = Class.forName("net.blay09.mods.cookingforblockheads.registry.CookingRegistry");
            final Method initFoodRegistryMethod = legacyRegistryClass.getMethod("initFoodRegistry", RecipeManager.class, RegistryAccess.class);
            initFoodRegistryMethod.invoke(null, recipeManager, registryAccess);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Field resolveRecipeByIdMapField(final RecipeManager recipeManager) {
        try {
            return RecipeManager.class.getDeclaredField(RECIPE_MANAGER_BY_NAME_FIELD);
        } catch (NoSuchFieldException ignored) {
            // Fall back to structural discovery for production-obfuscated runtimes.
        }

        for (final Field field : RecipeManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                final Object value = field.get(recipeManager);
                if (!(value instanceof Map<?, ?> map)) {
                    continue;
                }
                if (isRecipeByIdMap(map)) {
                    return field;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next field.
            }
        }

        return null;
    }

    private static boolean isRecipeByIdMap(final Map<?, ?> map) {
        if (map.isEmpty()) {
            return false;
        }

        final Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        if (!iterator.hasNext()) {
            return false;
        }

        final Map.Entry<?, ?> sample = iterator.next();
        return sample.getKey() instanceof ResourceLocation;
    }

    private static int countIndexedRecipesInLegacyRegistry() {
        final int injectedCount = LegacyCookingRegistryBridge.countInjectedRecipes();
        if (injectedCount > 0) {
            return injectedCount;
        }

        try {
            final Class<?> legacyRegistryClass = Class.forName("net.blay09.mods.cookingforblockheads.registry.CookingRegistry");
            final Method getFoodRecipesMethod = legacyRegistryClass.getMethod("getFoodRecipes");
            final Object rawRecipes = getFoodRecipesMethod.invoke(null);
            if (!(rawRecipes instanceof Multimap<?, ?> multimap)) {
                return 0;
            }

            final Set<ResourceLocation> indexedRecipeIds = new HashSet<>();
            for (final Object foodRecipeObj : multimap.values()) {
                if (foodRecipeObj == null) {
                    continue;
                }
                final Method getRegistryNameMethod = foodRecipeObj.getClass().getMethod("getRegistryName");
                final Object recipeIdValue = getRegistryNameMethod.invoke(foodRecipeObj);
                if (recipeIdValue instanceof ResourceLocation recipeId && isIndexedRecipeId(recipeId)) {
                    indexedRecipeIds.add(recipeId);
                }
            }
            return indexedRecipeIds.size();
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static ResourceLocation indexedRecipeId(final ResourceLocation originalRecipeId, final String targetKey) {
        final String path = INDEXED_RECIPE_PATH_PREFIX
                + targetKey + "/"
                + originalRecipeId.getNamespace() + "/"
                + originalRecipeId.getPath();
        return MinecraftApiCompat.resourceLocation(INDEXED_RECIPE_NAMESPACE, path);
    }

    private static boolean isIndexedRecipeId(final ResourceLocation recipeId) {
        return INDEXED_RECIPE_NAMESPACE.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(INDEXED_RECIPE_PATH_PREFIX);
    }
}
