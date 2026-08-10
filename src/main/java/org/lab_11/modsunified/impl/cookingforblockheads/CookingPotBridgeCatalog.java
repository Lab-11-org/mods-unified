package org.lab_11.modsunified.impl.cookingforblockheads;

import org.lab_11.modsunified.impl.platform.RuntimeBindings;
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
                logger.info(
                        "Skipping cooking-pot bridge target '{}' because required mods are not loaded: {}.",
                        target.displayName(),
                        String.join(", ", target.missingRequiredModIds())
                );
                continue;
            }

            final List<String> unavailableBindings = target.unavailableBindings();
            if (!unavailableBindings.isEmpty()) {
                logger.warn("Skipping cooking-pot bridge target '{}' because bindings are unavailable: {}.",
                        target.displayName(), String.join(", ", unavailableBindings));
                continue;
            }

            activeTargets.add(target);
            logger.info("Enabled cooking-pot bridge target '{}'.", target.displayName());
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
