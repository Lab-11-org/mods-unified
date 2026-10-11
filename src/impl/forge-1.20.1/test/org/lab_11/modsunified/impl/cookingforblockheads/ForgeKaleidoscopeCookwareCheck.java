package org.lab_11.modsunified.impl.cookingforblockheads;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ForgeKaleidoscopeCookwareCheck {
    private ForgeKaleidoscopeCookwareCheck() {
    }

    public static void main(final String[] args) throws Exception {
        final String mixin = read(
                "org/lab_11/modsunified/mixin/cookingforblockheads/KaleidoscopePotBlockMixin.class");
        final String processor = read(
                "org/lab_11/modsunified/impl/cookingforblockheads/CookingPotProcessorCapability.class");
        if (!mixin.contains("setPlacedBy") || !mixin.contains("has_base")) {
            throw new AssertionError("Forge Kaleidoscope cookware must remove its stand after oven placement");
        }
        if (!processor.contains("refresh")) {
            throw new AssertionError("Forge Kaleidoscope cookware must refresh after direct ingredient transfer");
        }
    }

    private static String read(final String path) throws Exception {
        try (InputStream stream = ForgeKaleidoscopeCookwareCheck.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
