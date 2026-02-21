package org.lab_11.modsunified.impl.cookingforblockheads;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.lab_11.modsunified.impl.platform.MinecraftApiCompat;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class CookingPotProcessorCapability {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_STOCKPOT_TRANSFER = Boolean.getBoolean("lab11.debug.stockpot_transfer");
    private static final String CFBH_COOKING_REGISTRY_CLASS = "net.blay09.mods.cookingforblockheads.registry.CookingRegistry";
    private static final String CFBH_GET_WATER_ITEMS_METHOD = "getWaterItems";
    private static final ResourceLocation CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY_ID =
            MinecraftApiCompat.resourceLocation(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS, "kitchen_item_processor");
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
    private static final String FEEDBACK_STOCKPOT_MISSING_LID_KEY =
            "lab_11_mods_unified.feedback.cooking_table.stockpot_missing_lid";
    private static final String FEEDBACK_STOCKPOT_MISSING_SOUP_BASE_KEY =
            "lab_11_mods_unified.feedback.cooking_table.stockpot_missing_soup_base";
    private static final String FEEDBACK_STOCKPOT_COOKING_KEY =
            "lab_11_mods_unified.feedback.cooking_table.stockpot_cooking";
    private static final String POT_COOK_TIME_FIELD = "cookTime";
    private static final String POT_COOK_TIME_TOTAL_FIELD = "cookTimeTotal";
    private static final String KALEIDOSCOPE_POT_BLOCK_ENTITY_CLASS =
            "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity";
    private static final String KALEIDOSCOPE_STOCKPOT_BLOCK_ENTITY_CLASS =
            "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity";
    private static final String KALEIDOSCOPE_POT_INPUTS_FIELD = "inputs";
    private static final String KALEIDOSCOPE_POT_STATUS_FIELD = "status";
    private static final String KALEIDOSCOPE_STOCKPOT_SOUP_BASE_ID_FIELD = "soupBaseId";
    private static final String KALEIDOSCOPE_POT_REFRESH_METHOD = "refresh";
    private static final String KALEIDOSCOPE_PROPERTY_HAS_OIL = "has_oil";
    private static final String KALEIDOSCOPE_PROPERTY_HAS_LID = "has_lid";
    private static final int KALEIDOSCOPE_POT_STATUS_PUT_INGREDIENT = 0;
    private static final int KALEIDOSCOPE_STOCKPOT_STATUS_PUT_SOUP_BASE = 0;
    private static final int KALEIDOSCOPE_STOCKPOT_STATUS_PUT_INGREDIENT = 1;
    private static final int KALEIDOSCOPE_STOCKPOT_STATUS_COOKING = 2;
    private static final ResourceLocation VANILLA_WATER_SOUP_BASE_ID =
            MinecraftApiCompat.resourceLocation("minecraft", "water");

    private static final Map<String, Predicate<BlockEntity>> REQUIRED_MARKER_CHECKS = Map.of(
            BridgeKeys.MARKER_DUNGEON_OVEN, CookingPotProcessorCapability::hasConnectedDungeonOven
    );

    private static final Map<BlockEntity, Recipe<?>> LAST_RECIPE_BY_POT = new WeakHashMap<>();
    private static volatile List<ItemStack> cachedWaterSoupBaseCandidates;

    private static volatile BlockCapability<Object, Void> kitchenItemProcessorCapability;
    private static volatile Object potTransferOperation;
    private static volatile Object potNotConnectedOperation;
    private static volatile Object potInputBlockedOperation;
    private static volatile Object potContainerBlockedOperation;
    private static volatile Object potTransferFailedOperation;
    private static volatile Object potStockpotMissingLidOperation;
    private static volatile Object potStockpotMissingSoupBaseOperation;
    private static volatile Object potStockpotCookingOperation;

    private enum TransferFailure {
        NONE,
        NO_INVENTORY,
        INPUT_SLOT_BLOCKED,
        CONTAINER_SLOT_BLOCKED,
        INPUT_TRANSFER_FAILED,
        CONTAINER_TRANSFER_FAILED,
        STOCKPOT_MISSING_LID,
        STOCKPOT_MISSING_SOUP_BASE,
        STOCKPOT_COOKING
    }

    private record TokenConsumption(Object token, ItemStack stack) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(final RegisterCapabilitiesEvent event, final List<CookingPotBridgeTarget> targets) {
        final BlockCapability<Object, Void> capability = resolveKitchenItemProcessorCapability();
        if (capability == null) {
            return;
        }

        for (final CookingPotBridgeTarget target : targets) {
            final var recipeTypeOptional = target.resolveRecipeType();
            final var blockEntityTypeOptional = target.resolveBlockEntityType();
            if (recipeTypeOptional.isEmpty() || blockEntityTypeOptional.isEmpty()) {
                continue;
            }

            final RecipeType<?> recipeType = recipeTypeOptional.get();
            final BlockEntityType<?> blockEntityType = blockEntityTypeOptional.get();

            event.registerBlockEntity(
                    (BlockCapability) capability,
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

    private static BlockCapability<Object, Void> resolveKitchenItemProcessorCapability() {
        final BlockCapability<Object, Void> cached = kitchenItemProcessorCapability;
        if (cached != null) {
            return cached;
        }

        final Class<?> processorClass = CfbhRuntime.kitchenItemProcessorClass();
        if (processorClass == null) {
            return null;
        }

        final BlockCapability<Object, Void> created = BlockCapability.createVoid(
                CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY_ID,
                (Class<Object>) processorClass
        );
        kitchenItemProcessorCapability = created;
        return created;
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
        if (isKaleidoscopeCookwareTarget(recipe) && isKaleidoscopeCookwareBlockEntity(blockEntity)) {
            return transferResolvedStacksToKaleidoscopePot(blockEntity, recipe, ingredientStacks, simulate);
        }

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

    private static Object potStockpotMissingLidOperation() {
        final Object cached = potStockpotMissingLidOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_STOCKPOT_MISSING_LID_KEY, ChatFormatting.RED);
        potStockpotMissingLidOperation = created;
        return created;
    }

    private static Object potStockpotMissingSoupBaseOperation() {
        final Object cached = potStockpotMissingSoupBaseOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_STOCKPOT_MISSING_SOUP_BASE_KEY, ChatFormatting.RED);
        potStockpotMissingSoupBaseOperation = created;
        return created;
    }

    private static Object potStockpotCookingOperation() {
        final Object cached = potStockpotCookingOperation;
        if (cached != null) {
            return cached;
        }
        final Object created = feedbackOperation(FEEDBACK_STOCKPOT_COOKING_KEY, ChatFormatting.YELLOW);
        potStockpotCookingOperation = created;
        return created;
    }

    private static boolean isRecipeAcceptedForTarget(final Recipe<?> recipe, final String targetKey) {
        return recipe instanceof CookingPotIndexedRecipe indexedRecipe
                && targetKey.equals(indexedRecipe.targetKey());
    }

    private static TransferFailure transferRecipeToPot(final BlockEntity blockEntity,
                                                       final Recipe<?> recipe,
                                                       final List<?> ingredientTokens) {
        if (isKaleidoscopeCookwareTarget(recipe) && isKaleidoscopeCookwareBlockEntity(blockEntity)) {
            return transferRecipeTokensToKaleidoscopePot(blockEntity, recipe, ingredientTokens);
        }

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

    private static TransferFailure transferRecipeTokensToKaleidoscopePot(final BlockEntity blockEntity,
                                                                         final Recipe<?> recipe,
                                                                         final List<?> ingredientTokens) {
        final boolean stockpotRecipe = isKaleidoscopeStockpotTarget(recipe);
        final ResourceLocation requiredSoupBaseId = stockpotRecipe
                ? StockpotSoupBridge.resolveRequiredSoupBaseId(recipe)
                : null;
        final int initialStatus = readKaleidoscopePotStatus(blockEntity);
        final boolean stockpotNeedsSoupBase =
                stockpotRecipe && initialStatus == KALEIDOSCOPE_STOCKPOT_STATUS_PUT_SOUP_BASE;
        if (stockpotRecipe) {
            if (initialStatus == KALEIDOSCOPE_STOCKPOT_STATUS_COOKING) {
                if (DEBUG_STOCKPOT_TRANSFER) {
                    LOGGER.info("Stockpot processor rejected recipe: stockpot is already cooking.");
                }
                return TransferFailure.STOCKPOT_COOKING;
            }
            if (hasBlockStateBooleanPropertyValue(blockEntity, KALEIDOSCOPE_PROPERTY_HAS_LID, true)) {
                if (DEBUG_STOCKPOT_TRANSFER) {
                    LOGGER.info("Stockpot processor rejected recipe: stockpot already has lid and is busy.");
                }
                return TransferFailure.STOCKPOT_COOKING;
            }
            if (initialStatus != KALEIDOSCOPE_STOCKPOT_STATUS_PUT_SOUP_BASE
                    && initialStatus != KALEIDOSCOPE_STOCKPOT_STATUS_PUT_INGREDIENT) {
                if (DEBUG_STOCKPOT_TRANSFER) {
                    LOGGER.info("Stockpot processor rejected recipe: unsupported stockpot status={}.", initialStatus);
                }
                return TransferFailure.INPUT_SLOT_BLOCKED;
            }
        } else if (!isKaleidoscopePotReadyForIngredientInsert(blockEntity, recipe)) {
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info("Stockpot processor rejected recipe: pot not ready for ingredient insert.");
            }
            return TransferFailure.INPUT_SLOT_BLOCKED;
        }

        final List<ItemStack> inputSlots = resolveKaleidoscopePotInputs(blockEntity);
        if (inputSlots == null) {
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info("Stockpot processor rejected recipe: input slots unavailable.");
            }
            return TransferFailure.NO_INVENTORY;
        }

        final int tokenStartIndex = resolveKaleidoscopeIngredientTokenStartIndex(recipe, ingredientTokens);
        final List<Ingredient> requiredIngredients = resolveKaleidoscopeRequiredIngredients(recipe);
        final List<Object> nonEmptyTokens = new ArrayList<>();
        for (int tokenIndex = tokenStartIndex; tokenIndex < ingredientTokens.size(); tokenIndex++) {
            final Object token = ingredientTokens.get(tokenIndex);
            if (CfbhRuntime.isEmptyIngredientToken(token)) {
                continue;
            }
            nonEmptyTokens.add(token);
        }

        if (nonEmptyTokens.size() < requiredIngredients.size()) {
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info(
                        "Stockpot processor rejected recipe: nonEmptyTokens={} < requiredIngredients={}.",
                        nonEmptyTokens.size(),
                        requiredIngredients.size()
                );
            }
            return TransferFailure.INPUT_TRANSFER_FAILED;
        }

        final List<TokenConsumption> consumed = new ArrayList<>();
        final List<ItemStack> ingredientUnits = new ArrayList<>();
        final List<Ingredient> unmatchedIngredients = new ArrayList<>(requiredIngredients);
        ItemStack consumedStockpotLid = ItemStack.EMPTY;
        ItemStack consumedSoupBasePrimary = ItemStack.EMPTY;
        ItemStack consumedSoupBaseWaterSupport = ItemStack.EMPTY;
        ItemStack consumedFishSoupIngredient = ItemStack.EMPTY;
        boolean soupBaseProvidedByWaterSink = false;
        boolean soupBaseProvidedByLavaSink = false;
        for (int tokenPosition = 0; tokenPosition < nonEmptyTokens.size(); tokenPosition++) {
            final Object token = nonEmptyTokens.get(tokenPosition);
            final ItemStack peekStack = CfbhRuntime.peekIngredientToken(token);
            final int matchedIngredientIndex = findMatchingIngredientIndex(unmatchedIngredients, peekStack);
            if (matchedIngredientIndex >= 0) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: token consumption returned empty at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.INPUT_TRANSFER_FAILED;
                }

                unmatchedIngredients.remove(matchedIngredientIndex);
                consumed.add(new TokenConsumption(token, consumedStack));
                ingredientUnits.add(consumedStack.copyWithCount(1));
                continue;
            }

            if (!stockpotRecipe) {
                continue;
            }

            if (consumedStockpotLid.isEmpty() && isKaleidoscopeStockpotLid(peekStack)) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: unable to consume startup lid token at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.CONTAINER_TRANSFER_FAILED;
                }
                consumed.add(new TokenConsumption(token, consumedStack));
                consumedStockpotLid = consumedStack.copyWithCount(1);
                continue;
            }

            if (!stockpotNeedsSoupBase) {
                continue;
            }

            if (StockpotSoupBridge.isSyntheticWaterSinkSoupMarker(peekStack)
                    && !soupBaseProvidedByWaterSink
                    && (StockpotSoupBridge.isWaterSoupBase(requiredSoupBaseId)
                    || StockpotSoupBridge.isFishBucketSoupBase(requiredSoupBaseId))) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: unable to consume water sink token at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.CONTAINER_TRANSFER_FAILED;
                }
                consumed.add(new TokenConsumption(token, consumedStack));
                soupBaseProvidedByWaterSink = true;
                continue;
            }

            if (StockpotSoupBridge.isSyntheticLavaSinkSoupMarker(peekStack)
                    && !soupBaseProvidedByLavaSink
                    && StockpotSoupBridge.isLavaSoupBase(requiredSoupBaseId)) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: unable to consume lava sink token at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.CONTAINER_TRANSFER_FAILED;
                }
                consumed.add(new TokenConsumption(token, consumedStack));
                soupBaseProvidedByLavaSink = true;
                continue;
            }

            if (consumedSoupBasePrimary.isEmpty() && isKaleidoscopeSoupBaseCandidate(peekStack, requiredSoupBaseId)) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: unable to consume soup-base token at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.CONTAINER_TRANSFER_FAILED;
                }
                consumed.add(new TokenConsumption(token, consumedStack));
                consumedSoupBasePrimary = consumedStack.copyWithCount(1);
                continue;
            }

            if (StockpotSoupBridge.isFishBucketSoupBase(requiredSoupBaseId)
                    && consumedFishSoupIngredient.isEmpty()
                    && isFishSoupIngredientCandidate(peekStack, requiredSoupBaseId)) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: unable to consume fish soup-base token at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.CONTAINER_TRANSFER_FAILED;
                }
                consumed.add(new TokenConsumption(token, consumedStack));
                consumedFishSoupIngredient = consumedStack.copyWithCount(1);
                continue;
            }

            if (StockpotSoupBridge.isFishBucketSoupBase(requiredSoupBaseId)
                    && consumedSoupBaseWaterSupport.isEmpty()
                    && isKaleidoscopeSoupBaseCandidate(peekStack, VANILLA_WATER_SOUP_BASE_ID)) {
                final ItemStack consumedStack = CfbhRuntime.consumeIngredientToken(token);
                if (consumedStack.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Stockpot processor rejected recipe: unable to consume fish soup-base water support token at tokenPosition={}.",
                                tokenPosition
                        );
                    }
                    return TransferFailure.CONTAINER_TRANSFER_FAILED;
                }
                consumed.add(new TokenConsumption(token, consumedStack));
                consumedSoupBaseWaterSupport = consumedStack.copyWithCount(1);
            }
        }

        if (!unmatchedIngredients.isEmpty()) {
            restoreConsumedTokens(consumed);
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info(
                        "Stockpot processor rejected recipe: ingredientUnits={} < requiredIngredients={}.",
                        ingredientUnits.size(),
                        requiredIngredients.size()
                );
            }
            return TransferFailure.INPUT_TRANSFER_FAILED;
        }

        final boolean missingStockpotLid = isStockpotLidMissingAfterTransfer(
                blockEntity,
                stockpotRecipe,
                consumedStockpotLid
        );
        if (missingStockpotLid && DEBUG_STOCKPOT_TRANSFER) {
            LOGGER.info("Stockpot processor continuing without startup lid; ingredients will be transferred.");
        }

        if (stockpotNeedsSoupBase) {
            final boolean appliedSoupBase;
            if (StockpotSoupBridge.isFishBucketSoupBase(requiredSoupBaseId)) {
                if (!consumedSoupBasePrimary.isEmpty()) {
                    appliedSoupBase = applyKaleidoscopeStockpotSoupBase(blockEntity, consumedSoupBasePrimary);
                } else {
                    final boolean hasWaterSupport = soupBaseProvidedByWaterSink || !consumedSoupBaseWaterSupport.isEmpty();
                    if (consumedFishSoupIngredient.isEmpty() || !hasWaterSupport) {
                        restoreConsumedTokens(consumed);
                        if (DEBUG_STOCKPOT_TRANSFER) {
                            LOGGER.info("Stockpot processor rejected recipe: missing fish soup-base resources.");
                        }
                        return TransferFailure.STOCKPOT_MISSING_SOUP_BASE;
                    }

                    if (!soupBaseProvidedByWaterSink
                            && MinecraftApiCompat.isSameItemSameData(consumedSoupBaseWaterSupport, new ItemStack(Items.WATER_BUCKET))) {
                        returnStackToPlayerOrWorld(blockEntity, new ItemStack(Items.BUCKET));
                    }
                    appliedSoupBase = applyKaleidoscopeStockpotSoupBaseFromSink(blockEntity, requiredSoupBaseId);
                }
            } else if (soupBaseProvidedByLavaSink || soupBaseProvidedByWaterSink) {
                appliedSoupBase = applyKaleidoscopeStockpotSoupBaseFromSink(blockEntity, requiredSoupBaseId);
            } else {
                if (consumedSoupBasePrimary.isEmpty()) {
                    restoreConsumedTokens(consumed);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info("Stockpot processor rejected recipe: missing soup-base token.");
                    }
                    return TransferFailure.STOCKPOT_MISSING_SOUP_BASE;
                }
                appliedSoupBase = applyKaleidoscopeStockpotSoupBase(blockEntity, consumedSoupBasePrimary);
            }
            if (!appliedSoupBase) {
                restoreConsumedTokens(consumed);
                if (DEBUG_STOCKPOT_TRANSFER) {
                    LOGGER.info(
                            "Stockpot processor rejected recipe: failed to apply soup-base id={}, sinkWater={}, sinkLava={}.",
                            requiredSoupBaseId,
                            soupBaseProvidedByWaterSink,
                            soupBaseProvidedByLavaSink
                    );
                }
                return TransferFailure.STOCKPOT_MISSING_SOUP_BASE;
            }
        }

        if (!isKaleidoscopePotReadyForIngredientInsert(blockEntity, recipe)) {
            restoreConsumedTokens(consumed);
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info("Stockpot processor rejected recipe: pot not ready after startup preparation.");
            }
            return TransferFailure.INPUT_SLOT_BLOCKED;
        }

        if (!canFillKaleidoscopePotInputs(inputSlots, ingredientUnits)) {
            restoreConsumedTokens(consumed);
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info(
                        "Stockpot processor rejected recipe: cannot fill input slots. slotCount={}, ingredientUnits={}",
                        inputSlots.size(),
                        ingredientUnits.size()
                );
            }
            return TransferFailure.INPUT_SLOT_BLOCKED;
        }

        if (tryInsertKaleidoscopeIngredientsInteractively(blockEntity, ingredientUnits)) {
            if (isKaleidoscopeStockpotTarget(recipe) && !consumedStockpotLid.isEmpty()) {
                applyKaleidoscopeStockpotLid(blockEntity, consumedStockpotLid);
            }
            synchronized (LAST_RECIPE_BY_POT) {
                LAST_RECIPE_BY_POT.put(blockEntity, recipe);
            }
            markKaleidoscopePotChanged(blockEntity);
            CookingPotHeatBridge.tryIgniteManagedOvenForPot(blockEntity);
            if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
                LOGGER.info(
                        "Stockpot processor applied recipe via interactive insert. ingredientUnits={}, appliedLid={}",
                        ingredientUnits.size(),
                        !consumedStockpotLid.isEmpty()
                );
            }
            return isStockpotLidMissingAfterTransfer(blockEntity, stockpotRecipe, consumedStockpotLid)
                    ? TransferFailure.STOCKPOT_MISSING_LID
                    : TransferFailure.NONE;
        }

        applyKaleidoscopePotInputs(inputSlots, ingredientUnits);
        if (isKaleidoscopeStockpotTarget(recipe) && !consumedStockpotLid.isEmpty()) {
            applyKaleidoscopeStockpotLid(blockEntity, consumedStockpotLid);
        }
        synchronized (LAST_RECIPE_BY_POT) {
            LAST_RECIPE_BY_POT.put(blockEntity, recipe);
        }
        markKaleidoscopePotChanged(blockEntity);
        CookingPotHeatBridge.tryIgniteManagedOvenForPot(blockEntity);
        if (DEBUG_STOCKPOT_TRANSFER && stockpotRecipe) {
            LOGGER.info(
                    "Stockpot processor applied recipe via direct slot fallback. ingredientUnits={}, appliedLid={}",
                    ingredientUnits.size(),
                    !consumedStockpotLid.isEmpty()
            );
        }
        return isStockpotLidMissingAfterTransfer(blockEntity, stockpotRecipe, consumedStockpotLid)
                ? TransferFailure.STOCKPOT_MISSING_LID
                : TransferFailure.NONE;
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
            case STOCKPOT_MISSING_LID -> potStockpotMissingLidOperation();
            case STOCKPOT_MISSING_SOUP_BASE -> potStockpotMissingSoupBaseOperation();
            case STOCKPOT_COOKING -> potStockpotCookingOperation();
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

    private static boolean transferResolvedStacksToKaleidoscopePot(final BlockEntity blockEntity,
                                                                   final Recipe<?> recipe,
                                                                   final List<ItemStack> ingredientStacks,
                                                                   final boolean simulate) {
        if (!isKaleidoscopePotReadyForIngredientInsert(blockEntity, recipe)) {
            return false;
        }

        final List<ItemStack> inputSlots = resolveKaleidoscopePotInputs(blockEntity);
        if (inputSlots == null) {
            return false;
        }

        if (!canFillKaleidoscopePotInputs(inputSlots, ingredientStacks)) {
            return false;
        }

        if (simulate) {
            return true;
        }

        applyKaleidoscopePotInputs(inputSlots, ingredientStacks);
        synchronized (LAST_RECIPE_BY_POT) {
            LAST_RECIPE_BY_POT.put(blockEntity, recipe);
        }
        markKaleidoscopePotChanged(blockEntity);
        CookingPotHeatBridge.tryIgniteManagedOvenForPot(blockEntity);
        return true;
    }

    private static boolean isKaleidoscopeCookwareTarget(final Recipe<?> recipe) {
        return isKaleidoscopePotTarget(recipe) || isKaleidoscopeStockpotTarget(recipe);
    }

    private static boolean isKaleidoscopePotTarget(final Recipe<?> recipe) {
        return recipe instanceof CookingPotIndexedRecipe indexedRecipe
                && BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT.equals(indexedRecipe.targetKey());
    }

    private static boolean isKaleidoscopeStockpotTarget(final Recipe<?> recipe) {
        return recipe instanceof CookingPotIndexedRecipe indexedRecipe
                && BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT.equals(indexedRecipe.targetKey());
    }

    private static boolean isKaleidoscopeCookwareBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final String className = blockEntity.getClass().getName();
        return KALEIDOSCOPE_POT_BLOCK_ENTITY_CLASS.equals(className)
                || KALEIDOSCOPE_STOCKPOT_BLOCK_ENTITY_CLASS.equals(className);
    }

    private static boolean isKaleidoscopePotReadyForIngredientInsert(final BlockEntity blockEntity,
                                                                     final Recipe<?> recipe) {
        if (blockEntity == null || blockEntity.getLevel() == null) {
            return false;
        }

        final int status = readKaleidoscopePotStatus(blockEntity);
        if (status == Integer.MIN_VALUE) {
            return false;
        }

        if (isKaleidoscopePotTarget(recipe)) {
            return hasBlockStateBooleanPropertyValue(blockEntity, KALEIDOSCOPE_PROPERTY_HAS_OIL, true)
                    && status == KALEIDOSCOPE_POT_STATUS_PUT_INGREDIENT;
        }
        if (isKaleidoscopeStockpotTarget(recipe)) {
            return hasBlockStateBooleanPropertyValue(blockEntity, KALEIDOSCOPE_PROPERTY_HAS_LID, false)
                    && status == KALEIDOSCOPE_STOCKPOT_STATUS_PUT_INGREDIENT;
        }

        return false;
    }

    private static int readKaleidoscopePotStatus(final BlockEntity blockEntity) {
        final Field statusField = findField(blockEntity.getClass(), KALEIDOSCOPE_POT_STATUS_FIELD);
        if (statusField == null) {
            return Integer.MIN_VALUE;
        }

        try {
            statusField.setAccessible(true);
            return statusField.getInt(blockEntity);
        } catch (ReflectiveOperationException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static boolean hasBlockStateBooleanPropertyValue(final BlockEntity blockEntity,
                                                             final String propertyName,
                                                             final boolean expectedValue) {
        final BlockState state = blockEntity.getLevel().getBlockState(blockEntity.getBlockPos());
        final var property = state.getProperties().stream()
                .filter(candidate -> propertyName.equals(candidate.getName()))
                .findFirst()
                .orElse(null);
        if (property == null) {
            return false;
        }

        return readBooleanProperty(state, property) == expectedValue;
    }

    private static boolean isKaleidoscopeStockpotLid(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && BridgeKeys.MOD_KALEIDOSCOPE_COOKERY.equals(itemId.getNamespace())
                && BridgeKeys.ITEM_KALEIDOSCOPE_STOCKPOT_LID.equals(itemId.getPath());
    }

    private static List<Ingredient> resolveKaleidoscopeRequiredIngredients(final Recipe<?> recipe) {
        final int start = recipe instanceof CookingPotIndexedRecipe indexedRecipe
                ? Math.max(0, indexedRecipe.syntheticIngredientCount())
                : 0;

        final List<Ingredient> required = new ArrayList<>();
        final List<Ingredient> ingredients = recipe.getIngredients();
        for (int index = start; index < ingredients.size(); index++) {
            final Ingredient ingredient = ingredients.get(index);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            required.add(ingredient);
        }
        return required;
    }

    private static boolean tryInsertKaleidoscopeIngredientsInteractively(final BlockEntity blockEntity,
                                                                         final List<ItemStack> ingredientUnits) {
        if (blockEntity == null || blockEntity.getLevel() == null || ingredientUnits.isEmpty()) {
            return false;
        }

        final Level level = blockEntity.getLevel();
        final BlockPos pos = blockEntity.getBlockPos();
        final Player player = resolveInteractionPlayer(level, pos);
        if (player == null) {
            if (DEBUG_STOCKPOT_TRANSFER) {
                LOGGER.info("Kaleidoscope interactive insert failed: no interaction player resolved.");
            }
            return false;
        }

        final List<ItemStack> inputSlots = resolveKaleidoscopePotInputs(blockEntity);
        if (inputSlots == null) {
            if (DEBUG_STOCKPOT_TRANSFER) {
                LOGGER.info("Kaleidoscope interactive insert failed: input slots unavailable.");
            }
            return false;
        }
        final List<ItemStack> snapshot = copyInputStacks(inputSlots);
        final int expectedInsertedCount = (int) ingredientUnits.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .count();

        try {
            final var bulkResult = InteractiveItemUseBridge.tryInvokeAddAllIngredients(
                    blockEntity,
                    player,
                    ingredientUnits
            );
            if (bulkResult.success()) {
                final int insertedCount = countNonEmptyStacks(inputSlots);
                if (insertedCount >= expectedInsertedCount) {
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info(
                                "Kaleidoscope interactive bulk insert succeeded: expectedInsertedCount={}, actualInsertedCount={}",
                                expectedInsertedCount,
                                insertedCount
                        );
                    }
                    return true;
                }
                restoreInputStacks(inputSlots, snapshot);
                markKaleidoscopePotChanged(blockEntity);
                if (DEBUG_STOCKPOT_TRANSFER) {
                    LOGGER.info(
                            "Kaleidoscope interactive bulk insert rolled back: expectedInsertedCount={}, actualInsertedCount={}",
                            expectedInsertedCount,
                            insertedCount
                    );
                }
                return false;
            }

            for (final ItemStack ingredientUnit : ingredientUnits) {
                if (ingredientUnit.isEmpty()) {
                    continue;
                }

                final var useResult = InteractiveItemUseBridge.tryUseMainHandItemOnBlock(
                        blockEntity,
                        player,
                        ingredientUnit.copyWithCount(1)
                );
                if (!useResult.success()) {
                    restoreInputStacks(inputSlots, snapshot);
                    markKaleidoscopePotChanged(blockEntity);
                    if (DEBUG_STOCKPOT_TRANSFER) {
                        LOGGER.info("Kaleidoscope interactive per-item insert failed: reasonCode={}", useResult.reasonCode());
                    }
                    return false;
                }
            }
            if (DEBUG_STOCKPOT_TRANSFER) {
                LOGGER.info("Kaleidoscope interactive per-item insert succeeded. ingredientUnits={}", ingredientUnits.size());
            }
            return true;
        } catch (RuntimeException ignored) {
            restoreInputStacks(inputSlots, snapshot);
            markKaleidoscopePotChanged(blockEntity);
            if (DEBUG_STOCKPOT_TRANSFER) {
                LOGGER.info("Kaleidoscope interactive insert failed due to runtime exception.");
            }
            return false;
        }
    }

    private static Player resolveInteractionPlayer(final Level level, final BlockPos pos) {
        final Player nearest = level.getNearestPlayer(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                64.0,
                false
        );
        if (nearest != null) {
            return nearest;
        }
        if (!level.players().isEmpty()) {
            return level.players().get(0);
        }
        return null;
    }

    private static List<ItemStack> copyInputStacks(final List<ItemStack> inputSlots) {
        final List<ItemStack> snapshot = new ArrayList<>(inputSlots.size());
        for (final ItemStack stack : inputSlots) {
            snapshot.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return snapshot;
    }

    private static int countNonEmptyStacks(final List<ItemStack> stacks) {
        int count = 0;
        for (final ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void restoreInputStacks(final List<ItemStack> inputSlots, final List<ItemStack> snapshot) {
        final int size = Math.min(inputSlots.size(), snapshot.size());
        for (int index = 0; index < size; index++) {
            final ItemStack stack = snapshot.get(index);
            inputSlots.set(index, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
    }

    private static int resolveKaleidoscopeIngredientTokenStartIndex(final Recipe<?> recipe, final List<?> ingredientTokens) {
        if (!(recipe instanceof CookingPotIndexedRecipe indexedRecipe) || ingredientTokens.isEmpty()) {
            return 0;
        }

        final int synthetic = Math.max(0, indexedRecipe.syntheticIngredientCount());
        if (synthetic <= 0) {
            return 0;
        }

        final Object firstToken = ingredientTokens.get(0);
        if (CfbhRuntime.isEmptyIngredientToken(firstToken)) {
            return 0;
        }

        final ItemStack firstStack = CfbhRuntime.peekIngredientToken(firstToken);
        if (firstStack.isEmpty()) {
            return 0;
        }

        final Ingredient markerIngredient = CookingPotActivationMarkerProvider.markerIngredient(indexedRecipe.targetKey());
        return markerIngredient.test(firstStack) ? synthetic : 0;
    }

    private static int findMatchingIngredientIndex(final List<Ingredient> ingredients, final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < ingredients.size(); index++) {
            final Ingredient ingredient = ingredients.get(index);
            if (ingredient != null && !ingredient.isEmpty() && ingredient.test(stack)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isKaleidoscopeSoupBaseCandidate(final ItemStack stack,
                                                           final ResourceLocation requiredSoupBaseId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (StockpotSoupBridge.isWaterSoupBase(requiredSoupBaseId)) {
            for (final ItemStack candidate : stockpotWaterSoupBaseCandidates()) {
                if (MinecraftApiCompat.isSameItemSameData(stack, candidate)
                        || ItemStack.isSameItem(stack, candidate)) {
                    return true;
                }
            }
            return false;
        }

        final ItemStack exactSoupBase = StockpotSoupBridge.soupBaseBucketStack(requiredSoupBaseId);
        if (exactSoupBase.isEmpty()) {
            return false;
        }

        return MinecraftApiCompat.isSameItemSameData(stack, exactSoupBase) || ItemStack.isSameItem(stack, exactSoupBase);
    }

    private static boolean isFishSoupIngredientCandidate(final ItemStack stack, final ResourceLocation requiredSoupBaseId) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        final ItemStack candidate = StockpotSoupBridge.fishSoupBaseIngredientStack(requiredSoupBaseId);
        if (candidate.isEmpty()) {
            return false;
        }
        return MinecraftApiCompat.isSameItemSameData(stack, candidate) || ItemStack.isSameItem(stack, candidate);
    }

    private static List<ItemStack> stockpotWaterSoupBaseCandidates() {
        final List<ItemStack> cached = cachedWaterSoupBaseCandidates;
        if (cached != null) {
            return cached;
        }

        final List<ItemStack> candidates = new ArrayList<>();
        try {
            final Class<?> registryClass = Class.forName(CFBH_COOKING_REGISTRY_CLASS);
            final Method getWaterItems = registryClass.getMethod(CFBH_GET_WATER_ITEMS_METHOD);
            final Object value = getWaterItems.invoke(null);
            if (value instanceof Iterable<?> iterable) {
                for (final Object entry : iterable) {
                    if (entry instanceof ItemStack stack && !stack.isEmpty()) {
                        addUniqueSoupBaseCandidate(candidates, stack.copyWithCount(1));
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to vanilla water bucket.
        }
        addUniqueSoupBaseCandidate(candidates, new ItemStack(Items.WATER_BUCKET));
        final List<ItemStack> immutable = List.copyOf(candidates);
        cachedWaterSoupBaseCandidates = immutable;
        return immutable;
    }

    private static void addUniqueSoupBaseCandidate(final List<ItemStack> candidates, final ItemStack candidate) {
        for (final ItemStack existing : candidates) {
            if (MinecraftApiCompat.isSameItemSameData(existing, candidate)
                    || ItemStack.isSameItem(existing, candidate)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private static boolean isStockpotLidMissingAfterTransfer(final BlockEntity blockEntity,
                                                             final boolean stockpotRecipe,
                                                             final ItemStack consumedStockpotLid) {
        if (!stockpotRecipe) {
            return false;
        }

        if (blockEntity == null || blockEntity.getLevel() == null) {
            return consumedStockpotLid == null || consumedStockpotLid.isEmpty();
        }
        return !hasBlockStateBooleanPropertyValue(blockEntity, KALEIDOSCOPE_PROPERTY_HAS_LID, true);
    }

    private static boolean applyKaleidoscopeStockpotSoupBase(final BlockEntity blockEntity, final ItemStack soupBaseStack) {
        if (blockEntity == null || blockEntity.getLevel() == null || soupBaseStack.isEmpty()) {
            return false;
        }

        final Level level = blockEntity.getLevel();
        final Player player = resolveInteractionPlayer(level, blockEntity.getBlockPos());
        if (player == null) {
            return false;
        }

        try {
            final Method addSoupBaseMethod = blockEntity.getClass().getMethod(
                    "addSoupBase",
                    Level.class,
                    net.minecraft.world.entity.LivingEntity.class,
                    ItemStack.class
            );
            final Object value = addSoupBaseMethod.invoke(blockEntity, level, player, soupBaseStack.copyWithCount(1));
            if (value instanceof Boolean success && success) {
                markKaleidoscopePotChanged(blockEntity);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to simulated right-click interaction.
        }

        final var useResult = InteractiveItemUseBridge.tryUseMainHandItemOnBlock(
                blockEntity,
                player,
                soupBaseStack.copyWithCount(1)
        );
        if (!useResult.success()) {
            return false;
        }
        markKaleidoscopePotChanged(blockEntity);
        return true;
    }

    private static boolean applyKaleidoscopeStockpotSoupBaseFromSink(final BlockEntity blockEntity,
                                                                     final ResourceLocation requiredSoupBaseId) {
        if (blockEntity == null || blockEntity.getLevel() == null) {
            return false;
        }
        final ResourceLocation effectiveSoupBaseId = requiredSoupBaseId == null
                ? VANILLA_WATER_SOUP_BASE_ID
                : requiredSoupBaseId;

        try {
            final Field soupBaseIdField = findField(blockEntity.getClass(), KALEIDOSCOPE_STOCKPOT_SOUP_BASE_ID_FIELD);
            final Field statusField = findField(blockEntity.getClass(), KALEIDOSCOPE_POT_STATUS_FIELD);
            if (soupBaseIdField != null) {
                soupBaseIdField.setAccessible(true);
                soupBaseIdField.set(blockEntity, effectiveSoupBaseId);
            }
            if (statusField != null) {
                statusField.setAccessible(true);
                statusField.setInt(blockEntity, KALEIDOSCOPE_STOCKPOT_STATUS_PUT_INGREDIENT);
            }
            markKaleidoscopePotChanged(blockEntity);
            return true;
        } catch (ReflectiveOperationException ignored) {
            // Fall back to direct soup-base interaction when fields are unavailable.
        }

        final ItemStack fallbackStack = StockpotSoupBridge.soupBaseBucketStack(effectiveSoupBaseId);
        if (fallbackStack.isEmpty()) {
            return false;
        }
        return applyKaleidoscopeStockpotSoupBase(blockEntity, fallbackStack);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyKaleidoscopeStockpotLid(final BlockEntity blockEntity, final ItemStack lidStack) {
        if (blockEntity == null || blockEntity.getLevel() == null || lidStack.isEmpty()) {
            return;
        }

        try {
            final Method setLidItemMethod = blockEntity.getClass().getMethod("setLidItem", ItemStack.class);
            setLidItemMethod.invoke(blockEntity, lidStack.copyWithCount(1));
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }

        final Level level = blockEntity.getLevel();
        final BlockPos pos = blockEntity.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        final var lidProperty = state.getProperties().stream()
                .filter(property -> KALEIDOSCOPE_PROPERTY_HAS_LID.equals(property.getName()))
                .findFirst()
                .orElse(null);
        if (lidProperty instanceof net.minecraft.world.level.block.state.properties.BooleanProperty boolProperty
                && !state.getValue(boolProperty)) {
            level.setBlockAndUpdate(pos, state.setValue(boolProperty, true));
        }
    }

    private static boolean readBooleanProperty(final BlockState state, final net.minecraft.world.level.block.state.properties.Property<?> property) {
        if (!(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty boolProperty)) {
            return false;
        }
        return state.getValue(boolProperty);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> resolveKaleidoscopePotInputs(final BlockEntity blockEntity) {
        final Field inputsField = findField(blockEntity.getClass(), KALEIDOSCOPE_POT_INPUTS_FIELD);
        if (inputsField == null) {
            return null;
        }

        try {
            inputsField.setAccessible(true);
            final Object value = inputsField.get(blockEntity);
            if (value instanceof NonNullList<?> nonNullList) {
                return (List<ItemStack>) nonNullList;
            }
            if (value instanceof List<?> list) {
                return (List<ItemStack>) list;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }

        return null;
    }

    private static boolean canFillKaleidoscopePotInputs(final List<ItemStack> inputSlots,
                                                        final List<ItemStack> ingredientStacks) {
        int requiredSlots = 0;
        for (final ItemStack ingredientStack : ingredientStacks) {
            if (ingredientStack != null && !ingredientStack.isEmpty()) {
                requiredSlots++;
            }
        }
        return requiredSlots <= inputSlots.size();
    }

    private static void applyKaleidoscopePotInputs(final List<ItemStack> inputSlots,
                                                   final List<ItemStack> ingredientStacks) {
        for (int slot = 0; slot < inputSlots.size(); slot++) {
            inputSlots.set(slot, ItemStack.EMPTY);
        }

        int slot = 0;
        for (final ItemStack ingredientStack : ingredientStacks) {
            if (ingredientStack == null || ingredientStack.isEmpty()) {
                continue;
            }
            if (slot >= inputSlots.size()) {
                break;
            }
            inputSlots.set(slot, ingredientStack.copyWithCount(1));
            slot++;
        }
    }

    private static void markKaleidoscopePotChanged(final BlockEntity blockEntity) {
        blockEntity.setChanged();
        try {
            final Method refreshMethod = blockEntity.getClass().getMethod(KALEIDOSCOPE_POT_REFRESH_METHOD);
            refreshMethod.invoke(blockEntity);
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
    }

    private static void restoreConsumedTokens(final List<TokenConsumption> consumed) {
        for (int i = consumed.size() - 1; i >= 0; i--) {
            final TokenConsumption tokenConsumption = consumed.get(i);
            CfbhRuntime.restoreIngredientToken(tokenConsumption.token(), tokenConsumption.stack());
        }
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
                            && CustomizeBlocks.isDungeonOvenBlockEntity(processorBlockEntity)) {
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to local adjacency check.
        }

        for (final var direction : net.minecraft.core.Direction.values()) {
            final BlockEntity nearbyBlockEntity = level.getBlockEntity(tablePos.relative(direction));
            if (CustomizeBlocks.isDungeonOvenBlockEntity(nearbyBlockEntity)) {
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
