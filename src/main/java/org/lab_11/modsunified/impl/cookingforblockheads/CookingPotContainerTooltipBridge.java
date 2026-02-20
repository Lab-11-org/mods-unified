package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.ChatFormatting;
import net.blay09.mods.balm.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
    private enum TargetConnectionState {
        MISSING,
        PRESENT_BUT_WRONG_SETUP,
        CONNECTED
    }

    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS;
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";
    private static final String CRAFT_MATRIX_FAKE_SLOT_CLASS = "net.blay09.mods.cookingforblockheads.menu.slot.CraftMatrixFakeSlot";
    private static final String CFBH_CACHE_HINT_CLASS = "net.blay09.mods.cookingforblockheads.api.CacheHint";
    private static final String CFBH_INGREDIENT_TOKEN_CLASS = "net.blay09.mods.cookingforblockheads.api.IngredientToken";
    private static final String CFBH_TOOLTIP_CLICK_TO_UNLOCK_KEY = "tooltip.cookingforblockheads.click_to_unlock";
    private static final String CFBH_TOOLTIP_CLICK_TO_LOCK_KEY = "tooltip.cookingforblockheads.click_to_lock";
    private static final String CFBH_TOOLTIP_SCROLL_TO_SWITCH_KEY = "tooltip.cookingforblockheads.scroll_to_switch";
    private static final String TOOLTIP_CONTAINER_COST_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_cost";
    private static final String TOOLTIP_CONTAINER_ENTRY_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_entry";
    private static final String TOOLTIP_CONTAINER_NOT_ENOUGH_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_not_enough";
    private static final String TOOLTIP_COOKS_IN_FROM_KEY = "lab_11_mods_unified.tooltip.cooking_table.cooks_in_from";
    private static final String TOOLTIP_POT_NOT_CONNECTED_KEY = "lab_11_mods_unified.tooltip.cooking_table.pot_not_connected";
    private static final int CUSTOM_TOOLTIP_Y_OFFSET = 14;

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

        removeIndexedRecipeLockHints(event, screen, selectedRecipeWithStatus);

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

        if (recipe instanceof CookingPotIndexedRecipe indexedRecipe && (resultHovered || ingredientHovered)) {
            appendPotTooltip(event, indexedRecipe);
        }

        if (!resultHovered) {
            return;
        }

        final boolean hasHardRequirementFailure = appendMissingRequirementTooltips(event, menu, recipe, minecraft.player);
        if (hasHardRequirementFailure) {
            return;
        }

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

    public static void adjustTooltipPosition(final RenderTooltipEvent.Pre event) {
        if (!ModList.get().isLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID)) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Object screen = minecraft.screen;
        if (screen == null || !KITCHEN_SCREEN_CLASS.equals(screen.getClass().getName())) {
            return;
        }

        if (!shouldApplyCustomTooltipOffset(minecraft, screen, event.getItemStack())) {
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

        if (menu == null || player == null) {
            return false;
        }

        final Object kitchen = invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return false;
        }

        final List<?> itemProviders = resolveItemProviders(kitchen, player);
        if (itemProviders == null || itemProviders.isEmpty()) {
            appendRedTooltip(event, TOOLTIP_POT_NOT_CONNECTED_KEY);
            return true;
        }

        final Class<?> cacheHintClass = resolveCacheHintClass();
        final Object cacheHintNone = resolveCacheHintNone(cacheHintClass);
        if (cacheHintClass == null || cacheHintNone == null) {
            return false;
        }

        if (resolveTargetConnectionState(itemProviders, indexedRecipe.targetKey(), cacheHintClass, cacheHintNone)
                != TargetConnectionState.CONNECTED) {
            appendMissingTargetTooltip(event, indexedRecipe.targetKey());
            return true;
        }

        if (BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(indexedRecipe.targetKey())
                || indexedRecipe.requiredMarkerKeys().isEmpty()) {
            return false;
        }

        boolean appended = false;
        for (final String requiredMarkerKey : indexedRecipe.requiredMarkerKeys()) {
            if (hasRequiredMarker(itemProviders, requiredMarkerKey, cacheHintClass, cacheHintNone)) {
                continue;
            }

            BridgeMarkerRegistry.missingRequirementTooltipKey(requiredMarkerKey).ifPresent(tooltipKey ->
                    appendRedTooltip(event, tooltipKey)
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

    private static void removeIndexedRecipeLockHints(final ItemTooltipEvent event,
                                                     final Object screen,
                                                     final Object selectedRecipeWithStatus) {
        if (!isIndexedRecipeSelection(selectedRecipeWithStatus)) {
            return;
        }

        final Object hoveredSlot = screen instanceof AbstractContainerScreenAccessor accessor
                ? accessor.getHoveredSlot()
                : invokeNoArg(screen, "getHoveredSlot");
        if (hoveredSlot == null || !CRAFT_MATRIX_FAKE_SLOT_CLASS.equals(hoveredSlot.getClass().getName())) {
            return;
        }

        event.getToolTip().removeIf(CookingPotContainerTooltipBridge::isIndexedLockHintLine);
    }

    private static boolean isIndexedLockHintLine(final Component tooltipLine) {
        if (tooltipLine == null) {
            return false;
        }

        if (!(tooltipLine.getContents() instanceof TranslatableContents translatable)) {
            return false;
        }

        final String key = translatable.getKey();
        return CFBH_TOOLTIP_CLICK_TO_UNLOCK_KEY.equals(key)
                || CFBH_TOOLTIP_CLICK_TO_LOCK_KEY.equals(key)
                || CFBH_TOOLTIP_SCROLL_TO_SWITCH_KEY.equals(key);
    }

    private static boolean isIndexedRecipeSelection(final Object selectedRecipeWithStatus) {
        final Object recipeIdObject = invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (!(recipeIdObject instanceof ResourceLocation recipeId)) {
            return false;
        }

        return BridgeKeys.MOD_LAB11_UNIFIED.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(BridgeKeys.INDEXED_CFBH_RECIPE_PATH_PREFIX);
    }

    private static boolean shouldApplyCustomTooltipOffset(final Minecraft minecraft,
                                                          final Object screen,
                                                          final ItemStack hoveredStack) {
        if (minecraft.level == null || hoveredStack.isEmpty()) {
            return false;
        }

        final Object menu = invokeNoArg(screen, "getMenu");
        final Object selectedRecipeWithStatus = invokeNoArg(menu, "getSelectedRecipe");
        if (selectedRecipeWithStatus == null) {
            return false;
        }

        final Recipe<?> recipe = resolveSelectedRecipe(minecraft, selectedRecipeWithStatus);
        if (recipe == null) {
            return false;
        }

        final ItemStack resultStack = recipe.getResultItem(minecraft.level.registryAccess());
        if (resultStack.isEmpty()) {
            return false;
        }

        final boolean resultHovered = ItemStack.isSameItemSameComponents(hoveredStack, resultStack);
        final boolean ingredientHovered = isHoveredStackInRecipeIngredients(hoveredStack, recipe);
        final boolean showsPotOriginLine = recipe instanceof CookingPotIndexedRecipe && (resultHovered || ingredientHovered);
        if (showsPotOriginLine) {
            return true;
        }

        if (!resultHovered) {
            return false;
        }

        return showsContainerLine(recipe)
                || showsMissingRequirementsLine(menu, recipe, minecraft.player);
    }

    private static Recipe<?> resolveSelectedRecipe(final Minecraft minecraft,
                                                   final Object selectedRecipeWithStatus) {
        final Object recipeIdObject = invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (!(recipeIdObject instanceof ResourceLocation recipeId)) {
            return null;
        }

        final RecipeHolder<?> recipeHolder = minecraft.level == null
                ? null
                : minecraft.level.getRecipeManager().byKey(recipeId).orElse(null);
        return recipeHolder != null ? recipeHolder.value() : null;
    }

    private static boolean showsContainerLine(final Recipe<?> recipe) {
        return !resolveContainerCost(recipe).isEmpty();
    }

    private static boolean showsMissingRequirementsLine(final Object menu,
                                                        final Recipe<?> recipe,
                                                        final Player player) {
        if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)
                || menu == null
                || player == null) {
            return false;
        }

        final Object kitchen = invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return false;
        }

        final List<?> itemProviders = resolveItemProviders(kitchen, player);
        if (itemProviders == null || itemProviders.isEmpty()) {
            return true;
        }

        final Class<?> cacheHintClass = resolveCacheHintClass();
        final Object cacheHintNone = resolveCacheHintNone(cacheHintClass);
        if (cacheHintClass == null || cacheHintNone == null) {
            return false;
        }

        if (resolveTargetConnectionState(itemProviders, indexedRecipe.targetKey(), cacheHintClass, cacheHintNone)
                != TargetConnectionState.CONNECTED) {
            return true;
        }

        if (BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(indexedRecipe.targetKey())
                || indexedRecipe.requiredMarkerKeys().isEmpty()) {
            return false;
        }

        for (final String requiredMarkerKey : indexedRecipe.requiredMarkerKeys()) {
            if (!hasRequiredMarker(itemProviders, requiredMarkerKey, cacheHintClass, cacheHintNone)) {
                return true;
            }
        }
        return false;
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

    private static void appendRedTooltip(final ItemTooltipEvent event, final String tooltipKey) {
        event.getToolTip().add(Component.translatable(tooltipKey).withStyle(ChatFormatting.RED));
    }

    private static void appendMissingTargetTooltip(final ItemTooltipEvent event, final String targetKey) {
        if (BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(targetKey)) {
            appendRedTooltip(event, BridgeKeys.TOOLTIP_MISSING_DUNGEON_OVEN);
            return;
        }

        appendRedTooltip(event, TOOLTIP_POT_NOT_CONNECTED_KEY);
    }

    private static TargetConnectionState resolveTargetConnectionState(final List<?> itemProviders,
                                                                      final String targetKey,
                                                                      final Class<?> cacheHintClass,
                                                                      final Object cacheHintNone) {
        boolean foundTargetProvider = false;
        for (final Object itemProvider : itemProviders) {
            if (!(itemProvider instanceof CookingPotActivationMarkerProvider markerProvider)
                    || !markerProvider.isMarkerKey(targetKey)) {
                continue;
            }

            foundTargetProvider = true;
            if (markerProvider.isTargetPotConnectedForCurrentTableMarker()) {
                return TargetConnectionState.CONNECTED;
            }
        }

        return foundTargetProvider
                ? TargetConnectionState.PRESENT_BUT_WRONG_SETUP
                : (hasRequiredMarker(
                itemProviders,
                targetKey,
                cacheHintClass,
                cacheHintNone
        ) ? TargetConnectionState.CONNECTED : TargetConnectionState.MISSING);
    }
}
