package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.hud.Draggable;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import com.spotifyclient.theme.Colors;
import com.spotifyclient.ui.Clip;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

public abstract class ListHudComp extends Draggable {
    private static final float HEADER_H = 22.0F;
    private static final float CONTENT_TOP = 5.0F;
    private static final float PAD_X = 9.0F;
    private static final float PAD_BOTTOM = 5.0F;
    private static final float ROW_H = 11.5F;
    private static final float TITLE_SIZE = 8.2F;
    private static final float ROW_SIZE = 6.5F;
    private static final float VALUE_GAP = 10.0F;
    private static final float HEADER_EXTRA = 10.0F;
    private static final float ICON_SIZE = 8.5F;
    private static final float ICON_GAP = 4.0F;
    private static final float HEADER_ICON_SIZE = 7.0F;
    static final int ROW_COLOR = -3355444;
    static final int DIVIDER = -1997220937;
    private final String title;
    private final BooleanSupplier enabled;
    private final Map<Object, ListHudComp.RowState> rows = new LinkedHashMap<>();
    private final Smooth visibility = new Smooth(0.0F);
    private final Smooth width = new Smooth(60.0F);
    private final Smooth divider = new Smooth(0.0F);
    private final Smooth headerIcon = new Smooth(0.0F);
    private boolean lastVisible;
    private boolean everHad;
    private long collapsedAt;
    private int activeCount;
    private float rowsProgress;
    private float currentW = 60.0F;
    private float currentH = 22.0F;

    protected ListHudComp(String id, String title, BooleanSupplier enabled, float x, float y) {
        super(id, x, y);
        this.title = title;
        this.enabled = enabled;
    }

    protected String headerIcon() {
        return null;
    }

    protected float iconSlot() {
        return 8.5F;
    }

    protected abstract List<ListHudComp.Row> collectRows();

    @Override
    public float width() {
        return this.currentW;
    }

    @Override
    public float height() {
        return this.currentH;
    }

    @Override
    public boolean interactive() {
        return this.enabled.getAsBoolean();
    }

    private void syncRows() {
        List<ListHudComp.Row> list = this.enabled.getAsBoolean() ? this.collectRows() : List.of();

        for (ListHudComp.RowState r : this.rows.values()) {
            r.active = false;
        }

        this.activeCount = 0;

        for (ListHudComp.Row row : list) {
            if (row != null && row.id() != null) {
                ListHudComp.RowState st = this.rows.computeIfAbsent(row.id(), k -> new ListHudComp.RowState());
                st.active = true;
                st.name = row.name() == null ? "" : row.name();
                st.value = row.value() == null ? "" : row.value();
                st.icon = row.icon();
                st.pulse = row.pulse();
                st.color = row.color();
                this.activeCount++;
            }
        }

        this.rowsProgress = 0.0F;
        Iterator<ListHudComp.RowState> it = this.rows.values().iterator();

        while (it.hasNext()) {
            ListHudComp.RowState st = it.next();
            if (st.active != st.lastActive) {
                st.anim.run(st.active ? 1.0F : 0.0F, st.active ? 0.22 : 0.18);
                st.lastActive = st.active;
            }

            float p = st.anim.get();
            this.rowsProgress += p;
            if (!st.active && p <= 0.001F) {
                it.remove();
            }
        }

        this.rowsProgress = Math.min(1.0F, this.rowsProgress);
    }

    private float headerMinWidth() {
        float base = R2D.width(MsdfFont.Font.SEMIBOLD, this.title, 8.2F) + 10.0F;
        float p = this.headerIcon.get();
        String icon = this.headerIcon();
        if (!(p <= 0.001F) && icon != null) {
            float[] tb = R2D.bounds(MsdfFont.Font.SEMIBOLD, this.title, 8.2F);
            float[] ib = R2D.bounds(MsdfFont.Font.ICONS, icon, 7.0F);
            float withIcon = 18.0F + (tb[2] - tb[0]) + 6.0F + (ib[2] - ib[0]);
            return base + Math.max(0.0F, withIcon - base) * p;
        } else {
            return base;
        }
    }

