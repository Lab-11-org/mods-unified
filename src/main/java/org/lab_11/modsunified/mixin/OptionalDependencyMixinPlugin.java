package org.lab_11.modsunified.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.lab_11.modsunified.impl.cookingforblockheads.BridgeKeys;
import org.lab_11.modsunified.impl.platform.LoaderApiCompat;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Skips integration mixins unless CFBH and their optional target are present. */
public final class OptionalDependencyMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (!LoaderApiCompat.isModLoaded(BridgeKeys.MOD_COOKING_FOR_BLOCKHEADS)) {
            return false;
        }

        final String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        final String targetMod = OptionalMixinTargetMods.forMixin(simpleName);
        return targetMod == null || LoaderApiCompat.isModLoaded(targetMod);
    }

    @Override public void onLoad(final String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(final String targetClassName, final ClassNode targetClass,
                                   final String mixinClassName, final IMixinInfo mixinInfo) {}
    @Override public void postApply(final String targetClassName, final ClassNode targetClass,
                                    final String mixinClassName, final IMixinInfo mixinInfo) {}
}
