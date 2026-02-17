package org.lab_11.modsunified.impl.cookingforblockheads;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.blay09.mods.cookingforblockheads.registry.CookingForBlockheadsRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Field;

public final class CookingPotRecipeIndexer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String INDEXED_RECIPE_NAMESPACE = "lab_11_mods_unified";
    private static final String INDEXED_RECIPE_PATH_PREFIX = "cfbh_indexed/";
    private static final String RECIPE_MANAGER_BY_NAME_FIELD = "byName";

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
            return;
        }

        final Multimap<ResourceLocation, RecipeHolder<Recipe<?>>> recipesByItemId = CookingForBlockheadsRegistry.getRecipesByItemId();
        final Map<ResourceLocation, RecipeHolder<?>> indexedRecipesById = new HashMap<>();

        int removed = 0;
        final var iterator = recipesByItemId.entries().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<ResourceLocation, RecipeHolder<Recipe<?>>> entry = iterator.next();
            if (indexedRecipeTypes.contains(entry.getValue().value().getType())) {
                iterator.remove();
                removed++;
            }
        }

        int added = 0;
        final Set<String> addedRecipeKeys = new HashSet<>();
        for (final CookingPotBridgeTarget target : targets) {
            final RecipeType<?> recipeType = target.resolveRecipeType().orElse(null);
            if (recipeType == null) {
                continue;
            }

            final Iterable<RecipeHolder<?>> recipesForType =
                    (Iterable<RecipeHolder<?>>) (Iterable<?>) recipeManager.getAllRecipesFor((RecipeType) recipeType);

            for (final RecipeHolder<?> rawRecipeHolder : recipesForType) {
                if (!target.acceptsRecipe(rawRecipeHolder)) {
                    continue;
                }
                if (BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(target.targetKey())
                        && DungeonsDelightCupRecipeMirror.shouldRouteToCopperPotOnly(rawRecipeHolder)) {
                    continue;
                }

                final String recipeKey = target.targetKey() + "|" + rawRecipeHolder.id();
                if (!addedRecipeKeys.add(recipeKey)) {
                    continue;
                }

                final Recipe<?> recipe = rawRecipeHolder.value();
                final ItemStack rawResult = recipe.getResultItem(registryAccess);
                if (rawResult.isEmpty()) {
                    continue;
                }

                final ResourceLocation indexedRecipeId = indexedRecipeId(rawRecipeHolder.id(), target.targetKey());
                final RecipeHolder<Recipe<?>> indexedRecipeHolder =
                        CookingPotIndexedRecipe.toIndexedRecipeHolder(
                                rawRecipeHolder,
                                indexedRecipeId,
                                registryAccess,
                                target.targetKey(),
                                target.requiredMarkerKeys()
                        );
                final ItemStack indexedResult = indexedRecipeHolder.value().getResultItem(registryAccess);
                if (indexedResult.isEmpty()) {
                    continue;
                }
                final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(indexedResult.getItem());
                recipesByItemId.put(itemId, indexedRecipeHolder);
                indexedRecipesById.put(indexedRecipeId, indexedRecipeHolder);
                added++;
            }
        }

        installIndexedRecipesByName(recipeManager, indexedRecipesById);
        LOGGER.info("Injected {} cooking-pot recipes into Cooking for Blockheads recipe index (removed {}) via {}.",
                added, removed, source);
    }

    @SuppressWarnings("unchecked")
    private static void installIndexedRecipesByName(final RecipeManager recipeManager,
                                                    final Map<ResourceLocation, RecipeHolder<?>> indexedRecipesById) {
        try {
            final Field byNameField = resolveRecipeByIdMapField(recipeManager);
            if (byNameField == null) {
                LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map: no compatible map field found.");
                return;
            }

            byNameField.setAccessible(true);
            final Object fieldValue = byNameField.get(recipeManager);
            if (!(fieldValue instanceof Map<?, ?> rawMap)) {
                LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map: resolved field is not a map.");
                return;
            }

            final Map<ResourceLocation, RecipeHolder<?>> byIdMap = (Map<ResourceLocation, RecipeHolder<?>>) rawMap;
            try {
                byIdMap.entrySet().removeIf(entry -> isIndexedRecipeId(entry.getKey()));
                byIdMap.putAll(indexedRecipesById);
            } catch (UnsupportedOperationException ignored) {
                final Map<ResourceLocation, RecipeHolder<?>> mutableByIdMap = new HashMap<>(byIdMap);
                mutableByIdMap.entrySet().removeIf(entry -> isIndexedRecipeId(entry.getKey()));
                mutableByIdMap.putAll(indexedRecipesById);
                byNameField.set(recipeManager, mutableByIdMap);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to install indexed cooking-pot recipes into RecipeManager byName map.", e);
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
        return sample.getKey() instanceof ResourceLocation && sample.getValue() instanceof RecipeHolder<?>;
    }

    private static ResourceLocation indexedRecipeId(final ResourceLocation originalRecipeId, final String targetKey) {
        final String path = INDEXED_RECIPE_PATH_PREFIX
                + targetKey + "/"
                + originalRecipeId.getNamespace() + "/"
                + originalRecipeId.getPath();
        return ResourceLocation.fromNamespaceAndPath(INDEXED_RECIPE_NAMESPACE, path);
    }

    private static boolean isIndexedRecipeId(final ResourceLocation recipeId) {
        return INDEXED_RECIPE_NAMESPACE.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(INDEXED_RECIPE_PATH_PREFIX);
    }
}
