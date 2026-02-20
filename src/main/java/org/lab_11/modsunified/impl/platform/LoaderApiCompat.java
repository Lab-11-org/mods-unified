package org.lab_11.modsunified.impl.platform;

import java.lang.reflect.Method;
import java.util.Optional;

public final class LoaderApiCompat {
    private static final String NEOFORGE_MOD_LIST_CLASS = "net.neoforged.fml.ModList";
    private static final String FORGE_MOD_LIST_CLASS = "net.minecraftforge.fml.ModList";

    private LoaderApiCompat() {
    }

    public static boolean isModLoaded(final String modId) {
        final Object modList = resolveModList();
        if (modList == null || modId == null || modId.isBlank()) {
            return false;
        }

        try {
            final Method isLoaded = modList.getClass().getMethod("isLoaded", String.class);
            final Object value = isLoaded.invoke(modList, modId);
            return value instanceof Boolean loaded && loaded;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static Optional<String> resolveModDisplayName(final String modId) {
        final Object modList = resolveModList();
        if (modList == null || modId == null || modId.isBlank()) {
            return Optional.empty();
        }

        try {
            final Method containerById = modList.getClass().getMethod("getModContainerById", String.class);
            final Object optionalContainer = containerById.invoke(modList, modId);
            if (!(optionalContainer instanceof Optional<?> optional) || optional.isEmpty()) {
                return Optional.empty();
            }

            final Object container = optional.get();
            final Method modInfoMethod = container.getClass().getMethod("getModInfo");
            final Object modInfo = modInfoMethod.invoke(container);
            if (modInfo == null) {
                return Optional.empty();
            }

            final Method displayNameMethod = modInfo.getClass().getMethod("getDisplayName");
            final Object displayName = displayNameMethod.invoke(modInfo);
            return displayName instanceof String name && !name.isBlank() ? Optional.of(name) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static Object resolveModList() {
        final Class<?> modListClass = resolveClass(NEOFORGE_MOD_LIST_CLASS, FORGE_MOD_LIST_CLASS);
        if (modListClass == null) {
            return null;
        }

        try {
            final Method getMethod = modListClass.getMethod("get");
            return getMethod.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Class<?> resolveClass(final String... classNames) {
        for (final String className : classNames) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException ignored) {
                // Try the next class name.
            }
        }
        return null;
    }
}
