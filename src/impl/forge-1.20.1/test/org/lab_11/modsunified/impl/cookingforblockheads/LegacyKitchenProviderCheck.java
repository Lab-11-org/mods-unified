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
        final String tag = read(
                "data/cookingforblockheads/tags/blocks/kitchen_item_providers.json",
                StandardCharsets.UTF_8
        );
        if (!bridge.contains("KitchenItemCapabilityProvider")
                || !bridge.contains("tagged_kitchen_item_provider")
                || !bridge.contains("LOWEST")
                || !tag.contains("#farmersdelight:cabinets")) {
            throw new AssertionError("Legacy tagged kitchen-provider bridge is incomplete");
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
