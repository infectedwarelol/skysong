package com.spotifyclient.mixin;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.hud.InputEvents;
import com.spotifyclient.viewmodel.ViewModel;
import com.spotifyclient.viewmodel.ViewModelEditScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void versefall$onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        int key = event.key();
        if (window == Compat.window()) {
            Minecraft mc = Minecraft.getInstance();
            if (key == ModSettings.viewModelEditKey && action == 1 && mc.screen == null && mc.player != null && ViewModel.enabled()) {
                Compat.later(() -> {
                    if (mc.screen == null) {
                        mc.setScreen(new ViewModelEditScreen());
                    }
                });
            }

            InputEvents.key(key, action);
        }
    }
}
