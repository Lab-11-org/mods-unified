package org.lab_11.modsunified.impl.cookingforblockheads;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Optional;

public final class BridgeMarkerRegistry {
    private static final Map<String, Item> MARKER_ITEMS = Map.of(
            BridgeKeys.TARGET_DUNGEONS_DELIGHT_MONSTER_POT, Items.STRUCTURE_BLOCK,
            BridgeKeys.TARGET_MINERS_DELIGHT_COPPER_POT, Items.JIGSAW,
            BridgeKeys.MARKER_DUNGEON_OVEN, Items.SMOKER
    );

    private static final Map<String, String> MISSING_REQUIREMENT_TOOLTIPS = Map.of(
            BridgeKeys.MARKER_DUNGEON_OVEN, BridgeKeys.TOOLTIP_MISSING_DUNGEON_OVEN
    );

    private BridgeMarkerRegistry() {
    }

    public static Item markerItemFor(final String markerKey) {
        return MARKER_ITEMS.getOrDefault(markerKey, Items.BARRIER);
    }

    public static Optional<String> missingRequirementTooltipKey(final String markerKey) {
        return Optional.ofNullable(MISSING_REQUIREMENT_TOOLTIPS.get(markerKey));
    }
}
