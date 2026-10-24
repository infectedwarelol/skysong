package com.spotifyclient.gui.click;

import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientTheme;
import com.spotifyclient.theme.Colors;
import com.spotifyclient.theme.ThemeManager;
import com.spotifyclient.ui.Clip;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class ThemesGrid {
    private static final float CARD_H = 35.0F;
    private static final float GAP = 3.0F;
    private static final float INSET = 4.0F;
    private static final float RADIUS = 4.0F;
    private final Map<ClientTheme, Anim> selected = new EnumMap<>(ClientTheme.class);
    private final Map<ClientTheme, Float> hover = new EnumMap<>(ClientTheme.class);
    private final Map<Integer, Anim> rowAppear = new HashMap<>();
    private final ScrollBar scrollBar = new ScrollBar();
    private float scroll;
    private float scrollTarget;
    private float contentH;

    public void open() {
        this.rowAppear.clear();
        this.scroll = this.scrollTarget = 0.0F;
    }

    public void scroll(double amount, float viewH) {
        float max = Math.max(0.0F, this.contentH - viewH + 8.0F);
        this.scrollTarget = Math.max(0.0F, Math.min(max, this.scrollTarget - (float)amount * 18.0F));
    }

    private float cardW(float areaW) {
        return (areaW - 3.0F - 8.0F - 6.0F) * 0.5F;
    }

    public float scrollTargetForTest() {
        return this.scrollTarget;
    }

    public boolean scrollbarGrab(float mx, float my) {
        return this.scrollBar.tryGrab(mx, my);
    }

    public void scrollbarRelease() {
        this.scrollBar.release();
    }

    public boolean click(float areaX, float areaY, float areaW, float areaH, float mx, float my) {
        if (!Ui.inside(mx, my, areaX, areaY, areaW, areaH)) {
            return false;
        }

        float cw = this.cardW(areaW);
        ClientTheme[] themes = ClientTheme.values();

        for (int i = 0; i < themes.length; i++) {
            float cx = i % 2 == 0 ? areaX + 4.0F : areaX + 4.0F + cw + 3.0F;
            float cy = areaY + 4.0F + i / 2 * 38.0F - this.scroll;
            if (Ui.inside(mx, my, cx, cy, cw, 35.0F)) {
                if (ThemeManager.current() != themes[i]) {
                    Sounds.play("button");
                }

                ThemeManager.set(themes[i]);
                return true;
            }
        }

        return false;
    }

    public void render(
        GuiGraphics c,
        float areaX,
        float areaY,
        float areaW,
        float areaH,
        float alpha,
        float appear,
        float dt,
        float mx,
        float my,
        boolean interactive,
        float panelRight,
        float panelRadius
    ) {
        this.scroll = Anim.follow(this.scroll, this.scrollTarget, dt, 14.0F);
        if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
            this.scroll = this.scrollTarget;
        }

        float cw = this.cardW(areaW);
        float slide = (1.0F - appear) * 8.0F;
        boolean inArea = interactive && Ui.inside(mx, my, areaX, areaY, areaW, areaH);
        ClientTheme[] themes = ClientTheme.values();
        Clip.push(c, areaX, areaY, areaX + areaW, areaY + areaH);

        for (int i = 0; i < themes.length; i++) {
            ClientTheme t = themes[i];
            int row = i / 2;
            float cx = i % 2 == 0 ? areaX + 4.0F : areaX + 4.0F + cw + 3.0F;
            float cy = areaY + 4.0F + row * 38.0F + slide - this.scroll;
            boolean over = inArea && Ui.inside(mx, my, cx, cy, cw, 35.0F);
            float hv = Anim.follow(this.hover.getOrDefault(t, 0.0F), over ? 1.0F : 0.0F, dt, 16.0F);
            this.hover.put(t, hv);
            if (!(cy + 35.0F < areaY - 5.0F) && !(cy > areaY + areaH + 5.0F)) {
                float rowP = this.rowAppear.computeIfAbsent(row, r -> new Anim(380)).forwards(true).get();
                if (!(rowP < 0.001F)) {
                    float fadeIn = Math.min(1.0F, rowP / 0.6F);
                    float a = alpha * (rowP < 0.999F ? fadeIn * fadeIn : 1.0F);
                    cy += (1.0F - fadeIn) * 6.0F;
                    boolean scaling = rowP < 0.999F;
                    if (scaling) {
                        float sc = 0.85F + 0.15F * fadeIn;
                        float pcx = cx + cw * 0.5F;
                        float pcy = cy + 17.5F;
                        c.pose().pushPose();
                        c.pose().translate(pcx, pcy, 0.0F);
                        c.pose().scale(sc, sc, 1.0F);
                        c.pose().translate(-pcx, -pcy, 0.0F);
                    }

                    float sel = this.selected.computeIfAbsent(t, k -> new Anim(220, ThemeManager.current() == k)).forwards(ThemeManager.current() == t).get();
                    R2D.rect(c, cx, cy, cw, 35.0F, 4.0F, Colors.rgba(0, 0, 0, (40.0F + 18.0F * hv) * a));
                    if (hv > 0.01F) {
                        R2D.rect(c, cx, cy, cw, 35.0F, 4.0F, Colors.rgba(255, 255, 255, 8.0F * hv * a));
                    }

                    float edgeA = (14.0F + 12.0F * hv) * (1.0F - sel) * a;
                    if (edgeA > 0.5F) {
                        R2D.outline(c, cx, cy, cw, 35.0F, 4.0F, 0.6F, Colors.rgba(255, 255, 255, edgeA));
                    }

                    if (sel > 0.01F) {
                        int ga = t.gradientA();
                        int gb = t.gradientB();
                        int mid = Colors.mixRgb(ga, gb, 0.5F);
                        R2D.rect(
                            c,
                            cx + 2.0F,
                            cy + 3.0F,
                            1.0F,
                            29.0F,
                            0.5F,
                            Colors.rgba(ga, 185.0F * sel * a),
                            Colors.rgba(ga, 185.0F * sel * a),
                            Colors.rgba(gb, 185.0F * sel * a),
                            Colors.rgba(gb, 185.0F * sel * a)
                        );
                        R2D.outline(
                            c,
                            cx,
                            cy,
                            cw,
                            35.0F,
                            4.0F,
                            0.7F,
                            Colors.rgba(ga, 110.0F * sel * a),
                            Colors.rgba(mid, 110.0F * sel * a),
                            Colors.rgba(gb, 110.0F * sel * a),
                            Colors.rgba(mid, 110.0F * sel * a)
                        );
                        R2D.rect(c, cx + cw - 9.0F, cy + 8.5F, 3.0F, 3.0F, 1.5F, Colors.rgba(t.accentBright(), 220.0F * sel * a));
                    }

                    Ui.text(c, t.displayName(), cx + 8.0F + sel * 3.0F, cy + 6.0F, 7.0F, Colors.rgba(255, 255, 255, (200.0F + 35.0F * hv + 20.0F * sel) * a));
                    int[] swatches = t.palette().length >= 2 ? t.palette() : t.shades();
                    int n = Math.min(swatches.length, 5);

                    for (int s = 0; s < n; s++) {
                        R2D.rect(c, cx + 8.0F + s * 12.0F, cy + 35.0F - 9.0F - 7.0F, 9.0F, 9.0F, 3.0F, Colors.rgba(swatches[s], 235.0F * a));
                    }

                    if (scaling) {
                        c.pose().popPose();
                    }
                }
            }
        }

        Clip.pop(c);
        int rows = (themes.length + 1) / 2;
        this.contentH = rows * 38.0F - 3.0F;
        float max = Math.max(0.0F, this.contentH - areaH + 8.0F);
        this.scrollTarget = Math.max(0.0F, Math.min(max, this.scrollTarget));
        this.scroll = Math.max(0.0F, Math.min(max, this.scroll));
        float edgeInset = Math.max(0.0F, ModuleCards.cornerEdgeInset(panelRadius, 1.0F) + 1.5F - 3.0F);
        float s = this.scrollBar.render(c, panelRight - 7.5F, areaY + 3.0F, areaH - 6.0F - edgeInset, areaH, this.contentH + 8.0F, this.scroll, alpha, mx, my);
        if (this.scrollBar.isDragging()) {
            this.scroll = this.scrollTarget = s;
        }
    }
}
