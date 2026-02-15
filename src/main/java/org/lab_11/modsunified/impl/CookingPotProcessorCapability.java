package org.lab_11.modsunified.impl;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.block.entity.CookingTableBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.Set;

public final class CookingPotProcessorCapability {
    private static final BlockCapability<KitchenItemProcessor, Void> CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_processor"),
                    KitchenItemProcessor.class
            );

    private CookingPotProcessorCapability() {
    }

    public static KitchenItemProcessor createProcessor(final BlockEntity blockEntity, final Set<RecipeType<?>> supportedRecipeTypes) {
        return new KitchenItemProcessor() {
            @Override
            public boolean canProcess(final RecipeType<?> recipeType) {
                return supportedRecipeTypes.contains(recipeType) && isDirectlyAboveCookingTable(blockEntity);
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
                    (blockEntity, context) -> createProcessor(blockEntity, Set.of(recipeType))
            );
        }
    }
}
