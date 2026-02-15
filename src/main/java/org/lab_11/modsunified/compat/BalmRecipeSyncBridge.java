package org.lab_11.modsunified.compat;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.client.RecipesUpdatedEvent;
import net.blay09.mods.balm.api.event.server.ServerReloadFinishedEvent;
import net.blay09.mods.balm.api.event.server.ServerStartedEvent;
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
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.Map;

public final class BalmRecipeSyncBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BalmRecipeSyncBridge() {
    }

    public static void registerListeners() {
        Balm.getEvents().onEvent(ServerStartedEvent.class, event ->
                injectCookingPotRecipes(event.getServer().getRecipeManager(), event.getServer().registryAccess(), "balm_server_started"));
        Balm.getEvents().onEvent(ServerReloadFinishedEvent.class, event ->
                injectCookingPotRecipes(event.getServer().getRecipeManager(), event.getServer().registryAccess(), "balm_server_reload_finished"));
        Balm.getEvents().onEvent(RecipesUpdatedEvent.class, event ->
                injectCookingPotRecipes(event.getRecipeManager(), event.getRegistryAccess(), "balm_client_recipes_updated"));
    }

    @SuppressWarnings("unchecked")
    private static void injectCookingPotRecipes(final RecipeManager recipeManager, final RegistryAccess registryAccess, final String source) {
        final RecipeType<CookingPotRecipe> cookingType = ModRecipeTypes.COOKING.get();
        final Multimap<ResourceLocation, RecipeHolder<Recipe<?>>> recipesByItemId = CookingForBlockheadsRegistry.getRecipesByItemId();

        int removed = 0;
        final var iterator = recipesByItemId.entries().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<ResourceLocation, RecipeHolder<Recipe<?>>> entry = iterator.next();
            if (entry.getValue().value().getType() == cookingType) {
                iterator.remove();
                removed++;
            }
        }

        int added = 0;
        for (final RecipeHolder<CookingPotRecipe> recipeHolder : recipeManager.getAllRecipesFor(cookingType)) {
            final ItemStack result = recipeHolder.value().getResultItem(registryAccess);
            if (result.isEmpty()) {
                continue;
            }

            final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
            final RecipeHolder<Recipe<?>> indexedRecipeHolder = FDCookingPotRecipeIndexing.toIndexedRecipeHolder(recipeHolder, registryAccess);
            recipesByItemId.put(itemId, indexedRecipeHolder);
            added++;
        }

        LOGGER.info("Injected {} Farmer's Delight cooking recipes into Cooking for Blockheads recipe index (removed {}) via {}.", added, removed, source);
    }
}
