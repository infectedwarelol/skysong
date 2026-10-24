package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.VScreen;
import dev.infectedware.skysong.hud.Crosshair;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Theme;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class CrosshairEditorScreen extends VScreen {
    private static final float PANEL_W = 300.0F;
    private static final float PANEL_H = 190.0F;
    private static final float PAD = 12.0F;
    private static final float GRID = 150.0F;
    private static final float GRID_X = 12.0F;
    private static final float GRID_Y = 32.0F;
    private static final float SIDE_X = 176.0F;
    private static final float SIDE_W = 112.0F;
    private final boolean[] cells = new boolean[225];
    private final Anim appear = new Anim(260);
    private int painting = -1;
    private int lastCellX = -1;
    private int lastCellY = -1;
    private float mirrorXT;
    private float mirrorYT;
    private float hoverBack;
    private float hoverCross;
    private float hoverClear;
    private long lastNs = System.nanoTime();

    public CrosshairEditorScreen() {
        super(Component.literal("Crosshair editor"));
        System.arraycopy(Crosshair.pattern(), 0, this.cells, 0, this.cells.length);
        this.mirrorXT = ModSettings.crosshairMirrorX ? 1.0F : 0.0F;
        this.mirrorYT = ModSettings.crosshairMirrorY ? 1.0F : 0.0F;
    }

    private float scale() {
        float fit = Math.min((this.width - 16.0F) / 300.0F, (this.height - 16.0F) / 190.0F);
        return Math.min(Theme.unitScale(), fit);
    }

    private float px() {
        return (this.width / this.scale() - 300.0F) / 2.0F;
    }

    private float py() {
        return (this.height / this.scale() - 190.0F) / 2.0F;
    }

    private float cellSize() {
        return 10.0F;
    }

    @Override
    protected void draw(GuiGraphics c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        float s = this.scale();
        float mx = mouseX / s;
        float my = mouseY / s;
        float a = this.appear.forwards(true).get();
        float x = this.px();
        float y = this.py() + (1.0F - a) * 6.0F;
        this.mirrorXT = Anim.follow(this.mirrorXT, ModSettings.crosshairMirrorX ? 1.0F : 0.0F, dt, 16.0F);
        this.mirrorYT = Anim.follow(this.mirrorYT, ModSettings.crosshairMirrorY ? 1.0F : 0.0F, dt, 16.0F);
        c.pose().pushPose();
        c.pose().scale(s, s, 1.0F);
        R2D.clientRectFixed(c, x, y, 300.0F, 190.0F, 12.0F, a);
        Ui.text(c, "Crosshair editor", x + 12.0F, y + 11.0F, 8.0F, Ui.white(240.0F * a));
        Ui.text(c, "15×15", x + 12.0F + Ui.width("Crosshair editor", 8.0F) + 5.0F, y + 12.2F, 6.0F, Ui.white(110.0F * a));
        String back = "‹ Back";
        float bw = Ui.width(back, 6.5F);
        float bx = x + 300.0F - 12.0F - bw;
        float by = y + 11.5F;
        boolean overBack = Ui.inside(mx, my, bx - 4.0F, by - 3.0F, bw + 8.0F, 12.0F);
        this.hoverBack = Anim.follow(this.hoverBack, overBack ? 1.0F : 0.0F, dt, 16.0F);
        Ui.text(c, back, bx, by, 6.5F, Colors.lerp(Ui.white(150.0F * a), ClientAccent.accentBright(255.0F * a), this.hoverBack));
        this.drawGrid(c, x + 12.0F, y + 32.0F, mx, my, a);
        this.drawSide(c, x + 176.0F, y + 32.0F, mx, my, a, dt);
        c.pose().popPose();
    }

    private void drawGrid(GuiGraphics c, float gx, float gy, float mx, float my, float a) {
        float cs = this.cellSize();
        R2D.rect(c, gx - 3.0F, gy - 3.0F, 156.0F, 156.0F, 6.0F, Colors.rgba(0, 0, 0, 70.0F * a));

        for (int i = 0; i <= 15; i++) {
            boolean mid = i == 7 || i == 8;
            int line = Ui.white((mid ? 34 : 14) * a);
            R2D.rect(c, gx + i * cs - 0.25F, gy, 0.5F, 150.0F, 0.0F, line);
            R2D.rect(c, gx, gy + i * cs - 0.25F, 150.0F, 0.5F, 0.0F, line);
        }

        for (int i = 0; i < this.cells.length; i++) {
            if (this.cells[i]) {
                float cx = gx + i % 15 * cs;
                float cy = gy + i / 15 * cs;
                R2D.rect(c, cx + 0.9F, cy + 0.9F, cs - 1.8F, cs - 1.8F, 1.8F, Ui.white(238.0F * a));
            }
        }

        int[] hc = this.cellAt(mx, my);
        if (hc != null) {
            for (int[] p : mirrored(hc[0], hc[1])) {
                R2D.outline(c, gx + p[0] * cs + 0.4F, gy + p[1] * cs + 0.4F, cs - 0.8F, cs - 0.8F, 1.8F, 0.8F, ClientAccent.accentBright(200.0F * a));
            }
        }
    }

    private void drawSide(GuiGraphics c, float sx, float sy, float mx, float my, float a, float dt) {
        float s = this.scale();
        label(c, "PREVIEW", sx, sy, a);
        float pvY = sy + 9.0F;
        float pvH = 52.0F;
        R2D.rect(c, sx, pvY, 112.0F, pvH, 5.0F, Colors.rgba(0, 0, 0, 70.0F * a));
        float fit = (pvH - 10.0F) / 15.0F;
        this.preview(c, sx + 31.36F, pvY + pvH / 2.0F, Math.min(fit * 0.5F, ModSettings.crosshairScale / s), a);
        this.preview(c, sx + 80.64F, pvY + pvH / 2.0F, Math.min(fit, ModSettings.crosshairScale * 2.0F / s), a);
        float symY = pvY + pvH + 10.0F;
        label(c, "SYMMETRY", sx, symY, a);
        toggleRow(c, "Mirror X", sx, symY + 9.0F, this.mirrorXT, a);
        toggleRow(c, "Mirror Y", sx, symY + 24.0F, this.mirrorYT, a);
        float btnY = symY + 42.0F;
        this.hoverCross = Anim.follow(this.hoverCross, Ui.inside(mx, my, sx, btnY, 112.0F, 14.0F) ? 1.0F : 0.0F, dt, 16.0F);
        this.hoverClear = Anim.follow(this.hoverClear, Ui.inside(mx, my, sx, btnY + 18.0F, 112.0F, 14.0F) ? 1.0F : 0.0F, dt, 16.0F);
        button(c, sx, btnY, "Cross", this.hoverCross, a);
        button(c, sx, btnY + 18.0F, "Clear", this.hoverClear, a);
        String hint = "LMB draw  ·  RMB erase";
        Ui.text(c, hint, sx + (112.0F - Ui.width(hint, 5.5F)) / 2.0F, btnY + 36.5F, 5.5F, Ui.white(105.0F * a));
    }

    private static void label(GuiGraphics c, String s, float x, float y, float a) {
        Ui.text(c, s, x, y, 5.5F, Ui.white(110.0F * a));
    }

    private static void toggleRow(GuiGraphics c, String s, float x, float y, float on, float a) {
        Ui.text(c, s, x, y + 2.5F, 6.5F, Ui.white(215.0F * a));
        Widget.drawToggle(c, x + 112.0F - 19.1F, y, 19.1F, 10.4F, on, a);
    }

    private static void button(GuiGraphics c, float x, float y, String s, float hover, float a) {
        Ui.panelBg(c, x, y, 112.0F, 14.0F, 3.0F, a * (1.0F + hover * 0.7F));
        R2D.outline(c, x, y, 112.0F, 14.0F, Ui.cornerRadius(3.0F, 112.0F, 14.0F), 0.5F, ClientAccent.accentSoft((30.0F + 60.0F * hover) * a));
        Ui.text(c, s, x + (112.0F - Ui.width(s, 6.5F)) / 2.0F, y + 3.6F, 6.5F, ClientAccent.accentSoft(230.0F * a));
    }

    private void preview(GuiGraphics c, float cx, float cy, float cell, float a) {
        float half = 15.0F * cell / 2.0F;
        int dark = Colors.rgba(0, 0, 0, 170.0F * a);
        float o = Math.max(0.35F, cell / 3.0F);

        for (int pass = 0; pass < 2; pass++) {
            if (pass != 0 || ModSettings.crosshairOutline) {
                for (int i = 0; i < this.cells.length; i++) {
                    if (this.cells[i]) {
                        float x = cx - half + i % 15 * cell;
                        float y = cy - half + i / 15 * cell;
                        if (pass == 0) {
                            R2D.rect(c, x - o, y - o, cell + o * 2.0F, cell + o * 2.0F, 0.0F, dark);
                        } else {
                            R2D.rect(c, x, y, cell, cell, 0.0F, Colors.multAlpha(Crosshair.cellColor(i % 15, i / 15), a));
                        }
                    }
                }
            }
        }
    }

    private int[] cellAt(float mx, float my) {
        float gx = this.px() + 12.0F;
        float gy = this.py() + 32.0F;
        if (!(mx < gx) && !(my < gy) && !(mx >= gx + 150.0F) && !(my >= gy + 150.0F)) {
            int cx = (int)((mx - gx) / this.cellSize());
            int cy = (int)((my - gy) / this.cellSize());
            return new int[]{Math.min(cx, 14), Math.min(cy, 14)};
        } else {
            return null;
        }
    }

    private static int[][] mirrored(int x, int y) {
        int mx = 14 - x;
        int my = 14 - y;
        boolean fx = ModSettings.crosshairMirrorX;
        boolean fy = ModSettings.crosshairMirrorY;
        if (fx && fy) {
            return new int[][]{{x, y}, {mx, y}, {x, my}, {mx, my}};
        } else if (fx) {
            return new int[][]{{x, y}, {mx, y}};
        } else {
            return fy ? new int[][]{{x, y}, {x, my}} : new int[][]{{x, y}};
        }
    }

    private void paint(int x, int y) {
        for (int[] p : mirrored(x, y)) {
            this.cells[p[1] * 15 + p[0]] = this.painting == 1;
        }
    }

    private void paintLine(int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));

        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0.0F : (float)i / steps;
            this.paint(Math.round(x0 + (x1 - x0) * t), Math.round(y0 + (y1 - y0) * t));
        }
    }

    @Override
    public boolean onClick(double mouseX, double mouseY, int button) {
        float s = this.scale();
        float mx = (float)mouseX / s;
        float my = (float)mouseY / s;
        float x = this.px();
        float y = this.py();
        int[] cell = this.cellAt(mx, my);
        if (cell == null || button != 0 && button != 1) {
            if (button != 0) {
                return true;
            } else {
                String back = "‹ Back";
                float bw = Ui.width(back, 6.5F);
                float bx = x + 300.0F - 12.0F - bw;
                if (Ui.inside(mx, my, bx - 4.0F, y + 8.5F, bw + 8.0F, 12.0F)) {
                    this.onClose();
                    return true;
                } else {
                    float sx = x + 176.0F;
                    float symY = y + 32.0F + 9.0F + 52.0F + 10.0F;
                    if (Ui.inside(mx, my, sx, symY + 8.0F, 112.0F, 13.0F)) {
                        ModSettings.crosshairMirrorX = !ModSettings.crosshairMirrorX;
                        Sounds.play(ModSettings.crosshairMirrorX ? "switch_right" : "switch_left");
                        ModConfig.save();
                        return true;
                    } else if (Ui.inside(mx, my, sx, symY + 23.0F, 112.0F, 13.0F)) {
                        ModSettings.crosshairMirrorY = !ModSettings.crosshairMirrorY;
                        Sounds.play(ModSettings.crosshairMirrorY ? "switch_right" : "switch_left");
                        ModConfig.save();
                        return true;
                    } else {
                        float btnY = symY + 42.0F;
                        if (Ui.inside(mx, my, sx, btnY, 112.0F, 14.0F)) {
                            Arrays.fill(this.cells, false);
                            Crosshair.cross(this.cells);
                            this.save();
                            Sounds.play("button");
                            return true;
                        } else if (Ui.inside(mx, my, sx, btnY + 18.0F, 112.0F, 14.0F)) {
                            Arrays.fill(this.cells, false);
                            this.save();
                            Sounds.play("button");
                            return true;
                        } else {
                            return true;
                        }
                    }
                }
            }
        } else {
            this.painting = button == 0 ? 1 : 0;
            this.lastCellX = cell[0];
            this.lastCellY = cell[1];
            this.paint(cell[0], cell[1]);
            return true;
        }
    }

    @Override
    public boolean onDrag(double mouseX, double mouseY, int button, double dx, double dy) {
        if (this.painting < 0) {
            return true;
        }

        float s = this.scale();
        int[] cell = this.cellAt((float)mouseX / s, (float)mouseY / s);
        if (cell != null) {
            if (this.lastCellX >= 0) {
                this.paintLine(this.lastCellX, this.lastCellY, cell[0], cell[1]);
            } else {
                this.paint(cell[0], cell[1]);
            }

            this.lastCellX = cell[0];
            this.lastCellY = cell[1];
        }

        return true;
    }

    @Override
    public boolean onRelease(double mouseX, double mouseY, int button) {
        if (this.painting >= 0) {
            this.painting = -1;
            this.lastCellX = this.lastCellY = -1;
            this.save();
        }

        return true;
    }

    @Override
    public boolean onKey(int key, int scanCode, int modifiers) {
        if (key == 256) {
            this.onClose();
            return true;
        } else {
            return super.onKey(key, scanCode, modifiers);
        }
    }

    private void save() {
        Crosshair.savePattern(this.cells);
        ModSettings.crosshairStyle = 0;
        ModConfig.save();
    }

    @Override
    public void onClose() {
        this.save();
        Minecraft.getInstance().setScreen(ClickGuiScreen.shared());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void paintForTest(int x, int y, boolean draw) {
        this.painting = draw ? 1 : 0;
        this.paint(x, y);
        this.painting = -1;
        this.save();
    }
}
