package org.lab_11.modsunified.impl;

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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CookingPotRecipeIndexer {
    private static final Logger LOGGER = LogUtils.getLogger();

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

                final String recipeKey = target.targetKey() + "|" + rawRecipeHolder.id();
                if (!addedRecipeKeys.add(recipeKey)) {
                    continue;
                }

                final Recipe<?> recipe = rawRecipeHolder.value();
                final ItemStack result = recipe.getResultItem(registryAccess);
                if (result.isEmpty()) {
                    continue;
                }

                final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
                final RecipeHolder<Recipe<?>> indexedRecipeHolder =
                        CookingPotIndexedRecipe.toIndexedRecipeHolder(rawRecipeHolder, registryAccess, target.targetKey());
                recipesByItemId.put(itemId, indexedRecipeHolder);
                added++;
            }
        }

        LOGGER.info("Injected {} cooking-pot recipes into Cooking for Blockheads recipe index (removed {}) via {}.",
                added, removed, source);
    }
}
