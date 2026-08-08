package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class CookingPotBridgeTarget {
    private final String targetKey;
    private final String displayName;
    private final List<String> requiredModIds;
    private final String recipeClassName;
    private final String recipeTypeOwnerClassName;
    private final String recipeTypeFieldName;
    private final String blockEntityClassName;
    private final String blockEntityTypeOwnerClassName;
    private final String blockEntityTypeFieldName;
    private final List<String> deniedRecipeIdPrefixes;
    private final List<String> requiredMarkerKeys;

    private volatile Optional<Class<?>> cachedRecipeClass;
    private volatile Optional<RecipeType<?>> cachedRecipeType;
    private volatile Optional<Class<? extends BlockEntity>> cachedBlockEntityClass;
    private volatile Optional<BlockEntityType<?>> cachedBlockEntityType;

    CookingPotBridgeTarget(final String targetKey,
                           final String displayName,
                           final List<String> requiredModIds,
                           final String recipeClassName,
                           final String recipeTypeOwnerClassName,
                           final String recipeTypeFieldName,
                           final String blockEntityClassName,
                           final String blockEntityTypeOwnerClassName,
                           final String blockEntityTypeFieldName,
                           final List<String> deniedRecipeIdPrefixes,
                           final List<String> requiredMarkerKeys) {
        this.targetKey = targetKey;
        this.displayName = displayName;
        this.requiredModIds = List.copyOf(requiredModIds);
        this.recipeClassName = recipeClassName;
        this.recipeTypeOwnerClassName = recipeTypeOwnerClassName;
        this.recipeTypeFieldName = recipeTypeFieldName;
        this.blockEntityClassName = blockEntityClassName;
        this.blockEntityTypeOwnerClassName = blockEntityTypeOwnerClassName;
        this.blockEntityTypeFieldName = blockEntityTypeFieldName;
        this.deniedRecipeIdPrefixes = List.copyOf(deniedRecipeIdPrefixes);
        this.requiredMarkerKeys = List.copyOf(requiredMarkerKeys);
    }

    public String targetKey() {
        return targetKey;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> requiredMarkerKeys() {
        return requiredMarkerKeys;
    }

    public boolean isModSetLoaded() {
        return requiredModIds.stream().allMatch(modId -> ModList.get().isLoaded(modId));
    }

    public Optional<Class<?>> resolveRecipeClass() {
        final Optional<Class<?>> cached = cachedRecipeClass;
        if (cached != null) {
            return cached;
        }

        Optional<Class<?>> resolved;
        try {
            resolved = Optional.of(Class.forName(recipeClassName));
        } catch (ClassNotFoundException ignored) {
            resolved = Optional.empty();
        }
        cachedRecipeClass = resolved;
        return resolved;
    }

    public Optional<RecipeType<?>> resolveRecipeType() {
        final Optional<RecipeType<?>> cached = cachedRecipeType;
        if (cached != null) {
            return cached;
        }

        final Object value = resolveHolderValue(recipeTypeOwnerClassName, recipeTypeFieldName);
        if (value instanceof RecipeType<?> recipeType) {
            final Optional<RecipeType<?>> resolved = Optional.of(recipeType);
            cachedRecipeType = resolved;
            return resolved;
        }

        final Optional<RecipeType<?>> resolved = Optional.empty();
        cachedRecipeType = resolved;
        return resolved;
    }

    public Optional<Class<? extends BlockEntity>> resolveBlockEntityClass() {
        final Optional<Class<? extends BlockEntity>> cached = cachedBlockEntityClass;
        if (cached != null) {
            return cached;
        }

        try {
            final Class<?> rawClass = Class.forName(blockEntityClassName);
            if (BlockEntity.class.isAssignableFrom(rawClass)) {
                @SuppressWarnings("unchecked")
                final Class<? extends BlockEntity> cast = (Class<? extends BlockEntity>) rawClass;
                final Optional<Class<? extends BlockEntity>> resolved = Optional.of(cast);
                cachedBlockEntityClass = resolved;
                return resolved;
            }
        } catch (ClassNotFoundException ignored) {
            // no-op
        }

        final Optional<Class<? extends BlockEntity>> resolved = Optional.empty();
        cachedBlockEntityClass = resolved;
        return resolved;
    }

    public Optional<BlockEntityType<?>> resolveBlockEntityType() {
        final Optional<BlockEntityType<?>> cached = cachedBlockEntityType;
        if (cached != null) {
            return cached;
        }

        final Object value = resolveHolderValue(blockEntityTypeOwnerClassName, blockEntityTypeFieldName);
        if (value instanceof BlockEntityType<?> blockEntityType) {
            final Optional<BlockEntityType<?>> resolved = Optional.of(blockEntityType);
            cachedBlockEntityType = resolved;
            return resolved;
        }

        final Optional<BlockEntityType<?>> resolved = Optional.empty();
        cachedBlockEntityType = resolved;
        return resolved;
    }

    public boolean matchesBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Optional<Class<? extends BlockEntity>> blockEntityClass = resolveBlockEntityClass();
        return blockEntityClass.filter(aClass -> aClass.isInstance(blockEntity)).isPresent();
    }

    public boolean acceptsRecipe(final RecipeHolder<?> recipeHolder) {
        final Class<?> recipeClass = resolveRecipeClass().orElse(null);
        if (recipeClass == null || !recipeClass.isInstance(recipeHolder.value())) {
            return false;
        }

        return !startsWithAny(recipeHolder.id().toString(), deniedRecipeIdPrefixes);
    }

    private static boolean startsWithAny(final String value, final List<String> prefixes) {
        return prefixes.stream().anyMatch(value::startsWith);
    }

    private static Object resolveHolderValue(final String ownerClassName, final String fieldName) {
        try {
            final Class<?> ownerClass = Class.forName(ownerClassName);
            final Field field = ownerClass.getField(fieldName);
            final Object holder = field.get(null);
            return unwrapHolder(holder);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object unwrapHolder(final Object holder) {
        if (holder == null) {
            return null;
        }

        if (holder instanceof Supplier<?> supplier) {
            return supplier.get();
        }

        try {
            final Method getMethod = holder.getClass().getMethod("get");
            return getMethod.invoke(holder);
        } catch (ReflectiveOperationException ignored) {
            return holder;
        }
    }
}
