package org.lab_11.modsunified.impl.cookingforblockheads;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class LegacyKitchenProviderCheck {
    private LegacyKitchenProviderCheck() {
    }

    public static void main(final String[] args) throws Exception {
        final String bridge = read(
                "org/lab_11/modsunified/impl/cookingforblockheads/LegacyKitchenConnectorCompat.class",
                StandardCharsets.ISO_8859_1
        );
        final String recipeBridge = read(
                "org/lab_11/modsunified/impl/cookingforblockheads/LegacyRecipeBookCraftBridge.class",
                StandardCharsets.ISO_8859_1
        );
        final String tag = read(
                "data/cookingforblockheads/tags/blocks/kitchen_item_providers.json",
                StandardCharsets.UTF_8
        );
        final String connectors = read(
                "data/lab_11_mods_unified/cookingforblockheads/compat/kaleidoscope_cookery.json",
                StandardCharsets.UTF_8
        );
        if (!bridge.contains("KitchenItemCapabilityProvider")
                || !bridge.contains("tagged_kitchen_item_provider")
                || !bridge.contains("LOWEST")
                || !tag.contains("#farmersdelight:cabinets")
                || !connectors.contains("\"kitchenConnectors\"")
                || !connectors.contains("\"kaleidoscope_cookery:stove\"")) {
            throw new AssertionError("Legacy tagged kitchen-provider bridge is incomplete");
        }
        if (!recipeBridge.contains("isVirtualSourceSlot")) {
            throw new AssertionError("Legacy fridge ice-unit source slots must stay non-consuming");
        }
    }

    private static String read(final String path, final java.nio.charset.Charset charset) throws Exception {
        try (InputStream stream = LegacyKitchenProviderCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), charset);
        }
    }
}
