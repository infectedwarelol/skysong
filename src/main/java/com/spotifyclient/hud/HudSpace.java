package com.spotifyclient.hud;

import com.mojang.blaze3d.platform.Window;
import com.spotifyclient.ui.Theme;
import net.minecraft.client.Minecraft;

public final class HudSpace {
    private HudSpace() {
    }

    public static float scale() {
        return Theme.unitScale();
    }

    public static float width() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth() / scale();
    }

    public static float height() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight() / scale();
    }

    public static float mouseX() {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        return (float)(mc.mouseHandler.xpos() * w.getGuiScaledWidth() / Math.max(1, w.getScreenWidth()) / scale());
    }

    public static float mouseY() {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        return (float)(mc.mouseHandler.ypos() * w.getGuiScaledHeight() / Math.max(1, w.getScreenHeight()) / scale());
    }

    public static float clampX(float x, float w) {
        return Math.max(0.0F, Math.min(x, width() - w));
    }

    public static float clampY(float y, float h) {
        return Math.max(0.0F, Math.min(y, height() - h));
    }
}