    @Override
    protected void render(GuiGraphics c) {
        this.syncRows();
        boolean hasRows = this.activeCount > 0;
        this.divider.target(hasRows ? 1.0F : 0.0F, hasRows ? 0.24 : 0.14);
        boolean wantIcon = this.headerIcon() != null && ModSettings.hudIcons;
        this.headerIcon.target(wantIcon ? 1.0F : 0.0F, wantIcon ? 0.25 : 0.2);
        float minW = this.headerMinWidth();
        float wantW = minW;
        float h = 22.0F;
        float dv = this.divider.get();
        if (dv > 0.001F) {
            h += 5.5F * dv;
        }

        for (ListHudComp.RowState st : this.rows.values()) {
            float p = st.anim.get();
            if (!(p <= 0.001F) || st.active) {
                float iconW = st.icon != null ? this.iconSlot() + 4.0F : 0.0F;
                wantW = Math.max(
                    wantW, 18.0F + iconW + R2D.width(MsdfFont.Font.MEDIUM, st.name, 6.5F) + 10.0F + R2D.width(MsdfFont.Font.MEDIUM, st.value, 6.5F)
                );
                h += 11.5F * p;
            }
        }

        if (dv > 0.001F) {
            h += 5.0F * dv;
        }

        this.width.target(Math.max(wantW, minW), 0.24);
        this.currentH = Math.max(22.0F, h);
        boolean visible = this.computeVisible();
        if (visible != this.lastVisible) {
            this.visibility.run(visible ? 1.0F : 0.0F, visible ? 0.18 : 0.12);
            this.lastVisible = visible;
        }

        float a = this.visibility.get();
        if (visible && a <= 0.01F) {
            a = 0.01F;
        }

        if (!(a <= 0.01F) || visible) {
            this.currentW = Math.max(minW, this.width.get());
            float x = this.x();
            float y = this.y();
            float s = 0.96F + a * 0.04F;
            pushScale(c, x + this.currentW / 2.0F, y + this.currentH / 2.0F, s);
            R2D.clientRect(c, x, y, this.currentW, this.currentH, a);
            float titleTop = y + 4.4F;
            Clip.push(c, x, titleTop, x + this.currentW, titleTop + 13.2F);
            float ip = this.headerIcon.get();
            float[] tb = R2D.bounds(MsdfFont.Font.SEMIBOLD, this.title, 8.2F);
            float tw = tb[2] - tb[0];
            float cy = y + 11.0F;
            float ty = cy - (tb[1] + tb[3]) / 2.0F;
            float centered = x + (this.currentW - tw) / 2.0F - tb[0];
            float left = x + 9.0F - tb[0];
            R2D.text(c, MsdfFont.Font.SEMIBOLD, this.title, centered + (left - centered) * ip, ty, 8.2F, Colors.multAlpha(titleColor(), a));
            String icon = this.headerIcon();
            if (icon != null && ip > 0.004F) {
                float[] ib = R2D.bounds(MsdfFont.Font.ICONS, icon, 7.0F);
                float ix = x + this.currentW - 9.0F - ib[2] + 5.0F * (1.0F - ip);
                R2D.text(c, MsdfFont.Font.ICONS, icon, ix, cy - (ib[1] + ib[3]) / 2.0F, 7.0F, Colors.multAlpha(titleColor(), a * ip * 0.8F));
            }

            Clip.pop(c);
            float dw = Math.max(0.0F, this.currentW - 18.0F) * smoothstep(dv);
            if (dw > 0.25F) {
                R2D.rect(c, x + (this.currentW - dw) / 2.0F, y + 22.0F, dw, 0.5F, 0.0F, Colors.multAlpha(-1997220937, a * smoothstep(dv)));
            }

            float rowY = y + 22.0F + 5.5F * dv;
            float[] ref = R2D.bounds(MsdfFont.Font.MEDIUM, "[H]", 6.5F);
            float refMid = (ref[1] + ref[3]) / 2.0F;

            for (ListHudComp.RowState st : this.rows.values()) {
                float p = st.anim.get();
                if (!(p <= 0.001F)) {
                    float pulse = st.pulse != null ? Math.max(0.0F, Math.min(1.0F, st.pulse.alpha())) : 1.0F;
                    float ra = a * pulse;
                    float valueP = delayed(p, 0.12F);
                    float iconW = st.icon != null ? this.iconSlot() + 4.0F : 0.0F;
                    float vw = R2D.width(MsdfFont.Font.MEDIUM, st.value, 6.5F);
                    float nameX = x + 9.0F + iconW - 7.0F * (1.0F - p);
                    float valueX = x + this.currentW - 9.0F - vw + 7.0F * (1.0F - valueP);
                    float rowH = 11.5F * p;
                    float mid = rowY + rowH / 2.0F;
                    float clipH = 15.5F * p;
                    Clip.push(c, x, Math.max(y, mid - clipH / 2.0F), x + this.currentW, Math.min(y + this.currentH, mid + clipH / 2.0F));
                    c.pose().pushPose();
                    c.pose().translate(0.0F, mid, 0.0F);
                    c.pose().scale(1.0F, Math.max(0.001F, p), 1.0F);
                    c.pose().translate(0.0F, -mid, 0.0F);
                    if (st.icon != null) {
                        st.icon.draw(c, x + 9.0F, mid - this.iconSlot() / 2.0F, this.iconSlot(), ra * p);
                    }

                    int col = st.color != 0 ? st.color : -3355444;
                    float textY = mid - refMid;
                    if (ra * p > 0.004F) {
                        R2D.text(c, MsdfFont.Font.MEDIUM, st.name, nameX, textY, 6.5F, Colors.multAlpha(col, ra * p));
                    }

                    if (ra * valueP > 0.004F) {
                        R2D.text(c, MsdfFont.Font.MEDIUM, st.value, valueX, textY, 6.5F, Colors.multAlpha(col, ra * valueP));
                    }

                    c.pose().popPose();
                    Clip.pop(c);
                    rowY += rowH;
                }
            }

            c.pose().popPose();
        }
    }

