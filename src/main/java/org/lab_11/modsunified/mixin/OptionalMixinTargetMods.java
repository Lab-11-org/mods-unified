package org.lab_11.modsunified.mixin;

import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;

final class OptionalMixinTargetMods {
    private OptionalMixinTargetMods() {
    }

    static String forMixin(final String mixinName) {
        if (mixinName.startsWith("CookingPot")) return BridgeKeys.MOD_FARMERS_DELIGHT;
        if (mixinName.startsWith("CopperPot")) return BridgeKeys.MOD_MINERS_DELIGHT;
        if (mixinName.startsWith("MonsterPot")) return BridgeKeys.MOD_DUNGEONS_DELIGHT;
        if (mixinName.startsWith("KaleidoscopePot")
                || mixinName.startsWith("Stockpot")
                || mixinName.startsWith("EnamelBasin")) {
            return BridgeKeys.MOD_KALEIDOSCOPE_COOKERY;
        }
        return null;
    }
}
