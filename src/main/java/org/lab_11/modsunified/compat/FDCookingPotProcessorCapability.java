package org.lab_11.modsunified.compat;

import net.blay09.mods.cookingforblockheads.api.IngredientToken;
import net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor;
import net.blay09.mods.cookingforblockheads.api.KitchenOperation;
import net.blay09.mods.cookingforblockheads.block.entity.CookingTableBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.registry.ModBlockEntityTypes;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.List;

public final class FDCookingPotProcessorCapability {
    private static final BlockCapability<KitchenItemProcessor, Void> CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY =
            BlockCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("cookingforblockheads", "kitchen_item_processor"),
                    KitchenItemProcessor.class
            );

    private FDCookingPotProcessorCapability() {
    }

    public static KitchenItemProcessor getProcessor(final CookingPotBlockEntity cookingPotBlockEntity) {
        return new KitchenItemProcessor() {
            @Override
            public boolean canProcess(final RecipeType<?> recipeType) {
                return recipeType == ModRecipeTypes.COOKING.get() && isDirectlyAboveCookingTable(cookingPotBlockEntity);
            }

            @Override
            public KitchenOperation processRecipe(final Recipe<?> recipe, final List<IngredientToken> ingredientTokens) {
                return KitchenOperation.EMPTY;
            }
        };
    }

    static boolean isDirectlyAboveCookingTable(final CookingPotBlockEntity cookingPotBlockEntity) {
        final Level level = cookingPotBlockEntity.getLevel();
        if (level == null) {
            return false;
        }

        return level.getBlockEntity(cookingPotBlockEntity.getBlockPos().below()) instanceof CookingTableBlockEntity;
    }

    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                CFBH_KITCHEN_ITEM_PROCESSOR_CAPABILITY,
                ModBlockEntityTypes.COOKING_POT.get(),
                (blockEntity, context) -> getProcessor(blockEntity)
        );
    }
}
