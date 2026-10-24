package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.module.Binds;
import dev.infectedware.skysong.module.Category;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ModuleCards {
    static final float PAD = 5.0F;
    static final float GAP = 5.0F;
    static final float RADIUS = 6.0F;
    private static final float NAME_SIZE = 7.5F;
    private static final float DESC_SIZE = 6.0F;
    private static final float DESC_LINE = 7.0F;
    private static final float DESC_TOP = 19.0F;
    private static final float NO_DESC_H = 22.0F;
    private static final float CHECK = 8.5F;
    private static final float GEAR = 7.0F;
    private static final float GEAR_GAP = 7.0F;
    private static final float TITLE_CY = 10.75F;
    private static final String GEAR_GLYPH = "f";
    private final Map<String, Anim> enabled = new HashMap<>();
    private final Map<String, Float> hover = new HashMap<>();
    private final Map<String, Float> gearHover = new HashMap<>();
    private final Map<String, Float> gearOpen = new HashMap<>();
    private final Map<String, List<String>> descCache = new HashMap<>();
    private final Map<Integer, Anim> rowAppear = new HashMap<>();
    private final ScrollBar scrollBar = new ScrollBar();
    private float scroll;
    private float scrollTarget;
    private float contentH;
    private Object lastShown;
    private int appearMs = 380;
    private String focusName;
    private long focusUntil;
    private boolean focusScrollPending;
    private float focusDim;

    public static float colW(float areaW) {
        return (areaW - 5.0F - 10.0F) * 0.5F;
    }

    public void resetScroll() {
        this.scroll = this.scrollTarget = 0.0F;
        this.lastShown = null;
    }

    public void focus(String name) {
        this.focusName = name;
        this.focusUntil = System.currentTimeMillis() + 5000L;
        this.focusScrollPending = true;
    }

    public void scroll(double amount, float viewH) {
        float max = Math.max(0.0F, this.contentH - viewH);
        this.scrollTarget = Math.max(0.0F, Math.min(max, this.scrollTarget - (float)amount * 16.0F));
    }

    List<String> descLines(ClientModule m, float colW) {
        String key = m.name() + "|" + (int)colW;
        return this.descCache.computeIfAbsent(key, k -> {
            List<String> lines = new ArrayList<>();
            String d = m.description() == null ? "" : m.description();
            if (d.isEmpty()) {
                return lines;
            }

            float max = colW - 16.0F - 4.0F;
            StringBuilder line = new StringBuilder();

            for (String word : d.split(" ")) {
                String tryLine = line.isEmpty() ? word : line + " " + word;
                if (Ui.width(tryLine, 6.0F) > max && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(tryLine);
                }
            }

            if (!line.isEmpty()) {
                lines.add(line.toString());
            }

            return lines;
        });
    }

    float cardHeight(ClientModule m, float colW) {
        List<String> lines = this.descLines(m, colW);
        return lines.isEmpty() ? 22.0F : 19.0F + lines.size() * 7.0F + 7.0F;
    }

    private float topInColumn(List<ClientModule> list, int index, float colW) {
        float y = 0.0F;

        for (int i = index % 2; i < index; i += 2) {
            y += this.cardHeight(list.get(i), colW) + 5.0F;
        }

        return y;
    }

    public float[] cardRect(List<ClientModule> list, int index, float areaX, float areaY, float areaW) {
        if (index >= 0 && index < list.size()) {
            float cw = colW(areaW);
            float cx = index % 2 == 0 ? areaX + 5.0F : areaX + 5.0F + cw + 5.0F;
            float cy = areaY + 5.0F + this.topInColumn(list, index, cw) - this.scroll;
            return new float[]{cx, cy, cw, this.cardHeight(list.get(index), cw)};
        } else {
            return null;
        }
    }

    private float totalHeight(List<ClientModule> list, float cw) {
        float left = 0.0F;
        float right = 0.0F;

        for (int i = 0; i < list.size(); i++) {
            float h = this.cardHeight(list.get(i), cw) + 5.0F;
            if (i % 2 == 0) {
                left += h;
            } else {
                right += h;
            }
        }

        float tallest = Math.max(left, right);
        if (tallest > 0.0F) {
            tallest -= 5.0F;
        }

        return 10.0F + tallest;
    }

    public boolean hitGear(ClientModule m, float mx, float my, float cardX, float cardY, float cardW) {
        if (!m.hasSettings()) {
            return false;
        }

        float checkX = cardX + cardW - 8.5F - 8.0F;
        float gw = R2D.width(MsdfFont.Font.ICONS, "f", 7.0F);
        float gx = checkX - 7.0F - gw;
        return mx >= gx - 3.5F && mx <= gx + gw + 3.5F && my >= cardY + 3.5F && my <= cardY + 18.0F;
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
        Object shownKey,
        List<ClientModule> list,
        float mx,
        float my,
        boolean interactive,
        ClientModule settingsOpenFor,
        float panelRight,
        float panelRadius
    ) {
        float cw = colW(areaW);
        this.scroll = Anim.follow(this.scroll, this.scrollTarget, dt, 14.0F);
        if (Math.abs(this.scrollTarget - this.scroll) < 0.05F) {
            this.scroll = this.scrollTarget;
        }

        if (!Objects.equals(shownKey, this.lastShown)) {
            this.lastShown = shownKey;
            this.rowAppear.clear();
            this.appearMs = 380;
        }

        this.contentH = this.totalHeight(list, cw);
        float max = Math.max(0.0F, this.contentH - areaH);
        this.scrollTarget = Math.max(0.0F, Math.min(this.scrollTarget, max));
        int focusIndex = -1;
        if (this.focusName != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).name().equals(this.focusName)) {
                    focusIndex = i;
                }
            }
        }

        boolean focusing = focusIndex >= 0 && System.currentTimeMillis() < this.focusUntil;
        if (this.focusScrollPending && focusIndex >= 0) {
            float top = 5.0F + this.topInColumn(list, focusIndex, cw);
            float h = this.cardHeight(list.get(focusIndex), cw);
            this.scrollTarget = Math.max(0.0F, Math.min(max, top - (areaH - h) * 0.5F));
            this.focusScrollPending = false;
        }

        this.focusDim = Anim.follow(this.focusDim, focusing ? 1.0F : 0.0F, dt, 8.0F);
        if (!focusing && this.focusDim < 0.01F && !this.focusScrollPending) {
            this.focusName = null;
        }

        float edgeInset = Math.max(0.0F, cornerEdgeInset(panelRadius, 1.0F) + 1.5F - 3.0F);
        float s = this.scrollBar.render(c, panelRight - 7.5F, areaY + 3.0F, areaH - 6.0F - edgeInset, areaH, this.contentH, this.scroll, alpha, mx, my);
        if (this.scrollBar.isDragging()) {
            this.scroll = this.scrollTarget = s;
        }

        Clip.push(c, areaX, areaY, areaX + areaW, areaY + areaH);
        float slide = (1.0F - appear) * 8.0F;
        boolean inArea = interactive && Ui.inside(mx, my, areaX, areaY, areaW, areaH);

        for (int i = 0; i < list.size(); i++) {
            ClientModule m = list.get(i);
            int col = i % 2;
            int row = i / 2;
            float cx = col == 0 ? areaX + 5.0F : areaX + 5.0F + cw + 5.0F;
            float ch = this.cardHeight(m, cw);
            float cy = areaY + 5.0F + this.topInColumn(list, i, cw) - this.scroll + slide;
            boolean over = inArea && Ui.inside(mx, my, cx, cy, cw, ch);
            float hv = Anim.follow(this.hover.getOrDefault(m.name(), 0.0F), over ? 1.0F : 0.0F, dt, 16.0F);
            this.hover.put(m.name(), hv);
            if (!(cy + ch < areaY - 5.0F) && !(cy > areaY + areaH + 5.0F)) {
                float rowP = this.rowAppear.computeIfAbsent(row, r -> new Anim(this.appearMs)).forwards(true).get();
                if (!(rowP < 0.001F)) {
                    float a = alpha;
                    if (this.focusDim > 0.01F && !m.name().equals(this.focusName)) {
                        a *= 1.0F - 0.75F * this.focusDim;
                    }

                    float fadeIn = Math.min(1.0F, rowP / 0.6F);
                    float cardA = a * (rowP < 0.999F ? fadeIn * fadeIn : 1.0F);
                    cy += (1.0F - fadeIn) * 6.0F;
                    boolean scaling = rowP < 0.999F;
                    if (scaling) {
                        float sc = 0.85F + 0.15F * fadeIn;
                        float pcx = cx + cw * 0.5F;
                        float pcy = cy + ch * 0.5F;
                        c.pose().pushPose();
                        c.pose().translate(pcx, pcy, 0.0F);
                        c.pose().scale(sc, sc, 1.0F);
                        c.pose().translate(-pcx, -pcy, 0.0F);
                    }

                    float rBR = 6.0F;
                    if (col == 1 && cy + ch > areaY + areaH - 16.0F) {
                        rBR = 6.0F + 6.0F * Anim.clamp01((cy + ch - (areaY + areaH - 16.0F)) / 16.0F);
                    }

                    Anim en = this.enabled.computeIfAbsent(m.name(), k -> new Anim(220, m.isOn()));
                    float on = en.forwards(m.isOn()).get();
                    R2D.rect(c, cx, cy, cw, ch, 6.0F, 6.0F, rBR, 6.0F, Colors.rgba(10, 12, 16, (72.0F + 26.0F * hv) * cardA));
                    if (on > 0.01F) {
                        int ga = ClientAccent.gradientA(30.0F * on * cardA);
                        int gb = ClientAccent.gradientB(30.0F * on * cardA);
                        R2D.rect(c, cx, cy, cw, ch, 6.0F, 6.0F, rBR, 6.0F, ga, gb, gb, ga);
                    }

                    int edge = Colors.rgba(255, 255, 255, (14.0F + 12.0F * hv) * cardA);
                    int ea = Colors.lerp(edge, ClientAccent.gradientA(65.0F * cardA), on);
                    int eb = Colors.lerp(edge, ClientAccent.gradientB(65.0F * cardA), on);
                    R2D.outline(c, cx, cy, cw, ch, 6.0F, 0.6F, ea, eb, eb, ea);
                    float nameX = cx + 8.0F;
                    int key = Binds.key(m.name());
                    if (key != -1) {
                        String badge = Binds.shortName(key);
                        float bw = Math.max(10.0F, Ui.width(badge, 5.5F) + 6.0F);
                        float by = cy + 10.75F - 5.0F;
                        R2D.rect(c, nameX, by, bw, 10.0F, 3.0F, Colors.rgba(255, 255, 255, 16.0F * cardA));
                        R2D.outline(c, nameX, by, bw, 10.0F, 3.0F, 0.5F, Colors.rgba(255, 255, 255, 30.0F * cardA));
                        Ui.text(c, badge, nameX + (bw - Ui.width(badge, 5.5F)) * 0.5F, by + 1.75F, 5.5F, Colors.rgba(255, 255, 255, 205.0F * cardA));
                        nameX += bw + 5.0F;
                    }

                    Ui.text(c, m.name(), nameX, cy + 7.0F, 7.5F, Colors.rgba(255, 255, 255, (160.0F + 95.0F * (m.hasToggle() ? on : 1.0F)) * cardA));
                    int descColor = Colors.rgba(255, 255, 255, (78.0F + 27.0F * on) * cardA);
                    float dy = cy + 19.0F;

                    for (String line : this.descLines(m, cw)) {
                        Ui.text(c, line, cx + 8.0F, dy, 6.0F, descColor);
                        dy += 7.0F;
                    }

                    float checkX = cx + cw - 8.5F - 8.0F;
                    float checkY = cy + 10.75F - 4.25F;
                    if (m.hasToggle()) {
                        boolean overCheck = over && Ui.inside(mx, my, checkX - 2.0F, checkY - 2.0F, 12.5F, 12.5F);
                        drawCheck(c, checkX, checkY, on, cardA, overCheck);
                    }

                    if (m.hasSettings()) {
                        boolean open = settingsOpenFor == m;
                        float gw = R2D.width(MsdfFont.Font.ICONS, "f", 7.0F);
                        float gx = (m.hasToggle() ? checkX - 7.0F : cx + cw - 8.0F) - gw;
                        float gy = cy + 10.75F - 3.5F - 0.7F;
                        boolean overGear = over && mx >= gx - 3.0F && mx <= gx + gw + 3.0F && my >= cy + 4.0F && my <= cy + 17.5F;
                        float gh = Anim.follow(this.gearHover.getOrDefault(m.name(), 0.0F), overGear ? 1.0F : 0.0F, dt, 16.0F);
                        this.gearHover.put(m.name(), gh);
                        float go = Anim.follow(this.gearOpen.getOrDefault(m.name(), 0.0F), open ? 1.0F : 0.0F, dt, 12.0F);
                        this.gearOpen.put(m.name(), go);
                        int gc = Colors.lerp(Colors.rgba(255, 255, 255, (120.0F + 105.0F * gh) * cardA), ClientAccent.accentSoft(235.0F * cardA), go);
                        R2D.text(c, MsdfFont.Font.ICONS, "f", gx, gy, 7.0F, gc);
                    }

                    if (scaling) {
                        c.pose().popPose();
                    }
                }
            }
        }

        Clip.pop(c);
    }

    static void drawCheck(GuiGraphics c, float x, float y, float on, float a, boolean hovered) {
        R2D.rect(c, x, y, 8.5F, 8.5F, 2.975F, Colors.rgba(17, 17, 18, 91.0F * a));
        if (on < 0.996F) {
            float off = a * (1.0F - on);
            float i1 = 1.275F;
            float i2 = 2.55F;
            R2D.outline(c, x + i1, y + i1, 8.5F - i1 * 2.0F, 8.5F - i1 * 2.0F, 2.55F, 0.5F, ClientAccent.accent((hovered ? 80 : 35) * off));
            R2D.outline(c, x + i2, y + i2, 8.5F - i2 * 2.0F, 8.5F - i2 * 2.0F, 2.125F, 0.5F, ClientAccent.accent((hovered ? 45 : 20) * off));
        }

        if (on > 0.004F) {
            float alpha = 255.0F * on * a;
            int ga = ClientAccent.gradientA(alpha);
            int gb = Colors.darken(ClientAccent.gradientB(alpha), 0.6F);
            R2D.rect(c, x, y, 8.5F, 8.5F, 2.975F, ga, ga, gb, gb);
            float inset = 2.125F;
            float d = 8.5F - inset * 2.0F;
            R2D.rect(c, x + inset, y + inset, d, d, d * 0.5F, Colors.rgba(255, 255, 255, 255.0F * on * a));
        }
    }

    public boolean hitCheck(float mx, float my, float cardX, float cardY, float cardW) {
        float checkX = cardX + cardW - 8.5F - 8.0F;
        float checkY = cardY + 10.75F - 4.25F;
        return Ui.inside(mx, my, checkX - 2.0F, checkY - 2.0F, 12.5F, 12.5F);
    }

    public boolean scrollbarGrab(float mx, float my) {
        return this.scrollBar.tryGrab(mx, my);
    }

    public void scrollbarRelease() {
        this.scrollBar.release();
    }

    static float cornerEdgeInset(float r, float t) {
        if (r <= t) {
            return 0.0F;
        }

        float d = r - t;
        return r - (float)Math.sqrt(Math.max(0.0F, r * r - d * d));
    }

    public static String categoryIcon(Category c) {
        return c.icon;
    }
}
