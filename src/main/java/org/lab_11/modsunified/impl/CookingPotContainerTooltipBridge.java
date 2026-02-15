package org.lab_11.modsunified.impl;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.Method;

public final class CookingPotContainerTooltipBridge {
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = "cookingforblockheads";
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";

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

        final ItemStack containerCost = resolveContainerCost(recipe);
        if (containerCost.isEmpty()) {
            return;
        }

        final Component tooltipLine = Component.literal("Consumes: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(containerCost.getCount() + "x ").withStyle(ChatFormatting.GOLD))
                .append(containerCost.getHoverName().copy().withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(tooltipLine);
    }

    private static ItemStack resolveContainerCost(final Recipe<?> recipe) {
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
            return indexedRecipe.indexedContainerCost();
        }

        return ItemStack.EMPTY;
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
