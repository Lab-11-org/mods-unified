package org.lab_11.modsunified.impl.cookingforblockheads;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.spi.ToolProvider;

final class ForgeKaleidoscopeCookwareCheck {
    private ForgeKaleidoscopeCookwareCheck() {
    }

    public static void main(final String[] args) throws Exception {
        final String mixin = read(
                "org/lab_11/modsunified/mixin/cookingforblockheads/KaleidoscopePotBlockMixin.class");
        final String kitchenShape = read(
                "org/lab_11/modsunified/mixin/cookingforblockheads/CfbhKitchenBlockShapeMixin.class");
        final String processor = read(
                "org/lab_11/modsunified/impl/cookingforblockheads/CookingPotProcessorCapability.class");
        if (!mixin.contains("setPlacedBy") || !mixin.contains("has_base")) {
            throw new AssertionError("Forge Kaleidoscope cookware must remove its stand after oven placement");
        }
        if (!kitchenShape.contains("BlockKitchen")) {
            throw new AssertionError("Forge CFBH kitchen collision must use full-block height 16 like NeoForge 1.21.1");
        }
        checkKitchenShapes();
        if (!processor.contains("refresh")) {
            throw new AssertionError("Forge Kaleidoscope cookware must refresh after direct ingredient transfer");
        }
    }

    private static void checkKitchenShapes() {
        final StringWriter output = new StringWriter();
        final PrintWriter writer = new PrintWriter(output);
        final int result = ToolProvider.findFirst("javap").orElseThrow().run(writer, writer,
                "-c", "-p", "-classpath", System.getProperty("java.class.path"),
                "org.lab_11.modsunified.mixin.cookingforblockheads.CfbhKitchenBlockShapeMixin");
        final String instructions = output.toString();
        if (result != 0 || !instructions.contains("double 16.0d")
                || instructions.contains("double 15.0d")) {
            throw new AssertionError("Kitchen shapes must use height 16 instead of 15: " + instructions);
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
