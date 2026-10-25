package dev.infectedware.skysong.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SkysongMixinPlugin implements IMixinConfigPlugin {
    private static final String MOD_MENU_MIXIN = "dev.infectedware.skysong.mixin.ModMenuCreditsMixin";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !MOD_MENU_MIXIN.equals(mixinClassName) || FabricLoader.getInstance().isModLoaded("modmenu");
    }
}
