package dev.infectedware.skysong.hud.components;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Targets;
import dev.infectedware.skysong.hud.DragSystem;
import dev.infectedware.skysong.hud.Draggable;
import dev.infectedware.skysong.hud.HudSpace;
import dev.infectedware.skysong.hud.ServerStats;
import dev.infectedware.skysong.render.r2d.R2D;
import net.minecraft.client.Minecraft;

public final class HpFocusComp extends Draggable {
    private static final float BASE_H = 46.0F;
    private static TextureTarget capture;
    private static boolean captured;
    private static boolean wanted;
    private static long capturedFrame = -1L;
    private final Smooth visibility = new Smooth(0.0F);
    private boolean lastVisible;

    public HpFocusComp() {
        super("hpfocus", 342.0F, 40.0F);
    }

    private static float[] size() {
        float aspect = ModSettings.hpFocusHeight > 0.5F ? ModSettings.hpFocusWidth / ModSettings.hpFocusHeight : 3.85F;
        float h = 46.0F * ModSettings.hpFocusScale;
        return new float[]{h * aspect, h};
    }

    @Override
    public float[] defaultPosition() {
        float[] s = size();
        return new float[]{(HudSpace.width() - s[0]) / 2.0F, HudSpace.height() * 0.58F - s[1]};
    }

    @Override
    public float width() {
        return size()[0];
    }

    @Override
    public float height() {
        return size()[1];
    }

    @Override
    public boolean interactive() {
        return ModSettings.hpFocusEnabled;
    }

    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        return ModSettings.hpFocusEnabled && mc.player != null && !mc.options.hideGui
            ? DragSystem.get().dragMode() || ServerStats.health(mc.player) <= ModSettings.hpFocusThreshold
            : false;
    }

    public static void captureIfNeeded() {
        Minecraft mc = Minecraft.getInstance();
        long frame = mc.getFrameTimeNs();
        if (frame != capturedFrame) {
            capturedFrame = frame;
            captured = false;
            if (wanted || shouldShow()) {
                Compat.drawCollectedGui();
                RenderTarget main = mc.getMainRenderTarget();
                double gui = mc.getWindow().getGuiScale();
                int cw = (int)Math.round(ModSettings.hpFocusWidth * gui);
                int ch = (int)Math.round(ModSettings.hpFocusHeight * gui);
                cw = Math.max(2, Math.min(cw, main.width));
                ch = Math.max(2, Math.min(ch, main.height));
                if (capture == null) {
                    capture = Targets.create(cw, ch, false);
                } else if (capture.width != cw || capture.height != ch) {
                    Targets.resize(capture, cw, ch);
                }

                int x0 = (main.width - cw) / 2;
                Gpu.blitRegion(main, capture, x0, 0, x0 + cw, ch, true);
                Gpu.clearAlpha(capture, 1.0F);
                Gpu.bind(main);
                captured = true;
            }
        }
    }

    @Override
    protected void render(GuiGraphics c) {
        boolean show = shouldShow();
        wanted = show;
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.18 : 0.14);
            this.lastVisible = show;
        }

        float a = this.visibility.get();
        if (!(a <= 0.01F)) {
            float[] s = size();
            float x = this.x();
            float y = this.y();
            R2D.clientRectFixed(c, x, y, s[0], s[1], Math.min(ModSettings.rectCornerRadius, 5.0F), a);
            if (captured && capture != null) {
                R2D.image(
                    c, Gpu.color(capture), x + 2.0F, y + 2.0F, s[0] - 4.0F, s[1] - 4.0F, 3.0F, 0.0F, 1.0F, 1.0F, 0.0F, Math.round(a * 255.0F) << 24 | 16777215
                );
            }
        }
    }
}
