package dev.infectedware.skysong.ui;

import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Clip {
    private Clip() {
    }

    public static void push(GuiGraphics ctx, float x1, float y1, float x2, float y2) {
        Compat.flush(ctx);
        Matrix4f m = ctx.pose().last().pose();
        Vector3f a = m.transformPosition(x1, y1, 0.0F, new Vector3f());
        Vector3f b = m.transformPosition(x2, y2, 0.0F, new Vector3f());
        ctx.pose().pushPose();
        ctx.pose().last().pose().identity();
        ctx.enableScissor((int)Math.floor(a.x), (int)Math.floor(a.y), (int)Math.ceil(b.x), (int)Math.ceil(b.y));
        ctx.pose().popPose();
    }

    public static void pop(GuiGraphics ctx) {
        Compat.flush(ctx);
        ctx.disableScissor();
    }
}
