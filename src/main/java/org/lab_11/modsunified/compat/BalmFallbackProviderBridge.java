package org.lab_11.modsunified.compat;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.neoforge.provider.NeoForgeBalmProviders;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

public final class BalmFallbackProviderBridge {
    private BalmFallbackProviderBridge() {
    }

    public static boolean registerFallbackKitchenProcessorProvider() {
        final var providers = Balm.getProviders();
        if (!(providers instanceof NeoForgeBalmProviders neoForgeProviders)) {
            return false;
        }

        final KitchenItemProcessor processor = FDCookingPotProcessorCapability.getProcessor();
        neoForgeProviders.registerFallbackBlockProvider(
                KitchenItemProcessor.class,
                (blockEntity, direction) -> blockEntity instanceof CookingPotBlockEntity ? processor : null
        );
        return true;
    }
}
