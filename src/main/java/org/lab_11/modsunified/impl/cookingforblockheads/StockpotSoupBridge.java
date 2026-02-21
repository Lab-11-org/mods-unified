package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.util.List;

final class StockpotSoupBridge {
    private static final String CFBH_SINK_PROVIDER_CLASS = "net.blay09.mods.cookingforblockheads.block.entity.SinkBlockEntity$SinkItemProvider";
    private static final String CFBH_SINK_PROVIDER_CLASS_LEGACY = "net.blay09.mods.cookingforblockheads.tile.SinkBlockEntity$SinkItemProvider";
    private static final String SYNTHETIC_SINK_TOKEN_NAME = "lab11.stockpot.sink_water";
    private static volatile Object syntheticSinkToken;

    private StockpotSoupBridge() {
    }

    static boolean isSinkItemProvider(final Object itemProvider) {
        if (itemProvider == null) {
            return false;
        }
        final String providerClassName = itemProvider.getClass().getName();
        if (CFBH_SINK_PROVIDER_CLASS.equals(providerClassName)
                || CFBH_SINK_PROVIDER_CLASS_LEGACY.equals(providerClassName)) {
            return true;
        }
        final List<?> combinedProviders = CfbhRuntime.tryGetCombinedProviders(itemProvider);
        if (combinedProviders.isEmpty()) {
            return false;
        }
        for (final Object nestedProvider : combinedProviders) {
            if (isSinkItemProvider(nestedProvider)) {
                return true;
            }
        }
        return false;
    }

    static Object syntheticSinkSoupToken() {
        final Object cached = syntheticSinkToken;
        if (cached != null) {
            return cached;
        }
        final ItemStack markerStack = syntheticSinkSoupMarkerStack();
        final Object created = CfbhRuntime.newIngredientTokenProxy(new CfbhRuntime.IngredientTokenView() {
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
        });
        syntheticSinkToken = created;
        return created;
    }

    static boolean isSyntheticSinkSoupMarker(final ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && MinecraftApiCompat.isSameItemSameData(stack, syntheticSinkSoupMarkerStack());
    }

    private static ItemStack syntheticSinkSoupMarkerStack() {
        final ItemStack markerStack = new ItemStack(Items.BARRIER);
        MinecraftApiCompat.setCustomName(markerStack, Component.literal(SYNTHETIC_SINK_TOKEN_NAME));
        return markerStack;
    }
}
