package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CookingPotContainerTooltipBridge {
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS;
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";
    private static final String CFBH_CACHE_HINT_CLASS = "net.blay09.mods.cookingforblockheads.api.CacheHint";
    private static final String CFBH_INGREDIENT_TOKEN_CLASS = "net.blay09.mods.cookingforblockheads.api.IngredientToken";
    private static final String TOOLTIP_CONTAINER_COST_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_cost";
    private static final String TOOLTIP_CONTAINER_ENTRY_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_entry";
    private static final String TOOLTIP_CONTAINER_NOT_ENOUGH_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_not_enough";
    private static final String TOOLTIP_COOKS_IN_FROM_KEY = "lab_11_mods_unified.tooltip.cooking_table.cooks_in_from";
    private static final int CUSTOM_TOOLTIP_Y_OFFSET = 14;

    private static ItemStack lastDecoratedTooltipStack = ItemStack.EMPTY;

    private CookingPotContainerTooltipBridge() {
    }

    public static void appendTooltip(final ItemTooltipEvent event) {
        clearDecoratedTooltip();
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
        if (selectedResult.isEmpty()) {
            return;
        }

        final boolean resultHovered = ItemStack.isSameItemSameComponents(hoveredStack, selectedResult);
        final boolean ingredientHovered = isHoveredStackInRecipeIngredients(hoveredStack, recipe);

        boolean appendedTooltip = false;
        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe && (resultHovered || ingredientHovered)) {
            appendPotTooltip(event, indexedRecipe);
            appendedTooltip = true;
        }

        if (!resultHovered) {
            if (appendedTooltip) {
                markDecoratedTooltip(hoveredStack);
            }
            return;
        }

        appendedTooltip |= appendMissingRequirementTooltips(event, menu, recipe, minecraft.player);

        final ItemStack containerCost = resolveContainerCost(recipe);
        if (containerCost.isEmpty()) {
            if (appendedTooltip) {
                markDecoratedTooltip(hoveredStack);
            }
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
        appendedTooltip = true;

        if (isContainerMissing(menu, containerCost, minecraft.player)) {
            event.getToolTip().add(
                    Component.translatable(TOOLTIP_CONTAINER_NOT_ENOUGH_KEY)
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            );
            appendedTooltip = true;
        }

        if (appendedTooltip) {
            markDecoratedTooltip(hoveredStack);
        }
    }

    public static void adjustTooltipPosition(final RenderTooltipEvent.Pre event) {
        if (!ModList.get().isLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID)) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Object screen = minecraft.screen;
        if (screen == null || !KITCHEN_SCREEN_CLASS.equals(screen.getClass().getName())) {
            clearDecoratedTooltip();
            return;
        }

        if (lastDecoratedTooltipStack.isEmpty()
                || !ItemStack.isSameItemSameComponents(event.getItemStack(), lastDecoratedTooltipStack)) {
            return;
        }

        event.setY(Math.max(6, event.getY() - CUSTOM_TOOLTIP_Y_OFFSET));
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

    private static boolean appendMissingRequirementTooltips(final ItemTooltipEvent event,
                                                            final Object menu,
                                                            final Recipe<?> recipe,
                                                            final Player player) {
        if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
            return false;
        }

        if (menu == null || player == null || indexedRecipe.requiredMarkerKeys().isEmpty()) {
            return false;
        }

        final Object kitchen = invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return false;
        }

        // If the kitchen can process this recipe, all hard requirements are already satisfied.
        if (canKitchenProcess(kitchen, recipe)) {
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

        boolean appended = false;
        for (final String requiredMarkerKey : indexedRecipe.requiredMarkerKeys()) {
            if (hasRequiredMarker(itemProviders, requiredMarkerKey, cacheHintClass, cacheHintNone)) {
                continue;
            }

            BridgeMarkerRegistry.missingRequirementTooltipKey(requiredMarkerKey).ifPresent(tooltipKey ->
                    event.getToolTip().add(Component.translatable(tooltipKey).withStyle(ChatFormatting.RED))
            );
            appended = true;
        }
        return appended;
    }

    private static boolean isHoveredStackInRecipeIngredients(final ItemStack hoveredStack, final Recipe<?> recipe) {
        if (hoveredStack.isEmpty() || recipe == null) {
            return false;
        }

        final List<Ingredient> ingredients = recipe.getIngredients();
        final int startIndex = recipe instanceof CookingPotIndexedRecipe indexedRecipe
                ? indexedRecipe.syntheticIngredientCount()
                : 0;
        for (int i = startIndex; i < ingredients.size(); i++) {
            if (ingredients.get(i).test(hoveredStack)) {
                return true;
            }
        }

        return false;
    }

    private static void appendPotTooltip(final ItemTooltipEvent event, final CookingPotIndexedRecipe indexedRecipe) {
        final Component potName = resolvePotName(indexedRecipe.targetKey()).copy().withStyle(ChatFormatting.GOLD);
        final Component modName = resolveModName(indexedRecipe.targetKey()).copy().withStyle(ChatFormatting.AQUA);
        event.getToolTip().add(Component.translatable(TOOLTIP_COOKS_IN_FROM_KEY, potName, modName)
                .withStyle(ChatFormatting.GRAY));
    }

    private static void markDecoratedTooltip(final ItemStack tooltipStack) {
        if (tooltipStack.isEmpty()) {
            clearDecoratedTooltip();
            return;
        }
        lastDecoratedTooltipStack = tooltipStack.copy();
    }

    private static void clearDecoratedTooltip() {
        lastDecoratedTooltipStack = ItemStack.EMPTY;
    }

    private static Component resolvePotName(final String targetKey) {
        final ResourceLocation blockId = switch (targetKey) {
            case BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT ->
                    ResourceLocation.fromNamespaceAndPath(BridgeKeys.MOD_FARMERS_DELIGHT, "cooking_pot");
            case BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT ->
                    ResourceLocation.fromNamespaceAndPath(BridgeKeys.MOD_DUNGEONS_DELIGHT, "monster_pot");
            case BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT ->
                    ResourceLocation.fromNamespaceAndPath(BridgeKeys.MOD_MINERS_DELIGHT, "copper_pot");
            default -> null;
        };
        if (blockId == null) {
            return Component.literal(targetKey);
        }

        final var block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            return Component.literal(blockId.toString());
        }
        return Component.translatable(block.getDescriptionId());
    }

    private static Component resolveModName(final String targetKey) {
        final String modId = switch (targetKey) {
            case BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT -> BridgeKeys.MOD_FARMERS_DELIGHT;
            case BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT -> BridgeKeys.MOD_DUNGEONS_DELIGHT;
            case BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT -> BridgeKeys.MOD_MINERS_DELIGHT;
            default -> null;
        };
        if (modId == null) {
            return Component.literal(targetKey);
        }

        return ModList.get()
                .getModContainerById(modId)
                .map(container -> Component.literal(container.getModInfo().getDisplayName()))
                .orElse(Component.literal(modId));
    }

    private static boolean canKitchenProcess(final Object kitchen, final Recipe<?> recipe) {
        if (kitchen == null || recipe == null || recipe.getType() == null) {
            return false;
        }

        try {
            final Method canProcess = kitchen.getClass().getMethod("canProcess", net.minecraft.world.item.crafting.RecipeType.class);
            final Object value = canProcess.invoke(kitchen, recipe.getType());
            return value instanceof Boolean canProcessValue && canProcessValue;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean hasRequiredMarker(final List<?> itemProviders,
                                             final String requiredMarkerKey,
                                             final Class<?> cacheHintClass,
                                             final Object cacheHintNone) {
        // Requirement markers are virtual tokens, so a successful find means the kitchen is fully connected.
        final Ingredient markerIngredient = CookingPotActivationMarkerProvider.markerIngredient(requiredMarkerKey);
        final Object markerToken = findIngredientToken(itemProviders, markerIngredient, List.of(), cacheHintClass, cacheHintNone);
        return markerToken != null;
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
            remaining -= peekTokenStackCount(token);
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
                if (token != null && !isEmptyIngredientToken(token)) {
                    return token;
                }
            } catch (ReflectiveOperationException ignored) {
                // no-op
            }
        }
        return null;
    }

    private static boolean isEmptyIngredientToken(final Object token) {
        if (token == null) {
            return true;
        }

        try {
            final Class<?> ingredientTokenClass = Class.forName(CFBH_INGREDIENT_TOKEN_CLASS);
            final Object emptyToken = ingredientTokenClass.getField("EMPTY").get(null);
            return token == emptyToken;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int peekTokenStackCount(final Object token) {
        if (token == null) {
            return 1;
        }

        try {
            final Method peek = token.getClass().getMethod("peek");
            final Object value = peek.invoke(token);
            if (value instanceof ItemStack stack) {
                return Math.max(1, stack.getCount());
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }

        return 1;
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
