package dev.infectedware.skysong.viewmodel;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.VScreen;
import dev.infectedware.skysong.gui.click.Sounds;
import dev.infectedware.skysong.render.GlassRenderer;
import dev.infectedware.skysong.render.fx.HandFx;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Theme;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

public final class ViewModelEditScreen extends VScreen {
    private static final float RING = 30.0F;
    private static final float SQUASH = 0.34F;
    private static final float HIT = 5.0F;
    private static final float HANDLE = 8.0F;
    private InteractionHand selected = InteractionHand.MAIN_HAND;
    private InteractionHand hoverHand;
    private ViewModelEditScreen.Mode mode = ViewModelEditScreen.Mode.NONE;
    private ViewModelEditScreen.Mode hoverMode = ViewModelEditScreen.Mode.NONE;
    private float grabDx;
    private float grabDy;
    private float startX;
    private float startY;
    private float startPitch;
    private float startYaw;
    private float startRoll;
    private float startAngle;
    private float appear;
    private long lastNs = System.nanoTime();

    public ViewModelEditScreen() {
        super(Component.literal("ViewModel"));
    }

    @Override
    protected void init() {
        Sounds.play("module_settings_open");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ViewModel.held = null;
        ModConfig.save();
        Sounds.play("module_settings_close");
        super.onClose();
    }

    @Override
    protected void draw(GuiGraphics c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        this.appear = Math.min(1.0F, this.appear + dt / 0.25F);
        float a = Theme.easeOutCubic(this.appear);
        ClientAccent.beginFrame();
        if (this.mode == ViewModelEditScreen.Mode.NONE) {
            this.updateHover(mouseX, mouseY);
        }

        this.drawGizmo(c, InteractionHand.OFF_HAND, a);
        this.drawGizmo(c, InteractionHand.MAIN_HAND, a);
        this.drawPanels(c, a);
    }

    private void drawGizmo(GuiGraphics c, InteractionHand hand, float a) {
        ViewModel.HandState s = ViewModel.state(hand);
        if (s.pivotValid) {
            float cx = s.screenX;
            float cy = s.screenY;
            float r = ringRadius(s);
            boolean sel = hand == this.selected;
            float base = (sel ? 1.0F : 0.45F) * a;
            int yawCol = ClientAccent.gradientA(255.0F * base);
            int pitchCol = ClientAccent.gradientB(255.0F * base);
            int rollCol = Colors.rgba(255, 255, 255, 215.0F * base);
            float thin = 1.6F;
            float thick = 2.8F;
            boolean hovered = hand == this.hoverHand;
            boolean active = hand == this.selected && this.mode != ViewModelEditScreen.Mode.NONE;
            ViewModelEditScreen.Mode lit = active ? this.mode : (hovered ? this.hoverMode : ViewModelEditScreen.Mode.NONE);
            GizmoDraw.ellipse(
                c,
                cx,
                cy,
                r,
                r,
                lit == ViewModelEditScreen.Mode.ROLL ? thick : thin,
                bright(rollCol, lit == ViewModelEditScreen.Mode.ROLL),
                Colors.multAlpha(rollCol, 0.6F)
            );
            GizmoDraw.ellipse(
                c,
                cx,
                cy,
                r,
                r * 0.34F,
                lit == ViewModelEditScreen.Mode.YAW ? thick : thin,
                bright(yawCol, lit == ViewModelEditScreen.Mode.YAW),
                Colors.multAlpha(yawCol, 0.35F)
            );
            GizmoDraw.ellipse(
                c,
                cx,
                cy,
                r * 0.34F,
                r,
                lit == ViewModelEditScreen.Mode.PITCH ? thick : thin,
                bright(pitchCol, lit == ViewModelEditScreen.Mode.PITCH),
                Colors.multAlpha(pitchCol, 0.35F)
            );
            boolean moveLit = lit == ViewModelEditScreen.Mode.MOVE;
            int handle = Colors.rgba(255, 255, 255, (moveLit ? 255 : 220) * base);
            R2D.circle(c, cx, cy, moveLit ? 4.2F : 3.4F, Colors.rgba(12, 12, 16, 150.0F * base));
            R2D.circle(c, cx, cy, moveLit ? 2.8F : 2.2F, handle);
            float arm = 9.5F;
            arrow(c, cx, cy - arm, 0.0F, -1.0F, handle);
            arrow(c, cx, cy + arm, 0.0F, 1.0F, handle);
            arrow(c, cx - arm, cy, -1.0F, 0.0F, handle);
            arrow(c, cx + arm, cy, 1.0F, 0.0F, handle);
        }
    }

