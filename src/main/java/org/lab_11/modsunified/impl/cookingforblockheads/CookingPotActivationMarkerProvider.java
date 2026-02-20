package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CookingPotActivationMarkerProvider implements CfbhRuntime.KitchenItemProviderView,
        MarkerProviderView {

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

    public Object asKitchenItemProvider() {
        return CfbhRuntime.newKitchenItemProviderProxy(this, MarkerProviderView.class);
    }

    @Override
    public boolean isMarkerKey(final String markerKey) {
        return this.markerKey.equals(markerKey);
    }

    @Override
    public boolean isActiveForCurrentTableMarker() {
        return isActiveForCurrentTable();
    }

    @Override
    public boolean isTargetPotConnectedForCurrentTableMarker() {
        if (!isPotTargetMarker(markerKey)) {
            return false;
        }
        return CookingPotHeatBridge.isTargetPotConnectedForCookingTable(blockEntity, markerKey);
    }

    @Override
    public Object findByIngredient(final Ingredient ingredient, final Collection<?> allocatedTokens) {
        final MarkerEntry marker = markerForKey(markerKey);
        if (!isActiveForCurrentTable() || !ingredient.test(marker.stack)) {
            return null;
        }

        return marker.token;
    }

    @Override
    public Object findByItem(final ItemStack itemStack, final Collection<?> allocatedTokens) {
        final MarkerEntry marker = markerForKey(markerKey);
        if (!isActiveForCurrentTable() || !MinecraftApiCompat.isSameItemSameData(marker.stack, itemStack)) {
            return null;
        }

        return marker.token;
    }

    private boolean isActiveForCurrentTable() {
        if (isPotTargetMarker(markerKey)) {
            if (CookingPotProcessorCapability.isDirectlyAboveCookingTable(blockEntity)) {
                return true;
            }
            return CookingPotHeatBridge.isTargetPotConnectedForCookingTable(blockEntity, markerKey);
        }

        if (!requiresDirectlyAboveCookingTable) {
            return true;
        }
        return CookingPotProcessorCapability.isDirectlyAboveCookingTable(blockEntity);
    }

    private static boolean isPotTargetMarker(final String markerKey) {
        return BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT.equals(markerKey)
                || BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT.equals(markerKey)
                || BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT.equals(markerKey);
    }

    private static MarkerEntry markerForKey(final String markerKey) {
        return MARKERS_BY_KEY.computeIfAbsent(markerKey, CookingPotActivationMarkerProvider::createMarkerEntry);
    }

    private static MarkerEntry createMarkerEntry(final String markerKey) {
        final ItemStack markerStack = createMarkerStack(markerKey);
        final Object token = CfbhRuntime.newIngredientTokenProxy(new MarkerToken(markerStack));
        return new MarkerEntry(markerStack, Ingredient.of(markerStack), token);
    }

    private static ItemStack createMarkerStack(final String markerKey) {
        final ItemStack markerStack = new ItemStack(BridgeMarkerRegistry.markerItemFor(markerKey));
        MinecraftApiCompat.setCustomName(
                markerStack,
                Component.translatable(markerTranslationKey(markerKey))
        );
        return markerStack;
    }

    private static String markerTranslationKey(final String markerKey) {
        return "lab_11_mods_unified.marker." + markerKey;
    }

    private record MarkerEntry(ItemStack stack, Ingredient ingredient, Object token) {
    }

    private static final class MarkerToken implements CfbhRuntime.IngredientTokenView {
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
