package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;

public final class LegacyKitchenConnectorCompat {
    private static final ResourceLocation CAPABILITY_ID =
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_LAB11_UNIFIED, "kaleidoscope_kitchen_connector");
    private static final ResourceLocation ITEM_PROVIDER_CAPABILITY_ID =
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_LAB11_UNIFIED, "tagged_kitchen_item_provider");
    private static final TagKey<Block> KITCHEN_ITEM_PROVIDERS = TagKey.create(
            Registries.BLOCK,
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS, "kitchen_item_providers")
    );

    private LegacyKitchenConnectorCompat() {
    }

    public static boolean register(final List<CookingPotBridgeTarget> targets) {
        try {
            final Class<?> balmClass = Class.forName("net.blay09.mods.balm.api.Balm");
            final Class<?> connectorClass = Class.forName(
                    "net.blay09.mods.cookingforblockheads.api.capability.IKitchenConnector");
            final Object providers = balmClass.getMethod("getProviders").invoke(null);
            final Object value = providers.getClass().getMethod("getCapability", Class.class)
                    .invoke(providers, connectorClass);
            if (!(value instanceof Capability<?> capability)) {
                return false;
            }
            final Object itemCapabilityValue = providers.getClass().getMethod("getCapability", Class.class)
                    .invoke(providers, Class.forName(
                            "net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider"));
            if (!(itemCapabilityValue instanceof Capability<?> itemCapability)) {
                return false;
            }
            final Constructor<?> itemProviderConstructor = Class.forName(
                    "net.blay09.mods.cookingforblockheads.compat.CompatCapabilityLoader$KitchenItemCapabilityProvider"
            ).getDeclaredConstructor(BlockEntity.class);
            itemProviderConstructor.setAccessible(true);
            final Object connector = Proxy.newProxyInstance(
                    connectorClass.getClassLoader(), new Class<?>[]{connectorClass}, (proxy, method, args) -> null);
            MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, EventPriority.LOWEST,
                    (AttachCapabilitiesEvent<BlockEntity> event) -> attach(
                            event,
                            targets,
                            capability,
                            itemCapability,
                            connector,
                            itemProviderConstructor
                    ));
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void attach(final AttachCapabilitiesEvent<? extends BlockEntity> event,
                               final List<CookingPotBridgeTarget> targets,
                               final Capability<?> capability,
                               final Capability<?> itemCapability,
                               final Object connectorInstance,
                               final Constructor<?> itemProviderConstructor) {
        final BlockEntity blockEntity = event.getObject();
        // Forge gathers capabilities from BlockEntity's constructor. In particular,
        // Central Kitchen's delegating entities have not assigned their delegate yet.
        // Only inspect state when the kitchen asks for a capability after construction.
        final DeferredTaggedItemProvider itemProvider = new DeferredTaggedItemProvider(
                blockEntity, itemCapability, itemProviderConstructor);
        event.addCapability(ITEM_PROVIDER_CAPABILITY_ID, itemProvider);
        event.addListener(itemProvider::invalidate);

        final LazyOptional<Object> connector = LazyOptional.of(() -> connectorInstance);
        event.addCapability(CAPABILITY_ID, new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(final Capability<T> requested, final Direction side) {
                if (requested != capability) {
                    return LazyOptional.empty();
                }
                final boolean isCookware = targets.stream().anyMatch(target ->
                        (BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(target.targetKey())
                                || BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(target.targetKey()))
                                && target.matchesBlockEntity(blockEntity));
                return isCookware || isLavaSink(blockEntity)
                        || CookingPotHeatBridge.isSupportedStove(blockEntity.getBlockState())
                        ? connector.cast() : LazyOptional.empty();
            }
        });
        event.addListener(connector::invalidate);
    }

    private static final class DeferredTaggedItemProvider implements ICapabilityProvider {
        private final BlockEntity blockEntity;
        private final Capability<?> capability;
        private final Constructor<?> constructor;
        private LazyOptional<?> resolved;
        private boolean invalidated;

        private DeferredTaggedItemProvider(final BlockEntity blockEntity,
                                           final Capability<?> capability,
                                           final Constructor<?> constructor) {
            this.blockEntity = blockEntity;
            this.capability = capability;
            this.constructor = constructor;
        }

        @Override
        public <T> LazyOptional<T> getCapability(final Capability<T> requested, final Direction side) {
            if (invalidated || requested != capability || !blockEntity.getBlockState().is(KITCHEN_ITEM_PROVIDERS)) {
                return LazyOptional.empty();
            }
            if (resolved == null) {
                try {
                    final ICapabilityProvider delegate = (ICapabilityProvider) constructor.newInstance(blockEntity);
                    resolved = delegate.getCapability(capability, null);
                } catch (ReflectiveOperationException ignored) {
                    return LazyOptional.empty();
                }
            }
            return resolved.cast();
        }

        private void invalidate() {
            invalidated = true;
            if (resolved != null) {
                resolved.invalidate();
            }
        }
    }

    private static boolean isLavaSink(final BlockEntity blockEntity) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        return blockId != null
                && BridgeKeys.MOD_LAB11_UNIFIED.equals(blockId.getNamespace())
                && (BridgeKeys.BLOCK_LAVA_SINK.equals(blockId.getPath())
                || blockId.getPath().endsWith("_" + BridgeKeys.BLOCK_LAVA_SINK));
    }
}
