package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.lab_11.modsunified.impl.platform.RecipeRuntimeCompat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.ArrayList;

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
    private final List<String> allowedRecipeNamespaces;
    private final List<String> allowedRecipeIdPrefixes;
    private final List<String> deniedRecipeNamespaces;
    private final List<String> deniedRecipeIdPrefixes;
    private final List<String> requiredMarkerKeys;

    private volatile Optional<Class<?>> cachedRecipeClass;
    private volatile Optional<RecipeType<?>> cachedRecipeType;
    private volatile Optional<Class<? extends BlockEntity>> cachedBlockEntityClass;
    private volatile Optional<BlockEntityType<?>> cachedBlockEntityType;

    public CookingPotBridgeTarget(final String targetKey,
                                  final String displayName,
                                  final List<String> requiredModIds,
                                  final String recipeClassName,
                                  final String recipeTypeOwnerClassName,
                                  final String recipeTypeFieldName,
                                  final String blockEntityClassName,
                                  final String blockEntityTypeOwnerClassName,
                                  final String blockEntityTypeFieldName,
                                  final List<String> allowedRecipeNamespaces,
                                  final List<String> allowedRecipeIdPrefixes,
                                  final List<String> deniedRecipeNamespaces,
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
        this.allowedRecipeNamespaces = List.copyOf(allowedRecipeNamespaces);
        this.allowedRecipeIdPrefixes = List.copyOf(allowedRecipeIdPrefixes);
        this.deniedRecipeNamespaces = List.copyOf(deniedRecipeNamespaces);
        this.deniedRecipeIdPrefixes = List.copyOf(deniedRecipeIdPrefixes);
        this.requiredMarkerKeys = List.copyOf(requiredMarkerKeys);
    }

    public CookingPotBridgeTarget(final String targetKey,
                                  final String displayName,
                                  final List<String> requiredModIds,
                                  final String recipeClassName,
                                  final String recipeTypeOwnerClassName,
                                  final String recipeTypeFieldName,
                                  final String blockEntityClassName,
                                  final String blockEntityTypeOwnerClassName,
                                  final String blockEntityTypeFieldName) {
        this(
                targetKey,
                displayName,
                requiredModIds,
                recipeClassName,
                recipeTypeOwnerClassName,
                recipeTypeFieldName,
                blockEntityClassName,
                blockEntityTypeOwnerClassName,
                blockEntityTypeFieldName,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public CookingPotBridgeTarget withRequiredMarkerKeys(final List<String> requiredMarkerKeys) {
        return new CookingPotBridgeTarget(
                targetKey,
                displayName,
                requiredModIds,
                recipeClassName,
                recipeTypeOwnerClassName,
                recipeTypeFieldName,
                blockEntityClassName,
                blockEntityTypeOwnerClassName,
                blockEntityTypeFieldName,
                allowedRecipeNamespaces,
                allowedRecipeIdPrefixes,
                deniedRecipeNamespaces,
                deniedRecipeIdPrefixes,
                requiredMarkerKeys
        );
    }

    public CookingPotBridgeTarget withDeniedRecipeIdPrefixes(final List<String> deniedRecipeIdPrefixes) {
        return new CookingPotBridgeTarget(
                targetKey,
                displayName,
                requiredModIds,
                recipeClassName,
                recipeTypeOwnerClassName,
                recipeTypeFieldName,
                blockEntityClassName,
                blockEntityTypeOwnerClassName,
                blockEntityTypeFieldName,
                allowedRecipeNamespaces,
                allowedRecipeIdPrefixes,
                deniedRecipeNamespaces,
                deniedRecipeIdPrefixes,
                requiredMarkerKeys
        );
    }

    public String targetKey() {
        return targetKey;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> requiredModIds() {
        return requiredModIds;
    }

    public List<String> missingRequiredModIds() {
        final List<String> missing = new ArrayList<>();
        for (final String modId : requiredModIds) {
            if (!LoaderApiCompat.isModLoaded(modId)) {
                missing.add(modId);
            }
        }
        return List.copyOf(missing);
    }

    public List<String> requiredMarkerKeys() {
        return requiredMarkerKeys;
    }

    public boolean isModSetLoaded() {
        for (final String modId : requiredModIds) {
            if (!LoaderApiCompat.isModLoaded(modId)) {
                return false;
            }
        }
        return true;
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

    public boolean acceptsRecipe(final Object recipeEntry) {
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
        final String recipeNamespace = recipeId.getNamespace();
        final String fullRecipeId = recipeId.toString();

        final boolean hasAllowFilter = !allowedRecipeNamespaces.isEmpty() || !allowedRecipeIdPrefixes.isEmpty();
        if (hasAllowFilter) {
            final boolean allowByNamespace = allowedRecipeNamespaces.contains(recipeNamespace);
            final boolean allowByIdPrefix = startsWithAny(fullRecipeId, allowedRecipeIdPrefixes);
            if (!allowByNamespace && !allowByIdPrefix) {
                return false;
            }
        }

        if (deniedRecipeNamespaces.contains(recipeNamespace)) {
            return false;
        }

        return !startsWithAny(fullRecipeId, deniedRecipeIdPrefixes);
    }

    private static boolean startsWithAny(final String value, final List<String> prefixes) {
        for (final String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
            // Some holders are direct values and do not expose a get() API.
            return holder;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Boolean queryPresence(final Object holder) {
        final Boolean byIsPresent = invokeBooleanMethod(holder, "isPresent");
        if (byIsPresent != null) {
            return byIsPresent;
        }
        return invokeBooleanMethod(holder, "isBound");
    }

    private static Boolean invokeBooleanMethod(final Object holder, final String methodName) {
        try {
            final Method method = holder.getClass().getMethod(methodName);
            final Object value = method.invoke(holder);
            if (value instanceof Boolean bool) {
                return bool;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }
}
