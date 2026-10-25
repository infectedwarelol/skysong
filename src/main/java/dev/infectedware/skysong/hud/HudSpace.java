package dev.infectedware.skysong.hud;

import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.Window;
import dev.infectedware.skysong.ui.Theme;
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
        return Mth.clamp(x, 0.0F, width() - w);
    }

    public static float clampY(float y, float h) {
        return Mth.clamp(y, 0.0F, height() - h);
    }
}
