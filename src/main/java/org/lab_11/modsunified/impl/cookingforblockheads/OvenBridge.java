package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.lab_11.modsunified.Unifiled;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class OvenBridge {
    private static final String ACTIVE_PROPERTY = "active";
    private static final String DUNGEON_OVEN_PATH = "dungeon_oven";
    private static final TagKey<Block> LAB11_CFBH_OVEN_BLOCK_TAG = TagKey.create(
            Registries.BLOCK,
            MinecraftApiCompat.resourceLocation(Unifiled.MOD_ID, "cfbh_ovens")
    );

    private static final String[] CFBH_OVEN_BLOCK_ENTITY_CLASS_CANDIDATES = {
            "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity",
            "net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity"
    };

    private static final String OVEN_FIELD_FURNACE_BURN_TIME = "furnaceBurnTime";
    private static final String OVEN_FIELD_CURRENT_ITEM_BURN_TIME = "currentItemBurnTime";
    private static final String OVEN_FIELD_IS_DIRTY = "isDirty";
    private static final String OVEN_GET_FUEL_CONTAINER_METHOD = "getFuelContainer";

    private static final String OVEN_FIELD_ENERGY_STORAGE = "energyStorage";
    private static final int OVEN_ENERGY_BURN_TARGET = 200;

    private static volatile Class<?> cachedCfbhOvenBlockEntityClass;
    private static volatile boolean cfbhOvenBlockEntityLookupFailed;

    private OvenBridge() {
    }

    // -- Public API --

    public static boolean isCfbhOven(final BlockEntity ovenBlockEntity) {
        if (ovenBlockEntity == null) {
            return false;
        }
        final Class<?> cfbhOvenClass = resolveCfbhOvenBlockEntityClass();
        return cfbhOvenClass != null && cfbhOvenClass.isInstance(ovenBlockEntity);
    }

    public static boolean matchesPolicy(final Level level,
                                        final BlockPos ovenPos,
                                        final BlockState ovenState,
                                        final OvenPolicy ovenPolicy) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(ovenState.getBlock());
        if (ovenPolicy == OvenPolicy.DUNGEON_OVEN_ONLY) {
            return isDungeonOvenBlockId(blockId);
        }
        if (isDungeonOvenBlockId(blockId) || isTaggedAsCfbhOven(ovenState)) {
            return true;
        }
        return level != null && ovenPos != null && isCfbhOven(level.getBlockEntity(ovenPos));
    }

    public static boolean isBurning(final Level level, final BlockPos ovenPos) {
        final BlockEntity ovenBlockEntity = level.getBlockEntity(ovenPos);
        if (!isCfbhOven(ovenBlockEntity)) {
            return false;
        }
        if (readIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME) > 0) {
            return true;
        }
        // Client-side animation checks can run before reflective burn-time fields are visible.
        return level.isClientSide && isActive(level.getBlockState(ovenPos));
    }

    public static boolean ignite(final Level level,
                                 final BlockPos ovenPos,
                                 final BlockState ovenState) {
        final BlockEntity ovenBlockEntity = level.getBlockEntity(ovenPos);
        if (!isCfbhOven(ovenBlockEntity)) {
            return false;
        }
        if (readIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME) > 0) {
            return true;
        }
        if (igniteFromEnergy(ovenBlockEntity, level, ovenPos, ovenState)) {
            return true;
        }
        return igniteFromFuel(ovenBlockEntity, level, ovenPos, ovenState);
    }

    public static void syncActiveVisual(final Level level,
                                        final BlockPos ovenPos,
                                        final BlockState ovenState) {
        if (level == null || ovenPos == null || ovenState == null) {
            return;
        }
        final boolean burning = isBurning(level, ovenPos);
        setActiveProperty(level, ovenPos, ovenState, burning);
    }

    // -- Package-private --

    static Class<?> resolveCfbhOvenBlockEntityClass() {
        final Class<?> cached = cachedCfbhOvenBlockEntityClass;
        if (cached != null) {
            return cached;
        }
        if (cfbhOvenBlockEntityLookupFailed) {
            return null;
        }

        synchronized (OvenBridge.class) {
            if (cachedCfbhOvenBlockEntityClass != null) {
                return cachedCfbhOvenBlockEntityClass;
            }
            if (cfbhOvenBlockEntityLookupFailed) {
                return null;
            }

            for (final String candidateClassName : CFBH_OVEN_BLOCK_ENTITY_CLASS_CANDIDATES) {
                try {
                    cachedCfbhOvenBlockEntityClass = Class.forName(candidateClassName);
                    return cachedCfbhOvenBlockEntityClass;
                } catch (ClassNotFoundException ignored) {
                    // Try next CFBH class layout.
                }
            }
            cfbhOvenBlockEntityLookupFailed = true;
            return null;
        }
    }

    // -- Private: Energy ignition --

    private static boolean igniteFromEnergy(final BlockEntity ovenBlockEntity,
                                            final Level level,
                                            final BlockPos ovenPos,
                                            final BlockState ovenState) {
        final Object energyStorage = resolveEnergyStorage(ovenBlockEntity);
        if (energyStorage == null) {
            return false;
        }

        final int currentEnergy = readEnergyAmount(energyStorage);
        if (currentEnergy <= 0) {
            return false;
        }

        final int currentBurnTime = readIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME);
        final int drainAmount = Math.max(1, OVEN_ENERGY_BURN_TARGET - currentBurnTime);
        if (drainAmount <= 0) {
            return false;
        }

        int drained = invokeDrain(energyStorage, drainAmount);
        if (drained <= 0) {
            drained = forceConsumeEnergy(energyStorage, currentEnergy, drainAmount);
        }
        if (drained <= 0) {
            return false;
        }

        final int newBurnTime = Math.max(1, currentBurnTime + drained);
        setIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME, newBurnTime);
        setIntField(
                ovenBlockEntity,
                OVEN_FIELD_CURRENT_ITEM_BURN_TIME,
                Math.max(readIntField(ovenBlockEntity, OVEN_FIELD_CURRENT_ITEM_BURN_TIME), OVEN_ENERGY_BURN_TARGET)
        );
        setBooleanField(ovenBlockEntity, OVEN_FIELD_IS_DIRTY, true);
        ovenBlockEntity.setChanged();
        setActiveProperty(level, ovenPos, ovenState, true);
        return true;
    }

    private static Object resolveEnergyStorage(final Object ovenBlockEntity) {
        final Object energyStorageField = readFieldValue(ovenBlockEntity, OVEN_FIELD_ENERGY_STORAGE);
        if (energyStorageField != null) {
            return energyStorageField;
        }
        return invokeNoArg(ovenBlockEntity, "getEnergyStorage");
    }

    private static int readEnergyAmount(final Object energyStorage) {
        final int energy = invokeIntNoArg(energyStorage, "getEnergy");
        if (energy > 0) {
            return energy;
        }
        return invokeIntNoArg(energyStorage, "getEnergyStored");
    }

    private static int invokeDrain(final Object energyStorage, final int amount) {
        try {
            final Method drainMethod = energyStorage.getClass().getMethod("drain", int.class, boolean.class);
            final Object result = drainMethod.invoke(energyStorage, amount, false);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            final Method extractMethod = energyStorage.getClass().getMethod("extract", int.class, boolean.class);
            final Object result = extractMethod.invoke(energyStorage, amount, false);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            final Method extractEnergyMethod = energyStorage.getClass().getMethod("extractEnergy", int.class, boolean.class);
            final Object result = extractEnergyMethod.invoke(energyStorage, amount, false);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static int forceConsumeEnergy(final Object energyStorage,
                                          final int currentEnergy,
                                          final int requestedDrain) {
        final int drained = Math.max(0, Math.min(currentEnergy, requestedDrain));
        if (drained <= 0) {
            return 0;
        }

        final int updatedEnergy = Math.max(0, currentEnergy - drained);
        if (invokeVoidInt(energyStorage, "setEnergy", updatedEnergy)) {
            return drained;
        }
        if (invokeVoidInt(energyStorage, "setEnergyStored", updatedEnergy)) {
            return drained;
        }
        return 0;
    }

    private static boolean invokeVoidInt(final Object target,
                                         final String methodName,
                                         final int value) {
        try {
            final Method method = target.getClass().getMethod(methodName, int.class);
            method.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int invokeIntNoArg(final Object target, final String methodName) {
        try {
            final Method method = target.getClass().getMethod(methodName);
            final Object result = method.invoke(target);
            return result instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    // -- Private: Fuel ignition --

    private static boolean igniteFromFuel(final BlockEntity ovenBlockEntity,
                                          final Level level,
                                          final BlockPos ovenPos,
                                          final BlockState ovenState) {
        final Object fuelContainerCandidate = invokeNoArg(ovenBlockEntity, OVEN_GET_FUEL_CONTAINER_METHOD);
        if (!(fuelContainerCandidate instanceof Container fuelContainer)) {
            return false;
        }

        for (int slot = 0; slot < fuelContainer.getContainerSize(); slot++) {
            final ItemStack fuelStack = fuelContainer.getItem(slot);
            if (fuelStack.isEmpty()) {
                continue;
            }

            final int baseBurnTime = ItemStackBridge.getBurnTime(fuelStack);
            if (baseBurnTime <= 0) {
                continue;
            }

            final int burnTime = ItemStackBridge.scaleBurnTime(baseBurnTime);
            setIntField(ovenBlockEntity, OVEN_FIELD_CURRENT_ITEM_BURN_TIME, burnTime);
            setIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME, burnTime);

            final ItemStack remainingItem = ItemStackBridge.getCraftingRemainingItem(fuelStack);
            fuelStack.shrink(1);
            if (fuelStack.isEmpty()) {
                fuelContainer.setItem(slot, remainingItem);
            }

            // Keep CFBH sync behavior consistent with its own slot-change flow.
            setBooleanField(ovenBlockEntity, OVEN_FIELD_IS_DIRTY, true);
            ovenBlockEntity.setChanged();
            setActiveProperty(level, ovenPos, ovenState, true);
            return true;
        }

        return false;
    }

    // -- Private: Block state helpers --

    private static boolean isDungeonOvenBlockId(final ResourceLocation blockId) {
        return blockId != null
                && Unifiled.MOD_ID.equals(blockId.getNamespace())
                && (DUNGEON_OVEN_PATH.equals(blockId.getPath())
                || blockId.getPath().endsWith("_" + DUNGEON_OVEN_PATH));
    }

    private static boolean isTaggedAsCfbhOven(final BlockState ovenState) {
        return ovenState.is(LAB11_CFBH_OVEN_BLOCK_TAG);
    }

    private static boolean isActive(final BlockState state) {
        final Property<?> property = state.getBlock().getStateDefinition().getProperty(ACTIVE_PROPERTY);
        if (!(property instanceof BooleanProperty booleanProperty) || !state.hasProperty(booleanProperty)) {
            return false;
        }
        return state.getValue(booleanProperty);
    }

    private static void setActiveProperty(final Level level,
                                          final BlockPos ovenPos,
                                          final BlockState ovenState,
                                          final boolean active) {
        final Property<?> property = ovenState.getBlock().getStateDefinition().getProperty(ACTIVE_PROPERTY);
        if (!(property instanceof BooleanProperty booleanProperty) || !ovenState.hasProperty(booleanProperty)) {
            return;
        }
        if (ovenState.getValue(booleanProperty) == active) {
            return;
        }
        // Toggle model state immediately after ignition so players get instant feedback.
        level.setBlock(ovenPos, ovenState.setValue(booleanProperty, active), 3);
    }

    // -- Reflection helpers --

    private static int readIntField(final Object target, final String fieldName) {
        final Field field = findField(target, fieldName);
        if (field == null) {
            return 0;
        }
        try {
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static boolean readBooleanField(final Object target, final String fieldName) {
        final Field field = findField(target, fieldName);
        if (field == null) {
            return false;
        }
        try {
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void setIntField(final Object target, final String fieldName, final int value) {
        final Field field = findField(target, fieldName);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void setBooleanField(final Object target, final String fieldName, final boolean value) {
        final Field field = findField(target, fieldName);
        if (field == null) {
            return;
        }
        try {
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object readFieldValue(final Object target, final String fieldName) {
        final Field field = findField(target, fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field findField(final Object target, final String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> currentClass = target.getClass();
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    public enum OvenPolicy {
        ANY_OVEN,
        DUNGEON_OVEN_ONLY
    }
}
