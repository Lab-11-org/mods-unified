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
import org.lab_11.modsunified.impl.platform.RuntimeBindings;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotRecipeIndexer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INDEXED_RECIPE_NAMESPACE = "lab_11_mods_unified";
    private static final String INDEXED_RECIPE_PATH_PREFIX = "cfbh_indexed/";
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

            final boolean installedByName = installIndexedRecipesByName(recipeManager, indexedRecipesById);
            if (!installedByName) {
                LOGGER.warn("Injected {} indexed recipes into Cooking for Blockheads index via {}, but failed to expose IDs in RecipeManager byName lookup.",
                        added, source);
            } else {
                LOGGER.info("Injected {} cooking-pot recipes into Cooking for Blockheads recipe index (removed {}) and updated RecipeManager byName lookup via {}.",
                        added, removed, source);
            }
            return;
        }

        installIndexedRecipes(recipeManager, indexedRecipesById);

        final boolean refreshedLegacyRegistry = refreshLegacyCookingForBlockheadsRegistry(recipeManager, registryAccess);
        if (!refreshedLegacyRegistry) {
            LOGGER.warn("Injected {} cooking-pot recipes into RecipeManager, but could not refresh Cooking for Blockheads registry via {}.",
                    added, source);
            return;
        }

        final int legacyInjectedRecipes = LegacyCookingRegistryBridge.injectIndexedRecipes(indexedRecipesById, registryAccess);
        final int indexedRecipesInManager = countIndexedRecipesInRecipeManager(recipeManager);
        final int indexedRecipesInLegacyRegistry = countIndexedRecipesInLegacyRegistry();
        if (!isLegacyVisibilitySynchronized(added, indexedRecipesInManager, indexedRecipesInLegacyRegistry)) {
            LOGGER.warn(
                    "Indexed recipe visibility is out of sync after legacy refresh via {} (expected={}, managerIndexed={}, legacyIndexed={}); forcing rebuild.",
                    source,
                    added,
                    indexedRecipesInManager,
                    indexedRecipesInLegacyRegistry
            );
            forceRebuildLegacyRegistry(recipeManager, registryAccess, indexedRecipesById);
        }

        final int finalIndexedRecipesInManager = countIndexedRecipesInRecipeManager(recipeManager);
        final int finalIndexedRecipesInLegacyRegistry = countIndexedRecipesInLegacyRegistry();
        if (!isLegacyVisibilitySynchronized(added, finalIndexedRecipesInManager, finalIndexedRecipesInLegacyRegistry)) {
            LOGGER.warn(
                    "Indexed recipe visibility remains out of sync via {} (expected={}, managerIndexed={}, legacyIndexed={}).",
                    source,
                    added,
                    finalIndexedRecipesInManager,
                    finalIndexedRecipesInLegacyRegistry
            );
        } else {
            LOGGER.info(
                    "Injected {} cooking-pot recipes into legacy Cooking for Blockheads registry via {} (legacyInjected={}, managerIndexed={}, legacyIndexed={}).",
                    added,
                    source,
                    legacyInjectedRecipes,
                    finalIndexedRecipesInManager,
                    finalIndexedRecipesInLegacyRegistry
            );
        }
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

    private static void cacheIndexedRecipes(final Map<ResourceLocation, Object> indexedRecipesById) {
        INDEXED_RECIPES_BY_ID.clear();
        for (final Map.Entry<ResourceLocation, Object> entry : indexedRecipesById.entrySet()) {
            final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(entry.getValue());
            if (recipe != null) {
                INDEXED_RECIPES_BY_ID.put(entry.getKey(), recipe);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void installIndexedRecipesByNameAndType(final RecipeManager recipeManager,
                                                           final Map<ResourceLocation, Object> indexedRecipesById) {
        boolean installedByName = installIndexedRecipesByName(recipeManager, indexedRecipesById);
        boolean installedByType = installIndexedRecipesByType(recipeManager, indexedRecipesById);
        if (!installedByName && !installedByType) {
            LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager fallback maps (byName/byType).");
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean installIndexedRecipesByName(final RecipeManager recipeManager,
                                                       final Map<ResourceLocation, Object> indexedRecipesById) {
        try {
            final Field byNameField = resolveRecipeByIdMapField(recipeManager);
            if (byNameField == null) {
                LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map: no compatible map field found.");
                return false;
            }

            byNameField.setAccessible(true);
            final Object fieldValue = byNameField.get(recipeManager);
            if (!(fieldValue instanceof Map<?, ?> rawMap)) {
                LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map: resolved field is not a map.");
                return false;
            }

            final Map<ResourceLocation, Object> byIdMap = new HashMap<>((Map<ResourceLocation, Object>) rawMap);
            byIdMap.entrySet().removeIf(entry -> isIndexedRecipeId(entry.getKey()));
            byIdMap.putAll(indexedRecipesById);
            byNameField.set(recipeManager, byIdMap);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map.", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean installIndexedRecipesByType(final RecipeManager recipeManager,
                                                       final Map<ResourceLocation, Object> indexedRecipesById) {
        try {
            final Field byTypeField = resolveRecipeByTypeMapField(recipeManager);
            if (byTypeField == null) {
                return false;
            }

            byTypeField.setAccessible(true);
            final Object fieldValue = byTypeField.get(recipeManager);
            if (!(fieldValue instanceof Map<?, ?> rawMap)) {
                return false;
            }

            final Map<Object, Object> byTypeMap = new HashMap<>();
            for (final Map.Entry<?, ?> entry : rawMap.entrySet()) {
                final Object key = entry.getKey();
                final Object value = entry.getValue();
                if (!(value instanceof Map<?, ?> nestedRawMap)) {
                    byTypeMap.put(key, value);
                    continue;
                }

                final Map<ResourceLocation, Object> nestedMap = new HashMap<>();
                for (final Map.Entry<?, ?> nestedEntry : nestedRawMap.entrySet()) {
                    if (!(nestedEntry.getKey() instanceof ResourceLocation nestedRecipeId)) {
                        continue;
                    }
                    if (isIndexedRecipeId(nestedRecipeId)) {
                        continue;
                    }
                    nestedMap.put(nestedRecipeId, nestedEntry.getValue());
                }
                byTypeMap.put(key, nestedMap);
            }

            for (final Map.Entry<ResourceLocation, Object> indexedEntry : indexedRecipesById.entrySet()) {
                final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(indexedEntry.getValue());
                if (recipe == null) {
                    continue;
                }
                final Object typeKey = recipe.getType();
                final Object existing = byTypeMap.get(typeKey);
                final Map<ResourceLocation, Object> recipeMap;
                if (existing instanceof Map<?, ?> existingMap) {
                    recipeMap = new HashMap<>((Map<ResourceLocation, Object>) existingMap);
                } else {
                    recipeMap = new HashMap<>();
                }
                recipeMap.put(indexedEntry.getKey(), indexedEntry.getValue());
                byTypeMap.put(typeKey, recipeMap);
            }

            byTypeField.set(recipeManager, byTypeMap);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byType map.", e);
            return false;
        }
    }

    private static void installIndexedRecipes(final RecipeManager recipeManager,
                                              final Map<ResourceLocation, Object> indexedRecipesById) {
        if (shouldUseMapInjectionOnly()) {
            installIndexedRecipesByNameAndType(recipeManager, indexedRecipesById);
            return;
        }

        final List<Object> mergedRecipes = new ArrayList<>();
        for (final Object recipeEntry : RecipeRuntimeCompat.getAllRecipes(recipeManager)) {
            final ResourceLocation recipeId = RecipeRuntimeCompat.recipeId(recipeEntry);
            if (recipeId != null && isIndexedRecipeId(recipeId)) {
                continue;
            }
            mergedRecipes.add(recipeEntry);
        }
        mergedRecipes.addAll(indexedRecipesById.values());

        try {
            RecipeRuntimeCompat.replaceRecipes(recipeManager, mergedRecipes);
        } catch (RuntimeException e) {
            LOGGER.warn("Falling back to RecipeManager byName/byType injection because replaceRecipes failed.", e);
            installIndexedRecipesByNameAndType(recipeManager, indexedRecipesById);
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

    private static Field resolveRecipeByTypeMapField(final RecipeManager recipeManager) {
        for (final Field field : RecipeManager.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            final Field byNameField = resolveRecipeByIdMapField(recipeManager);
            if (byNameField != null && byNameField.getName().equals(field.getName())) {
                continue;
            }

            try {
                field.setAccessible(true);
                final Object value = field.get(recipeManager);
                if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
                    continue;
                }
                if (isRecipeByTypeMap(map)) {
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

    private static boolean isRecipeByTypeMap(final Map<?, ?> map) {
        if (map.isEmpty()) {
            return false;
        }

        final Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        if (!iterator.hasNext()) {
            return false;
        }

        final Map.Entry<?, ?> sample = iterator.next();
        if (!(sample.getKey() instanceof RecipeType<?>)) {
            return false;
        }
        if (!(sample.getValue() instanceof Map<?, ?> nestedMap) || nestedMap.isEmpty()) {
            return true;
        }
        final Iterator<? extends Map.Entry<?, ?>> nestedIterator = nestedMap.entrySet().iterator();
        if (!nestedIterator.hasNext()) {
            return true;
        }
        final Map.Entry<?, ?> nestedSample = nestedIterator.next();
        return nestedSample.getKey() instanceof ResourceLocation;
    }

    private static boolean isLegacyVisibilitySynchronized(final int expectedIndexedRecipes,
                                                          final int indexedRecipesInManager,
                                                          final int indexedRecipesInLegacyRegistry) {
        if (expectedIndexedRecipes <= 0) {
            return true;
        }
        return indexedRecipesInManager >= expectedIndexedRecipes
                && indexedRecipesInLegacyRegistry >= expectedIndexedRecipes;
    }

    private static void forceRebuildLegacyRegistry(final RecipeManager recipeManager,
                                                   final RegistryAccess registryAccess,
                                                   final Map<ResourceLocation, Object> indexedRecipesById) {
        installIndexedRecipesByNameAndType(recipeManager, indexedRecipesById);
        if (!refreshLegacyCookingForBlockheadsRegistry(recipeManager, registryAccess)) {
            LOGGER.warn("Forced legacy Cooking for Blockheads registry rebuild failed.");
            return;
        }
        LegacyCookingRegistryBridge.injectIndexedRecipes(indexedRecipesById, registryAccess);
    }

    private static int countIndexedRecipesInRecipeManager(final RecipeManager recipeManager) {
        final Set<ResourceLocation> indexedRecipeIds = new HashSet<>();
        for (final Object recipeEntry : RecipeRuntimeCompat.getAllRecipes(recipeManager)) {
            final ResourceLocation recipeId = RecipeRuntimeCompat.recipeId(recipeEntry);
            if (recipeId != null && isIndexedRecipeId(recipeId)) {
                indexedRecipeIds.add(recipeId);
            }
        }
        return indexedRecipeIds.size();
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

    private static boolean shouldUseMapInjectionOnly() {
        final var profile = RuntimeBindings.active().profile();
        return "forge".equals(profile.loader()) && profile.minecraftVersion().startsWith("1.20.1");
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
