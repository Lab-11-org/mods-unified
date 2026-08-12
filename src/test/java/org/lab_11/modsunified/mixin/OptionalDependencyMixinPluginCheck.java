package org.lab_11.modsunified.mixin;

import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;

import java.lang.reflect.Method;

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
        checkEarlyModDiscovery();
    }

    private static void check(final String mixinName, final String expected) {
        final String actual = OptionalMixinTargetMods.forMixin(mixinName);
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(mixinName + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void checkEarlyModDiscovery() {
        try {
            final Method method = LoaderApiCompat.class.getDeclaredMethod("hasModFile", Object.class, String.class);
            method.setAccessible(true);
            if (!Boolean.TRUE.equals(method.invoke(null, new EarlyModList(), BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS))) {
                throw new AssertionError("Early NeoForge mod discovery must enable optional mixins");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static final class EarlyModList {
        public Object getModFileById(final String modId) {
            return BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS.equals(modId) ? this : null;
        }
    }
}
