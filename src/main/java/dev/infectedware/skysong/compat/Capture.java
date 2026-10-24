package dev.infectedware.skysong.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;

public final class Capture {
    private static RenderTarget target;

    private Capture() {
    }

    public static void begin(RenderTarget into) {
        target = into;
        Gpu.bind(into);
    }

    public static void end() {
        target = null;
    }

    public static RenderTarget target() {
        return target;
    }
}
