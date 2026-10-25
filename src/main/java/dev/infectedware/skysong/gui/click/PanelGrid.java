package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.module.ClientModule;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class PanelGrid {
    private static final float GAP = 5.0F;
    private static final float MIN_COLUMN = 150.0F;
    private static final int MAX_COLUMNS = 3;
    private static final float TITLE_H = 14.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float BOTTOM_PAD = 4.0F;
    private static final float BAR_ROOM = 5.0F;
    private static final float SCROLL_STEP = 16.0F;
    private final ScrollBar scrollBar = new ScrollBar();
    private static final float[] AWAY = new float[]{-100000.0F, -100000.0F};
    public boolean holo;
    private ClientModule module;
    private List<Group> groups = List.of();
    private float x;
    private float y;
    private float width;
    private float height;
    private float scroll;
    private float scrollTarget;
    private long lastNs = System.nanoTime();

    private record Group(String title, List<Widget> widgets) {
    }

    private record Box(Group group, float x, float y, float w, float h) {
    }

    private record Slot(Widget widget, float x, float y, float w, int box) {
    }

    private record Layout(List<Box> boxes, List<Slot> slots, float contentH) {
    }

    public void show(ClientModule m) {
        if (m == this.module) {
            return;
        }

        this.closeOverlays();
        this.module = m;
        this.groups = this.build();
        this.scroll = this.scrollTarget = 0.0F;
    }

    public void rebuild() {
        this.groups = this.build();
    }

    public boolean isEmpty() {
        return this.groups.isEmpty();
    }

    private List<Group> build() {
        if (this.module == null || !this.module.hasSettings()) {
            return List.of();
        }

        List<Group> out = new ArrayList<>();
        String title = this.module.name();
        List<Widget> current = new ArrayList<>();

        for (Widget w : Widget.build(this.module.settings().get())) {
            if (w instanceof Widget.Separator separator) {
                if (!current.isEmpty()) {
                    out.add(new Group(title, List.copyOf(current)));
                }

                title = separator.title();
                current = new ArrayList<>();
            } else {
                current.add(w);
            }
        }

        if (!current.isEmpty()) {
            out.add(new Group(title, List.copyOf(current)));
        }

        return List.copyOf(out);
    }

    private static float groupHeight(Group g) {
        float h = TITLE_H;
        boolean any = false;

        for (Widget w : g.widgets()) {
            if (w.visible()) {
                h += w.height() + ROW_GAP;
                any = true;
            }
        }

        return any ? h + BOTTOM_PAD : 0.0F;
    }

    private Layout layout() {
        float usable = this.width - BAR_ROOM;
        int columns = Mth.clamp((int)((usable + GAP) / (MIN_COLUMN + GAP)), 1, MAX_COLUMNS);
        float columnW = (usable - GAP * (columns - 1)) / columns;
        float[] bottoms = new float[columns];
        List<Box> boxes = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();

        for (Group g : this.groups) {
            float gh = groupHeight(g);
            if (gh <= 0.0F) {
                continue;
            }

            int column = 0;
            for (int i = 1; i < columns; i++) {
                if (bottoms[i] < bottoms[column] - 0.5F) {
                    column = i;
                }
            }

            float bx = this.x + column * (columnW + GAP);
            float by = this.y + bottoms[column] - this.scroll;
            boxes.add(new Box(g, bx, by, columnW, gh));
            float wy = by + TITLE_H;

            for (Widget w : g.widgets()) {
                if (w.visible()) {
                    slots.add(new Slot(w, bx + 1.0F, wy, columnW - 2.0F, boxes.size() - 1));
                    wy += w.height() + ROW_GAP;
                }
            }

            bottoms[column] += gh + GAP;
        }

        float content = 0.0F;
        for (float b : bottoms) {
            content = Math.max(content, b - GAP);
        }

        return new Layout(boxes, slots, Math.max(0.0F, content));
    }

    public void render(GuiGraphics c, float x, float y, float w, float h, float a, float mx, float my) {
        boolean previous = Widget.flat;
        Widget.flat = true;

        try {
            this.x = x;
            this.y = y;
            this.width = w;
            this.height = h;
            if (SettingsPopup.takeRebuild()) {
                this.rebuild();
            }

            long now = System.nanoTime();
            float dt = Math.min(0.1F, (now - this.lastNs) / 1.0E9F);
            this.lastNs = now;
            Layout layout = this.layout();
            float max = Math.max(0.0F, layout.contentH() - h);
            this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, max);
            this.scroll = Anim.follow(this.scroll, this.scrollTarget, dt, 16.0F);
            if (Math.abs(this.scroll - this.scrollTarget) < 0.05F) {
                this.scroll = this.scrollTarget;
            }

            layout = this.layout();
            if (this.holo) {
                this.renderHolo(c, layout, a, mx, my);
                return;
            }

            Clip.push(c, x, y, x + w, y + h);

            for (Box b : layout.boxes()) {
                if (b.y() + b.h() >= y && b.y() <= y + h) {
                    drawBox(c, b, a);
                }
            }

            for (Slot s : layout.slots()) {
                float sh = s.widget().height();
                if (s.y() + sh >= y - 4.0F && s.y() <= y + h + 4.0F) {
                    s.widget().render(c, s.x(), s.y(), s.w(), a, mx, my);
                }
            }

            Clip.pop(c);
            if (layout.contentH() > h + 0.5F) {
                float s = this.scrollBar.render(c, x + w - 3.0F, y, h, h, layout.contentH(), this.scroll, a, mx, my);
                if (this.scrollBar.isDragging()) {
                    this.scroll = this.scrollTarget = s;
                }
            }

            for (Slot s : layout.slots()) {
                if (s.widget().hasOverlay()) {
                    s.widget().renderOverlay(c, s.x(), s.y(), s.w(), a, mx, my);
                }
            }
        } finally {
            Widget.flat = previous;
        }
    }

    private Matrix4f transform(Layout layout, int box) {
        Box b = layout.boxes().get(box);
        return Holo.place(b.x(), b.y(), b.w(), b.h(), box + 3, 8.0F);
    }

    private float[] mouse(Layout layout, int box, float mx, float my) {
        if (!this.holo) {
            return new float[]{mx, my};
        }

        float[] p = Holo.unproject(this.transform(layout, box), mx, my);
        return p == null ? AWAY : p;
    }

    private boolean visibleIn(Box b, float[] p) {
        float top = Math.max(this.y, b.y());
        float bottom = Math.min(this.y + this.height, b.y() + b.h());
        return Ui.inside(p[0], p[1], b.x(), top, b.w(), bottom - top);
    }

    private boolean hitsBox(Layout layout, float mx, float my) {
        List<Box> boxes = layout.boxes();
        for (int i = 0; i < boxes.size(); i++) {
            if (this.visibleIn(boxes.get(i), this.mouse(layout, i, mx, my))) {
                return true;
            }
        }

        return false;
    }

    private void renderHolo(GuiGraphics c, Layout layout, float a, float mx, float my) {
        List<Box> boxes = layout.boxes();
        for (int i = 0; i < boxes.size(); i++) {
            Box b = boxes.get(i);
            float top = Math.max(this.y, b.y());
            float bottom = Math.min(this.y + this.height, b.y() + b.h());
            if (bottom <= top) {
                continue;
            }

            float[] m = this.mouse(layout, i, mx, my);
            Holo.begin(this.transform(layout, i));
            try {
                Clip.push(c, b.x() - 2.0F, top - 2.0F, b.x() + b.w() + 2.0F, bottom + 2.0F);
                Holo.backing(c, b.x(), b.y(), b.w(), b.h(), a, i + 3);
                R2D.text(c, MsdfFont.Font.SEMIBOLD, b.group().title(), b.x() + 5.0F, b.y() + 4.5F, 6.5F, Holo.cyan(250.0F * a));
                for (Slot s : layout.slots()) {
                    if (s.box() == i) {
                        s.widget().render(c, s.x(), s.y(), s.w(), a, m[0], m[1]);
                    }
                }

                Clip.pop(c);
            } finally {
                Holo.end();
            }
        }

        for (Slot s : layout.slots()) {
            if (s.widget().hasOverlay()) {
                float[] m = this.mouse(layout, s.box(), mx, my);
                Holo.begin(this.transform(layout, s.box()));
                try {
                    s.widget().renderOverlay(c, s.x(), s.y(), s.w(), a, m[0], m[1]);
                } finally {
                    Holo.end();
                }
            }
        }
    }

    private static void drawBox(GuiGraphics c, Box b, float a) {
        R2D.rect(c, b.x(), b.y(), b.w(), b.h(), 0.0F, Colors.rgba(8, 8, 14, 120.0F * a));
        R2D.outline(c, b.x(), b.y(), b.w(), b.h(), 0.0F, 0.5F, Ui.white(20.0F * a));
        R2D.rect(c, b.x(), b.y(), b.w(), 1.0F, 0.0F, Widget.flatFill(235.0F * a));
        R2D.text(c, MsdfFont.Font.SEMIBOLD, b.group().title(), b.x() + 5.0F, b.y() + 4.5F, 6.5F, Ui.white(245.0F * a));
    }

    public boolean contains(float mx, float my) {
        return this.holo ? this.hitsBox(this.layout(), mx, my) : Ui.inside(mx, my, this.x, this.y, this.width, this.height);
    }

    public boolean click(float mx, float my) {
        boolean previous = Widget.flat;
        Widget.flat = true;

        try {
            Layout layout = this.layout();
            List<Slot> slots = layout.slots();
            boolean overlayOpen = false;

            for (Slot s : slots) {
                if (s.widget().overlayOpen()) {
                    overlayOpen = true;
                    float[] m = this.mouse(layout, s.box(), mx, my);
                    if (s.widget().clickOverlay(s.x(), s.y(), s.w(), m[0], m[1])) {
                        return true;
                    }
                }
            }

            if (overlayOpen) {
                this.closeOverlays();
                return true;
            }

            if (!this.contains(mx, my)) {
                this.blurFields();
                return false;
            }

            if (!this.holo && this.scrollBar.tryGrab(mx, my)) {
                return true;
            }

            for (Slot s : slots) {
                Widget w = s.widget();
                float[] m = this.mouse(layout, s.box(), mx, my);
                if (Ui.inside(m[0], m[1], s.x(), s.y(), s.w(), w.height()) && this.visibleIn(layout.boxes().get(s.box()), m)) {
                    boolean hit = w.click(s.x(), s.y(), s.w(), m[0], m[1]);
                    if (hit && w.overlayOpen()) {
                        for (Slot other : slots) {
                            if (other.widget() != w) {
                                other.widget().closeOverlay();
                            }
                        }
                    }

                    if (hit) {
                        ModConfig.save();
                        return true;
                    }

                    break;
                }
            }

            this.blurFields();
            return true;
        } finally {
            Widget.flat = previous;
        }
    }

    public boolean middleClick(float mx, float my) {
        if (!this.contains(mx, my)) {
            return false;
        }

        boolean previous = Widget.flat;
        Widget.flat = true;

        try {
            Layout layout = this.layout();
            for (Slot s : layout.slots()) {
                float[] m = this.mouse(layout, s.box(), mx, my);
                if (Ui.inside(m[0], m[1], s.x(), s.y(), s.w(), s.widget().height())) {
                    if (s.widget().middleClick(s.x(), s.y(), s.w(), m[0], m[1])) {
                        ModConfig.save();
                    }

                    return true;
                }
            }

            return true;
        } finally {
            Widget.flat = previous;
        }
    }

    public boolean scroll(float mx, float my, double amount) {
        boolean previous = Widget.flat;
        Widget.flat = true;

        try {
            Layout layout = this.layout();
            for (Slot s : layout.slots()) {
                float[] m = this.mouse(layout, s.box(), mx, my);
                if (s.widget().overlayOpen() && s.widget().scrollOverlay(s.x(), s.y(), s.w(), m[0], m[1], amount)) {
                    return true;
                }
            }

            if (this.contains(mx, my)) {
                float max = Math.max(0.0F, layout.contentH() - this.height);
                this.scrollTarget = Mth.clamp(this.scrollTarget - (float)amount * SCROLL_STEP, 0.0F, max);
                return true;
            }

            return false;
        } finally {
            Widget.flat = previous;
        }
    }

    private void blurFields() {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                if (w.focused()) {
                    w.click(-1000.0F, -1000.0F, 0.0F, -1000.0F, -1000.0F);
                }
            }
        }
    }

    public void releaseDrags() {
        this.scrollBar.release();

        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                w.release();
            }
        }

        ModConfig.save();
    }

    public boolean hasOpenOverlay() {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                if (w.overlayOpen()) {
                    return true;
                }
            }
        }

        return false;
    }

    public void closeOverlays() {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                w.closeOverlay();
            }
        }
    }

    public boolean keyPressed(int key) {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                if (w.focused() && w.keyPressed(key)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseBind(int button) {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                if (w.focused() && w.mouseBind(button)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean charTyped(char chr) {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                if (w.focused() && w.charTyped(chr)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean typing() {
        for (Group g : this.groups) {
            for (Widget w : g.widgets()) {
                if (w.focused()) {
                    return true;
                }
            }
        }

        return false;
    }
}
