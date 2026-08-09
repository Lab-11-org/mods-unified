package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class CookingPotProcessorCapability {
    private static final String CFBH_KITCHEN_IMPL_CLASS = "net.blay09.mods.cookingforblockheads.crafting.KitchenImpl";
    private static final String[] CFBH_COOKING_TABLE_BLOCK_ENTITY_CLASS_CANDIDATES = {
            "net.blay09.mods.cookingforblockheads.block.entity.CookingTableBlockEntity",
            "net.blay09.mods.cookingforblockheads.tile.CookingTableBlockEntity"
    };
    private static final String POT_GET_INVENTORY_METHOD = "getInventory";
    private static final String POT_INVENTORY_FIELD = "inventory";
    private static final String POT_MEAL_DISPLAY_SLOT_FIELD = "MEAL_DISPLAY_SLOT";
    private static final String POT_CONTAINER_SLOT_FIELD = "CONTAINER_SLOT";
    private static final String POT_OUTPUT_SLOT_FIELD = "OUTPUT_SLOT";
    private static final String FEEDBACK_MOVED_TO_POT_KEY = "lab_11_mods_unified.feedback.cooking_table.moved_to_pot";
    private static final String FEEDBACK_POT_NOT_CONNECTED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_not_connected";
    private static final String FEEDBACK_POT_INPUT_BLOCKED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_input_blocked";
    private static final String FEEDBACK_POT_CONTAINER_BLOCKED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_container_blocked";
    private static final String FEEDBACK_POT_TRANSFER_FAILED_KEY = "lab_11_mods_unified.feedback.cooking_table.pot_transfer_failed";
    private static final String POT_COOK_TIME_FIELD = "cookTime";
    private static final String POT_COOK_TIME_TOTAL_FIELD = "cookTimeTotal";

    private static final Map<String, Predicate<BlockEntity>> REQUIRED_MARKER_CHECKS = Map.of(
            BridgeKeys.MARKER_DUNGEON_OVEN, CookingPotProcessorCapability::hasConnectedDungeonOven
    );

    private static final Map<BlockEntity, Recipe<?>> LAST_RECIPE_BY_POT = new WeakHashMap<>();

    private static volatile Object potTransferOperation;
    private static volatile Object potNotConnectedOperation;
    private static volatile Object potInputBlockedOperation;
    private static volatile Object potContainerBlockedOperation;
    private static volatile Object potTransferFailedOperation;

    private enum TransferFailure {
        NONE,
        NO_INVENTORY,
        INPUT_SLOT_BLOCKED,
        CONTAINER_SLOT_BLOCKED,
        INPUT_TRANSFER_FAILED,
        CONTAINER_TRANSFER_FAILED
    }

    private CookingPotProcessorCapability() {
    }

    public static Object createProcessor(final BlockEntity blockEntity,
                                         final Set<RecipeType<?>> supportedRecipeTypes,
                                         final List<String> requiredMarkerKeys,
                                         final String targetKey) {
        return CfbhRuntime.newKitchenItemProcessorProxy(new CfbhRuntime.KitchenItemProcessorView() {
            @Override
            public boolean canProcess(final RecipeType<?> recipeType) {
                return supportedRecipeTypes.contains(recipeType)
                        && requiredMarkersSatisfied(blockEntity, requiredMarkerKeys);
            }

            @Override
            public Object processRecipe(final Recipe<?> recipe, final List<?> ingredientTokens) {
                if (!isRecipeAcceptedForTarget(recipe, targetKey)) {
                    return CfbhRuntime.kitchenOperationEmpty();
                }

                final boolean directTablePlacement = isDirectlyAboveCookingTable(blockEntity);
                final boolean ovenConnectedPlacement =
                        CookingPotHeatBridge.isTargetPotConnectedForCookingTable(blockEntity, targetKey);
                if (!directTablePlacement && !ovenConnectedPlacement) {
                    return potNotConnectedOperation();
                }

                final TransferFailure transferFailure = transferRecipeToPot(blockEntity, recipe, ingredientTokens);
                if (transferFailure != TransferFailure.NONE) {
                    return transferFailureOperation(transferFailure);
                }

                return potTransferOperation();
            }
        });
    }

    public static boolean isDirectlyAboveCookingTable(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        return isCfbhCookingTableBlockEntity(level.getBlockEntity(blockEntity.getBlockPos().below()));
    }

    public static void register(final RegisterCapabilitiesEvent event, final List<CookingPotBridgeTarget> targets) {
        final Class<?> processorClass = CfbhRuntime.kitchenItemProcessorClass();
        if (processorClass == null) {
            return;
        }

        try {
            event.register(processorClass);
        } catch (RuntimeException ignored) {
            // Some environments may reject late/duplicate capability registration.
        }
    }

    public static boolean canTransferResolvedStacksToPot(final BlockEntity blockEntity,
                                                         final Recipe<?> recipe,
                                                         final List<ItemStack> ingredientStacks,
                                                         final ItemStack containerCost) {
        return transferResolvedStacksToPotInternal(blockEntity, recipe, ingredientStacks, containerCost, true);
    }

    public static boolean transferResolvedStacksToPot(final BlockEntity blockEntity,
                                                      final Recipe<?> recipe,
                                                      final List<ItemStack> ingredientStacks,
                                                      final ItemStack containerCost) {
        return transferResolvedStacksToPotInternal(blockEntity, recipe, ingredientStacks, containerCost, false);
    }

    private static Object potTransferOperation() {
        final Object cached = potTransferOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_MOVED_TO_POT_KEY, ChatFormatting.YELLOW);
        potTransferOperation = created;
        return created;
    }

    private static boolean transferResolvedStacksToPotInternal(final BlockEntity blockEntity,
                                                               final Recipe<?> recipe,
                                                               final List<ItemStack> ingredientStacks,
                                                               final ItemStack containerCost,
                                                               final boolean simulate) {
        final IItemHandler potInventory = resolvePotInventory(blockEntity);
        if (potInventory == null) {
            return false;
        }

        final Class<?> blockEntityClass = blockEntity.getClass();
        final int mealDisplaySlot = resolveStaticIntField(blockEntityClass, POT_MEAL_DISPLAY_SLOT_FIELD, 6);
        final int containerSlot = resolveStaticIntField(blockEntityClass, POT_CONTAINER_SLOT_FIELD, 7);
        final int outputSlot = resolveStaticIntField(blockEntityClass, POT_OUTPUT_SLOT_FIELD, 8);
        final int inputSlotCount = Math.max(1, Math.min(mealDisplaySlot, Math.min(containerSlot, outputSlot)));
        final int safeContainerSlot = Math.max(inputSlotCount, containerSlot);

        final Recipe<?> previousRecipe;
        synchronized (LAST_RECIPE_BY_POT) {
            previousRecipe = LAST_RECIPE_BY_POT.get(blockEntity);
        }
        final boolean willFlush = previousRecipe != null && previousRecipe != recipe;

        if (simulate && willFlush) {
            if (!canFitAfterFlush(potInventory, ingredientStacks, containerCost, inputSlotCount, safeContainerSlot)) {
                return false;
            }
            return true;
        }

        if (!simulate && willFlush) {
            flushPendingPotInputs(blockEntity, potInventory, inputSlotCount, safeContainerSlot);
            synchronized (LAST_RECIPE_BY_POT) {
                LAST_RECIPE_BY_POT.remove(blockEntity);
            }
        }

        if (!canInsertResolvedIngredients(potInventory, ingredientStacks, inputSlotCount)) {
            return false;
        }
        if (!canInsertResolvedContainerCost(potInventory, containerCost, safeContainerSlot)) {
            return false;
        }

        if (simulate) {
            return true;
        }

        if (!insertResolvedIngredients(potInventory, ingredientStacks, inputSlotCount)) {
            return false;
        }
        if (!insertResolvedContainerCost(potInventory, containerCost, safeContainerSlot)) {
            return false;
        }

        synchronized (LAST_RECIPE_BY_POT) {
            LAST_RECIPE_BY_POT.put(blockEntity, recipe);
        }
        blockEntity.setChanged();
        CookingPotHeatBridge.tryIgniteManagedOvenForPot(blockEntity);
        return true;
    }

    private static boolean canFitAfterFlush(final IItemHandler potInventory,
                                            final List<ItemStack> ingredientStacks,
                                            final ItemStack containerCost,
                                            final int inputSlotCount,
                                            final int containerSlot) {
        int requiredInputSlots = 0;
        for (final ItemStack ingredientStack : ingredientStacks) {
            if (!ingredientStack.isEmpty()) {
                requiredInputSlots++;
            }
        }
        if (requiredInputSlots > inputSlotCount) {
            return false;
        }

        if (containerCost.isEmpty()) {
            return true;
        }

        final int slotLimit = Math.max(1, potInventory.getSlotLimit(containerSlot));
        final int maxStack = Math.min(slotLimit, containerCost.getMaxStackSize());
        return containerCost.getCount() <= maxStack;
    }

    private static boolean canInsertResolvedIngredients(final IItemHandler potInventory,
                                                        final List<ItemStack> ingredientStacks,
                                                        final int inputSlotCount) {
        int slot = 0;
        for (final ItemStack ingredientStack : ingredientStacks) {
            if (ingredientStack.isEmpty()) {
                continue;
            }
            if (slot >= inputSlotCount) {
                return false;
            }

            final ItemStack unit = ingredientStack.copyWithCount(1);
            final ItemStack remaining = potInventory.insertItem(slot, unit, true);
            if (!remaining.isEmpty()) {
                return false;
            }
            slot++;
        }
        return true;
    }

    private static boolean insertResolvedIngredients(final IItemHandler potInventory,
                                                     final List<ItemStack> ingredientStacks,
                                                     final int inputSlotCount) {
        int slot = 0;
        for (final ItemStack ingredientStack : ingredientStacks) {
            if (ingredientStack.isEmpty()) {
                continue;
            }
            if (slot >= inputSlotCount) {
                return false;
            }

            final ItemStack unit = ingredientStack.copyWithCount(1);
            final ItemStack remaining = potInventory.insertItem(slot, unit, false);
            if (!remaining.isEmpty()) {
                return false;
            }
            slot++;
        }
        return true;
    }

    private static boolean canInsertResolvedContainerCost(final IItemHandler potInventory,
                                                          final ItemStack containerCost,
                                                          final int containerSlot) {
        if (containerCost.isEmpty()) {
            return true;
        }

        final ItemStack existing = potInventory.getStackInSlot(containerSlot);
        final int slotLimit = Math.max(1, potInventory.getSlotLimit(containerSlot));
        if (existing.isEmpty()) {
            final int maxStack = Math.min(slotLimit, containerCost.getMaxStackSize());
            return containerCost.getCount() <= maxStack;
        }

        if (!MinecraftApiCompat.isSameItemSameData(existing, containerCost)) {
            return false;
        }

        final int maxStack = Math.min(slotLimit, existing.getMaxStackSize());
        return existing.getCount() + containerCost.getCount() <= maxStack;
    }

    private static boolean insertResolvedContainerCost(final IItemHandler potInventory,
                                                       final ItemStack containerCost,
                                                       final int containerSlot) {
        if (containerCost.isEmpty()) {
            return true;
        }

        int remainingCount = containerCost.getCount();
        while (remainingCount > 0) {
            final ItemStack unit = containerCost.copyWithCount(1);
            final ItemStack remainder = potInventory.insertItem(containerSlot, unit, false);
            if (!remainder.isEmpty()) {
                return false;
            }
            remainingCount--;
        }
        return true;
    }

    private static Object potNotConnectedOperation() {
        final Object cached = potNotConnectedOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_POT_NOT_CONNECTED_KEY, ChatFormatting.RED);
        potNotConnectedOperation = created;
        return created;
    }

    private static Object potInputBlockedOperation() {
        final Object cached = potInputBlockedOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_POT_INPUT_BLOCKED_KEY, ChatFormatting.RED);
        potInputBlockedOperation = created;
        return created;
    }

    private static Object potContainerBlockedOperation() {
        final Object cached = potContainerBlockedOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_POT_CONTAINER_BLOCKED_KEY, ChatFormatting.RED);
        potContainerBlockedOperation = created;
        return created;
    }

    private static Object potTransferFailedOperation() {
        final Object cached = potTransferFailedOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_POT_TRANSFER_FAILED_KEY, ChatFormatting.RED);
        potTransferFailedOperation = created;
        return created;
    }

    private static boolean isRecipeAcceptedForTarget(final Recipe<?> recipe, final String targetKey) {
        return recipe instanceof CookingPotIndexedRecipe indexedRecipe
                && targetKey.equals(indexedRecipe.targetKey());
    }

    private static TransferFailure transferRecipeToPot(final BlockEntity blockEntity,
                                                       final Recipe<?> recipe,
                                                       final List<?> ingredientTokens) {
        final IItemHandler potInventory = resolvePotInventory(blockEntity);
        if (potInventory == null) {
            return TransferFailure.NO_INVENTORY;
        }

        final Class<?> blockEntityClass = blockEntity.getClass();
        final int mealDisplaySlot = resolveStaticIntField(blockEntityClass, POT_MEAL_DISPLAY_SLOT_FIELD, 6);
        final int containerSlot = resolveStaticIntField(blockEntityClass, POT_CONTAINER_SLOT_FIELD, 7);
        final int outputSlot = resolveStaticIntField(blockEntityClass, POT_OUTPUT_SLOT_FIELD, 8);
        final int inputSlotCount = Math.max(1, Math.min(mealDisplaySlot, Math.min(containerSlot, outputSlot)));
        final int safeContainerSlot = Math.max(inputSlotCount, containerSlot);

        final Recipe<?> previousRecipe;
        synchronized (LAST_RECIPE_BY_POT) {
            previousRecipe = LAST_RECIPE_BY_POT.get(blockEntity);
        }
        if (previousRecipe != null && previousRecipe != recipe) {
            flushPendingPotInputs(blockEntity, potInventory, inputSlotCount, safeContainerSlot);
            synchronized (LAST_RECIPE_BY_POT) {
                LAST_RECIPE_BY_POT.remove(blockEntity);
            }
        }

        final int syntheticTokenCount = recipe instanceof CookingPotIndexedRecipe indexedRecipe
                ? indexedRecipe.syntheticIngredientCount()
                : 0;
        final int ingredientEnd = Math.min(ingredientTokens.size(), recipe.getIngredients().size());

        if (!canInsertIntoInputSlots(potInventory, ingredientTokens, syntheticTokenCount, ingredientEnd, inputSlotCount)) {
            return TransferFailure.INPUT_SLOT_BLOCKED;
        }
        final boolean canInsertContainers = canInsertIntoContainerSlot(
                potInventory,
                ingredientTokens,
                ingredientEnd,
                ingredientTokens.size(),
                safeContainerSlot
        );

        if (!consumeIntoInputSlots(potInventory, ingredientTokens, syntheticTokenCount, ingredientEnd, inputSlotCount)) {
            return TransferFailure.INPUT_TRANSFER_FAILED;
        }
        if (canInsertContainers
                && !consumeIntoContainerSlot(potInventory, ingredientTokens, ingredientEnd, ingredientTokens.size(), safeContainerSlot)) {
            return TransferFailure.CONTAINER_TRANSFER_FAILED;
        }

        synchronized (LAST_RECIPE_BY_POT) {
            LAST_RECIPE_BY_POT.put(blockEntity, recipe);
        }

        blockEntity.setChanged();
        CookingPotHeatBridge.tryIgniteManagedOvenForPot(blockEntity);
        return TransferFailure.NONE;
    }

    private static Object feedbackOperation(final String translationKey, final ChatFormatting style) {
        return CfbhRuntime.newKitchenOperationWithFeedback(
                Component.translatable(translationKey).withStyle(style)
        );
    }

    private static Object transferFailureOperation(final TransferFailure failure) {
        return switch (failure) {
            case INPUT_SLOT_BLOCKED -> potInputBlockedOperation();
            case CONTAINER_SLOT_BLOCKED -> potContainerBlockedOperation();
            case NO_INVENTORY, INPUT_TRANSFER_FAILED, CONTAINER_TRANSFER_FAILED -> potTransferFailedOperation();
            case NONE -> CfbhRuntime.kitchenOperationEmpty();
        };
    }

    private static void flushPendingPotInputs(final BlockEntity blockEntity,
                                              final IItemHandler potInventory,
                                              final int inputSlotCount,
                                              final int containerSlot) {
        for (int slot = 0; slot < inputSlotCount; slot++) {
            final ItemStack extracted = potInventory.extractItem(slot, Integer.MAX_VALUE, false);
            returnStackToPlayerOrWorld(blockEntity, extracted);
        }

        final ItemStack extractedContainer = potInventory.extractItem(containerSlot, Integer.MAX_VALUE, false);
        returnStackToPlayerOrWorld(blockEntity, extractedContainer);

        // Reset active progress when switching to a different requested recipe.
        setIntField(blockEntity, POT_COOK_TIME_FIELD, 0);
        setIntField(blockEntity, POT_COOK_TIME_TOTAL_FIELD, 0);
        blockEntity.setChanged();
    }

    private static void returnStackToPlayerOrWorld(final BlockEntity blockEntity, final ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        final Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        final double x = blockEntity.getBlockPos().getX() + 0.5;
        final double y = blockEntity.getBlockPos().getY() + 0.75;
        final double z = blockEntity.getBlockPos().getZ() + 0.5;
        final Player nearestPlayer = level.getNearestPlayer(x, y, z, 8.0, false);
        final ItemStack remaining = stack.copy();
        if (nearestPlayer != null && nearestPlayer.getInventory().add(remaining)) {
            return;
        }

        if (!remaining.isEmpty()) {
            Containers.dropItemStack(level, x, y, z, remaining);
        }
    }

    private static boolean canInsertIntoInputSlots(final IItemHandler potInventory,
                                                   final List<?> ingredientTokens,
                                                   final int startInclusive,
                                                   final int endExclusive,
                                                   final int inputSlotCount) {
        int slot = 0;
        for (int index = startInclusive; index < endExclusive && slot < inputSlotCount; index++, slot++) {
            final Object token = ingredientTokens.get(index);
            if (CfbhRuntime.isEmptyIngredientToken(token)) {
                continue;
            }

            final ItemStack ingredient = CfbhRuntime.peekIngredientToken(token).copyWithCount(1);
            if (ingredient.isEmpty()) {
                return false;
            }

            final ItemStack remaining = potInventory.insertItem(slot, ingredient, true);
            if (!remaining.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static boolean canInsertIntoContainerSlot(final IItemHandler potInventory,
                                                      final List<?> ingredientTokens,
                                                      final int startInclusive,
                                                      final int endExclusive,
                                                      final int containerSlot) {
        for (int index = startInclusive; index < endExclusive; index++) {
            final Object token = ingredientTokens.get(index);
            if (CfbhRuntime.isEmptyIngredientToken(token)) {
                continue;
            }

            final ItemStack container = CfbhRuntime.peekIngredientToken(token).copyWithCount(1);
            if (container.isEmpty()) {
                return false;
            }

            final ItemStack remaining = potInventory.insertItem(containerSlot, container, true);
            if (!remaining.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static boolean consumeIntoInputSlots(final IItemHandler potInventory,
                                                 final List<?> ingredientTokens,
                                                 final int startInclusive,
                                                 final int endExclusive,
                                                 final int inputSlotCount) {
        int slot = 0;
        for (int index = startInclusive; index < endExclusive && slot < inputSlotCount; index++, slot++) {
            final Object token = ingredientTokens.get(index);
            if (CfbhRuntime.isEmptyIngredientToken(token)) {
                continue;
            }

            final ItemStack consumed = CfbhRuntime.consumeIngredientToken(token);
            if (consumed.isEmpty()) {
                return false;
            }

            final ItemStack oneIngredient = consumed.copyWithCount(1);
            final ItemStack remaining = potInventory.insertItem(slot, oneIngredient, false);
            if (!remaining.isEmpty()) {
                CfbhRuntime.restoreIngredientToken(token, consumed);
                return false;
            }
        }

        return true;
    }

    private static boolean consumeIntoContainerSlot(final IItemHandler potInventory,
                                                    final List<?> ingredientTokens,
                                                    final int startInclusive,
                                                    final int endExclusive,
                                                    final int containerSlot) {
        for (int index = startInclusive; index < endExclusive; index++) {
            final Object token = ingredientTokens.get(index);
            if (CfbhRuntime.isEmptyIngredientToken(token)) {
                continue;
            }

            final ItemStack consumed = CfbhRuntime.consumeIngredientToken(token);
            if (consumed.isEmpty()) {
                return false;
            }

            final ItemStack remaining = potInventory.insertItem(containerSlot, consumed.copy(), false);
            if (!remaining.isEmpty()) {
                CfbhRuntime.restoreIngredientToken(token, consumed);
                return false;
            }
        }

        return true;
    }

    private static IItemHandler resolvePotInventory(final BlockEntity blockEntity) {
        final Object inventoryFromMethod = CfbhRuntime.invokeNoArg(blockEntity, POT_GET_INVENTORY_METHOD);
        if (inventoryFromMethod instanceof IItemHandler itemHandler) {
            return itemHandler;
        }

        final Field inventoryField = findField(blockEntity.getClass(), POT_INVENTORY_FIELD);
        if (inventoryField == null) {
            return null;
        }

        try {
            inventoryField.setAccessible(true);
            final Object value = inventoryField.get(blockEntity);
            if (value instanceof IItemHandler itemHandler) {
                return itemHandler;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }

        return null;
    }

    private static int resolveStaticIntField(final Class<?> ownerClass,
                                             final String fieldName,
                                             final int fallback) {
        final Field field = findField(ownerClass, fieldName);
        if (field == null) {
            return fallback;
        }

        try {
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static void setIntField(final Object target, final String fieldName, final int value) {
        final Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return;
        }

        try {
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static Field findField(final Class<?> ownerClass, final String fieldName) {
        Class<?> current = ownerClass;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean requiredMarkersSatisfied(final BlockEntity blockEntity, final List<String> requiredMarkerKeys) {
        for (final String requiredMarkerKey : requiredMarkerKeys) {
            final Predicate<BlockEntity> requirementCheck = REQUIRED_MARKER_CHECKS.get(requiredMarkerKey);
            if (requirementCheck != null && !requirementCheck.test(blockEntity)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasConnectedDungeonOven(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        final BlockPos tablePos = blockEntity.getBlockPos().below();
        try {
            final Class<?> kitchenImplClass = Class.forName(CFBH_KITCHEN_IMPL_CLASS);
            final Constructor<?> constructor = kitchenImplClass.getConstructor(Level.class, BlockPos.class);
            final Object kitchen = constructor.newInstance(level, tablePos);
            final Method getItemProcessors = kitchenImplClass.getMethod("getItemProcessors");
            final Object processorsObject = getItemProcessors.invoke(kitchen);
            if (processorsObject instanceof Iterable<?> processors) {
                for (final Object processor : processors) {
                    if (processor instanceof BlockEntity processorBlockEntity
                            && DungeonOvenCompat.isDungeonOvenBlockEntity(processorBlockEntity)) {
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to local adjacency check.
        }

        for (final var direction : net.minecraft.core.Direction.values()) {
            final BlockEntity nearbyBlockEntity = level.getBlockEntity(tablePos.relative(direction));
            if (DungeonOvenCompat.isDungeonOvenBlockEntity(nearbyBlockEntity)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isCfbhCookingTableBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Class<?> blockEntityClass = blockEntity.getClass();
        final String className = blockEntityClass.getName();
        for (final String candidateClassName : CFBH_COOKING_TABLE_BLOCK_ENTITY_CLASS_CANDIDATES) {
            if (candidateClassName.equals(className)) {
                return true;
            }
        }
        return false;
    }
}
