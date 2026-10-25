package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.hud.InputEvents;
import dev.infectedware.skysong.viewmodel.ViewModel;
import dev.infectedware.skysong.viewmodel.ViewModelEditScreen;
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
    private void skysong$onKey(long l, int i, KeyEvent keyEvent, CallbackInfo ci) {
        int key = keyEvent.key();
        if (l == Compat.window()) {
            Minecraft mc = Minecraft.getInstance();
            if (key == ModSettings.viewModelEditKey && i == 1 && mc.screen == null && mc.player != null && ViewModel.enabled()) {
                Compat.later(() -> {
                    if (mc.screen == null) {
                        mc.setScreen(new ViewModelEditScreen());
                    }
                });
            }

            InputEvents.key(key, i);
        }
    }
}
