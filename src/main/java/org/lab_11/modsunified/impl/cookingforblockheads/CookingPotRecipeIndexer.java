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
import java.lang.reflect.Method;
import java.util.WeakHashMap;

public final class CookingPotRecipeIndexer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CFBH_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.registry.CookingForBlockheadsRegistry";
    private static final IndexedRecipeSnapshot EMPTY_SNAPSHOT = new IndexedRecipeSnapshot(Map.of(), Map.of());
    private static final Map<RecipeManager, IndexedRecipeSnapshot> INDEXED_SNAPSHOTS_BY_MANAGER =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile IndexedRecipeSnapshot latestSnapshot = EMPTY_SNAPSHOT;

    private record IndexedRecipeSnapshot(Map<ResourceLocation, Recipe<?>> recipesById,
                                         Map<ResourceLocation, Object> holdersById) {
        private IndexedRecipeSnapshot {
            recipesById = Map.copyOf(recipesById);
            holdersById = Map.copyOf(holdersById);
        }
    }

    private CookingPotRecipeIndexer() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void injectRecipes(final RecipeManager recipeManager,
                                     final RegistryAccess registryAccess,
                                     final String source,
                                     final List<CookingPotBridgeTarget> targets) {
        final Set<RecipeType<?>> indexedRecipeTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final CookingPotBridgeTarget target : targets) {
            indexedRecipeTypes.addAll(target.resolveRecipeTypes());
        }

        if (indexedRecipeTypes.isEmpty()) {
            cacheIndexedRecipes(recipeManager, Map.of());
            return;
        }

        final Map<ResourceLocation, Object> indexedRecipesById = new HashMap<>();
        int added = 0;
        final Set<String> addedRecipeKeys = new HashSet<>();
        for (final CookingPotBridgeTarget target : targets) {
            for (final CookingPotBridgeTarget.RecipeBinding binding : target.recipeBindings()) {
                if (!binding.isAvailable()) {
                    continue;
                }
                final RecipeType<?> recipeType = binding.resolveRecipeType().orElse(null);
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
                    if (!binding.acceptsRecipe(rawRecipeEntry)) {
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
        }

        cacheIndexedRecipes(recipeManager, indexedRecipesById);

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

            LOGGER.info("Injected {} cooking-pot recipes into Cooking for Blockheads recipe index (removed {}) via {}.",
                    added, removed, source);
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
        return latestSnapshot.recipesById().get(recipeId);
    }

    public static Object findIndexedRecipeHolder(final ResourceLocation recipeId) {
        if (recipeId == null) {
            return null;
        }
        return latestSnapshot.holdersById().get(recipeId);
    }

    public static Object findIndexedRecipeHolder(final RecipeManager recipeManager,
                                                 final ResourceLocation recipeId) {
        if (recipeManager == null || recipeId == null) {
            return null;
        }

        final IndexedRecipeSnapshot snapshot = INDEXED_SNAPSHOTS_BY_MANAGER.get(recipeManager);
        return snapshot == null ? null : snapshot.holdersById().get(recipeId);
    }

    static Set<ResourceLocation> indexedRecipeIds(final RecipeManager recipeManager) {
        if (recipeManager == null) {
            return Set.of();
        }

        final IndexedRecipeSnapshot snapshot = INDEXED_SNAPSHOTS_BY_MANAGER.get(recipeManager);
        return snapshot == null ? Set.of() : snapshot.holdersById().keySet();
    }

    public static List<Recipe<?>> indexedRecipes() {
        return List.copyOf(latestSnapshot.recipesById().values());
    }

    static List<Recipe<?>> indexedRecipes(final RecipeManager recipeManager) {
        if (recipeManager == null) {
            return List.of();
        }

        final IndexedRecipeSnapshot snapshot = INDEXED_SNAPSHOTS_BY_MANAGER.get(recipeManager);
        return snapshot == null ? List.of() : List.copyOf(snapshot.recipesById().values());
    }

    private static void cacheIndexedRecipes(final RecipeManager recipeManager,
                                            final Map<ResourceLocation, Object> indexedRecipesById) {
        final Map<ResourceLocation, Recipe<?>> recipesById = new HashMap<>();
        for (final Map.Entry<ResourceLocation, Object> entry : indexedRecipesById.entrySet()) {
            final Recipe<?> recipe = RecipeRuntimeCompat.recipeValue(entry.getValue());
            if (recipe != null) {
                recipesById.put(entry.getKey(), recipe);
            }
        }

        final IndexedRecipeSnapshot snapshot = new IndexedRecipeSnapshot(recipesById, indexedRecipesById);
        synchronized (INDEXED_SNAPSHOTS_BY_MANAGER) {
            if (recipeManager != null) {
                INDEXED_SNAPSHOTS_BY_MANAGER.put(recipeManager, snapshot);
            }
            latestSnapshot = snapshot;
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
