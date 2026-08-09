package org.lab_11.modsunified.impl.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MinecraftApiCompat {
    private MinecraftApiCompat() {
    }

    public static ResourceLocation resourceLocation(final String namespace, final String path) {
        try {
            final Method method = ResourceLocation.class.getMethod("fromNamespaceAndPath", String.class, String.class);
            final Object value = method.invoke(null, namespace, path);
            if (value instanceof ResourceLocation resourceLocation) {
                return resourceLocation;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback to older constructor/parse APIs.
        }

        try {
            final Method parseMethod = ResourceLocation.class.getMethod("parse", String.class);
            final Object value = parseMethod.invoke(null, namespace + ":" + path);
            if (value instanceof ResourceLocation resourceLocation) {
                return resourceLocation;
            }
        } catch (ReflectiveOperationException ignored) {
            // Continue to constructor fallback.
        }

        try {
            final Constructor<ResourceLocation> constructor =
                    ResourceLocation.class.getDeclaredConstructor(String.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(namespace, path);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to construct ResourceLocation for " + namespace + ":" + path, e);
        }
    }

    public static boolean isSameItemSameData(final ItemStack left, final ItemStack right) {
        try {
            final Method method = ItemStack.class.getMethod("isSameItemSameComponents", ItemStack.class, ItemStack.class);
            final Object value = method.invoke(null, left, right);
            if (value instanceof Boolean result) {
                return result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Try legacy method.
        }

        try {
            final Method method = ItemStack.class.getMethod("isSameItemSameTags", ItemStack.class, ItemStack.class);
            final Object value = method.invoke(null, left, right);
            if (value instanceof Boolean result) {
                return result;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback to item identity.
        }

        return ItemStack.isSameItem(left, right);
    }

    public static String stackDataKey(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        try {
            final Method componentsMethod = ItemStack.class.getMethod("getComponents");
            final Object components = componentsMethod.invoke(stack);
            return components != null ? components.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            // Legacy fallback.
        }

        try {
            final Method tagMethod = ItemStack.class.getMethod("getTag");
            final Object tag = tagMethod.invoke(stack);
            return tag != null ? tag.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static void setCustomName(final ItemStack stack, final Component customName) {
        if (stack == null || customName == null) {
            return;
        }

        try {
            final Class<?> dataComponentsClass = Class.forName("net.minecraft.core.component.DataComponents");
            final Field customNameField = dataComponentsClass.getField("CUSTOM_NAME");
            final Object customNameComponentType = customNameField.get(null);
            final Method setMethod = ItemStack.class.getMethod("set", customNameComponentType.getClass(), Object.class);
            setMethod.invoke(stack, customNameComponentType, customName);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Legacy fallback.
        }

        try {
            final Method setHoverName = ItemStack.class.getMethod("setHoverName", Component.class);
            setHoverName.invoke(stack, customName);
        } catch (ReflectiveOperationException ignored) {
            // No compatible naming API found.
        }
    }
}
