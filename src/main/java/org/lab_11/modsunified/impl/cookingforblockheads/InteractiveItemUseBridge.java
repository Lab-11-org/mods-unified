package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

final class InteractiveItemUseBridge {
    record UseResult(boolean success, String reasonCode) {
    }

    private static final String METHOD_USE_ITEM_ON = "useItemOn";
    private static final String METHOD_ADD_ALL_INGREDIENTS = "addAllIngredients";
    private static final String METHOD_ADD_INGREDIENT = "addIngredient";
    private static final String METHOD_CONSUMES_ACTION = "consumesAction";

    private InteractiveItemUseBridge() {
    }

    static UseResult tryUseMainHandItemOnBlock(final BlockEntity blockEntity,
                                               final Player player,
                                               final ItemStack offeredStack) {
        if (blockEntity == null || player == null || offeredStack == null || offeredStack.isEmpty()) {
            return new UseResult(false, "invalid_input");
        }

        final Level level = blockEntity.getLevel();
        if (level == null) {
            return new UseResult(false, "missing_level");
        }

        final BlockPos pos = blockEntity.getBlockPos();
        final BlockState state = level.getBlockState(pos);
        final ItemStack originalHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, offeredStack.copy());

        try {
            final Object useResult = invokeBlockUseItemOn(level, pos, state, player);
            if (isSuccessfulUseResult(useResult)) {
                return new UseResult(true, "block_use_success");
            }

            final Boolean ingredientResult = invokeAddIngredient(blockEntity, level, player);
            if (Boolean.TRUE.equals(ingredientResult)) {
                return new UseResult(true, "add_ingredient_success");
            }

            return new UseResult(false, "interaction_rejected");
        } catch (ReflectiveOperationException ignored) {
            return new UseResult(false, "interaction_exception");
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
        }
    }

    static UseResult tryInvokeAddAllIngredients(final BlockEntity blockEntity,
                                                final Player player,
                                                final java.util.List<ItemStack> offeredStacks) {
        if (blockEntity == null || player == null || offeredStacks == null || offeredStacks.isEmpty()) {
            return new UseResult(false, "invalid_input");
        }

        final Method method = findMethod(
                blockEntity.getClass(),
                METHOD_ADD_ALL_INGREDIENTS,
                java.util.List.class,
                net.minecraft.world.entity.LivingEntity.class
        );
        if (method == null) {
            return new UseResult(false, "no_add_all_ingredients");
        }

        try {
            method.setAccessible(true);
            method.invoke(blockEntity, offeredStacks, player);
            return new UseResult(true, "add_all_ingredients_success");
        } catch (ReflectiveOperationException ignored) {
            return new UseResult(false, "add_all_ingredients_exception");
        }
    }

    private static Object invokeBlockUseItemOn(final Level level,
                                               final BlockPos pos,
                                               final BlockState state,
                                               final Player player) throws ReflectiveOperationException {
        final Method method = findMethod(
                state.getBlock().getClass(),
                METHOD_USE_ITEM_ON,
                ItemStack.class,
                BlockState.class,
                Level.class,
                BlockPos.class,
                Player.class,
                InteractionHand.class,
                BlockHitResult.class
        );
        if (method == null) {
            return null;
        }

        final BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        method.setAccessible(true);
        return method.invoke(
                state.getBlock(),
                player.getMainHandItem(),
                state,
                level,
                pos,
                player,
                InteractionHand.MAIN_HAND,
                hitResult
        );
    }

    private static Boolean invokeAddIngredient(final BlockEntity blockEntity,
                                               final Level level,
                                               final Player player) throws ReflectiveOperationException {
        final Method method = findMethod(
                blockEntity.getClass(),
                METHOD_ADD_INGREDIENT,
                Level.class,
                net.minecraft.world.entity.LivingEntity.class,
                ItemStack.class
        );
        if (method == null) {
            return null;
        }

        method.setAccessible(true);
        final Object result = method.invoke(blockEntity, level, player, player.getMainHandItem());
        if (result instanceof Boolean success) {
            return success;
        }
        return null;
    }

    private static boolean isSuccessfulUseResult(final Object useResult) {
        if (useResult == null) {
            return false;
        }
        if (useResult instanceof Boolean boolResult) {
            return boolResult;
        }

        try {
            final Method consumesActionMethod = useResult.getClass().getMethod(METHOD_CONSUMES_ACTION);
            final Object consumed = consumesActionMethod.invoke(useResult);
            if (consumed instanceof Boolean consumedBool) {
                return consumedBool;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to string heuristic.
        }

        final String value = useResult.toString().toUpperCase();
        return value.contains("SUCCESS") || value.contains("CONSUME");
    }

    private static Method findMethod(final Class<?> ownerClass,
                                     final String methodName,
                                     final Class<?>... parameterTypes) {
        Class<?> current = ownerClass;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }

        try {
            return ownerClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
