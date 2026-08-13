package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class CookingPotContainerTooltipBridge {
    private static final String COOKING_FOR_BLOCKHEADS_MOD_ID = BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS;
    private static final String KITCHEN_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.KitchenScreen";
    private static final String RECIPE_BOOK_SCREEN_CLASS = "net.blay09.mods.cookingforblockheads.client.gui.screen.RecipeBookScreen";
    private static final Set<String> CFBH_RECIPE_SCREEN_CLASS_NAMES = Set.of(
            KITCHEN_SCREEN_CLASS,
            RECIPE_BOOK_SCREEN_CLASS
    );
    private static final String CRAFT_MATRIX_FAKE_SLOT_CLASS = "net.blay09.mods.cookingforblockheads.menu.slot.CraftMatrixFakeSlot";
    private static final String CFBH_TOOLTIP_CLICK_TO_UNLOCK_KEY = "tooltip.cookingforblockheads.click_to_unlock";
    private static final String CFBH_TOOLTIP_CLICK_TO_LOCK_KEY = "tooltip.cookingforblockheads.click_to_lock";
    private static final String CFBH_TOOLTIP_SCROLL_TO_SWITCH_KEY = "tooltip.cookingforblockheads.scroll_to_switch";
    private static final String TOOLTIP_CONTAINER_COST_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_cost";
    private static final String TOOLTIP_CONTAINER_ENTRY_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_entry";
    private static final String TOOLTIP_CONTAINER_NOT_ENOUGH_KEY = "lab_11_mods_unified.tooltip.cooking_table.container_not_enough";
    private static final String TOOLTIP_COOKS_IN_FROM_KEY = "lab_11_mods_unified.tooltip.cooking_table.cooks_in_from";
    private static final String TOOLTIP_POT_NOT_CONNECTED_KEY = "lab_11_mods_unified.tooltip.cooking_table.pot_not_connected";
    private static final String TOOLTIP_STOCKPOT_SOUP_BASE_KEY = "lab_11_mods_unified.tooltip.cooking_table.stockpot_soup_base";
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

        final Object menu = CfbhRuntime.invokeNoArg(screen, "getMenu");
        final Object selectedRecipeWithStatus = CfbhRuntime.invokeNoArg(menu, "getSelectedRecipe");
        if (selectedRecipeWithStatus == null) {
            return;
        }

        removeIndexedRecipeLockHints(event, screen, selectedRecipeWithStatus);

        final Object recipeIdObject = CfbhRuntime.invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (!(recipeIdObject instanceof ResourceLocation recipeId)) {
            return;
        }

        final Recipe<?> recipe = resolveRecipeById(minecraft, recipeId);
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

        appendStockpotSoupBaseTooltip(event, recipe);

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

        // Only show "Not Enough!" when the server-computed recipe status confirms
        // the recipe cannot be crafted. Client-side provider queries are unreliable
        // for block entity inventories (e.g. FD cabinets) that don't sync to client.
        final boolean serverSaysCraftable = Boolean.TRUE.equals(CfbhRuntime.invokeNoArg(selectedRecipeWithStatus, "canCraft"));
        if (!serverSaysCraftable && isContainerMissing(menu, containerCost, minecraft.player)) {
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
        return visitMissingRequirements(
                menu,
                recipe,
                player,
                tooltipKey -> appendRedTooltip(event, tooltipKey),
                false
        );
    }

    private static boolean visitMissingRequirements(final Object menu,
                                                    final Recipe<?> recipe,
                                                    final Player player,
                                                    final Consumer<String> tooltipConsumer,
                                                    final boolean stopAfterFirstMissingMarker) {
        if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
            return false;
        }

        if (menu == null || player == null) {
            return false;
        }

        final Object kitchen = CfbhRuntime.invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return false;
        }

        final List<?> itemProviders = CfbhRuntime.itemProviders(kitchen, player);
        if (itemProviders == null || itemProviders.isEmpty()) {
            tooltipConsumer.accept(TOOLTIP_POT_NOT_CONNECTED_KEY);
            return true;
        }

        if (!CfbhRuntime.ingredientLookupAvailable()) {
            return false;
        }

        if (!isTargetConnected(itemProviders, indexedRecipe.targetKey())) {
            tooltipConsumer.accept(BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(indexedRecipe.targetKey())
                    ? BridgeKeys.TOOLTIP_MISSING_DUNGEON_OVEN
                    : TOOLTIP_POT_NOT_CONNECTED_KEY);
            return true;
        }

        if (BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(indexedRecipe.targetKey())
                || indexedRecipe.requiredMarkerKeys().isEmpty()) {
            return false;
        }

        boolean missing = false;
        for (final String requiredMarkerKey : indexedRecipe.requiredMarkerKeys()) {
            if (hasRequiredMarker(itemProviders, requiredMarkerKey)) {
                continue;
            }

            BridgeMarkerRegistry.missingRequirementTooltipKey(requiredMarkerKey).ifPresent(tooltipConsumer);
            missing = true;
            if (stopAfterFirstMissingMarker) {
                return true;
            }
        }
        return missing;
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

    private static void appendStockpotSoupBaseTooltip(final ItemTooltipEvent event, final Recipe<?> recipe) {
        if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe)) {
            return;
        }
        if (!BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexedRecipe.targetKey())) {
            return;
        }
        final ResourceLocation soupBaseId = StockpotSoupBridge.resolveRequiredSoupBaseId(recipe);
        final ItemStack soupBaseStack = StockpotSoupBridge.soupBaseBucketStack(soupBaseId);
        final Component soupBaseName = soupBaseStack.isEmpty()
                ? Component.literal(soupBaseId.toString())
                : soupBaseStack.getHoverName();
        event.getToolTip().add(
                Component.translatable(TOOLTIP_STOCKPOT_SOUP_BASE_KEY, soupBaseName.copy().withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GRAY)
        );
    }

    private static void removeIndexedRecipeLockHints(final ItemTooltipEvent event,
                                                     final Object screen,
                                                     final Object selectedRecipeWithStatus) {
        if (!isIndexedRecipeSelection(selectedRecipeWithStatus)) {
            return;
        }

        final Object hoveredSlot = CfbhRuntime.invokeNoArg(screen, "getHoveredSlot");
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
        final Object recipeIdObject = CfbhRuntime.invokeNoArg(selectedRecipeWithStatus, "recipeId");
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

        final Object menu = CfbhRuntime.invokeNoArg(screen, "getMenu");
        final Object selectedRecipeWithStatus = CfbhRuntime.invokeNoArg(menu, "getSelectedRecipe");
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
                || showsMissingRequirementsLine(menu, recipe, minecraft.player)
                || showsSoupBaseLine(recipe);
    }

    private static Recipe<?> resolveSelectedRecipe(final Minecraft minecraft,
                                                   final Object selectedRecipeWithStatus) {
        final Object recipeIdObject = CfbhRuntime.invokeNoArg(selectedRecipeWithStatus, "recipeId");
        if (!(recipeIdObject instanceof ResourceLocation recipeId)) {
            return null;
        }

        return resolveRecipeById(minecraft, recipeId);
    }

    private static Recipe<?> resolveRecipeById(final Minecraft minecraft, final ResourceLocation recipeId) {
        if (minecraft.level == null) {
            return null;
        }

        final var recipeHolder = minecraft.level.getRecipeManager().byKey(recipeId).orElse(null);
        if (recipeHolder != null) {
            return recipeHolder.value();
        }

        return CookingPotRecipeIndexer.findIndexedRecipe(recipeId);
    }

    private static boolean showsContainerLine(final Recipe<?> recipe) {
        return !resolveContainerCost(recipe).isEmpty();
    }

    private static boolean showsSoupBaseLine(final Recipe<?> recipe) {
        return recipe instanceof CookingPotIndexedRecipe indexedRecipe
                && BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexedRecipe.targetKey());
    }

    private static boolean showsMissingRequirementsLine(final Object menu,
                                                        final Recipe<?> recipe,
                                                        final Player player) {
        return visitMissingRequirements(menu, recipe, player, tooltipKey -> {}, true);
    }

    private static Component resolvePotName(final String targetKey) {
        final ResourceLocation blockId = switch (targetKey) {
            case BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_FARMERS_DELIGHT, "cooking_pot");
            case BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_DUNGEONS_DELIGHT, "monster_pot");
            case BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_MINERS_DELIGHT, "copper_pot");
            case BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "pot");
            case BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT ->
                    MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "stockpot");
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
            case BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT -> BridgeKeys.MOD_KALEIDOSCOPE_COOKERY;
            case BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT -> BridgeKeys.MOD_KALEIDOSCOPE_COOKERY;
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
                                             final String requiredMarkerKey) {
        // Requirement markers are virtual tokens, so a successful find means the kitchen is fully connected.
        final Ingredient markerIngredient = CookingPotActivationMarkerProvider.markerIngredient(requiredMarkerKey);
        final Object markerToken = findIngredientToken(itemProviders, markerIngredient, List.of());
        return markerToken != null;
    }

    private static boolean isContainerMissing(final Object menu,
                                              final ItemStack containerCost,
                                              final Player player) {
        if (menu == null || containerCost.isEmpty() || player == null) {
            return false;
        }

        final Object kitchen = CfbhRuntime.invokeNoArg(menu, "getKitchen");
        if (kitchen == null) {
            return false;
        }

        final List<?> itemProviders = CfbhRuntime.itemProviders(kitchen, player);
        if (itemProviders == null || itemProviders.isEmpty()) {
            return false;
        }

        if (!CfbhRuntime.ingredientLookupAvailable()) {
            return false;
        }

        // Use item-only matching to avoid strict component comparison issues
        final Ingredient expectedContainer = Ingredient.of(containerCost.getItem());
        final Collection<Object> allocatedTokens = new ArrayList<>();

        int remaining = containerCost.getCount();
        while (remaining > 0) {
            final Object token = findIngredientToken(itemProviders, expectedContainer, allocatedTokens);
            if (token == null) {
                break;
            }
            allocatedTokens.add(token);
            remaining -= peekTokenStackCount(token);
        }

        return remaining > 0;
    }

    private static Object findIngredientToken(final List<?> itemProviders,
                                              final Ingredient ingredient,
                                              final Collection<?> allocatedTokens) {
        for (final Object itemProvider : itemProviders) {
            final Object token = CfbhRuntime.findIngredientToken(itemProvider, ingredient, allocatedTokens);
            if (token != null) {
                return token;
            }
        }
        return null;
    }

    private static int peekTokenStackCount(final Object token) {
        if (token == null) {
            return 1;
        }

        return Math.max(1, CfbhRuntime.peekIngredientToken(token).getCount());
    }

    private static boolean isSupportedRecipeScreen(final Object screen) {
        return screen != null && CFBH_RECIPE_SCREEN_CLASS_NAMES.contains(screen.getClass().getName());
    }

    private static void appendRedTooltip(final ItemTooltipEvent event, final String tooltipKey) {
        event.getToolTip().add(Component.translatable(tooltipKey).withStyle(ChatFormatting.RED));
    }

    private static boolean isTargetConnected(final List<?> itemProviders, final String targetKey) {
        boolean foundTargetProvider = false;
        for (final Object itemProvider : itemProviders) {
            if (!(itemProvider instanceof MarkerProviderView markerProvider)
                    || !markerProvider.isMarkerKey(targetKey)) {
                continue;
            }

            foundTargetProvider = true;
            if (markerProvider.isTargetPotConnectedForCurrentTableMarker()) {
                return true;
            }
        }

        return !foundTargetProvider && hasRequiredMarker(itemProviders, targetKey);
    }
}
