package org.lab_11.modsunified.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.Method;

public final class CookingPotContainerTooltipBridge {
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = "cookingforblockheads";
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";
    private static final String TOOLTIP_CONTAINER_COST_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_cost";
    private static final String TOOLTIP_CONTAINER_ENTRY_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_entry";
    private static final String TOOLTIP_MISSING_DUNGEON_OVEN_KEY = "lab_11_mods_unified.tooltip.cooking_table.missing_dungeon_oven";
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
