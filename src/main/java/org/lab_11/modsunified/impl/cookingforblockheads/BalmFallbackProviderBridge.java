package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public final class BalmFallbackProviderBridge {
    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";
    private static final String REGISTER_FALLBACK_BLOCK_PROVIDER_METHOD = "registerFallbackBlockProvider";
    private static final Logger LOGGER = LogUtils.getLogger();

    private BalmFallbackProviderBridge() {
    }

    public static boolean registerFallbackKitchenProcessorProvider(final List<CookingPotBridgeTarget> targets) {
        final Class<?> kitchenItemProcessorClass = CfbhRuntime.kitchenItemProcessorClass();
        final Class<?> kitchenItemProviderClass = CfbhRuntime.kitchenItemProviderClass();
        if (kitchenItemProcessorClass == null || kitchenItemProviderClass == null) {
            return false;
        }

        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Object providers = balmClass.getMethod("getProviders").invoke(null);
            final Method registerFallbackProvider = providers.getClass().getMethod(
                    REGISTER_FALLBACK_BLOCK_PROVIDER_METHOD,
                    Class.class,
                    BiFunction.class
            );

            registerFallbackProvider.invoke(
                    providers,
                    kitchenItemProcessorClass,
                    (BiFunction<BlockEntity, Object, Object>) (blockEntity, direction) ->
                            createProcessorForTarget(blockEntity, targets)
            );

            registerFallbackProvider.invoke(
                    providers,
                    kitchenItemProviderClass,
                    (BiFunction<BlockEntity, Object, Object>) (blockEntity, direction) -> {
                        try {
                            if (CustomizeBlocks.isDungeonOvenBlockEntity(blockEntity)) {
                                return new CookingPotActivationMarkerProvider(
                                        blockEntity,
                                        BridgeKeys.MARKER_DUNGEON_OVEN,
                                        false
                                ).asKitchenItemProvider();
                            }
                            if (CustomizeBlocks.isLavaSinkBlockEntity(blockEntity)) {
                                return new CookingPotActivationMarkerProvider(
                                        blockEntity,
                                        BridgeKeys.MARKER_LAVA_SINK,
                                        false
                                ).asKitchenItemProvider();
                            }
                            if (CustomizeBlocks.isEnamelBasinBridge(blockEntity)) {
                                return new EnamelBasinOilProvider(blockEntity).asKitchenItemProvider();
                            }

                            return resolveTargetForBlockEntity(blockEntity, targets)
                                    .map(target -> new CookingPotActivationMarkerProvider(
                                            blockEntity,
                                            target.targetKey()
                                    ).asKitchenItemProvider())
                                    .orElse(null);
                        } catch (RuntimeException e) {
                            LOGGER.warn("Failed to create runtime KitchenItemProvider marker proxy.", e);
                            return null;
                        }
                    }
            );
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object createProcessorForTarget(final BlockEntity blockEntity,
                                                   final List<CookingPotBridgeTarget> targets) {
        for (final CookingPotBridgeTarget target : targets) {
            if (!target.matchesBlockEntity(blockEntity)) {
                continue;
            }

            final var recipeTypeOptional = target.resolveRecipeType();
            if (recipeTypeOptional.isEmpty()) {
                continue;
            }

            final java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>> recipeTypes = new java.util.LinkedHashSet<>();
            for (final CookingPotBridgeTarget candidate : targets) {
                if (candidate.matchesBlockEntity(blockEntity)) {
                    candidate.resolveRecipeType().ifPresent(recipeTypes::add);
                }
            }
            return CookingPotProcessorCapability.createProcessor(
                    blockEntity,
                    Set.copyOf(recipeTypes),
                    target.requiredMarkerKeys(),
                    target.targetKey()
            );
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
