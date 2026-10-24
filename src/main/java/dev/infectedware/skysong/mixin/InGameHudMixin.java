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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class InGameHudMixin {
    private static final String HOTBAR = "renderItemHotbar";
    private static final String CROSSHAIR = "renderCrosshair";
    private static final String EFFECTS = "renderEffects";
    private static final String CHAT = "renderChat";

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void skysong$renderHotbar(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (CustomHotbar.render(Compat.gui(context), tickCounter.getGameTimeDeltaPartialTick(true))) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void skysong$renderCrosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Crosshair.render(Compat.gui(context))) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void skysong$renderStatusEffects(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (ModSettings.potionsEnabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderChat", at = @At("HEAD"))
    private void skysong$beforeChat(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Compat.flush(Compat.gui(context));
        HpFocusComp.captureIfNeeded();
        Hooks.drawHud(context, tickCounter.getGameTimeDeltaPartialTick(true));
    }
}
