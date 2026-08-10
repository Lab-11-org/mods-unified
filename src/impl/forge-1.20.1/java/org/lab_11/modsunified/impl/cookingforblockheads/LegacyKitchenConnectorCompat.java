package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.util.List;
import java.lang.reflect.Proxy;

public final class LegacyKitchenConnectorCompat {
    private static final ResourceLocation CAPABILITY_ID =
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_LAB11_UNIFIED, "kaleidoscope_kitchen_connector");
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
            final Object connector = Proxy.newProxyInstance(
                    connectorClass.getClassLoader(), new Class<?>[]{connectorClass}, (proxy, method, args) -> null);
            MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class,
                    (AttachCapabilitiesEvent<BlockEntity> event) -> attach(event, targets, capability, connector));
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static void attach(final AttachCapabilitiesEvent<? extends BlockEntity> event,
                               final List<CookingPotBridgeTarget> targets,
                               final Capability<?> capability,
                               final Object connectorInstance) {
        final BlockEntity blockEntity = event.getObject();
        final boolean isCookware = targets.stream().anyMatch(target ->
                (BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(target.targetKey())
                        || BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(target.targetKey()))
                        && target.matchesBlockEntity(blockEntity));
        if (!isCookware && !isLavaSink(blockEntity)) {
            return;
        }

        final LazyOptional<Object> connector = LazyOptional.of(() -> connectorInstance);
        event.addCapability(CAPABILITY_ID, new ICapabilityProvider() {
            @Override
            public <T> LazyOptional<T> getCapability(final Capability<T> requested, final Direction side) {
                return requested == capability ? connector.cast() : LazyOptional.empty();
            }
        });
        event.addListener(connector::invalidate);
    }

    private static boolean isLavaSink(final BlockEntity blockEntity) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
        return blockId != null
                && BridgeKeys.MOD_LAB11_UNIFIED.equals(blockId.getNamespace())
                && (BridgeKeys.BLOCK_LAVA_SINK.equals(blockId.getPath())
                || blockId.getPath().endsWith("_" + BridgeKeys.BLOCK_LAVA_SINK));
    }
}