    private boolean computeVisible() {
        boolean shown = this.enabled.getAsBoolean() && (this.activeCount > 0 || DragSystem.get().dragMode());
        if (shown) {
            this.everHad = true;
            this.collapsedAt = 0L;
            return true;
        }

        if (!this.everHad) {
            return false;
        }

        boolean stillAnimating = this.rowsProgress > 0.001F
            || this.divider.get() > 0.004F
            || this.currentH > 22.5F
            || Math.abs(this.width.get() - this.width.targetValue()) > 0.5F;
        if (stillAnimating) {
            this.collapsedAt = 0L;
            return true;
        }

        if (this.collapsedAt == 0L) {
            this.collapsedAt = System.currentTimeMillis();
        }

        return System.currentTimeMillis() - this.collapsedAt < 100L;
    }

    static int titleColor() {
        return Colors.lerp(-1, ClientAccent.gradientA(255.0F) | 0xFF000000, 0.1F);
    }

    static float smoothstep(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static float delayed(float p, float delay) {
        return p <= delay ? 0.0F : Math.min(1.0F, (p - delay) / (1.0F - delay));
    }

    public interface AlphaPulse {
        float alpha();
    }

    public interface IconDrawer {
        void draw(GuiGraphics var1, float var2, float var3, float var4, float var5);
    }

    public record Row(Object id, String name, String value, ListHudComp.IconDrawer icon, ListHudComp.AlphaPulse pulse, int color) {
        public Row(Object id, String name, String value, ListHudComp.IconDrawer icon) {
            this(id, name, value, icon, null, 0);
        }
    }

    private static final class RowState {
        final Smooth anim = new Smooth(0.0F);
        boolean active;
        boolean lastActive;
        String name = "";
        String value = "";
        ListHudComp.IconDrawer icon;
        ListHudComp.AlphaPulse pulse;
        int color;
    }
}
