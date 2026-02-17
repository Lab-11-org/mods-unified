package org.lab_11.modsunified.impl.cookingforblockheads;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.block.entity.CookingTableBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

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
    private static final BlockCapability<KitchenItemProcessor, Void> CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS, "kitchen_item_processor"),
                    KitchenItemProcessor.class
            );
    private static final String CFBH_KITCHEN_IMPL_CLASS = "net.blay09.mods.cookingforblockheads.crafting.KitchenImpl";
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

    private static final KitchenOperation POT_TRANSFER_OPERATION = new KitchenOperation() {
        @Override
        public Optional<Component> getFeedback() {
            return Optional.of(Component.translatable(FEEDBACK_MOVED_TO_POT_KEY).withStyle(ChatFormatting.YELLOW));
        }
    };
    private static final KitchenOperation POT_NOT_CONNECTED_OPERATION = feedbackOperation(
            FEEDBACK_POT_NOT_CONNECTED_KEY,
            ChatFormatting.RED
    );
    private static final KitchenOperation POT_INPUT_BLOCKED_OPERATION = feedbackOperation(
            FEEDBACK_POT_INPUT_BLOCKED_KEY,
            ChatFormatting.RED
    );
    private static final KitchenOperation POT_CONTAINER_BLOCKED_OPERATION = feedbackOperation(
            FEEDBACK_POT_CONTAINER_BLOCKED_KEY,
            ChatFormatting.RED
    );
    private static final KitchenOperation POT_TRANSFER_FAILED_OPERATION = feedbackOperation(
            FEEDBACK_POT_TRANSFER_FAILED_KEY,
            ChatFormatting.RED
    );
    private static final Map<BlockEntity, Recipe<?>> LAST_RECIPE_BY_POT = new WeakHashMap<>();

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

    public static KitchenItemProcessor createProcessor(final BlockEntity blockEntity,
                                                       final Set<RecipeType<?>> supportedRecipeTypes,
                                                       final List<String> requiredMarkerKeys,
                                                       final String targetKey) {
        return new KitchenItemProcessor() {
            @Override
            public boolean canProcess(final RecipeType<?> recipeType) {
                return supportedRecipeTypes.contains(recipeType)
                        && requiredMarkersSatisfied(blockEntity, requiredMarkerKeys);
            }

            @Override
            public KitchenOperation processRecipe(final Recipe<?> recipe, final List<IngredientToken> ingredientTokens) {
                if (!isRecipeAcceptedForTarget(recipe, targetKey)) {
                    return KitchenOperation.EMPTY;
                }

                final boolean directTablePlacement = isDirectlyAboveCookingTable(blockEntity);
                final boolean ovenConnectedPlacement =
                        CookingPotHeatBridge.isTargetPotConnectedForCookingTable(blockEntity, targetKey);
                if (!directTablePlacement && !ovenConnectedPlacement) {
                    return POT_NOT_CONNECTED_OPERATION;
                }

                final TransferFailure transferFailure = transferRecipeToPot(blockEntity, recipe, ingredientTokens);
                if (transferFailure != TransferFailure.NONE) {
                    return transferFailureOperation(transferFailure);
                }

                return POT_TRANSFER_OPERATION;
            }
        };
    }

    public static boolean isDirectlyAboveCookingTable(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Level level = blockEntity.getLevel();
        if (level == null) {
            return false;
        }

        return level.getBlockEntity(blockEntity.getBlockPos().below()) instanceof CookingTableBlockEntity;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(final RegisterCapabilitiesEvent event, final List<CookingPotBridgeTarget> targets) {
        for (final CookingPotBridgeTarget target : targets) {
            final var recipeTypeOptional = target.resolveRecipeType();
            final var blockEntityTypeOptional = target.resolveBlockEntityType();
            if (recipeTypeOptional.isEmpty() || blockEntityTypeOptional.isEmpty()) {
                continue;
            }

            final RecipeType<?> recipeType = recipeTypeOptional.get();
            final BlockEntityType<?> blockEntityType = blockEntityTypeOptional.get();

            event.registerBlockEntity(
                    CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY,
                    (BlockEntityType) blockEntityType,
                    (blockEntity, context) -> createProcessor(
                            blockEntity,
                            Set.of(recipeType),
                            target.requiredMarkerKeys(),
                            target.targetKey()
                    )
            );
        }
    }

    private static boolean isRecipeAcceptedForTarget(final Recipe<?> recipe, final String targetKey) {
        return recipe instanceof CookingPotIndexedRecipe indexedRecipe
                && targetKey.equals(indexedRecipe.targetKey());
    }

    private static TransferFailure transferRecipeToPot(final BlockEntity blockEntity,
                                                       final Recipe<?> recipe,
                                                       final List<IngredientToken> ingredientTokens) {
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
        return TransferFailure.NONE;
    }

    private static KitchenOperation feedbackOperation(final String translationKey, final ChatFormatting style) {
        return new KitchenOperation() {
            @Override
            public Optional<Component> getFeedback() {
                return Optional.of(Component.translatable(translationKey).withStyle(style));
            }
        };
    }

    private static KitchenOperation transferFailureOperation(final TransferFailure failure) {
        return switch (failure) {
            case INPUT_SLOT_BLOCKED -> POT_INPUT_BLOCKED_OPERATION;
            case CONTAINER_SLOT_BLOCKED -> POT_CONTAINER_BLOCKED_OPERATION;
            case NO_INVENTORY, INPUT_TRANSFER_FAILED, CONTAINER_TRANSFER_FAILED -> POT_TRANSFER_FAILED_OPERATION;
            case NONE -> KitchenOperation.EMPTY;
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
                                                   final List<IngredientToken> ingredientTokens,
                                                   final int startInclusive,
                                                   final int endExclusive,
                                                   final int inputSlotCount) {
        int slot = 0;
        for (int index = startInclusive; index < endExclusive && slot < inputSlotCount; index++, slot++) {
            final IngredientToken token = ingredientTokens.get(index);
            if (token == null || token == IngredientToken.EMPTY) {
                continue;
            }

            final ItemStack ingredient = token.peek().copyWithCount(1);
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
                                                      final List<IngredientToken> ingredientTokens,
                                                      final int startInclusive,
                                                      final int endExclusive,
                                                      final int containerSlot) {
        for (int index = startInclusive; index < endExclusive; index++) {
            final IngredientToken token = ingredientTokens.get(index);
            if (token == null || token == IngredientToken.EMPTY) {
                continue;
            }

            final ItemStack container = token.peek().copyWithCount(1);
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
                                                 final List<IngredientToken> ingredientTokens,
                                                 final int startInclusive,
                                                 final int endExclusive,
                                                 final int inputSlotCount) {
        int slot = 0;
        for (int index = startInclusive; index < endExclusive && slot < inputSlotCount; index++, slot++) {
            final IngredientToken token = ingredientTokens.get(index);
            if (token == null || token == IngredientToken.EMPTY) {
                continue;
            }

            final ItemStack consumed = token.consume();
            if (consumed.isEmpty()) {
                return false;
            }

            final ItemStack oneIngredient = consumed.copyWithCount(1);
            final ItemStack remaining = potInventory.insertItem(slot, oneIngredient, false);
            if (!remaining.isEmpty()) {
                token.restore(consumed);
                return false;
            }
        }

        return true;
    }

    private static boolean consumeIntoContainerSlot(final IItemHandler potInventory,
                                                    final List<IngredientToken> ingredientTokens,
                                                    final int startInclusive,
                                                    final int endExclusive,
                                                    final int containerSlot) {
        for (int index = startInclusive; index < endExclusive; index++) {
            final IngredientToken token = ingredientTokens.get(index);
            if (token == null || token == IngredientToken.EMPTY) {
                continue;
            }

            final ItemStack consumed = token.consume();
            if (consumed.isEmpty()) {
                return false;
            }

            final ItemStack oneContainer = consumed.copyWithCount(1);
            final ItemStack remaining = potInventory.insertItem(containerSlot, oneContainer, false);
            if (!remaining.isEmpty()) {
                token.restore(consumed);
                return false;
            }
        }

        return true;
    }

    private static IItemHandler resolvePotInventory(final BlockEntity blockEntity) {
        final Object inventoryFromMethod = invokeNoArg(blockEntity, POT_GET_INVENTORY_METHOD);
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
}
