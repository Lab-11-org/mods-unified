package org.lab_11.modsunified.mixin;

import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;

final class OptionalDependencyMixinPluginCheck {
    private OptionalDependencyMixinPluginCheck() {
    }

    public static void main(final String[] args) {
        check("CookingPotBlockMixin", BridgeKeys.MOD_FARMERS_DELIGHT);
        check("CopperPotBlockMixin", BridgeKeys.MOD_MINERS_DELIGHT);
        check("MonsterPotBlockMixin", BridgeKeys.MOD_DUNGEONS_DELIGHT);
        check("KaleidoscopePotBlockMixin", BridgeKeys.MOD_KALEIDOSCOPE_COOKERY);
        check("StockpotBlockEntityRenderMixin", BridgeKeys.MOD_KALEIDOSCOPE_COOKERY);
        check("EnamelBasinBlockMixin", BridgeKeys.MOD_KALEIDOSCOPE_COOKERY);
        check("KitchenMenuMixin", null);
    }

    private static void check(final String mixinName, final String expected) {
        final String actual = OptionalMixinTargetMods.forMixin(mixinName);
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(mixinName + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