    private static int bright(int color, boolean lit) {
        return lit ? Colors.mixWhite(color | 0xFF000000, 0.35F) & 16777215 | color & 0xFF000000 : color;
    }

    private static void arrow(GuiGraphics c, float x, float y, float dx, float dy, int col) {
        float px = -dy;
        float py = dx;
        GlassRenderer.fillPolygon(
            c,
            new float[][]{
                {x + dx * 2.6F, y + dy * 2.6F}, {x - dx * 1.2F + px * 2.2F, y - dy * 1.2F + py * 2.2F}, {x - dx * 1.2F - px * 2.2F, y - dy * 1.2F - py * 2.2F}
            },
            col
        );
    }

    private void drawPanels(GuiGraphics c, float a) {
        float s = Theme.unitScale();
        float vw = this.width / s;
        float vh = this.height / s;
        c.pose().pushPose();
        c.pose().scale(s, s, 1.0F);
        float[] t = ViewModel.targets(this.selected);
        String title = (this.selected == InteractionHand.MAIN_HAND ? "Main hand" : "Off hand") + "  ·  Edit mode";
        String values = String.format(
            Locale.ROOT, "Size %.2f   X %.2f  Y %.2f  Z %.2f   Pitch %.0f°  Yaw %.0f°  Roll %.0f°", t[6], t[0], t[1], t[2], t[3], t[4], t[5]
        );
        float tw = Math.max(R2D.width(MsdfFont.Font.SEMIBOLD, title, 8.0F), R2D.width(MsdfFont.Font.MEDIUM, values, 6.5F)) + 20.0F;
        float tx = (vw - tw) / 2.0F;
        float ty = 50.0F - (1.0F - a) * 6.0F;
        R2D.clientRect(c, tx, ty, tw, 30.0F, a);
        R2D.text(c, MsdfFont.Font.SEMIBOLD, title, tx + (tw - R2D.width(MsdfFont.Font.SEMIBOLD, title, 8.0F)) / 2.0F, ty + 5.0F, 8.0F, Colors.multAlpha(-1, a));
        R2D.text(
            c,
            MsdfFont.Font.MEDIUM,
            values,
            tx + (tw - R2D.width(MsdfFont.Font.MEDIUM, values, 6.5F)) / 2.0F,
            ty + 17.0F,
            6.5F,
            Colors.rgba(215, 220, 235, 220.0F * a)
        );
        String help = "Drag center: move   ·   Rings: turn   ·   Scroll: size   ·   Shift + scroll: depth   ·   R: reset   ·   Tab: other hand   ·   Q: done";
        float hw = R2D.width(MsdfFont.Font.MEDIUM, help, 6.5F) + 18.0F;
        float hx = (vw - hw) / 2.0F;
        float hy = vh - 58.0F + (1.0F - a) * 6.0F;
        R2D.clientRect(c, hx, hy, hw, 16.0F, a);
        R2D.text(c, MsdfFont.Font.MEDIUM, help, hx + 9.0F, hy + 4.5F, 6.5F, Colors.rgba(230, 234, 245, 235.0F * a));
        c.pose().popPose();
    }

