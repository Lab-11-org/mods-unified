package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class CfbhRuntime {
    private interface RetryableKitchenOperation {
    }

    private static final String CFBH_CACHE_HINT_CLASS = "net.blay09.mods.cookingforblockheads.api.CacheHint";
    private static final String CFBH_INGREDIENT_TOKEN_CLASS = "net.blay09.mods.cookingforblockheads.api.IngredientToken";
    private static final String CFBH_KITCHEN_OPERATION_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenOperation";
    private static final String CFBH_KITCHEN_ITEM_PROVIDER_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenItemProvider";
    private static final String CFBH_KITCHEN_ITEM_PROCESSOR_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenItemProcessor";
    private static final String CFBH_KITCHEN_RECIPE_HANDLER_CLASS = "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler";
    private static final String CFBH_COMBINED_ITEM_PROVIDER_CLASS =
            "net.blay09.mods.cookingforblockheads.kitchen.CombinedKitchenItemProvider";

    private static final Map<String, Optional<Class<?>>> RESOLVED_CLASSES = new ConcurrentHashMap<>();
    private static volatile Class<?> kitchenOperationClass;

    private CfbhRuntime() {
    }

    interface IngredientTokenView {
        ItemStack peek();

        ItemStack consume();

        ItemStack restore(ItemStack itemStack);
    }

    interface KitchenItemProviderView {
        Object findByIngredient(Ingredient ingredient, Collection<?> allocatedTokens);

        Object findByItem(ItemStack itemStack, Collection<?> allocatedTokens);
    }

    interface KitchenItemProcessorView {
        boolean canProcess(RecipeType<?> recipeType);

        Object processRecipe(Recipe<?> recipe, List<?> ingredientTokens);
    }

    interface KitchenRecipeHandlerView {
        int mapToMatrixSlot(Recipe<?> recipe, int ingredientIndex);

        ItemStack assemble(Object context, Recipe<?> recipe, List<?> ingredientTokens, RegistryAccess registryAccess);
    }

    static Class<?> kitchenItemProviderClass() {
        return resolveClass(CFBH_KITCHEN_ITEM_PROVIDER_CLASS);
    }

    static Class<?> kitchenItemProcessorClass() {
        return resolveClass(CFBH_KITCHEN_ITEM_PROCESSOR_CLASS);
    }

    static Object kitchenOperationEmpty() {
        return staticFieldValue(resolveKitchenOperationClass(), "EMPTY");
    }

    static Object cacheHintNone() {
        return staticFieldValue(resolveCacheHintClass(), "NONE");
    }

    static boolean ingredientLookupAvailable() {
        return resolveCacheHintClass() != null && cacheHintNone() != null;
    }

    static boolean isEmptyKitchenOperation(final Object operation) {
        final Object emptyOperation = kitchenOperationEmpty();
        return operation == null || (emptyOperation != null && operation == emptyOperation);
    }

    static boolean isEmptyIngredientToken(final Object token) {
        if (token == null) {
            return true;
        }
        final Class<?> tokenClass = resolveIngredientTokenClass();
        if (tokenClass == null) {
            return false;
        }
        return token == staticFieldValue(tokenClass, "EMPTY");
    }

    static ItemStack peekIngredientToken(final Object token) {
        return invokeTokenMethod(token, "peek");
    }

    static ItemStack consumeIngredientToken(final Object token) {
        return invokeTokenMethod(token, "consume");
    }

    static ItemStack restoreIngredientToken(final Object token, final ItemStack stack) {
        final Object value = invoke(token, "restore", new Class<?>[]{ItemStack.class}, stack);
        return value instanceof ItemStack result ? result : stack;
    }

    static Object newKitchenOperationWithFeedback(final Component feedback) {
        return newKitchenOperationWithFeedback(feedback, false);
    }

    static Object newRetryableKitchenOperationWithFeedback(final Component feedback) {
        return newKitchenOperationWithFeedback(feedback, true);
    }

    private static Object newKitchenOperationWithFeedback(final Component feedback,
                                                          final boolean retryable) {
        final Class<?> operationClass = resolveKitchenOperationClass();
        if (operationClass == null || feedback == null) {
            return kitchenOperationEmpty();
        }
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("getFeedback".equals(method.getName()) && method.getParameterCount() == 0) {
                return Optional.of(feedback);
            }
            return objectMethodFallback(proxy, method, args, "KitchenOperationProxy");
        };
        final Class<?>[] interfaces = retryable
                ? new Class<?>[]{operationClass, RetryableKitchenOperation.class}
                : new Class<?>[]{operationClass};
        return Proxy.newProxyInstance(operationClass.getClassLoader(), interfaces, handler);
    }

    static boolean isRetryableKitchenOperation(final Object operation) {
        return operation instanceof RetryableKitchenOperation;
    }

    static Object newKitchenItemProcessorProxy(final KitchenItemProcessorView view) {
        final Class<?> processorClass = kitchenItemProcessorClass();
        if (processorClass == null || view == null) {
            return null;
        }
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("canProcess".equals(method.getName())
                    && args != null
                    && args.length == 1
                    && args[0] instanceof RecipeType<?> recipeType) {
                return view.canProcess(recipeType);
            }
            if ("processRecipe".equals(method.getName())
                    && args != null
                    && args.length == 2
                    && args[0] instanceof Recipe<?> recipe
                    && args[1] instanceof List<?> ingredientTokens) {
                return view.processRecipe(recipe, ingredientTokens);
            }
            return objectMethodFallback(proxy, method, args, "KitchenItemProcessorProxy");
        };
        return Proxy.newProxyInstance(processorClass.getClassLoader(), new Class<?>[]{processorClass}, handler);
    }

    static Object newKitchenRecipeHandlerProxy(final KitchenRecipeHandlerView view) {
        final Class<?> handlerClass = resolveKitchenRecipeHandlerClass();
        if (handlerClass == null || view == null) {
            return null;
        }
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("mapToMatrixSlot".equals(method.getName())
                    && args != null
                    && args.length == 2
                    && args[0] instanceof Recipe<?> recipe
                    && args[1] instanceof Integer index) {
                return view.mapToMatrixSlot(recipe, index);
            }
            if ("assemble".equals(method.getName())
                    && args != null
                    && args.length == 4
                    && args[0] != null
                    && args[1] instanceof Recipe<?> recipe
                    && args[2] instanceof List<?> ingredientTokens
                    && args[3] instanceof RegistryAccess registryAccess) {
                return view.assemble(args[0], recipe, ingredientTokens, registryAccess);
            }
            return objectMethodFallback(proxy, method, args, "KitchenRecipeHandlerProxy");
        };
        return Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class<?>[]{handlerClass}, handler);
    }

    static Object newKitchenItemProviderProxy(final KitchenItemProviderView view, final Class<?>... extraInterfaces) {
        final Class<?> providerClass = kitchenItemProviderClass();
        if (providerClass == null || view == null) {
            return null;
        }

        final List<Class<?>> proxyInterfaces = new ArrayList<>();
        proxyInterfaces.add(providerClass);
        if (extraInterfaces != null) {
            for (final Class<?> iface : extraInterfaces) {
                if (iface != null && iface.isInterface()) {
                    proxyInterfaces.add(iface);
                }
            }
        }

        final InvocationHandler handler = (proxy, method, args) -> {
            if ("findIngredient".equals(method.getName()) && args != null && args.length == 3) {
                if (args[0] instanceof Ingredient ingredient && args[1] instanceof Collection<?> allocatedTokens) {
                    return view.findByIngredient(ingredient, allocatedTokens);
                }
                if (args[0] instanceof ItemStack itemStack && args[1] instanceof Collection<?> allocatedTokens) {
                    return view.findByItem(itemStack, allocatedTokens);
                }
            }
            if ("getCacheHint".equals(method.getName()) && args != null && args.length == 1) {
                return cacheHintNone();
            }
            return invokeBestMatch(view, method.getName(), args, proxy, method);
        };
        return Proxy.newProxyInstance(
                providerClass.getClassLoader(),
                proxyInterfaces.toArray(new Class<?>[0]),
                handler
        );
    }

    static Object newIngredientTokenProxy(final IngredientTokenView view) {
        final Class<?> tokenClass = resolveIngredientTokenClass();
        if (tokenClass == null || view == null) {
            return null;
        }
        final InvocationHandler handler = (proxy, method, args) -> {
            if ("peek".equals(method.getName()) && (args == null || args.length == 0)) {
                return view.peek();
            }
            if ("consume".equals(method.getName()) && (args == null || args.length == 0)) {
                return view.consume();
            }
            if ("restore".equals(method.getName())
                    && args != null
                    && args.length == 1
                    && args[0] instanceof ItemStack itemStack) {
                return view.restore(itemStack);
            }
            return objectMethodFallback(proxy, method, args, "IngredientTokenProxy");
        };
        return Proxy.newProxyInstance(tokenClass.getClassLoader(), new Class<?>[]{tokenClass}, handler);
    }

    static Object newStaticIngredientToken(final ItemStack stack) {
        return newIngredientTokenProxy(new IngredientTokenView() {
            @Override public ItemStack peek() { return stack.copy(); }
            @Override public ItemStack consume() { return stack.copy(); }
            @Override public ItemStack restore(final ItemStack itemStack) { return ItemStack.EMPTY; }
        });
    }

    static List<?> contextItemProviders(final Object context) {
        final Object providers = invokeNoArg(context, "getItemProviders");
        if (providers instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    static List<?> itemProviders(final Object kitchen, final Player player) {
        final Object providers = invoke(kitchen, "getItemProviders", new Class<?>[]{Player.class}, player);
        return providers instanceof List<?> list ? list : List.of();
    }

    static List<?> contextItemProcessors(final Object context) {
        final Object processors = invokeNoArg(context, "getItemProcessors");
        if (processors instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    static void contextNotify(final Object context, final Object operation) {
        if (context == null || operation == null) {
            return;
        }
        final Class<?> operationClass = resolveKitchenOperationClass();
        if (operationClass == null) {
            return;
        }
        invoke(context, "notify", new Class<?>[]{operationClass}, operation);
    }

    static ItemStack contextRestore(final Object context, final ItemStack stack) {
        if (context == null || stack == null || stack.isEmpty()) {
            return stack;
        }
        final Object result = invoke(context, "restore", new Class<?>[]{ItemStack.class}, stack);
        if (result instanceof ItemStack restored) {
            return restored;
        }
        return stack;
    }

    static boolean processorCanProcess(final Object processor, final RecipeType<?> recipeType) {
        final Object result = invoke(processor, "canProcess", new Class<?>[]{RecipeType.class}, recipeType);
        return result instanceof Boolean value && value;
    }

    static Object processorProcessRecipe(final Object processor, final Recipe<?> recipe, final List<?> ingredientTokens) {
        final Object result = invoke(processor, "processRecipe", new Class<?>[]{Recipe.class, List.class}, recipe, ingredientTokens);
        return result != null ? result : kitchenOperationEmpty();
    }

    static Object findIngredientToken(final Object itemProvider,
                                      final Ingredient ingredient,
                                      final Collection<?> allocatedTokens) {
        final Class<?> hintClass = resolveCacheHintClass();
        final Object hintNone = cacheHintNone();
        if (hintClass == null || hintNone == null || itemProvider == null) {
            return null;
        }

        final Object result = invoke(itemProvider, "findIngredient",
                new Class<?>[]{Ingredient.class, Collection.class, hintClass},
                ingredient, allocatedTokens, hintNone);
        if (isEmptyIngredientToken(result)) {
            return null;
        }
        return result;
    }

    static Object findItemToken(final Object itemProvider,
                                final ItemStack itemStack,
                                final Collection<?> allocatedTokens) {
        final Class<?> hintClass = resolveCacheHintClass();
        final Object hintNone = cacheHintNone();
        if (hintClass == null || hintNone == null || itemProvider == null || itemStack == null || itemStack.isEmpty()) {
            return null;
        }

        final Object result = invoke(itemProvider, "findIngredient",
                new Class<?>[]{ItemStack.class, Collection.class, hintClass},
                itemStack, allocatedTokens, hintNone);
        if (isEmptyIngredientToken(result)) {
            return null;
        }
        return result;
    }

    static List<?> tryGetCombinedProviders(final Object itemProvider) {
        if (itemProvider == null || !CFBH_COMBINED_ITEM_PROVIDER_CLASS.equals(itemProvider.getClass().getName())) {
            return List.of();
        }
        final Object result = invokeNoArg(itemProvider, "providers");
        if (result instanceof List<?> providers) {
            return providers;
        }
        return List.of();
    }

    static Object invokeNoArg(final Object target, final String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    static Object invoke(final Object target, final String methodName, final Class<?>[] parameterTypes, final Object... args) {
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static Field findField(final Object target, final String fieldName) {
        return target == null ? null : findField(target.getClass(), fieldName);
    }

    static Field findField(final Class<?> ownerClass, final String fieldName) {
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

    private static Object invokeBestMatch(final Object view,
                                          final String methodName,
                                          final Object[] args,
                                          final Object proxy,
                                          final Method method) throws Throwable {
        try {
            final Method candidate = findMethodByNameAndCount(view.getClass(), methodName, args == null ? 0 : args.length);
            if (candidate == null) {
                return objectMethodFallback(proxy, method, args, view.getClass().getSimpleName());
            }
            candidate.setAccessible(true);
            return candidate.invoke(view, args);
        } catch (ReflectiveOperationException ignored) {
            return objectMethodFallback(proxy, method, args, view.getClass().getSimpleName());
        }
    }

    private static Method findMethodByNameAndCount(final Class<?> ownerClass,
                                                   final String methodName,
                                                   final int argumentCount) {
        Class<?> current = ownerClass;
        while (current != null) {
            for (final Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == argumentCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object objectMethodFallback(final Object proxy,
                                               final Method method,
                                               final Object[] args,
                                               final String proxyName) {
        return switch (method.getName()) {
            case "toString" -> proxyName;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
            default -> null;
        };
    }

    private static ItemStack invokeTokenMethod(final Object token, final String methodName) {
        final Object value = invokeNoArg(token, methodName);
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private static Object staticFieldValue(final Class<?> ownerClass, final String fieldName) {
        if (ownerClass == null) {
            return null;
        }
        try {
            return ownerClass.getField(fieldName).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> resolveKitchenOperationClass() {
        final Class<?> override = kitchenOperationClass;
        return override != null ? override : resolveClass(CFBH_KITCHEN_OPERATION_CLASS);
    }

    private static Class<?> resolveCacheHintClass() {
        return resolveClass(CFBH_CACHE_HINT_CLASS);
    }

    private static Class<?> resolveIngredientTokenClass() {
        return resolveClass(CFBH_INGREDIENT_TOKEN_CLASS);
    }

    private static Class<?> resolveKitchenRecipeHandlerClass() {
        return resolveClass(CFBH_KITCHEN_RECIPE_HANDLER_CLASS);
    }

    static Class<?> resolveClass(final String className) {
        return RESOLVED_CLASSES.computeIfAbsent(className, name -> {
            try {
                return Optional.of(Class.forName(name));
            } catch (ClassNotFoundException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
    }
}
