package org.lab_11.modsunified.impl;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.block.entity.CookingTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public final class CookingPotProcessorCapability {
    private static final BlockCapability<KitchenItemProcessor, Void> CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_processor"),
                    KitchenItemProcessor.class
            );
    private static final String CFBH_KITCHEN_IMPL_CLASS = "net.blay09.mods.cookingforblockheads.crafting.KitchenImpl";
    private static final String DUNGEON_OVEN_MARKER_KEY = "dungeon_oven";

    private CookingPotProcessorCapability() {
    }

    public static KitchenItemProcessor createProcessor(final BlockEntity blockEntity,
                                                       final Set<RecipeType<?>> supportedRecipeTypes,
                                                       final List<String> requiredMarkerKeys) {
        return new KitchenItemProcessor() {
            @Override
            public boolean canProcess(final RecipeType<?> recipeType) {
                return supportedRecipeTypes.contains(recipeType)
                        && isDirectlyAboveCookingTable(blockEntity)
                        && requiredMarkersSatisfied(blockEntity, requiredMarkerKeys);
            }

            @Override
            public KitchenOperation processRecipe(final Recipe<?> recipe, final List<IngredientToken> ingredientTokens) {
                return KitchenOperation.EMPTY;
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

    private static boolean requiredMarkersSatisfied(final BlockEntity blockEntity, final List<String> requiredMarkerKeys) {
        for (final String requiredMarkerKey : requiredMarkerKeys) {
            if (DUNGEON_OVEN_MARKER_KEY.equals(requiredMarkerKey) && !hasConnectedDungeonOven(blockEntity)) {
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
            // Fall through to a local adjacency check.
        }

        for (final var direction : net.minecraft.core.Direction.values()) {
            final BlockEntity nearbyBlockEntity = level.getBlockEntity(tablePos.relative(direction));
            if (DungeonOvenCompat.isDungeonOvenBlockEntity(nearbyBlockEntity)) {
                return true;
            }
        }

        return false;
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
                    (blockEntity, context) -> createProcessor(blockEntity, Set.of(recipeType), target.requiredMarkerKeys())
            );
        }
    }
}
