package dev.infectedware.skysong.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;

public final class Targets {
    private Targets() {
    }

    public static TextureTarget create(int w, int h, boolean depth) {
        return new TextureTarget("Skysong", w, h, depth);
    }

    public static void resize(RenderTarget t, int w, int h) {
        t.resize(w, h);
    }

    public static TextureTarget ensure(TextureTarget t, int w, int h, boolean depth) {
        if (t == null) {
            return create(w, h, depth);
        }

        if (t.width != w || t.height != h) {
            resize(t, w, h);
        }

        return t;
    }

    public static void setFilter(RenderTarget t, boolean linear) {
        Gpu.setSampling(Gpu.color(t), linear, false);
    }

}