    private static float ringRadius(ViewModel.HandState s) {
        return 30.0F * Math.clamp(s.screenSize / 60.0F, 0.8F, 1.6F);
    }

    private void updateHover(float mx, float my) {
        this.hoverHand = null;
        this.hoverMode = ViewModelEditScreen.Mode.NONE;
        float best = Float.MAX_VALUE;

        for (InteractionHand hand : new InteractionHand[]{this.selected, other(this.selected)}) {
            ViewModel.HandState s = ViewModel.state(hand);
            if (s.pivotValid) {
                ViewModelEditScreen.Mode m = this.hit(s, mx, my);
                if (m != ViewModelEditScreen.Mode.NONE) {
                    float d = (float)Math.hypot(mx - s.screenX, my - s.screenY);
                    if (d < best) {
                        best = d;
                        this.hoverHand = hand;
                        this.hoverMode = m;
                    }
                }
            }
        }
    }

    private ViewModelEditScreen.Mode hit(ViewModel.HandState s, float mx, float my) {
        float cx = s.screenX;
        float cy = s.screenY;
        float r = ringRadius(s);
        if (Math.hypot(mx - cx, my - cy) <= 12.0) {
            return ViewModelEditScreen.Mode.MOVE;
        } else if (GizmoDraw.distanceToEllipse(mx, my, cx, cy, r, r * 0.34F) <= 5.0F) {
            return ViewModelEditScreen.Mode.YAW;
        } else if (GizmoDraw.distanceToEllipse(mx, my, cx, cy, r * 0.34F, r) <= 5.0F) {
            return ViewModelEditScreen.Mode.PITCH;
        } else {
            return GizmoDraw.distanceToEllipse(mx, my, cx, cy, r, r) <= 5.0F ? ViewModelEditScreen.Mode.ROLL : ViewModelEditScreen.Mode.NONE;
        }
    }

    @Override
    public boolean onClick(double mouseX, double mouseY, int button) {
        float mx = (float)mouseX;
        float my = (float)mouseY;
        if (button != 0) {
            return true;
        }

        this.updateHover(mx, my);
        InteractionHand hand = this.hoverHand;
        ViewModelEditScreen.Mode m = this.hoverMode;
        if (hand == null && HandFx.coveredAt(mx, my, this.width, this.height)) {
            hand = nearestHand(mx, my);
            m = ViewModelEditScreen.Mode.MOVE;
        }

        if (hand == null) {
            return true;
        }

        this.selected = hand;
        this.mode = m;
        ViewModel.HandState s = ViewModel.state(hand);
        float[] t = ViewModel.targets(hand);
        this.grabDx = s.screenX - mx;
        this.grabDy = s.screenY - my;
        this.startX = mx;
        this.startY = my;
        this.startPitch = t[3];
        this.startYaw = t[4];
        this.startRoll = t[5];
        this.startAngle = (float)Math.toDegrees(Math.atan2(my - s.screenY, mx - s.screenX));
        if (this.mode == ViewModelEditScreen.Mode.MOVE) {
            ViewModel.held = hand;
        }

        Sounds.play("button");
        return true;
    }

    @Override
    public boolean onDrag(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.mode == ViewModelEditScreen.Mode.NONE) {
            return true;
        }

        float mx = (float)mouseX;
        float my = (float)mouseY;
        float[] t = ViewModel.targets(this.selected);
        ViewModel.HandState s = ViewModel.state(this.selected);
        switch (this.mode) {
            case MOVE:
                float[] o = ViewModel.solveOffsetFor(this.selected, mx + this.grabDx, my + this.grabDy);
                ViewModel.setOffset(this.selected, o[0], o[1], t[2]);
                break;
            case YAW:
                ViewModel.setRotation(this.selected, t[3], this.startYaw + (mx - this.startX) * 0.8F, t[5]);
                break;
            case PITCH:
                ViewModel.setRotation(this.selected, this.startPitch + (my - this.startY) * 0.8F, t[4], t[5]);
                break;
            case ROLL:
                float angle = (float)Math.toDegrees(Math.atan2(my - s.screenY, mx - s.screenX));
                ViewModel.setRotation(this.selected, t[3], t[4], this.startRoll - ViewModel.wrap(angle - this.startAngle));
        }

