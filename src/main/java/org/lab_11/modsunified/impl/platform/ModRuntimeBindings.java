package org.lab_11.modsunified.impl.platform;

public record ModRuntimeBindings(MinecraftRuntimeProfile profile,
                                 String cfbhApiClassName,
                                 String fallbackProviderBridgeClassName,
                                 String recipeSyncBridgeClassName,
                                 String dungeonOvenCompatClassName,
                                 String dungeonMonsterRecipeClassName,
                                 String dungeonMonsterBlockEntityClassName) {
}
