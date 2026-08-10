package org.lab_11.modsunified.impl.cookingforblockheads;

import java.util.List;

final class MultiPotSelectionCheck {
    private MultiPotSelectionCheck() {
    }

    public static void main(final String[] args) {
        final int[] checks = {0};
        final String selected = FirstReady.find(
                List.of("near-occupied", "far-ready", "later-ready"),
                candidate -> {
                    checks[0]++;
                    return candidate.endsWith("-ready");
                }
        );
        if (!"far-ready".equals(selected) || checks[0] != 2) {
            throw new AssertionError("selected=" + selected + ", checks=" + checks[0]);
        }
    }
}
