package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class CookingPotBridgeTarget {
    private final String targetKey;
    private final String displayName;
    private final List<String> requiredModIds;
    private final String blockEntityClassName;
    private final String blockEntityTypeOwnerClassName;
    private final String blockEntityTypeFieldName;
    private final List<String> requiredMarkerKeys;
    private final List<RecipeBinding> recipeBindings;

    private volatile Optional<Class<? extends BlockEntity>> cachedBlockEntityClass;
    private volatile Optional<BlockEntityType<?>> cachedBlockEntityType;

    CookingPotBridgeTarget(final String targetKey,
                           final String displayName,
                           final List<String> requiredModIds,
                           final String blockEntityClassName,
                           final String blockEntityTypeOwnerClassName,
                           final String blockEntityTypeFieldName,
                           final List<String> requiredMarkerKeys,
                           final List<RecipeBinding> recipeBindings) {
        this.targetKey = targetKey;
        this.displayName = displayName;
        this.requiredModIds = List.copyOf(requiredModIds);
        this.blockEntityClassName = blockEntityClassName;
        this.blockEntityTypeOwnerClassName = blockEntityTypeOwnerClassName;
        this.blockEntityTypeFieldName = blockEntityTypeFieldName;
        this.requiredMarkerKeys = List.copyOf(requiredMarkerKeys);
        this.recipeBindings = List.copyOf(recipeBindings);
    }

    public String targetKey() {
        return targetKey;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> missingRequiredModIds() {
        return requiredModIds.stream().filter(modId -> !LoaderApiCompat.isModLoaded(modId)).toList();
    }

    public List<String> requiredMarkerKeys() {
        return requiredMarkerKeys;
    }

    List<RecipeBinding> recipeBindings() {
        return recipeBindings;
    }

    public List<Class<?>> resolveRecipeClasses() {
        final Set<Class<?>> classes = new LinkedHashSet<>();
        for (final RecipeBinding binding : recipeBindings) {
            if (binding.isAvailable()) {
                binding.resolveRecipeClass().ifPresent(classes::add);
            }
        }
        return List.copyOf(classes);
    }

    public Set<RecipeType<?>> resolveRecipeTypes() {
        final Set<RecipeType<?>> recipeTypes = new LinkedHashSet<>();
        for (final RecipeBinding binding : recipeBindings) {
            if (binding.isAvailable()) {
                binding.resolveRecipeType().ifPresent(recipeTypes::add);
            }
        }
        return Set.copyOf(recipeTypes);
    }

    List<String> unavailableContainerBindings() {
        final List<String> missing = new ArrayList<>();
        if (resolveBlockEntityClass().isEmpty()) missing.add(blockEntityClassName);
        if (resolveBlockEntityType().isEmpty()) {
            missing.add(blockEntityTypeOwnerClassName + "#" + blockEntityTypeFieldName);
        }
        return List.copyOf(missing);
    }

    String blockEntityClassName() {
        return blockEntityClassName;
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

        return Optional.empty();
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

        return Optional.empty();
    }

    public boolean matchesBlockEntity(final BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }

        final Optional<Class<? extends BlockEntity>> blockEntityClass = resolveBlockEntityClass();
        return blockEntityClass.filter(aClass -> aClass.isInstance(blockEntity)).isPresent();
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

        final Boolean present = queryPresence(holder);
        if (Boolean.FALSE.equals(present)) {
            return null;
        }

        if (holder instanceof Supplier<?> supplier) {
            try {
                return supplier.get();
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        try {
            final Method getMethod = holder.getClass().getMethod("get");
            return getMethod.invoke(holder);
        } catch (ReflectiveOperationException ignored) {
            return holder;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Boolean queryPresence(final Object holder) {
        final Boolean byIsPresent = invokeBooleanMethod(holder, "isPresent");
        return byIsPresent != null ? byIsPresent : invokeBooleanMethod(holder, "isBound");
    }

    private static Boolean invokeBooleanMethod(final Object holder, final String methodName) {
        try {
            final Method method = holder.getClass().getMethod(methodName);
            final Object value = method.invoke(holder);
            if (value instanceof Boolean bool) {
                return bool;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    static final class RecipeBinding {
        private final String recipeClassName;
        private final String recipeTypeOwnerClassName;
        private final String recipeTypeFieldName;
        private final List<String> deniedRecipeIdPrefixes;

        private volatile Optional<Class<?>> cachedRecipeClass;
        private volatile Optional<RecipeType<?>> cachedRecipeType;

        RecipeBinding(final String recipeClassName,
                      final String recipeTypeOwnerClassName,
                      final String recipeTypeFieldName) {
            this(recipeClassName, recipeTypeOwnerClassName, recipeTypeFieldName, List.of());
        }

        RecipeBinding(final String recipeClassName,
                      final String recipeTypeOwnerClassName,
                      final String recipeTypeFieldName,
                      final List<String> deniedRecipeIdPrefixes) {
            this.recipeClassName = recipeClassName;
            this.recipeTypeOwnerClassName = recipeTypeOwnerClassName;
            this.recipeTypeFieldName = recipeTypeFieldName;
            this.deniedRecipeIdPrefixes = List.copyOf(deniedRecipeIdPrefixes);
        }

        String recipeClassName() {
            return recipeClassName;
        }

        Optional<Class<?>> resolveRecipeClass() {
            final Optional<Class<?>> cached = cachedRecipeClass;
            if (cached != null) {
                return cached;
            }

            try {
                final Optional<Class<?>> resolved = Optional.of(Class.forName(recipeClassName));
                cachedRecipeClass = resolved;
                return resolved;
            } catch (ClassNotFoundException ignored) {
                return Optional.empty();
            }
        }

        Optional<RecipeType<?>> resolveRecipeType() {
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

            return Optional.empty();
        }

        List<String> unavailableBindings() {
            final List<String> missing = new ArrayList<>();
            if (resolveRecipeClass().isEmpty()) missing.add(recipeClassName);
            if (resolveRecipeType().isEmpty()) missing.add(recipeTypeOwnerClassName + "#" + recipeTypeFieldName);
            return List.copyOf(missing);
        }

        boolean isAvailable() {
            return resolveRecipeClass().isPresent() && resolveRecipeType().isPresent();
        }

        boolean acceptsRecipe(final Object recipeEntry) {
            final var recipe = RecipeRuntimeCompat.recipeValue(recipeEntry);
            if (recipe == null) {
                return false;
            }

            final Class<?> recipeClass = resolveRecipeClass().orElse(null);
            if (recipeClass == null || !recipeClass.isInstance(recipe)) {
                return false;
            }

            final ResourceLocation recipeId = RecipeRuntimeCompat.recipeId(recipeEntry);
            if (recipeId == null) {
                return false;
            }
            final String fullRecipeId = recipeId.toString();
            return deniedRecipeIdPrefixes.stream().noneMatch(fullRecipeId::startsWith);
        }
    }
}
