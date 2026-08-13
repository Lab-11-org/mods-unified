package org.lab_11.modsunified.impl.cookingforblockheads;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotRecipeIndexer {
    private static final Logger LOGGER = LogUtils.getLogger();
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

        final Map.Entry<?, ?> sample = map.entrySet().iterator().next();
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
        final String path = BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX
                + targetKey + "/"
                + originalRecipeId.getNamespace() + "/"
                + originalRecipeId.getPath();
        return MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_LAB11_UNIFIED, path);
    }

    private static boolean isIndexedRecipeId(final ResourceLocation recipeId) {
        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }
}
