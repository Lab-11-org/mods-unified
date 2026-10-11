package org.lab_11.modsunified.impl.cookingforblockheads;

import org.lab_11.modsunified.impl.platform.RuntimeBindings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BridgeCatalogCheck {
    private BridgeCatalogCheck() {
    }

    public static void main(final String[] args) {
        final List<CookingPotBridgeTarget> targets = CookingPotBridgeCatalog.allTargets();
        require(targets.size() == 5, "expected 5 container families, got " + targets.size());

        final Set<String> keys = new HashSet<>();
        int recipeBindingCount = 0;
        for (final CookingPotBridgeTarget target : targets) {
            require(keys.add(target.targetKey()), "duplicate target key " + target.targetKey());
            require(CookingPotBridgeCatalog.findByKey(target.targetKey()).orElse(null) == target,
                    "catalog lookup did not return " + target.targetKey());
            require(CookingPotBridgeCatalog.isTargetKey(target.targetKey()),
                    "catalog did not recognize " + target.targetKey());
            recipeBindingCount += target.recipeBindings().size();
        }
        require(recipeBindingCount == 7, "expected 7 recipe bindings, got " + recipeBindingCount);
        require(CookingPotBridgeCatalog.findByKey("missing_target").isEmpty(),
                "unknown target lookup must be empty");
        require(!CookingPotBridgeCatalog.isTargetKey("missing_target"),
                "unknown target key must not be recognized");

        requireBindingCount(BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_POT, 2);
        requireBindingCount(BridgeKeys.TARGET_KALEIDOSCOPE_COOKERY_STOCKPOT, 2);
        checkRuntimeDungeonBindings();
    }

    private static void requireBindingCount(final String targetKey, final int expected) {
        final CookingPotBridgeTarget target = CookingPotBridgeCatalog.findByKey(targetKey).orElseThrow();
        require(target.recipeBindings().size() == expected,
                targetKey + " expected " + expected + " recipe bindings, got " + target.recipeBindings().size());
    }

    private static void checkRuntimeDungeonBindings() {
        final var runtime = RuntimeBindings.active();
        final CookingPotBridgeTarget target = CookingPotBridgeCatalog
                .findByKey(BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT)
                .orElseThrow();
        require(target.blockEntityClassName().equals(runtime.dungeonMonsterBlockEntityClassName()),
                "dungeon block entity class does not match " + runtime.profile().id());
        require(target.recipeBindings().size() == 1,
                "dungeon target must have exactly one recipe binding");
        require(target.recipeBindings().get(0).recipeClassName().equals(runtime.dungeonMonsterRecipeClassName()),
                "dungeon recipe class does not match " + runtime.profile().id());
    }

    private static void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
