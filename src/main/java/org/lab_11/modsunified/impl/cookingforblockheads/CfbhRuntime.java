package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    private static volatile Class<?> cacheHintClass;
    private static volatile boolean cacheHintLookupFailed;
    private static volatile Class<?> ingredientTokenClass;
    private static volatile boolean ingredientTokenLookupFailed;
    private static volatile Class<?> kitchenOperationClass;
    private static volatile boolean kitchenOperationLookupFailed;
    private static volatile Class<?> kitchenItemProviderClass;
    private static volatile boolean kitchenItemProviderLookupFailed;
    private static volatile Class<?> kitchenItemProcessorClass;
    private static volatile boolean kitchenItemProcessorLookupFailed;
    private static volatile Class<?> kitchenRecipeHandlerClass;
    private static volatile boolean kitchenRecipeHandlerLookupFailed;

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
        final Class<?> cached = kitchenItemProviderClass;
        if (cached != null) {
            return cached;
        }
        if (kitchenItemProviderLookupFailed) {
            return null;
        }
        synchronized (CfbhRuntime.class) {
            if (kitchenItemProviderClass != null) {
                return kitchenItemProviderClass;
            }
            if (kitchenItemProviderLookupFailed) {
                return null;
            }
            kitchenItemProviderClass = resolveClass(CFBH_KITCHEN_ITEM_PROVIDER_CLASS);
            if (kitchenItemProviderClass == null) {
                kitchenItemProviderLookupFailed = true;
            }
            return kitchenItemProviderClass;
        }
    }

    static Class<?> kitchenItemProcessorClass() {
        final Class<?> cached = kitchenItemProcessorClass;
        if (cached != null) {
            return cached;
        }
        if (kitchenItemProcessorLookupFailed) {
            return null;
        }
        synchronized (CfbhRuntime.class) {
            if (kitchenItemProcessorClass != null) {
                return kitchenItemProcessorClass;
            }
            if (kitchenItemProcessorLookupFailed) {
                return null;
            }
            kitchenItemProcessorClass = resolveClass(CFBH_KITCHEN_ITEM_PROCESSOR_CLASS);
            if (kitchenItemProcessorClass == null) {
                kitchenItemProcessorLookupFailed = true;
            }
            return kitchenItemProcessorClass;
        }
    }

    static Object kitchenOperationEmpty() {
        final Class<?> operationClass = resolveKitchenOperationClass();
        if (operationClass == null) {
            return null;
        }
        try {
            return operationClass.getField("EMPTY").get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static Object cacheHintNone() {
        final Class<?> hintClass = resolveCacheHintClass();
        if (hintClass == null) {
            return null;
        }
        try {
            return hintClass.getField("NONE").get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
        try {
            final Object emptyToken = tokenClass.getField("EMPTY").get(null);
            return token == emptyToken;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    static ItemStack peekIngredientToken(final Object token) {
        return invokeTokenMethod(token, "peek");
    }

    static ItemStack consumeIngredientToken(final Object token) {
        return invokeTokenMethod(token, "consume");
    }

    static ItemStack restoreIngredientToken(final Object token, final ItemStack stack) {
        if (token == null) {
            return stack;
        }
        try {
            final Method restoreMethod = token.getClass().getMethod("restore", ItemStack.class);
            final Object value = restoreMethod.invoke(token, stack);
            if (value instanceof ItemStack result) {
                return result;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return stack;
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

    static List<?> contextItemProviders(final Object context) {
        final Object providers = invokeNoArg(context, "getItemProviders");
        if (providers instanceof List<?> list) {
            return list;
        }
        return List.of();
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
        if (target == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
        if (token == null) {
            return ItemStack.EMPTY;
        }
        try {
            final Method method = token.getClass().getMethod(methodName);
            final Object value = method.invoke(token);
            if (value instanceof ItemStack stack) {
                return stack;
            }
        } catch (ReflectiveOperationException ignored) {
            // no-op
        }
        return ItemStack.EMPTY;
    }

    private static Class<?> resolveKitchenOperationClass() {
        final Class<?> cached = kitchenOperationClass;
        if (cached != null) {
            return cached;
        }
        if (kitchenOperationLookupFailed) {
            return null;
        }
        synchronized (CfbhRuntime.class) {
            if (kitchenOperationClass != null) {
                return kitchenOperationClass;
            }
            if (kitchenOperationLookupFailed) {
                return null;
            }
            kitchenOperationClass = resolveClass(CFBH_KITCHEN_OPERATION_CLASS);
            if (kitchenOperationClass == null) {
                kitchenOperationLookupFailed = true;
            }
            return kitchenOperationClass;
        }
    }

    private static Class<?> resolveCacheHintClass() {
        final Class<?> cached = cacheHintClass;
        if (cached != null) {
            return cached;
        }
        if (cacheHintLookupFailed) {
            return null;
        }
        synchronized (CfbhRuntime.class) {
            if (cacheHintClass != null) {
                return cacheHintClass;
            }
            if (cacheHintLookupFailed) {
                return null;
            }
            cacheHintClass = resolveClass(CFBH_CACHE_HINT_CLASS);
            if (cacheHintClass == null) {
                cacheHintLookupFailed = true;
            }
            return cacheHintClass;
        }
    }

    private static Class<?> resolveIngredientTokenClass() {
        final Class<?> cached = ingredientTokenClass;
        if (cached != null) {
            return cached;
        }
        if (ingredientTokenLookupFailed) {
            return null;
        }
        synchronized (CfbhRuntime.class) {
            if (ingredientTokenClass != null) {
                return ingredientTokenClass;
            }
            if (ingredientTokenLookupFailed) {
                return null;
            }
            ingredientTokenClass = resolveClass(CFBH_INGREDIENT_TOKEN_CLASS);
            if (ingredientTokenClass == null) {
                ingredientTokenLookupFailed = true;
            }
            return ingredientTokenClass;
        }
    }

    private static Class<?> resolveKitchenRecipeHandlerClass() {
        final Class<?> cached = kitchenRecipeHandlerClass;
        if (cached != null) {
            return cached;
        }
        if (kitchenRecipeHandlerLookupFailed) {
            return null;
        }
        synchronized (CfbhRuntime.class) {
            if (kitchenRecipeHandlerClass != null) {
                return kitchenRecipeHandlerClass;
            }
            if (kitchenRecipeHandlerLookupFailed) {
                return null;
            }
            kitchenRecipeHandlerClass = resolveClass(CFBH_KITCHEN_RECIPE_HANDLER_CLASS);
            if (kitchenRecipeHandlerClass == null) {
                kitchenRecipeHandlerLookupFailed = true;
            }
            return kitchenRecipeHandlerClass;
        }
    }

    private static Class<?> resolveClass(final String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
