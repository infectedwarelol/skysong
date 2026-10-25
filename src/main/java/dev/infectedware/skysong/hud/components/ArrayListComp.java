package dev.infectedware.skysong.hud.components;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.hud.Draggable;
import dev.infectedware.skysong.hud.HudSpace;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.module.ModuleRegistry;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArrayListComp extends Draggable {
    private static final float SIZE = 7.2F;
    private static final float ROW_H = 8.7F;
    private static final float PAD_X = 1.5F;
    private static final float RADIUS = 3.0F;
    private final Map<ClientModule, Smooth> rows = new HashMap<>();
    private final Map<ClientModule, Boolean> targets = new HashMap<>();
    private final Smooth visibility = new Smooth(0.0F);
    private boolean lastVisible;
    private boolean leftAligned = true;
    private boolean bottom;
    private float width = 1.0F;
    private float height = 9.7F;
    private float lastPinnedHeight = Float.NaN;
    private double wavePhase;
    private long lastWave;

    public ArrayListComp() {
        super("arraylist", 888.0F, 5.0F);
    }

    @Override
    public float[] defaultPosition() {
        return new float[]{HudSpace.width() - 72.0F, 5.0F};
    }

    @Override
    public float width() {
        return this.width;
    }

    @Override
    public float height() {
        return this.height;
    }

    @Override
    public boolean interactive() {
        return ModSettings.moduleListEnabled;
    }

    private static boolean listed(ClientModule m) {
        return m.isOn() && !m.name().equals("Click GUI") && ModuleRegistry.listedCategory(m.category());
    }

    @Override
    protected void render(GuiGraphics c) {
        for (ClientModule m : ModuleRegistry.ALL) {
            if (m.hasToggle()) {
                boolean on = listed(m);
                Smooth s = this.rows.computeIfAbsent(m, k -> new Smooth(0.0F));
                Boolean was = this.targets.get(m);
                if (was == null || was != on) {
                    s.run(on ? 1.0F : 0.0F, on ? 0.22 : 0.16);
                    this.targets.put(m, on);
                }
            }
        }

        this.leftAligned = this.x() + this.width / 2.0F <= HudSpace.width() / 2.0F;
        this.bottom = this.y() + this.height / 2.0F > HudSpace.height() / 2.0F;
        List<ClientModule> shown = new ArrayList<>();

        for (ClientModule m : this.rows.keySet()) {
            if (this.rows.get(m).get() > 0.001F) {
                shown.add(m);
            }
        }

        shown.sort(
            Comparator.<ClientModule>comparingDouble(mx -> R2D.width(MsdfFont.Font.MEDIUM, mx.name(), 7.2F)).reversed().thenComparing(ClientModule::name)
        );
        if (this.bottom) {
            Collections.reverse(shown);
        }

        float[] widths = new float[shown.size()];
        float[] heights = new float[shown.size()];
        float maxW = 0.0F;
        float total = 0.0F;

        for (int i = 0; i < shown.size(); i++) {
            float p = this.rows.get(shown.get(i)).get();
            widths[i] = R2D.width(MsdfFont.Font.MEDIUM, shown.get(i).name(), 7.2F) + 3.0F;
            heights[i] = 8.7F * p;
            maxW = Math.max(maxW, widths[i]);
            total += heights[i];
        }

        this.width = Math.max(1.0F, maxW);
        this.height = total + 1.0F;
        if (this.bottom && !Float.isNaN(this.lastPinnedHeight) && !this.drag().dragging() && Math.abs(this.height - this.lastPinnedHeight) > 1.0E-4F) {
            this.drag().adjustY(-(this.height - this.lastPinnedHeight));
            this.drag().syncToTarget();
        }

        this.lastPinnedHeight = this.height;
        boolean visible = ModSettings.moduleListEnabled && !shown.isEmpty();
        if (visible != this.lastVisible) {
            this.visibility.run(visible ? 1.0F : 0.0F, visible ? 0.18 : 0.12);
            this.lastVisible = visible;
        }

        float a = this.visibility.get();
        if (visible && a <= 0.01F) {
            a = 0.01F;
        }

        if (visible || !(a <= 0.01F)) {
            float x = this.x();
            float y = this.y();
            pushScale(c, x + this.width / 2.0F, y + this.height / 2.0F, 0.92F + a * 0.08F);
            float rowY = y;

            for (int i = 0; i < shown.size(); i++) {
                float rw = widths[i];
                float rh = heights[i];
                if (!(rh <= 0.05F)) {
                    float rx = this.leftAligned ? x : x + this.width - rw;
                    float overlapTop = i > 0 ? 0.75F : 0.0F;
                    boolean first = i == 0;
                    boolean last = i + 1 >= shown.size();
                    boolean topSticksOut = first || rw > widths[i - 1] + 0.01F;
                    boolean bottomSticksOut = last || rw > widths[i + 1] + 0.01F;
                    float straightTop = first ? 3.0F : 0.0F;
                    float straightBottom = last ? 3.0F : 0.0F;
                    float raggedTop = topSticksOut ? 3.0F : 0.0F;
                    float raggedBottom = bottomSticksOut ? 3.0F : 0.0F;
                    float rTL = this.leftAligned ? straightTop : raggedTop;
                    float rTR = this.leftAligned ? raggedTop : straightTop;
                    float rBL = this.leftAligned ? straightBottom : raggedBottom;
                    float rBR = this.leftAligned ? raggedBottom : straightBottom;
                    float extra = last ? 0.0F : 0.75F;
                    R2D.glass(c, rx, rowY - overlapTop, rw, rh + overlapTop + extra, rTL, rTR, rBR, rBL, a, first);
                    rowY += rh;
                }
            }

            long now = System.currentTimeMillis();
            long dt = this.lastWave == 0L ? 0L : Math.clamp(now - this.lastWave, 0L, 100L);
            this.lastWave = now;
            this.wavePhase = (this.wavePhase + (float)dt * ModSettings.arrayListWaveSpeed * 1.0E-4) % (Math.PI * 2);
            rowY = y;
            boolean rainbow = ClientAccent.isRainbow();

            for (int i = 0; i < shown.size(); i++) {
                ClientModule m = shown.get(i);
                float p = this.rows.get(m).get();
                float rh = heights[i];
                float tw = R2D.width(MsdfFont.Font.MEDIUM, m.name(), 7.2F);
                float tx = this.leftAligned ? x + 1.5F : x + this.width - 1.5F - tw;
                float ta = Math.clamp(a * p, 0.0F, 1.0F);
                float wave = 0.5F + 0.5F * (float)Math.sin((rowY + rh * 0.5F) * 0.05F - this.wavePhase);
                int color;
                if (rainbow) {
                    color = Colors.lerp(ClientAccent.rainbowFlow(-i * 0.05F, ta * 255.0F), ClientAccent.rainbowFlow(-i * 0.05F - 0.1F, ta * 255.0F), wave);
                } else {
                    int ga = ClientAccent.gradientA(ta * 255.0F);
                    int gb = ClientAccent.gradientB(ta * 255.0F);
                    if ((ga & 16777215) == (gb & 16777215)) {
                        gb = Colors.mixWhite(ga, 0.3F);
                    }

                    color = Colors.lerp(ga, gb, wave);
                }

                float[] bb = R2D.bounds(MsdfFont.Font.MEDIUM, "H", 7.2F);
                float ty = rowY + rh / 2.0F - (bb[1] + bb[3]) / 2.0F;
                if (ta > 0.004F) {
                    R2D.text(c, MsdfFont.Font.MEDIUM, m.name(), tx, ty, 7.2F, color);
                }

                rowY += rh;
            }

            c.pose().popPose();
        }
    }
}