        return true;
    }

    @Override
    public boolean onRelease(double mouseX, double mouseY, int button) {
        if (this.mode != ViewModelEditScreen.Mode.NONE) {
            ModConfig.save();
        }

        this.mode = ViewModelEditScreen.Mode.NONE;
        ViewModel.held = null;
        return true;
    }

    @Override
    protected boolean scrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0.0) {
            return true;
        }

        float mx = (float)mouseX;
        float my = (float)mouseY;
        InteractionHand hand = nearestHandWithin(mx, my, 90.0F);
        if (hand == null) {
            hand = this.selected;
        }

        this.selected = hand;
        float[] t = ViewModel.targets(hand);
        if (Compat.shiftDown()) {
            ViewModel.setOffset(hand, t[0], t[1], ViewModel.clamp(t[2] + (float)vertical * 0.05F, -1.5F, 1.5F));
        } else {
            ViewModel.setScale(hand, t[6] + (float)vertical * 0.08F);
        }

        ModConfig.save();
        Sounds.play("slider");
        return true;
    }

    @Override
    public boolean onKey(int key, int scanCode, int mods) {
        if (key == 81 || key == 256) {
            this.onClose();
            return true;
        } else if (key == 82) {
            ViewModel.reset(this.selected);
            Sounds.play("button");
            return true;
        } else if (key == 258) {
            this.selected = other(this.selected);
            Sounds.play(this.selected == InteractionHand.MAIN_HAND ? "switch_right" : "switch_left");
            return true;
        } else {
            return super.onKey(key, scanCode, mods);
        }
    }

    private static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static InteractionHand nearestHand(float mx, float my) {
        InteractionHand best = null;
        float bestD = Float.MAX_VALUE;

        for (InteractionHand hand : InteractionHand.values()) {
            ViewModel.HandState s = ViewModel.state(hand);
            if (s.pivotValid) {
                float d = (float)Math.hypot(mx - s.screenX, my - s.screenY);
                if (d < bestD) {
                    bestD = d;
                    best = hand;
                }
            }
        }

        return best;
    }

    private static InteractionHand nearestHandWithin(float mx, float my, float max) {
        InteractionHand h = nearestHand(mx, my);
        if (h == null) {
            return null;
        }

        ViewModel.HandState s = ViewModel.state(h);
        return Math.hypot(mx - s.screenX, my - s.screenY) <= max ? h : null;
    }

    public void dragForTest(InteractionHand hand, float dx, float dy) {
        ViewModel.HandState s = ViewModel.state(hand);
        this.onClick(s.screenX, s.screenY, 0);
        this.onDrag(s.screenX + dx, s.screenY + dy, 0, dx, dy);
        this.onRelease(s.screenX + dx, s.screenY + dy, 0);
    }

    public void scrollForTest(InteractionHand hand, double amount) {
        ViewModel.HandState s = ViewModel.state(hand);
        this.scrolled(s.screenX, s.screenY, 0.0, amount);
    }

    public static boolean pivotVisible(InteractionHand hand) {
        return ViewModel.state(hand).pivotValid;
    }

    public static float[] pivot(InteractionHand hand) {
        ViewModel.HandState s = ViewModel.state(hand);
        return new float[]{s.screenX, s.screenY};
    }

    public static boolean settingsChanged() {
        return ModSettings.vmMainX != 0.0F || ModSettings.vmMainY != 0.0F || ModSettings.vmMainScale != 1.0F;
    }

    private enum Mode {
        NONE,
        MOVE,
        YAW,
        PITCH,
        ROLL
    }
}
