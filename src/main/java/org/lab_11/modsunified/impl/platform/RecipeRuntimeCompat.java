package org.lab_11.modsunified.impl.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RecipeRuntimeCompat {
    private static final String RECIPE_HOLDER_CLASS_NAME = "net.minecraft.world.item.crafting.RecipeHolder";
    private static final String[] REPLACE_RECIPES_METHOD_NAME_CANDIDATES = {"replaceRecipes", "m_44024_"};

    private RecipeRuntimeCompat() {
    }

    public static ResourceLocation recipeId(final Object recipeEntry) {
        if (recipeEntry == null) {
            return null;
        }
        final ResourceLocation byNamedMethod = invokeResourceLocationMethod(recipeEntry, "id");
        if (byNamedMethod != null) {
            return byNamedMethod;
        }

        final ResourceLocation byLegacyMethod = invokeResourceLocationMethod(recipeEntry, "getId");
        if (byLegacyMethod != null) {
            return byLegacyMethod;
        }

        final ResourceLocation byMethodSignature = findNoArgMethodValue(recipeEntry, ResourceLocation.class);
        if (byMethodSignature != null) {
            return byMethodSignature;
        }

        return findFieldValue(recipeEntry, ResourceLocation.class);
    }

    public static Recipe<?> recipeValue(final Object recipeEntry) {
        if (recipeEntry instanceof Recipe<?> recipe) {
            return recipe;
        }
        if (recipeEntry == null) {
            return null;
        }
        final Recipe<?> byNamedMethod = invokeRecipeMethod(recipeEntry, "value");
        if (byNamedMethod != null) {
            return byNamedMethod;
        }

        final Recipe<?> byLegacyMethod = invokeRecipeMethod(recipeEntry, "getValue");
        if (byLegacyMethod != null) {
            return byLegacyMethod;
        }

        final Recipe<?> byMethodSignature = findNoArgMethodValue(recipeEntry, Recipe.class);
        if (byMethodSignature != null) {
            return byMethodSignature;
        }

        return findFieldValue(recipeEntry, Recipe.class);
    }

    public static List<Object> getAllRecipes(final RecipeManager recipeManager) {
        final Collection<?> raw = recipeManager.getRecipes();
        final List<Object> entries = new ArrayList<>(raw);

        final Map<ResourceLocation, Object> byNameRecipes = resolveRecipeByNameMap(recipeManager);
        if (byNameRecipes == null || byNameRecipes.isEmpty()) {
            return entries;
        }

        final Set<ResourceLocation> knownIds = new HashSet<>();
        for (final Object entry : entries) {
            final ResourceLocation recipeId = recipeId(entry);
            if (recipeId != null) {
                knownIds.add(recipeId);
            }
        }

        for (final Map.Entry<ResourceLocation, Object> byNameEntry : byNameRecipes.entrySet()) {
            final ResourceLocation id = byNameEntry.getKey();
            if (id == null || knownIds.contains(id)) {
                continue;
            }

            final Object recipeEntry = byNameEntry.getValue();
            if (recipeEntry == null) {
                continue;
            }

            entries.add(recipeEntry);
            knownIds.add(id);
        }

        return entries;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Object> getAllRecipesFor(final RecipeManager recipeManager, final RecipeType<?> recipeType) {
        final Iterable<?> raw = recipeManager.getAllRecipesFor((RecipeType) recipeType);
        final List<Object> entries = new ArrayList<>();
        for (final Object entry : raw) {
            entries.add(entry);
        }
        return entries;
    }

    public static Recipe<?> findById(final RecipeManager recipeManager, final ResourceLocation recipeId) {
        try {
            final Method byKeyMethod = recipeManager.getClass().getMethod("byKey", ResourceLocation.class);
            final Object result = byKeyMethod.invoke(recipeManager, recipeId);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                return recipeValue(optional.get());
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to linear scan.
        }

        for (final Object entry : getAllRecipes(recipeManager)) {
            final ResourceLocation id = recipeId(entry);
            if (recipeId.equals(id)) {
                return recipeValue(entry);
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object recipeEntry(final ResourceLocation recipeId, final Recipe<?> recipe) {
        try {
            final Class<?> recipeHolderClass = Class.forName(RECIPE_HOLDER_CLASS_NAME);
            final Constructor<?> constructor = recipeHolderClass.getConstructor(ResourceLocation.class, Recipe.class);
            return constructor.newInstance(recipeId, recipe);
        } catch (ReflectiveOperationException ignored) {
            return recipe;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void replaceRecipes(final RecipeManager recipeManager, final Collection<?> recipeEntries) {
        final Class<?> recipeManagerClass = recipeManager.getClass();
        final List<String> attemptedSignatures = new ArrayList<>();
        ReflectiveOperationException lastFailure = null;

        for (final String methodName : REPLACE_RECIPES_METHOD_NAME_CANDIDATES) {
            final Method namedMethod = resolveNamedReplaceRecipesMethod(recipeManagerClass, methodName);
            if (namedMethod == null) {
                continue;
            }
            attemptedSignatures.add(methodSignature(namedMethod));
            try {
                namedMethod.setAccessible(true);
                namedMethod.invoke(recipeManager, recipeEntries);
                return;
            } catch (ReflectiveOperationException e) {
                lastFailure = e;
            }
        }

        final Method structuralMethod = resolveStructuralReplaceRecipesMethod(recipeManagerClass);
        if (structuralMethod != null) {
            attemptedSignatures.add(methodSignature(structuralMethod));
            try {
                structuralMethod.setAccessible(true);
                structuralMethod.invoke(recipeManager, recipeEntries);
                return;
            } catch (ReflectiveOperationException e) {
                lastFailure = e;
            }
        }

        final String signatures = attemptedSignatures.isEmpty() ? "<none>" : String.join(", ", attemptedSignatures);
        throw new IllegalStateException(
                "Unable to replace recipes through RecipeManager runtime API. Attempted: " + signatures,
                lastFailure
        );
    }

    private static Method resolveNamedReplaceRecipesMethod(final Class<?> recipeManagerClass,
                                                           final String methodName) {
        try {
            final Method method = recipeManagerClass.getMethod(methodName, Iterable.class);
            return isValidReplaceRecipesMethod(method) ? method : null;
        } catch (NoSuchMethodException ignored) {
            // Fall through to declared lookup.
        }

        try {
            final Method method = recipeManagerClass.getDeclaredMethod(methodName, Iterable.class);
            return isValidReplaceRecipesMethod(method) ? method : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method resolveStructuralReplaceRecipesMethod(final Class<?> recipeManagerClass) {
        Method structuralMatch = null;
        for (final Method method : recipeManagerClass.getDeclaredMethods()) {
            if (!isValidReplaceRecipesMethod(method)) {
                continue;
            }
            if (structuralMatch != null) {
                // Ambiguous reflective match, abort structural fallback.
                return null;
            }
            structuralMatch = method;
        }
        return structuralMatch;
    }

    private static boolean isValidReplaceRecipesMethod(final Method method) {
        if (method == null || Modifier.isStatic(method.getModifiers())) {
            return false;
        }
        if (!void.class.equals(method.getReturnType())) {
            return false;
        }

        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            return false;
        }
        return Iterable.class.isAssignableFrom(parameterTypes[0]);
    }

    private static String methodSignature(final Method method) {
        final StringBuilder builder = new StringBuilder();
        builder.append(method.getDeclaringClass().getName())
                .append('#')
                .append(method.getName())
                .append('(');
        final Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(parameterTypes[i].getTypeName());
        }
        builder.append(')');
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, Object> resolveRecipeByNameMap(final RecipeManager recipeManager) {
        for (final Field field : recipeManager.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }

            try {
                field.setAccessible(true);
                final Object value = field.get(recipeManager);
                if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
                    continue;
                }

                final Object sampleKey = map.keySet().iterator().next();
                if (sampleKey instanceof ResourceLocation) {
                    return (Map<ResourceLocation, Object>) map;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next field.
            }
        }

        return null;
    }

    private static ResourceLocation invokeResourceLocationMethod(final Object recipeEntry, final String methodName) {
        try {
            final Method method = recipeEntry.getClass().getMethod(methodName);
            final Object value = method.invoke(recipeEntry);
            return value instanceof ResourceLocation id ? id : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Recipe<?> invokeRecipeMethod(final Object recipeEntry, final String methodName) {
        try {
            final Method method = recipeEntry.getClass().getMethod(methodName);
            final Object value = method.invoke(recipeEntry);
            return value instanceof Recipe<?> recipe ? recipe : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T findNoArgMethodValue(final Object target, final Class<T> expectedType) {
        for (final Method method : target.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }
            if (!expectedType.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                method.setAccessible(true);
                final Object value = method.invoke(target);
                if (expectedType.isInstance(value)) {
                    return (T) value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next method.
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T findFieldValue(final Object target, final Class<T> expectedType) {
        for (final Field field : target.getClass().getDeclaredFields()) {
            if (!expectedType.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                final Object value = field.get(target);
                if (expectedType.isInstance(value)) {
                    return (T) value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next field.
            }
        }

        return null;
    }
}
