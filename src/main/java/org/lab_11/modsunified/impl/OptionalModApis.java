package org.lab_11.modsunified.impl;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public final class OptionalModApis {
    private static final String CFBH_API = "net.blay09.mods.cookingforblockheads.api.CookingForBlockheadsAPI";
    private static final String CFBH_HANDLER = "net.blay09.mods.cookingforblockheads.api.KitchenRecipeHandler";
    private static final String CFBH_RECIPE_STATUS = "net.blay09.mods.cookingforblockheads.crafting.RecipeWithStatus";

    private static final boolean COOKING_FOR_BLOCKHEADS = detectCookingForBlockheads();

    private OptionalModApis() {
    }

    public static boolean supportsCookingForBlockheads() {
        return COOKING_FOR_BLOCKHEADS;
    }

    private static boolean detectCookingForBlockheads() {
        try {
            final Class<?> handler = Class.forName(CFBH_HANDLER, false, OptionalModApis.class.getClassLoader());
            Class.forName(CFBH_API, false, OptionalModApis.class.getClassLoader())
                    .getMethod("registerKitchenRecipeHandler", Class.class, handler);
            return Arrays.stream(Class.forName(CFBH_RECIPE_STATUS, false, OptionalModApis.class.getClassLoader())
                            .getConstructors())
                    .mapToInt(Constructor::getParameterCount)
                    .anyMatch(count -> count == 5 || count == 6);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
