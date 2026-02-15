package org.lab_11.modsunified.impl;

import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotActivationMarkerProvider implements KitchenItemProvider {
    private static final Map<String, MarkerEntry> MARKERS_BY_KEY = new ConcurrentHashMap<>();

    private final BlockEntity blockEntity;
    private final String markerKey;
    private final boolean requiresDirectlyAboveCookingTable;

    public CookingPotActivationMarkerProvider(final BlockEntity blockEntity, final String markerKey) {
        this(blockEntity, markerKey, true);
    }

    public CookingPotActivationMarkerProvider(final BlockEntity blockEntity,
                                              final String markerKey,
                                              final boolean requiresDirectlyAboveCookingTable) {
        this.blockEntity = blockEntity;
        this.markerKey = markerKey;
        this.requiresDirectlyAboveCookingTable = requiresDirectlyAboveCookingTable;
    }

    public static Ingredient markerIngredient(final String markerKey) {
        return markerForKey(markerKey).ingredient;
    }

    public boolean isMarkerKey(final String markerKey) {
        return this.markerKey.equals(markerKey);
    }

    boolean isActiveForCurrentTableMarker() {
        return isActiveForCurrentTable();
    }

    @Override
    public IngredientToken findIngredient(final Ingredient ingredient,
                                          final Collection<IngredientToken> ingredientTokens,
                                          final CacheHint cacheHint) {
        final MarkerEntry marker = markerForKey(markerKey);
        if (!isActiveForCurrentTable() || !ingredient.test(marker.stack)) {
            return null;
        }

        return marker.token;
    }

    @Override
    public IngredientToken findIngredient(final ItemStack itemStack,
                                          final Collection<IngredientToken> ingredientTokens,
                                          final CacheHint cacheHint) {
        final MarkerEntry marker = markerForKey(markerKey);
        if (!isActiveForCurrentTable() || !ItemStack.isSameItemSameComponents(marker.stack, itemStack)) {
            return null;
        }

        return marker.token;
    }

    @Override
    public CacheHint getCacheHint(final IngredientToken ingredientToken) {
        return CacheHint.NONE;
    }

    private boolean isActiveForCurrentTable() {
        if (!requiresDirectlyAboveCookingTable) {
            return true;
        }
        return CookingPotProcessorCapability.isDirectlyAboveCookingTable(blockEntity);
    }

    private static MarkerEntry markerForKey(final String markerKey) {
        return MARKERS_BY_KEY.computeIfAbsent(markerKey, CookingPotActivationMarkerProvider::createMarkerEntry);
    }

    private static MarkerEntry createMarkerEntry(final String markerKey) {
        final ItemStack markerStack = createMarkerStack(markerKey);
        return new MarkerEntry(markerStack, Ingredient.of(markerStack), new MarkerToken(markerStack));
    }

    private static ItemStack createMarkerStack(final String markerKey) {
        final ItemStack markerStack = new ItemStack(markerItem(markerKey));
        markerStack.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable(markerTranslationKey(markerKey))
        );
        return markerStack;
    }

    private static String markerTranslationKey(final String markerKey) {
        return "lab_11_mods_unified.marker." + markerKey;
    }

    private static Item markerItem(final String markerKey) {
        if ("dungeonsdelight_monster_pot".equals(markerKey)) {
            return Items.STRUCTURE_BLOCK;
        }
        if ("minersdelight_copper_pot".equals(markerKey)) {
            return Items.JIGSAW;
        }
        if ("dungeon_oven".equals(markerKey)) {
            return Items.SMOKER;
        }
        return Items.BARRIER;
    }

    private record MarkerEntry(ItemStack stack, Ingredient ingredient, IngredientToken token) {
    }

    private static final class MarkerToken implements IngredientToken {
        private final ItemStack markerStack;

        private MarkerToken(final ItemStack markerStack) {
            this.markerStack = markerStack;
        }

        @Override
        public ItemStack peek() {
            return markerStack.copy();
        }

        @Override
        public ItemStack consume() {
            return markerStack.copy();
        }

        @Override
        public ItemStack restore(final ItemStack itemStack) {
            return ItemStack.EMPTY;
        }
    }
}
