package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.util.Collection;

final class EnamelBasinOilProvider implements CfbhRuntime.KitchenItemProviderView {
    private static final String OIL_COUNT = "oil_count";
    private final BlockEntity basin;
    private final Object token;

    EnamelBasinOilProvider(final BlockEntity basin) {
        this.basin = basin;
        token = CfbhRuntime.newIngredientTokenProxy(new OilToken());
    }

    Object asKitchenItemProvider() {
        return CfbhRuntime.newKitchenItemProviderProxy(this);
    }

    @Override
    public Object findByIngredient(final Ingredient ingredient, final Collection<?> allocated) {
        final ItemStack oil = oilStack();
        return hasOil() && ingredient.test(oil) && !allocated.contains(token) ? token : null;
    }

    @Override
    public Object findByItem(final ItemStack stack, final Collection<?> allocated) {
        final ItemStack oil = oilStack();
        return hasOil() && MinecraftApiCompat.isSameItemSameData(oil, stack) && !allocated.contains(token) ? token : null;
    }

    private ItemStack oilStack() {
        return BuiltInRegistries.ITEM.getOptional(MinecraftApiCompat.resourceLocation(
                BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, BridgeKeys.ITEM_KALEIDOSCOPE_OIL
        )).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private boolean hasOil() {
        return oilCount() > 0;
    }

    private int oilCount() {
        final IntegerProperty property = oilProperty();
        return property == null ? 0 : basin.getBlockState().getValue(property);
    }

    private IntegerProperty oilProperty() {
        final var property = basin.getBlockState().getBlock().getStateDefinition().getProperty(OIL_COUNT);
        return property instanceof IntegerProperty integerProperty ? integerProperty : null;
    }

    private void changeOil(final int delta) {
        final var level = basin.getLevel();
        final IntegerProperty property = oilProperty();
        if (level == null || property == null) return;
        final BlockState state = basin.getBlockState();
        final int value = Math.max(property.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0),
                Math.min(state.getValue(property) + delta, property.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(32)));
        level.setBlock(basin.getBlockPos(), state.setValue(property, value), Block.UPDATE_ALL);
    }

    private final class OilToken implements CfbhRuntime.IngredientTokenView {
        @Override public ItemStack peek() { return hasOil() ? oilStack() : ItemStack.EMPTY; }
        @Override public ItemStack consume() { if (!hasOil()) return ItemStack.EMPTY; changeOil(-1); return oilStack(); }
        @Override public ItemStack restore(final ItemStack stack) { if (!stack.isEmpty()) changeOil(1); return ItemStack.EMPTY; }
    }
}
