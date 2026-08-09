package org.lab_11.modsunified.impl.platform;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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

        final Method isLoadedMethod;
        try {
            isLoadedMethod = modList.getClass().getMethod("isLoaded", String.class);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }

        for (final String candidate : modIdCandidates(modId)) {
            try {
                final Object value = isLoadedMethod.invoke(modList, candidate);
                if (value instanceof Boolean loaded && loaded) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next alias candidate.
            }
        }
        return false;
    }

    public static Optional<String> resolveModDisplayName(final String modId) {
        final Object modList = resolveModList();
        if (modList == null || modId == null || modId.isBlank()) {
            return Optional.empty();
        }

        final Method containerById;
        try {
            containerById = modList.getClass().getMethod("getModContainerById", String.class);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }

        for (final String candidate : modIdCandidates(modId)) {
            final Optional<String> resolved = resolveDisplayNameForCandidate(modList, containerById, candidate);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveDisplayNameForCandidate(final Object modList,
                                                                   final Method containerById,
                                                                   final String modId) {
        try {
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

    private static Set<String> modIdCandidates(final String modId) {
        final Set<String> candidates = new LinkedHashSet<>();
        candidates.add(modId);

        if ("minersdelight".equals(modId)) {
            candidates.add("miners_delight");
        } else if ("miners_delight".equals(modId)) {
            candidates.add("minersdelight");
        }

        return candidates;
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
