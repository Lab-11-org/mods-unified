package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public final class CookingPotHeatBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FARMERS_DELIGHT_COOKING_POT_ID = "farmersdelight:cooking_pot";
    private static final String MINERS_DELIGHT_COPPER_POT_ID = "minersdelight:copper_pot";
    private static final String MINERS_DELIGHT_COPPER_POT_ID_ALT = "miners_delight:copper_pot";
    private static final String DUNGEONS_DELIGHT_MONSTER_POT_ID = "dungeonsdelight:monster_pot";
    private static final String KALEIDOSCOPE_COOKERY_POT_ID = "kaleidoscope_cookery:pot";
    private static final String KALEIDOSCOPE_COOKERY_STOCKPOT_ID = "kaleidoscope_cookery:stockpot";
    private static final String KALEIDOSCOPE_PROPERTY_HAS_BASE = "has_base";
    private static final String KALEIDOSCOPE_PROPERTY_HAS_OIL = "has_oil";
    private static final String KALEIDOSCOPE_POT_INPUTS_FIELD = "inputs";
    private static final String KALEIDOSCOPE_STOCKPOT_SOUP_BASE_ID_FIELD = "soupBaseId";
    private static final String KALEIDOSCOPE_POT_REFRESH_METHOD = "refresh";
    private static final ResourceLocation KALEIDOSCOPE_STOCKPOT_SOUP_BASE_LEGACY_WATER_ID =
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_KALEIDOSCOPE_COOKERY, "water");
    private static final ResourceLocation KALEIDOSCOPE_STOCKPOT_SOUP_BASE_VANILLA_WATER_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "water");
    private static final String POT_METHOD_HAS_INPUT = "hasInput";
    private static final String POT_METHOD_CREATE_FAKE_RECIPE_WRAPPER = "createFakeRecipeWrapper";
    private static final String POT_METHOD_GET_MATCHING_RECIPE = "getMatchingRecipe";
    private static final String POT_METHOD_CAN_COOK = "canCook";
    private static final String POT_METHOD_GET_INVENTORY = "getInventory";
    private static final String POT_FIELD_INVENTORY = "inventory";
    private static final String[] RECIPE_WRAPPER_CLASS_CANDIDATES = {
            "net.neoforged.neoforge.items.wrapper.RecipeWrapper",
            "net.minecraftforge.items.wrapper.RecipeWrapper"
    };
    private static final String[] ITEM_HANDLER_CLASS_CANDIDATES = {
            "net.neoforged.neoforge.items.IItemHandler",
            "net.minecraftforge.items.IItemHandler"
    };

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
            case BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT ->
                    isKaleidoscopeCookeryPot(level, potPos) && isAnyManagedOvenBelow(level, potPos);
            case BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT ->
                    isKaleidoscopeCookeryStockpot(level, potPos) && isAnyManagedOvenBelow(level, potPos);
            default -> false;
        };
    }

    public static boolean shouldUseOvenHeatForPot(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null) {
            return false;
        }

        final ResourceLocation potBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        final String id = potBlockId.toString();
        return FARMERS_DELIGHT_COOKING_POT_ID.equals(id)
                || DUNGEONS_DELIGHT_MONSTER_POT_ID.equals(id)
                || isMinersDelightCopperPotId(id)
                || KALEIDOSCOPE_COOKERY_POT_ID.equals(id)
                || KALEIDOSCOPE_COOKERY_STOCKPOT_ID.equals(id);
    }

    public static boolean isAnyOvenHeatedBelow(final Level level, final BlockPos potPos) {
        return isOvenHeatedBelow(level, potPos, OvenBridge.OvenPolicy.ANY_OVEN, null);
    }

    public static boolean isAnyOvenHeatedBelow(final Level level,
                                               final BlockPos potPos,
                                               final Object potBlockEntity) {
        return isOvenHeatedBelow(level, potPos, OvenBridge.OvenPolicy.ANY_OVEN, potBlockEntity);
    }

    public static boolean isAnyManagedOvenBelow(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null) {
            return false;
        }

        final BlockPos ovenPos = potPos.below();
        final BlockState belowState = level.getBlockState(ovenPos);
        return OvenBridge.matchesPolicy(level, ovenPos, belowState, OvenBridge.OvenPolicy.ANY_OVEN);
    }

    /**
     * Returns true if the pot/stockpot is NOT in a finished/burnt state.
     * Used by block interaction injects to avoid re-igniting the oven when food is done.
     * For Pot: FINISHED=2, BURNT=3 → skip ignition.
     * For Stockpot: FINISHED=3 → skip ignition.
     */
    public static boolean shouldIgniteForInteraction(final BlockEntity potBlockEntity) {
        if (potBlockEntity == null) {
            return false;
        }
        final String className = potBlockEntity.getClass().getName();
        if (!className.contains("com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen")) {
            return true;
        }
        final int status = readIntField(potBlockEntity, "status");
        final Level level = potBlockEntity.getLevel();
        final BlockPos pos = potBlockEntity.getBlockPos();
        if (level != null && isKaleidoscopeCookeryStockpot(level, pos)) {
            // Stockpot: 0=PUT_SOUP_BASE, 1=PUT_INGREDIENT, 2=COOKING, 3=FINISHED
            return status < 3;
        }
        // Pot: 0=PUT_INGREDIENT, 1=COOKING, 2=FINISHED, 3=BURNT
        return status < 2;
    }

    public static boolean tryIgniteManagedOvenForPot(final BlockEntity potBlockEntity) {
        if (potBlockEntity == null) {
            return false;
        }

        final Level level = potBlockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }

        final BlockPos potPos = potBlockEntity.getBlockPos();
        if (!shouldUseOvenHeatForPot(level, potPos) || !isAnyManagedOvenBelow(level, potPos)) {
            LOGGER.info("Skipping managed-oven ignition for pot at {} because no managed oven is detected below.", potPos);
            return false;
        }
        normalizeKaleidoscopePotBaseForManagedOven(level, potPos);
        normalizeKaleidoscopeStockpotSoupBaseForManagedOven(level, potPos);

        final BlockPos ovenPos = potPos.below();
        final BlockState ovenState = level.getBlockState(ovenPos);
        if (!OvenBridge.matchesPolicy(level, ovenPos, ovenState, OvenBridge.OvenPolicy.ANY_OVEN)) {
            LOGGER.info("Skipping managed-oven ignition for pot at {} because block below {} does not match managed oven policy.", potPos, ovenPos);
            return false;
        }

        if (OvenBridge.isBurning(level, ovenPos)) {
            OvenBridge.syncActiveVisual(level, ovenPos, ovenState);
            return true;
        }

        final boolean ignited = OvenBridge.ignite(level, ovenPos, ovenState);
        if (ignited) {
            OvenBridge.syncActiveVisual(level, ovenPos, level.getBlockState(ovenPos));
        }
        return ignited;
    }

    public static void tickOvenForPotHeat(final Level level,
                                          final BlockPos ovenPos,
                                          final BlockState ovenState) {
        if (level == null || ovenPos == null || level.isClientSide) {
            return;
        }

        if (!OvenBridge.matchesPolicy(level, ovenPos, ovenState, OvenBridge.OvenPolicy.ANY_OVEN)) {
            return;
        }

        OvenBridge.syncActiveVisual(level, ovenPos, ovenState);

        final BlockPos potPos = ovenPos.above();
        normalizeKaleidoscopePotBaseForManagedOven(level, potPos);
        normalizeKaleidoscopeStockpotSoupBaseForManagedOven(level, potPos);
        final ResourceLocation potBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        if (potBlockId == null) {
            return;
        }

        final String potId = potBlockId.toString();
        if (!FARMERS_DELIGHT_COOKING_POT_ID.equals(potId)
                && !isMinersDelightCopperPotId(potId)
                && !DUNGEONS_DELIGHT_MONSTER_POT_ID.equals(potId)
                && !KALEIDOSCOPE_COOKERY_POT_ID.equals(potId)
                && !KALEIDOSCOPE_COOKERY_STOCKPOT_ID.equals(potId)) {
            return;
        }

        final BlockEntity potBlockEntity = level.getBlockEntity(potPos);
        if (!shouldIgniteForCookingAttempt(potBlockEntity)) {
            return;
        }

        if (DUNGEONS_DELIGHT_MONSTER_POT_ID.equals(potId)
                && !OvenBridge.matchesPolicy(level, ovenPos, ovenState, OvenBridge.OvenPolicy.DUNGEON_OVEN_ONLY)) {
            return;
        }

        if (OvenBridge.ignite(level, ovenPos, ovenState)) {
            OvenBridge.syncActiveVisual(level, ovenPos, level.getBlockState(ovenPos));
        }
    }

    public static boolean isDungeonOvenHeatedBelow(final Level level, final BlockPos potPos) {
        return isOvenHeatedBelow(level, potPos, OvenBridge.OvenPolicy.DUNGEON_OVEN_ONLY, null);
    }

    public static boolean isDungeonOvenHeatedBelow(final Level level,
                                                   final BlockPos potPos,
                                                   final Object potBlockEntity) {
        return isOvenHeatedBelow(level, potPos, OvenBridge.OvenPolicy.DUNGEON_OVEN_ONLY, potBlockEntity);
    }

    public static boolean isDungeonOvenBelow(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null) {
            return false;
        }
        final BlockPos ovenPos = potPos.below();
        return OvenBridge.matchesPolicy(level, ovenPos, level.getBlockState(ovenPos), OvenBridge.OvenPolicy.DUNGEON_OVEN_ONLY);
    }

    private static boolean isOvenHeatedBelow(final Level level,
                                             final BlockPos potPos,
                                             final OvenBridge.OvenPolicy ovenPolicy,
                                             final Object potBlockEntity) {
        if (level == null || potPos == null) {
            return false;
        }

        final BlockPos ovenPos = potPos.below();
        final BlockState belowState = level.getBlockState(ovenPos);
        if (!OvenBridge.matchesPolicy(level, ovenPos, belowState, ovenPolicy)) {
            return false;
        }

        if (OvenBridge.isBurning(level, ovenPos)) {
            return true;
        }

        if (level.isClientSide) {
            return false;
        }

        if (!shouldIgniteForCookingAttempt(potBlockEntity)) {
            return false;
        }

        return OvenBridge.ignite(level, ovenPos, belowState);
    }

    private static boolean shouldIgniteForCookingAttempt(final Object potBlockEntity) {
        if (potBlockEntity == null) {
            return false;
        }

        final String className = potBlockEntity.getClass().getName();
        if (className.contains("com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen")) {
            final int status = readIntField(potBlockEntity, "status");
            // For Pot: 0 = PUT_INGREDIENT, 1 = COOKING, 2 = FINISHED, 3 = BURNT
            // For Stockpot: 0 = PUT_SOUP_BASE, 1 = PUT_INGREDIENT, 2 = COOKING, 3 = FINISHED
            // We want to ignite if it's in a state where it can progress cooking.
            final Level level = ((BlockEntity) potBlockEntity).getLevel();
            final BlockPos pos = ((BlockEntity) potBlockEntity).getBlockPos();
            final boolean isStockpot = level != null && isKaleidoscopeCookeryStockpot(level, pos);

            // For stockpot, check status 0-2; for pot, check status 0-1
            if (status == 0 || status == 1 || (isStockpot && status == 2)) {
                if (hasAnyInputInPotInventory(potBlockEntity)) {
                    return true;
                }
                // Check if it's a regular Pot and has oil
                if (level != null && isKaleidoscopeCookeryPot(level, pos)) {
                    final BlockState state = level.getBlockState(pos);
                    final Property<?> hasOilProp = state.getBlock().getStateDefinition().getProperty(KALEIDOSCOPE_PROPERTY_HAS_OIL);
                    if (hasOilProp instanceof BooleanProperty boolProp && state.getValue(boolProp)) {
                        return true;
                    }
                }
            }
        }

        // For non-kaleidoscope pots, any real input means we should attempt heating.
        // Relying only on reflective recipe-wrapper checks can miss valid runtime states.
        if (hasAnyInputInPotInventory(potBlockEntity)) {
            return true;
        }

        try {
            if (!invokeBooleanNoArg(potBlockEntity, POT_METHOD_HAS_INPUT)) {
                return hasAnyInputInPotInventory(potBlockEntity);
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

            final Object recipeOptionalValue = recipeOptional.get();
            final Object recipe = resolveRecipeFromOptionalValue(recipeOptionalValue);
            if (recipe == null) {
                return false;
            }

            final Method canCookMethod = findCompatibleSingleArgMethod(
                    potBlockEntity.getClass(),
                    POT_METHOD_CAN_COOK,
                    recipe,
                    recipeOptionalValue
            );
            if (canCookMethod == null) {
                return false;
            }
            canCookMethod.setAccessible(true);
            final Object canCook = invokeCanCook(canCookMethod, potBlockEntity, recipe, recipeOptionalValue);
            return canCook instanceof Boolean result && result;
        } catch (ReflectiveOperationException ignored) {
            return hasAnyInputInPotInventory(potBlockEntity);
        }
    }

    private static boolean hasAnyInputInPotInventory(final Object potBlockEntity) {
        final Object inventoryObject = invokeNoArg(potBlockEntity, POT_METHOD_GET_INVENTORY);
        Object inventory = inventoryObject != null ? inventoryObject : readFieldValue(potBlockEntity, POT_FIELD_INVENTORY);
        if (inventory == null) {
            inventory = readFieldValue(potBlockEntity, KALEIDOSCOPE_POT_INPUTS_FIELD);
        }

        if (inventory == null) {
            return false;
        }

        if (inventory instanceof java.util.List<?> list) {
            for (final Object entry : list) {
                if (entry instanceof ItemStack stack && !stack.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        try {
            final Method getSlotsMethod = findMethod(inventory.getClass(), "getSlots", 0);
            if (getSlotsMethod == null) {
                return false;
            }
            getSlotsMethod.setAccessible(true);
            final Object slotsValue = getSlotsMethod.invoke(inventory);
            if (!(slotsValue instanceof Number number)) {
                return false;
            }

            final int slots = Math.max(0, number.intValue());
            final Method getStackInSlotMethod = findMethod(inventory.getClass(), "getStackInSlot", 1);
            if (getStackInSlotMethod == null) {
                return false;
            }
            getStackInSlotMethod.setAccessible(true);

            final int mealDisplaySlot = resolveStaticIntField(potBlockEntity.getClass(), "MEAL_DISPLAY_SLOT", 6);
            final int inputSlotLimit = Math.max(1, Math.min(slots, mealDisplaySlot));
            for (int slot = 0; slot < inputSlotLimit; slot++) {
                final Object value = getStackInSlotMethod.invoke(inventory, slot);
                if (value instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object resolveRecipeFromOptionalValue(final Object recipeOptionalValue) {
        if (recipeOptionalValue == null) {
            return null;
        }

        // 1.20.x returns Optional<Recipe>; 1.21.x may return Optional<RecipeHolder>.
        final Object valueMethodResult = invokeNoArg(recipeOptionalValue, "value");
        if (valueMethodResult != null) {
            return valueMethodResult;
        }

        return recipeOptionalValue;
    }

    private static Object invokeCanCook(final Method canCookMethod,
                                        final Object potBlockEntity,
                                        final Object resolvedRecipe,
                                        final Object recipeOptionalValue) throws ReflectiveOperationException {
        try {
            return canCookMethod.invoke(potBlockEntity, resolvedRecipe);
        } catch (IllegalArgumentException ignored) {
            if (recipeOptionalValue != null && recipeOptionalValue != resolvedRecipe) {
                return canCookMethod.invoke(potBlockEntity, recipeOptionalValue);
            }
            throw ignored;
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

        for (final String recipeWrapperClassName : RECIPE_WRAPPER_CLASS_CANDIDATES) {
            for (final String itemHandlerClassName : ITEM_HANDLER_CLASS_CANDIDATES) {
                try {
                    final Class<?> recipeWrapperClass = Class.forName(recipeWrapperClassName);
                    final Class<?> itemHandlerClass = Class.forName(itemHandlerClassName);
                    return recipeWrapperClass.getConstructor(itemHandlerClass).newInstance(effectiveInventory);
                } catch (ReflectiveOperationException ignored) {
                    // Try next wrapper/item handler pair.
                }
            }
        }
        return null;
    }

    private static boolean invokeBooleanNoArg(final Object target, final String methodName) {
        final Object value = invokeNoArg(target, methodName);
        return value instanceof Boolean b && b;
    }

    private static boolean isFarmersDelightCookingPot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, FARMERS_DELIGHT_COOKING_POT_ID);
    }

    private static boolean isMinersDelightCopperPot(final Level level, final BlockPos potPos) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        return blockId != null && isMinersDelightCopperPotId(blockId.toString());
    }

    private static boolean isDungeonsDelightMonsterPot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, DUNGEONS_DELIGHT_MONSTER_POT_ID);
    }

    private static boolean isKaleidoscopeCookeryPot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, KALEIDOSCOPE_COOKERY_POT_ID);
    }

    private static boolean isKaleidoscopeCookeryStockpot(final Level level, final BlockPos potPos) {
        return isPotBlockId(level, potPos, KALEIDOSCOPE_COOKERY_STOCKPOT_ID);
    }

    private static void normalizeKaleidoscopePotBaseForManagedOven(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null
                || (!isKaleidoscopeCookeryPot(level, potPos) && !isKaleidoscopeCookeryStockpot(level, potPos))) {
            return;
        }

        final BlockPos ovenPos = potPos.below();
        final BlockState ovenState = level.getBlockState(ovenPos);
        if (!OvenBridge.matchesPolicy(level, ovenPos, ovenState, OvenBridge.OvenPolicy.ANY_OVEN)) {
            return;
        }

        final BlockState stockpotState = level.getBlockState(potPos);
        final Property<?> baseProperty = stockpotState.getProperties().stream()
                .filter(property -> KALEIDOSCOPE_PROPERTY_HAS_BASE.equals(property.getName()))
                .findFirst()
                .orElse(null);
        if (!(baseProperty instanceof BooleanProperty boolProperty) || !stockpotState.getValue(boolProperty)) {
            return;
        }

        level.setBlockAndUpdate(potPos, stockpotState.setValue(boolProperty, false));
    }

    private static void normalizeKaleidoscopeStockpotSoupBaseForManagedOven(final Level level, final BlockPos potPos) {
        if (level == null || potPos == null || !isKaleidoscopeCookeryStockpot(level, potPos)) {
            return;
        }

        final BlockPos ovenPos = potPos.below();
        final BlockState ovenState = level.getBlockState(ovenPos);
        if (!OvenBridge.matchesPolicy(level, ovenPos, ovenState, OvenBridge.OvenPolicy.ANY_OVEN)) {
            return;
        }

        final BlockEntity stockpotBlockEntity = level.getBlockEntity(potPos);
        final Field soupBaseIdField = findField(stockpotBlockEntity, KALEIDOSCOPE_STOCKPOT_SOUP_BASE_ID_FIELD);
        if (soupBaseIdField == null) {
            return;
        }

        try {
            soupBaseIdField.setAccessible(true);
            final Object soupBaseIdValue = soupBaseIdField.get(stockpotBlockEntity);
            if (!(soupBaseIdValue instanceof ResourceLocation soupBaseId)
                    || !KALEIDOSCOPE_STOCKPOT_SOUP_BASE_LEGACY_WATER_ID.equals(soupBaseId)) {
                return;
            }

            soupBaseIdField.set(stockpotBlockEntity, KALEIDOSCOPE_STOCKPOT_SOUP_BASE_VANILLA_WATER_ID);
            stockpotBlockEntity.setChanged();
            invokeNoArg(stockpotBlockEntity, KALEIDOSCOPE_POT_REFRESH_METHOD);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static boolean isPotBlockId(final Level level, final BlockPos potPos, final String expectedId) {
        final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(potPos).getBlock());
        return blockId != null && expectedId.equals(blockId.toString());
    }

    private static boolean isMinersDelightCopperPotId(final String blockId) {
        return MINERS_DELIGHT_COPPER_POT_ID.equals(blockId)
                || MINERS_DELIGHT_COPPER_POT_ID_ALT.equals(blockId);
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

    private static int resolveStaticIntField(final Class<?> ownerClass,
                                             final String fieldName,
                                             final int fallback) {
        Class<?> currentClass = ownerClass;
        while (currentClass != null) {
            try {
                final Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(null);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return fallback;
            }
        }
        return fallback;
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

    private static Method findCompatibleSingleArgMethod(final Class<?> ownerClass,
                                                        final String methodName,
                                                        final Object... candidates) {
        Method fallback = null;
        Class<?> currentClass = ownerClass;
        while (currentClass != null) {
            for (final Method method : currentClass.getDeclaredMethods()) {
                if (!methodName.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }

                if (fallback == null) {
                    fallback = method;
                }

                final Class<?> parameterType = wrapPrimitive(method.getParameterTypes()[0]);
                for (final Object candidate : candidates) {
                    if (candidate == null) {
                        continue;
                    }
                    if (parameterType.isAssignableFrom(candidate.getClass())) {
                        return method;
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return fallback;
    }

    private static Class<?> wrapPrimitive(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }
}
