package org.lab_11.modsunified.impl.cookingforblockheads;

import org.lab_11.modsunified.impl.runtime.RuntimeBindings;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class CookingPotBridgeCatalog {
    private CookingPotBridgeCatalog() {
    }

    public static List<CookingPotBridgeTarget> resolveActiveTargets(final Logger logger) {
        final List<CookingPotBridgeTarget> activeTargets = new ArrayList<>();
        for (final CookingPotBridgeTarget target : allTargets()) {
            if (!target.isModSetLoaded()) {
                continue;
            }

            if (target.resolveRecipeClass().isEmpty()
                    || target.resolveRecipeType().isEmpty()
                    || target.resolveBlockEntityClass().isEmpty()
                    || target.resolveBlockEntityType().isEmpty()) {
                logger.warn("Skipping cooking-pot bridge target '{}' because one or more reflective bindings are unavailable.",
                        target.displayName());
                continue;
            }

            activeTargets.add(target);
        }

        return List.copyOf(activeTargets);
    }

    public static String describeTargets(final List<CookingPotBridgeTarget> targets) {
        final List<String> names = new ArrayList<>(targets.size());
        for (final CookingPotBridgeTarget target : targets) {
            names.add(target.displayName());
        }
        return String.join(", ", names);
    }

    private static List<CookingPotBridgeTarget> allTargets() {
        return RuntimeBindings.active().bridgeTargetProvider().allTargets();
    }
}
