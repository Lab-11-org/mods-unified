package org.lab_11.modsunified.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class OptionalModMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (!isLoaded("cookingforblockheads")) {
            return false;
        }
        if (mixinClassName.endsWith("CookingPotBlockEntityMixin")) {
            return isLoaded("farmersdelight");
        }
        if (mixinClassName.endsWith("CopperPotBlockEntityMixin")) {
            return isLoaded("minersdelight");
        }
        if (mixinClassName.endsWith("MonsterPotBlockEntityMixin")) {
            return isLoaded("dungeonsdelight");
        }
        return true;
    }

    private static boolean isLoaded(final String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
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
