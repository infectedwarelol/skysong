package dev.infectedware.skysong.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

public final class Theme {

    private Theme() {
    }

    public static int pixelsPerUnit() {
        Window w = Minecraft.getInstance().getWindow();
        int ppu = Math.round(w.getHeight() / 540.0F);
        return Math.clamp(ppu, 2, 4);
    }

    public static float unitScale() {
        return (float)pixelsPerUnit() / Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static float easeOutCubic(float t) {
        t = Math.clamp(t, 0.0F, 1.0F);
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    public static int alpha(int argb, float factor) {
        int a = Math.round((argb >>> 24 & 0xFF) * Math.clamp(factor, 0.0F, 1.0F));
        return a << 24 | argb & 16777215;
    }
}
