package org.lab_11.modsunified.compat;

import net.blay09.mods.cookingforblockheads.api.CacheHint;
import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

import java.util.Collection;

public final class FDCookingPotActivationMarkerProvider implements KitchenItemProvider {
    private static final ItemStack MARKER_STACK = createMarkerStack();
    private static final Ingredient MARKER_INGREDIENT = Ingredient.of(MARKER_STACK);
    private static final IngredientToken MARKER_TOKEN = new MarkerToken();

    private final CookingPotBlockEntity cookingPotBlockEntity;

    public FDCookingPotActivationMarkerProvider(final CookingPotBlockEntity cookingPotBlockEntity) {
        this.cookingPotBlockEntity = cookingPotBlockEntity;
    }

    public static Ingredient markerIngredient() {
        return MARKER_INGREDIENT;
    }

    @Override
    public IngredientToken findIngredient(final Ingredient ingredient,
                                          final Collection<IngredientToken> ingredientTokens,
                                          final CacheHint cacheHint) {
        if (!isActiveForCurrentTable() || !ingredient.test(MARKER_STACK)) {
            return null;
        }

        return MARKER_TOKEN;
    }

    @Override
    public IngredientToken findIngredient(final ItemStack itemStack,
                                          final Collection<IngredientToken> ingredientTokens,
                                          final CacheHint cacheHint) {
        if (!isActiveForCurrentTable() || !ItemStack.isSameItemSameComponents(MARKER_STACK, itemStack)) {
            return null;
        }

        return MARKER_TOKEN;
    }

    @Override
    public CacheHint getCacheHint(final IngredientToken ingredientToken) {
        return CacheHint.NONE;
    }

    private boolean isActiveForCurrentTable() {
        return FDCookingPotProcessorCapability.isDirectlyAboveCookingTable(cookingPotBlockEntity);
    }

    private static ItemStack createMarkerStack() {
        final ItemStack markerStack = new ItemStack(Items.BARRIER);
        markerStack.set(DataComponents.CUSTOM_NAME, Component.literal("lab_11_mods_unified:fd_cooking_pot_activation_marker"));
        return markerStack;
    }

    private static final class MarkerToken implements IngredientToken {
        @Override
        public ItemStack peek() {
            return MARKER_STACK.copy();
        }

        @Override
        public ItemStack consume() {
            return MARKER_STACK.copy();
        }

        @Override
        public ItemStack restore(final ItemStack itemStack) {
            return ItemStack.EMPTY;
        }
    }
}
