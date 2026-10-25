package dev.infectedware.skysong.mixin;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Hooks;
import dev.infectedware.skysong.hud.Crosshair;
import dev.infectedware.skysong.hud.CustomHotbar;
import dev.infectedware.skysong.hud.components.HpFocusComp;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class InGameHudMixin {
    @Unique
    private static final String HOTBAR = "renderItemHotbar";
    @Unique
    private static final String CROSSHAIR = "renderCrosshair";
    @Unique
    private static final String EFFECTS = "renderEffects";
    @Unique
    private static final String CHAT = "renderChat";

    @Inject(method = HOTBAR, at = @At("HEAD"), cancellable = true)
    private void skysong$renderHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CustomHotbar.render(Compat.gui(guiGraphics), deltaTracker.getGameTimeDeltaPartialTick(true))) {
            ci.cancel();
        }
    }

    @Inject(method = CROSSHAIR, at = @At("HEAD"), cancellable = true)
    private void skysong$renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Crosshair.render(Compat.gui(guiGraphics))) {
            ci.cancel();
        }
    }

    @Inject(method = EFFECTS, at = @At("HEAD"), cancellable = true)
    private void skysong$renderStatusEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModSettings.potionsEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = CHAT, at = @At("HEAD"))
    private void skysong$beforeChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Compat.flush();
        HpFocusComp.captureIfNeeded();
        Hooks.drawHud(guiGraphics);
    }
}
