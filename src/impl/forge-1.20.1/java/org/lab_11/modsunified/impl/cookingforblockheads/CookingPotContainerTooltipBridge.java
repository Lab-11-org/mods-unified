package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class CookingPotContainerTooltipBridge {
    private enum TargetConnectionState {
        MISSING,
        PRESENT_BUT_WRONG_SETUP,
        CONNECTED
    }

    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS;
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";
    private static final String RECIPE_BOOK_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.RecipeBookScreen";
    private static final Set<String> CFBH_RECIPE_SCREEN_CLASS_NAMES = Set.of(
            KITCHEN_SCREEN_CLASS,
            RECIPE_BOOK_SCREEN_CLASS
    );
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
        if (!LoaderApiCompat.isModLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID)) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Object screen = minecraft.screen;
        if (!isSupportedRecipeScreen(screen)) {
            return;
        }

        final Object menu = invokeNoArg(screen, "getMenu");
        final Object selectedRecipeWithStatus = invokeNoArg(menu, "getSelectedRecipe");
        if (selectedRecipeWithStatus == null) {
            return;
        }

        removeIndexedRecipeLockHints(event, screen, selectedRecipeWithStatus);

        final Recipe<?> recipe = resolveSelectedRecipe(minecraft, menu, selectedRecipeWithStatus);
        if (recipe == null) {
            return;
        }
        final ItemStack hoveredStack = event.getItemStack();
        final ItemStack selectedResult = recipe.getResultItem(minecraft.level.registryAccess());
        if (selectedResult.isEmpty()) {
            return;
        }

        final boolean resultHovered = MinecraftApiCompat.isSameItemSameData(hoveredStack, selectedResult);
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
        if (!LoaderApiCompat.isModLoaded(COOKING_FOR_BLOCKHEADS_MOD_ID)) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Object screen = minecraft.screen;
        if (!isSupportedRecipeScreen(screen)) {
            return;
        }

        if (!shouldApplyCustomTooltipOffset(minecraft, screen, event.getItemStack())) {
            return;
        }

        event.setY(Math.max(6, event.getY() - CUSTOM_TOOLTIP_Y_OFFSET));
    }

    private static ItemStack resolveContainerCost(final Recipe<?> recipe) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            if (recipe instanceof CookingPotIndexedRecipe indexedRecipe) {
                return indexedRecipe.indexedContainerCost();
            }
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

        final Object hoveredSlot = invokeNoArg(screen, "getHoveredSlot");
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

        final Recipe<?> recipe = resolveSelectedRecipe(minecraft, menu, selectedRecipeWithStatus);
        if (recipe == null) {
            return false;
        }

        final ItemStack resultStack = recipe.getResultItem(minecraft.level.registryAccess());
        if (resultStack.isEmpty()) {
            return false;
        }

        final boolean resultHovered = MinecraftApiCompat.isSameItemSameData(hoveredStack, resultStack);
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
                                                   final Object menu,
                                                   final Object selectedRecipeWithStatus) {
        final Object recipeIdObject = invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (recipeIdObject instanceof ResourceLocation recipeId) {
            return resolveRecipeById(minecraft, recipeId);
        }

        return resolveLegacySelectedIndexedRecipe(minecraft, menu);
    }

    private static Recipe<?> resolveRecipeById(final Minecraft minecraft, final ResourceLocation recipeId) {
        if (minecraft.level == null) {
            return null;
        }

        final Recipe<?> recipe = RecipeRuntimeCompat.findById(minecraft.level.getRecipeManager(), recipeId);
        if (recipe != null) {
            return recipe;
        }

        return CookingPotRecipeIndexer.findIndexedRecipe(recipeId);
    }

    private static Recipe<?> resolveLegacySelectedIndexedRecipe(final Minecraft minecraft, final Object menu) {
        if (minecraft.level == null || menu == null) {
            return null;
        }

        final Object selection = invokeNoArg(menu, "getSelection");
        if (selection == null) {
            return null;
        }

        final Object recipeType = invokeNoArg(selection, "getRecipeType");
        if (recipeType == null || !"CRAFTING".equals(recipeType.toString())) {
            return null;
        }

        final ItemStack outputItem = readLegacySelectionOutput(selection);
        if (outputItem.isEmpty()) {
            return null;
        }

        final List<ItemStack> matrixStacks = readLegacyDisplayedMatrixStacks(menu);
        if (matrixStacks.isEmpty()) {
            return null;
        }

        return findIndexedRecipeByOutputAndMatrix(minecraft, outputItem, matrixStacks);
    }

    private static ItemStack readLegacySelectionOutput(final Object selection) {
        final Object outputValue = invokeNoArg(selection, "getOutputItem");
        return outputValue instanceof ItemStack outputItem ? outputItem : ItemStack.EMPTY;
    }

    private static List<ItemStack> readLegacyDisplayedMatrixStacks(final Object menu) {
        final Object slotsValue = invokeNoArg(menu, "getCraftingMatrixSlots");
        if (!(slotsValue instanceof List<?> slots) || slots.isEmpty()) {
            return List.of();
        }

        final List<ItemStack> stacks = new ArrayList<>(slots.size());
        for (final Object slot : slots) {
            final Object stackValue = invokeNoArg(slot, "getItem");
            if (stackValue instanceof ItemStack stack) {
                stacks.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            } else {
                stacks.add(ItemStack.EMPTY);
            }
        }
        return stacks;
    }

    private static Recipe<?> findIndexedRecipeByOutputAndMatrix(final Minecraft minecraft,
                                                                 final ItemStack outputItem,
                                                                 final List<ItemStack> matrixStacks) {
        for (final Recipe<?> recipe : CookingPotRecipeIndexer.indexedRecipes()) {
            if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
                continue;
            }

            final ItemStack indexedResult = indexedRecipe.getResultItem(minecraft.level.registryAccess());
            if (!MinecraftApiCompat.isSameItemSameData(indexedResult, outputItem)) {
                continue;
            }
            if (!matchesDisplayedMatrix(indexedRecipe, matrixStacks, minecraft.level.registryAccess())) {
                continue;
            }
            return indexedRecipe;
        }

        return null;
    }

    private static boolean matchesDisplayedMatrix(final CookingPotIndexedRecipe indexedRecipe,
                                                  final List<ItemStack> matrixStacks,
                                                  final RegistryAccess registryAccess) {
        final List<ItemStack> remainingStacks = new ArrayList<>();
        for (final ItemStack matrixStack : matrixStacks) {
            if (matrixStack != null && !matrixStack.isEmpty()) {
                remainingStacks.add(matrixStack);
            }
        }

        final int start = Math.max(0, indexedRecipe.syntheticIngredientCount());
        final List<Ingredient> ingredients = indexedRecipe.getIngredients();
        for (int i = start; i < ingredients.size(); i++) {
            final Ingredient ingredient = ingredients.get(i);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            boolean matched = false;
            for (int stackIndex = 0; stackIndex < remainingStacks.size(); stackIndex++) {
                final ItemStack candidate = remainingStacks.get(stackIndex);
                if (ingredient.test(candidate)) {
                    remainingStacks.remove(stackIndex);
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                return false;
            }
        }

        if (remainingStacks.isEmpty()) {
            return true;
        }

        ItemStack containerCost = indexedRecipe.indexedContainerCost();
        if (containerCost.isEmpty()) {
            containerCost = CookingPotContainerCost.resolveForIndexedRecipe(
                    indexedRecipe.delegateRecipe(),
                    registryAccess,
                    indexedRecipe.targetKey()
            );
        }
        if (containerCost.isEmpty()) {
            return false;
        }

        int remainingContainerCount = containerCost.getCount();
        final ItemStack containerUnit = containerCost.copy();
        containerUnit.setCount(1);
        for (final ItemStack extraStack : remainingStacks) {
            if (remainingContainerCount <= 0) {
                break;
            }
            if (extraStack.isEmpty() || !MinecraftApiCompat.isSameItemSameData(extraStack, containerUnit)) {
                continue;
            }

            final int used = Math.min(remainingContainerCount, extraStack.getCount());
            remainingContainerCount -= used;
            extraStack.shrink(used);
        }

        if (remainingContainerCount > 0) {
            return false;
        }

        for (final ItemStack extraStack : remainingStacks) {
            if (!extraStack.isEmpty()) {
                return false;
            }
        }
        return true;
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
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_FARMERS_DELIGHT, "cooking_pot");
            case BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_DUNGEONS_DELIGHT, "monster_pot");
            case BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_MINERS_DELIGHT, "copper_pot");
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

        return LoaderApiCompat.resolveModDisplayName(modId)
                .map(Component::literal)
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

    private static boolean isSupportedRecipeScreen(final Object screen) {
        return screen != null && CFBH_RECIPE_SCREEN_CLASS_NAMES.contains(screen.getClass().getName());
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
            if (!(itemProvider instanceof MarkerProviderView markerProvider)
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
