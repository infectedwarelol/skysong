package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.hud.InputEvents;
import dev.infectedware.skysong.viewmodel.ViewModel;
import dev.infectedware.skysong.viewmodel.ViewModelEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void skysong$onMouseButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        int button = info.button();
        Minecraft mc = Minecraft.getInstance();
        if (window == Compat.window()) {
            if (-100 - button == ModSettings.viewModelEditKey && action == 1 && mc.screen == null && mc.player != null && ViewModel.enabled()) {
                Compat.later(() -> {
                    if (mc.screen == null) {
                        mc.setScreen(new ViewModelEditScreen());
                    }
                });
                ci.cancel();
            } else {
                InputEvents.mouseButton(button, action);
            }
        }
    }
}
