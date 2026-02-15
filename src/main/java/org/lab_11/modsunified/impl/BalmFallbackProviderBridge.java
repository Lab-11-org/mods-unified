package org.lab_11.modsunified.impl;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.neoforge.provider.NeoForgeBalmProviders;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Set;

public final class BalmFallbackProviderBridge {
    private BalmFallbackProviderBridge() {
    }

    public static boolean registerFallbackKitchenProcessorProvider(final List<CookingPotBridgeTarget> targets) {
        final var providers = Balm.getProviders();
        if (!(providers instanceof NeoForgeBalmProviders neoForgeProviders)) {
            return false;
        }

        neoForgeProviders.registerFallbackBlockProvider(
                KitchenItemProcessor.class,
                (blockEntity, direction) -> createProcessorForTarget(blockEntity, targets)
        );

        neoForgeProviders.registerFallbackBlockProvider(
                KitchenItemProvider.class,
                (blockEntity, direction) ->
                        resolveTargetForBlockEntity(blockEntity, targets)
                                .map(target -> new CookingPotActivationMarkerProvider(blockEntity, target.targetKey()))
                                .orElse(null)
        );
        return true;
    }

    private static KitchenItemProcessor createProcessorForTarget(final BlockEntity blockEntity,
                                                                 final List<CookingPotBridgeTarget> targets) {
        for (final CookingPotBridgeTarget target : targets) {
            if (!target.matchesBlockEntity(blockEntity)) {
                continue;
            }

            final var recipeTypeOptional = target.resolveRecipeType();
            if (recipeTypeOptional.isEmpty()) {
                continue;
            }

            return CookingPotProcessorCapability.createProcessor(blockEntity, Set.of(recipeTypeOptional.get()));
        }

        return null;
    }

    private static java.util.Optional<CookingPotBridgeTarget> resolveTargetForBlockEntity(final BlockEntity blockEntity,
                                                                                           final List<CookingPotBridgeTarget> targets) {
        for (final CookingPotBridgeTarget target : targets) {
            if (target.matchesBlockEntity(blockEntity)) {
                return java.util.Optional.of(target);
            }
        }

        return java.util.Optional.empty();
    }
}
