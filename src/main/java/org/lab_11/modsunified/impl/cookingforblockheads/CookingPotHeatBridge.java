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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class CookingPotHeatBridge {
    private static final String ACTIVE_PROPERTY = "active";
    private static final String FARMERS_DELIGHT_COOKING_POT_ID = "farmersdelight:cooking_pot";
    private static final String MINERS_DELIGHT_COPPER_POT_ID = "minersdelight:copper_pot";
    private static final String DUNGEONS_DELIGHT_MONSTER_POT_ID = "dungeonsdelight:monster_pot";
    private static final String DUNGEON_OVEN_PATH = "dungeon_oven";
    private static final TagKey<Block> LAB11_CFBH_OVEN_BLOCK_TAG = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Unifiled.MOD_ID, "cfbh_ovens")
    );

    private static final String[] CFBH_OVEN_BLOCK_ENTITY_CLASS_CANDIDATES = {
            "net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity",
            "net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity"
    };
    private static final String CFBH_CONFIG_CLASS = "net.blay09.mods.cookingforblockheads.CookingForBlockheadsConfig";
    private static final String CFBH_CONFIG_FUEL_MULTIPLIER_FIELD = "ovenFuelTimeMultiplier";
    private static final String CFBH_GET_ACTIVE_CONFIG_METHOD = "getActive";
    private static final String CFBH_IS_ITEM_FUEL_METHOD = "isItemFuel";

    private static final String BALM_CLASS = "net.blay09.mods.balm.api.Balm";
    private static final String BALM_GET_HOOKS_METHOD = "getHooks";
    private static final String BALM_GET_REMAINING_ITEM_METHOD = "getCraftingRemainingItem";
    private static final String BALM_GET_BURN_TIME_METHOD = "getBurnTime";

    private static final String OVEN_GET_FUEL_CONTAINER_METHOD = "getFuelContainer";
    private static final String OVEN_FIELD_FURNACE_BURN_TIME = "furnaceBurnTime";
    private static final String OVEN_FIELD_CURRENT_ITEM_BURN_TIME = "currentItemBurnTime";
    private static final String OVEN_FIELD_IS_DIRTY = "isDirty";
    private static final String POT_METHOD_HAS_INPUT = "hasInput";
    private static final String POT_METHOD_CREATE_FAKE_RECIPE_WRAPPER = "createFakeRecipeWrapper";
    private static final String POT_METHOD_GET_MATCHING_RECIPE = "getMatchingRecipe";
    private static final String POT_METHOD_CAN_COOK = "canCook";
    private static final String POT_METHOD_GET_INVENTORY = "getInventory";
    private static final String POT_FIELD_INVENTORY = "inventory";
    private static final String NEOFORGE_RECIPE_WRAPPER_CLASS = "net.neoforged.neoforge.items.wrapper.RecipeWrapper";
    private static final String NEOFORGE_ITEM_HANDLER_CLASS = "net.neoforged.neoforge.items.IItemHandler";

    private static volatile Class<?> cachedCfbhOvenBlockEntityClass;
    private static volatile boolean cfbhOvenBlockEntityLookupFailed;

    private CookingPotHeatBridge() {
    }

    public static boolean isTargetPotConnectedForCookingTable(final BlockEntity potBlockEntity,
                                                              final String targetKey) {
        if (potBlockEntity == null) {
            return false;
        }

        final Level level = potBlockEntity.getLevel();
        if (level == null) {
            return false;
        }

        final BlockPos potPos = potBlockEntity.getBlockPos();
        return switch (targetKey) {
            case BridgeKeys.TARGET_FARMERS_DELIGHT_COOKING_POT ->
                    isFarmersDelightCookingPot(level, potPos) && isAnyManagedOvenBelow(level, potPos);
            case BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT ->
                    isMinersDelightCopperPot(level, potPos) && isAnyManagedOvenBelow(level, potPos);
            case BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT ->
                    isDungeonsDelightMonsterPot(level, potPos) && isDungeonOvenBelow(level, potPos);
            default -> false;
        };
    }

    public static boolean shouldUseOvenHeatForPot(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null) {
            return false;
        }

        final ResourceLocation potBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        final String id = potBlockId.toString();
        return FARMERS_DELIGHT_COOKING_POT_ID.equals(id) || MINERS_DELIGHT_COPPER_POT_ID.equals(id);
    }

    public static boolean isAnyOvenHeatedBelow(final Level level, final BlockPos potPos) {
        return isOvenHeatedBelow(level, potPos, OvenPolicy.ANY_OVEN, null);
    }

    public static boolean isAnyOvenHeatedBelow(final Level level,
                                               final BlockPos potPos,
                                               final Object potBlockEntity) {
        return isOvenHeatedBelow(level, potPos, OvenPolicy.ANY_OVEN, potBlockEntity);
    }

    public static boolean isAnyManagedOvenBelow(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null) {
            return false;
        }

        final BlockState belowState = level.getBlockState(potPos.below());
        return matchesOvenPolicy(belowState, OvenPolicy.ANY_OVEN);
    }

    public static void tickOvenForPotHeat(final Level level,
                                          final BlockPos ovenPos,
                                          final BlockState ovenState) {
        if (level == null || ovenPos == null || level.isClientSide) {
            return;
        }

        if (!matchesOvenPolicy(ovenState, OvenPolicy.ANY_OVEN)) {
            return;
        }

        syncOvenActiveVisual(level, ovenPos, ovenState);

        final BlockPos potPos = ovenPos.above();
        final ResourceLocation potBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        if (potBlockId == null) {
            return;
        }

        final String potId = potBlockId.toString();
        if (!FARMERS_DELIGHT_COOKING_POT_ID.equals(potId)
                && !MINERS_DELIGHT_COPPER_POT_ID.equals(potId)
                && !DUNGEONS_DELIGHT_MONSTER_POT_ID.equals(potId)) {
            return;
        }

        final BlockEntity potBlockEntity = level.getBlockEntity(potPos);
        if (!shouldIgniteForCookingAttempt(potBlockEntity)) {
            return;
        }

        if (DUNGEONS_DELIGHT_MONSTER_POT_ID.equals(potId)
                && !isDungeonOvenBlockId(BuiltInRegistries.BLOCK.getKey(ovenState.getBlock()))) {
            return;
        }

        if (igniteOvenFromFuel(level, ovenPos, ovenState)) {
            syncOvenActiveVisual(level, ovenPos, level.getBlockState(ovenPos));
        }
    }

    public static boolean isDungeonOvenHeatedBelow(final Level level, final BlockPos potPos) {
        return isOvenHeatedBelow(level, potPos, OvenPolicy.DUNGEON_OVEN_ONLY, null);
    }

    public static boolean isDungeonOvenHeatedBelow(final Level level,
                                                   final BlockPos potPos,
                                                   final Object potBlockEntity) {
        return isOvenHeatedBelow(level, potPos, OvenPolicy.DUNGEON_OVEN_ONLY, potBlockEntity);
    }

    public static boolean isDungeonOvenBelow(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null) {
            return false;
        }
        return matchesOvenPolicy(level.getBlockState(potPos.below()), OvenPolicy.DUNGEON_OVEN_ONLY);
    }

    private static boolean isOvenHeatedBelow(final Level level,
                                             final BlockPos potPos,
                                             final OvenPolicy ovenPolicy,
                                             final Object potBlockEntity) {
        if (level == null || potPos == null) {
            return false;
        }

        final BlockPos ovenPos = potPos.below();
        final BlockState belowState = level.getBlockState(ovenPos);
        if (!matchesOvenPolicy(belowState, ovenPolicy)) {
            return false;
        }

        if (isOvenBurning(level, ovenPos)) {
            return true;
        }

        if (level.isClientSide) {
            return false;
        }

        if (!shouldIgniteForCookingAttempt(potBlockEntity)) {
            return false;
        }

        return igniteOvenFromFuel(level, ovenPos, belowState);
    }

    private static boolean shouldIgniteForCookingAttempt(final Object potBlockEntity) {
        if (potBlockEntity == null) {
            return false;
        }

        try {
            if (!invokeBooleanNoArg(potBlockEntity, POT_METHOD_HAS_INPUT)) {
                return false;
            }

            final Object recipeWrapper = resolveRecipeWrapper(potBlockEntity);
            if (recipeWrapper == null) {
                return false;
            }

            final Method getMatchingRecipeMethod = findMethod(potBlockEntity.getClass(), POT_METHOD_GET_MATCHING_RECIPE, 1);
            if (getMatchingRecipeMethod == null) {
                return false;
            }
            getMatchingRecipeMethod.setAccessible(true);
            final Object recipeOptionalObject = getMatchingRecipeMethod.invoke(potBlockEntity, recipeWrapper);
            if (!(recipeOptionalObject instanceof Optional<?> recipeOptional) || recipeOptional.isEmpty()) {
                return false;
            }

            final Object recipeHolder = recipeOptional.get();
            final Object recipe = invokeNoArg(recipeHolder, "value");
            if (recipe == null) {
                return false;
            }

            final Method canCookMethod = findMethod(potBlockEntity.getClass(), POT_METHOD_CAN_COOK, 1);
            if (canCookMethod == null) {
                return false;
            }
            canCookMethod.setAccessible(true);
            final Object canCook = canCookMethod.invoke(potBlockEntity, recipe);
            return canCook instanceof Boolean result && result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object resolveRecipeWrapper(final Object potBlockEntity) {
        final Object fakeRecipeWrapper = invokeNoArg(potBlockEntity, POT_METHOD_CREATE_FAKE_RECIPE_WRAPPER);
        if (fakeRecipeWrapper != null) {
            return fakeRecipeWrapper;
        }

        final Object inventoryObject = invokeNoArg(potBlockEntity, POT_METHOD_GET_INVENTORY);
        final Object effectiveInventory = inventoryObject != null
                ? inventoryObject
                : readFieldValue(potBlockEntity, POT_FIELD_INVENTORY);
        if (effectiveInventory == null) {
            return null;
        }

        try {
            final Class<?> recipeWrapperClass = Class.forName(NEOFORGE_RECIPE_WRAPPER_CLASS);
            return recipeWrapperClass.getConstructor(Class.forName(NEOFORGE_ITEM_HANDLER_CLASS))
                    .newInstance(effectiveInventory);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeBooleanNoArg(final Object target, final String methodName) {
        final Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean b && b;
    }

    private static boolean matchesOvenPolicy(final BlockState ovenState,
                                             final OvenPolicy ovenPolicy) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(ovenState.getBlock());
        if (ovenPolicy == OvenPolicy.DUNGEON_OVEN_ONLY) {
            return isDungeonOvenBlockId(blockId);
        }
        return isDungeonOvenBlockId(blockId) || isTaggedAsCfbhOven(ovenState);
    }

    private static boolean isDungeonOvenBlockId(final ResourceLocation blockId) {
        return blockId != null
                && Unifiled.MOD_ID.equals(blockId.getNamespace())
                && DUNGEON_OVEN_PATH.equals(blockId.getPath());
    }

    private static boolean isFarmersDelightCookingPot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, FARMERS_DELIGHT_COOKING_POT_ID);
    }

    private static boolean isMinersDelightCopperPot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, MINERS_DELIGHT_COPPER_POT_ID);
    }

    private static boolean isDungeonsDelightMonsterPot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, DUNGEONS_DELIGHT_MONSTER_POT_ID);
    }

    private static boolean isPotBlockId(final Level level, final BlockPos potPos, final String expectedId) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        return blockId != null && expectedId.equals(blockId.toString());
    }

    private static boolean isTaggedAsCfbhOven(final BlockState ovenState) {
        return ovenState.is(LAB11_CFBH_OVEN_BLOCK_TAG);
    }

    private static boolean isOvenBurning(final Level level, final BlockPos ovenPos) {
        final BlockEntity ovenBlockEntity = level.getBlockEntity(ovenPos);
        if (!isCfbhOvenBlockEntity(ovenBlockEntity)) {
            return false;
        }

        return readIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME) > 0;
    }

    private static void syncOvenActiveVisual(final Level level,
                                             final BlockPos ovenPos,
                                             final BlockState ovenState) {
        if (level == null || ovenPos == null || ovenState == null) {
            return;
        }

        final boolean burning = isOvenBurning(level, ovenPos);
        setActiveProperty(level, ovenPos, ovenState, burning);
    }

    private static boolean igniteOvenFromFuel(final Level level,
                                              final BlockPos ovenPos,
                                              final BlockState ovenState) {
        final BlockEntity ovenBlockEntity = level.getBlockEntity(ovenPos);
        if (!isCfbhOvenBlockEntity(ovenBlockEntity)) {
            return false;
        }

        if (readIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME) > 0) {
            return true;
        }

        final Object fuelContainerCandidate = invokeNoArg(ovenBlockEntity, OVEN_GET_FUEL_CONTAINER_METHOD);
        if (!(fuelContainerCandidate instanceof Container fuelContainer)) {
            return false;
        }

        for (int slot = 0; slot < fuelContainer.getContainerSize(); slot++) {
            final ItemStack fuelStack = fuelContainer.getItem(slot);
            if (fuelStack.isEmpty()) {
                continue;
            }

            final int baseBurnTime = readOvenBurnTime(fuelStack);
            if (baseBurnTime <= 0) {
                continue;
            }

            final int burnTime = scaleBurnTime(baseBurnTime);
            setIntField(ovenBlockEntity, OVEN_FIELD_CURRENT_ITEM_BURN_TIME, burnTime);
            setIntField(ovenBlockEntity, OVEN_FIELD_FURNACE_BURN_TIME, burnTime);

            final ItemStack remainingItem = getCraftingRemainingItem(fuelStack);
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

    private static int readOvenBurnTime(final ItemStack fuelStack) {
        if (!isValidOvenFuel(fuelStack)) {
            return 0;
        }

        final int burnTime = readBurnTimeFromBalmHooks(fuelStack);
        if (burnTime > 0) {
            return burnTime;
        }

        // CFBH accepts cooking oil even when generic burn-time lookups return 0.
        return 800;
    }

    private static boolean isValidOvenFuel(final ItemStack fuelStack) {
        try {
            final Class<?> ovenClass = resolveCfbhOvenBlockEntityClass();
            if (ovenClass == null) {
                return false;
            }
            final Method isItemFuelMethod = ovenClass.getMethod(CFBH_IS_ITEM_FUEL_METHOD, ItemStack.class);
            final Object value = isItemFuelMethod.invoke(null, fuelStack);
            return value instanceof Boolean result && result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int readBurnTimeFromBalmHooks(final ItemStack fuelStack) {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Method getHooksMethod = balmClass.getMethod(BALM_GET_HOOKS_METHOD);
            final Object balmHooks = getHooksMethod.invoke(null);
            if (balmHooks == null) {
                return 0;
            }

            final Method getBurnTimeMethod = balmHooks.getClass().getMethod(BALM_GET_BURN_TIME_METHOD, ItemStack.class);
            final Object value = getBurnTimeMethod.invoke(balmHooks, fuelStack);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static int scaleBurnTime(final int baseBurnTime) {
        try {
            final Class<?> configClass = Class.forName(CFBH_CONFIG_CLASS);
            final Method getActiveConfigMethod = configClass.getMethod(CFBH_GET_ACTIVE_CONFIG_METHOD);
            final Object activeConfig = getActiveConfigMethod.invoke(null);
            if (activeConfig == null) {
                return Math.max(1, baseBurnTime);
            }

            final Field multiplierField = activeConfig.getClass().getField(CFBH_CONFIG_FUEL_MULTIPLIER_FIELD);
            final double multiplier = multiplierField.getDouble(activeConfig);
            return (int) Math.max(1d, baseBurnTime * multiplier);
        } catch (ReflectiveOperationException ignored) {
            return Math.max(1, baseBurnTime);
        }
    }

    private static ItemStack getCraftingRemainingItem(final ItemStack fuelStack) {
        try {
            final Class<?> balmClass = Class.forName(BALM_CLASS);
            final Method getHooksMethod = balmClass.getMethod(BALM_GET_HOOKS_METHOD);
            final Object balmHooks = getHooksMethod.invoke(null);
            if (balmHooks == null) {
                return ItemStack.EMPTY;
            }

            final Method getRemainingItemMethod =
                    balmHooks.getClass().getMethod(BALM_GET_REMAINING_ITEM_METHOD, ItemStack.class);
            final Object value = getRemainingItemMethod.invoke(balmHooks, fuelStack);
            return value instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean isCfbhOvenBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Class<?> cfbhOvenBlockEntityClass = resolveCfbhOvenBlockEntityClass();
        return cfbhOvenBlockEntityClass != null && cfbhOvenBlockEntityClass.isInstance(blockEntity);
    }

    private static Class<?> resolveCfbhOvenBlockEntityClass() {
        final Class<?> cached = cachedCfbhOvenBlockEntityClass;
        if (cached != null) {
            return cached;
        }
        if (cfbhOvenBlockEntityLookupFailed) {
            return null;
        }

        synchronized (CookingPotHeatBridge.class) {
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

    private static Object invokeNoArg(final Object target, final String methodName) {
        if (target == null) {
            return null;
        }

        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            final Method method = findMethod(target.getClass(), methodName, 0);
            if (method == null) {
                return null;
            }

            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
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

    private static Method findMethod(final Class<?> ownerClass,
                                     final String methodName,
                                     final int parameterCount) {
        Class<?> currentClass = ownerClass;
        while (currentClass != null) {
            for (final Method method : currentClass.getDeclaredMethods()) {
                if (methodName.equals(method.getName()) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
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

    private enum OvenPolicy {
        ANY_OVEN,
        DUNGEON_OVEN_ONLY
    }
}
