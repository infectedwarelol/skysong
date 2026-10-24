package com.spotifyclient.mixin;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.hud.InputEvents;
import com.spotifyclient.viewmodel.ViewModel;
import com.spotifyclient.viewmodel.ViewModelEditScreen;
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
    private void versefall$onMouseButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
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
