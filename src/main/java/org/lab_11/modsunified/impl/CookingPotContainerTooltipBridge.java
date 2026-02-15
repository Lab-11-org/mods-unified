package org.lab_11.modsunified.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CookingPotContainerTooltipBridge {
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = "cookingforblockheads";
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";
    private static final String CFBH_CACHE_HINT_CLASS = "net.blay09.mods.cookingforblockheads.api.CacheHint";
    private static final String TOOLTIP_CONTAINER_COST_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_cost";
    private static final String TOOLTIP_CONTAINER_ENTRY_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_entry";
    private static final String TOOLTIP_MISSING_DUNGEON_OVEN_KEY = "lab_11_mods_unified.tooltip.cooking_table.missing_dungeon_oven";
    private static final String TOOLTIP_CONTAINER_NOT_ENOUGH_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_not_enough";
    private static final String INDEXED_RECIPE_NAMESPACE = "lab_11_mods_unified";
    private static final String INDEXED_RECIPE_DUNGEON_POT_PREFIX = "cfbh_indexed/dungeonsdelight_monster_pot/";

    private CookingPotContainerTooltipBridge() {
    }

    public static void appendTooltip(final ItemTooltipEvent event) {
        if (!ModList.get().isLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID)) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Object screen = minecraft.screen;
        if (screen == null || !KITCHEN_SCREEN_CLASS.equals(screen.getClass().getName())) {
            return;
        }

        final Object menu = invokeNoArg(screen, "getMenu");
        final Object selectedRecipeWithStatus = invokeNoArg(menu, "getSelectedRecipe");
        if (selectedRecipeWithStatus == null) {
            return;
        }

        final Object recipeIdObject = invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (!(recipeIdObject instanceof ResourceLocation recipeId)) {
            return;
        }

        final RecipeHolder<?> recipeHolder = minecraft.level == null
                ? null
                : minecraft.level.getRecipeManager().byKey(recipeId).orElse(null);
        if (recipeHolder == null) {
            return;
        }

        final Recipe<?> recipe = recipeHolder.value();
        final ItemStack hoveredStack = event.getItemStack();
        final ItemStack selectedResult = recipe.getResultItem(minecraft.level.registryAccess());
        if (selectedResult.isEmpty() || !ItemStack.isSameItemSameComponents(hoveredStack, selectedResult)) {
            return;
        }

        appendMissingDungeonOvenTooltip(event, menu, recipe, recipeId);

        final ItemStack containerCost = resolveContainerCost(recipe);
        if (containerCost.isEmpty()) {
            return;
        }

        final Component containerEntry = Component.translatable(
                TOOLTIP_CONTAINER_ENTRY_KEY,
                containerCost.getCount(),
                containerCost.getHoverName()
        ).withStyle(ChatFormatting.GOLD);

        final Component tooltipLine = Component.translatable(
                TOOLTIP_CONTAINER_COST_KEY,
                containerEntry
        ).withStyle(ChatFormatting.GRAY);
        event.getToolTip().add(tooltipLine);

        if (isContainerMissing(menu, containerCost, minecraft.player)) {
            event.getToolTip().add(
                    Component.translatable(TOOLTIP_CONTAINER_NOT_ENOUGH_KEY)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            );
        }
    }

    private static ItemStack resolveContainerCost(final Recipe<?> recipe) {
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
            return indexedRecipe.indexedContainerCost();
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return ItemStack.EMPTY;
        }

        return CookingPotContainerCost.resolveForTooltip(recipe, minecraft.level.registryAccess());
    }

    private static void appendMissingDungeonOvenTooltip(final ItemTooltipEvent event,
                                                        final Object menu,
                                                        final Recipe<?> recipe,
                                                        final ResourceLocation recipeId) {
        if (!isIndexedDungeonPotRecipe(recipeId)) {
            return;
        }

        if (canKitchenProcessRecipe(menu, recipe.getType())) {
            return;
        }

        event.getToolTip().add(
                Component.translatable(TOOLTIP_MISSING_DUNGEON_OVEN_KEY).withStyle(ChatFormatting.RED)
        );
    }

    private static boolean isIndexedDungeonPotRecipe(final ResourceLocation recipeId) {
        return INDEXED_RECIPE_NAMESPACE.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(INDEXED_RECIPE_DUNGEON_POT_PREFIX);
    }

    private static boolean canKitchenProcessRecipe(final Object menu, final RecipeType<?> recipeType) {
        if (menu == null || recipeType == null) {
            return true;
        }

        final Object kitchen = invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return true;
        }

        try {
            final Method canProcess = kitchen.getClass().getMethod("canProcess", RecipeType.class);
            final Object result = canProcess.invoke(kitchen, recipeType);
            return !(result instanceof Boolean canProcessValue) || canProcessValue;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private static boolean isContainerMissing(final Object menu,
                                              final ItemStack containerCost,
                                              final Player player) {
        if (menu == null || containerCost.isEmpty() || player == null) {
            return false;
        }

        final Object kitchen = invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return false;
        }

        final List<?> itemProviders = resolveItemProviders(kitchen, player);
        if (itemProviders == null || itemProviders.isEmpty()) {
            return false;
        }

        final Class<?> cacheHintClass = resolveCacheHintClass();
        final Object cacheHintNone = resolveCacheHintNone(cacheHintClass);
        if (cacheHintClass == null || cacheHintNone == null) {
            return false;
        }

        final ItemStack containerUnit = containerCost.copy();
        containerUnit.setCount(1);
        final Ingredient expectedContainer = Ingredient.of(containerUnit);
        final Collection<Object> allocatedTokens = new ArrayList<>();

        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final Object token = findIngredientToken(itemProviders, expectedContainer, allocatedTokens, cacheHintClass, cacheHintNone);
            if (token == null) {
                return true;
            }
            allocatedTokens.add(token);
            remaining--;
        }

        return false;
    }

    private static List<?> resolveItemProviders(final Object kitchen, final Player player) {
        try {
            final Method getItemProviders = kitchen.getClass().getMethod("getItemProviders", Player.class);
            final Object result = getItemProviders.invoke(kitchen, player);
            if (result instanceof List<?> providers) {
                return providers;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return null;
    }

    private static Object findIngredientToken(final List<?> itemProviders,
                                              final Ingredient ingredient,
                                              final Collection<Object> allocatedTokens,
                                              final Class<?> cacheHintClass,
                                              final Object cacheHintNone) {
        for (final Object itemProvider : itemProviders) {
            if (itemProvider == null) {
                continue;
            }

            try {
                final Method findIngredient = itemProvider.getClass().getMethod(
                        "findIngredient",
                        Ingredient.class,
                        Collection.class,
                        cacheHintClass
                );
                final Object token = findIngredient.invoke(itemProvider, ingredient, allocatedTokens, cacheHintNone);
                if (token != null) {
                    return token;
                }
            } catch (ReflectiveOperationException ignored) {
                // no-op
            }
        }
        return null;
    }

    private static Class<?> resolveCacheHintClass() {
        try {
            return Class.forName(CFBH_CACHE_HINT_CLASS);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Object resolveCacheHintNone(final Class<?> cacheHintClass) {
        if (cacheHintClass == null) {
            return null;
        }

        try {
            return cacheHintClass.getField("NONE").get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
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
}
