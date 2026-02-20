package org.lab_11.modsunified.impl.runtime;

public record ModRuntimeBindings(MinecraftRuntimeProfile profile,
                                 String cfbhApiClassName,
                                 String cfbhHandlerClassName,
                                 String fallbackProviderBridgeClassName,
                                 String recipeSyncBridgeClassName,
                                 String dungeonOvenCompatClassName,
                                 BridgeTargetProvider bridgeTargetProvider) {
}
