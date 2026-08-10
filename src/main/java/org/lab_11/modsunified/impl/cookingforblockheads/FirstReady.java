package org.lab_11.modsunified.impl.cookingforblockheads;

import java.util.function.Predicate;

final class FirstReady {
    private FirstReady() {
    }

    static <T> T find(final Iterable<T> candidates, final Predicate<T> ready) {
        for (final T candidate : candidates) {
            if (ready.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
