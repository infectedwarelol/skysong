package com.spotifyclient.gui.click;

import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import com.spotifyclient.theme.Colors;
import com.spotifyclient.ui.Clip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class SearchField {
    private static final float SIZE = 7.0F;
    private static final float PAD_X = 5.0F;
    private String text = "";
    private boolean typing;
    private boolean dragging;
    private int cursor;
    private int selStart = -1;
    private int selEnd = -1;
    private float x;
    private float y;
    private float w;
    private float h;
    private float textStartX;
    private float visibleW;
    private float xOffset;
    private float hoverT;
    private float focusT;
    private float cursorX;
    private boolean cursorSnap = true;
    private final List<Float> reveal = new ArrayList<>();
    private long lastClick;

    public String getText() {
        return this.text;
    }

    public boolean hasText() {
        return !this.text.isEmpty();
    }

    public boolean isTyping() {
        return this.typing;
    }

    public void setText(String s) {
        this.text = s == null ? "" : s;
        this.cursor = Math.min(this.cursor, this.text.length());
        this.clearSelection();
        this.dragging = false;
        this.cursorSnap = true;
        this.reveal.clear();

        for (int i = 0; i < this.text.length(); i++) {
            this.reveal.add(1.0F);
        }
    }

    public void focus() {
        this.typing = true;
        this.cursor = this.text.length();
        this.cursorSnap = true;
    }

    public void blur() {
        this.typing = false;
        this.dragging = false;
        this.clearSelection();
    }

    private static float w(String s) {
        return R2D.width(MsdfFont.Font.MEDIUM, s, 7.0F);
    }

    public void render(GuiGraphics c, float x, float y, float w, float h, float a, float mx, float my, float dt) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.cursor = Math.max(0, Math.min(this.cursor, this.text.length()));
        boolean over = Ui.inside(mx, my, x, y, w, h);
        this.hoverT = Anim.follow(this.hoverT, over ? 1.0F : 0.0F, dt, 16.0F);
        this.focusT = Anim.follow(this.focusT, this.typing ? 1.0F : 0.0F, dt, 11.0F);
        float k = Math.max(this.hoverT, this.focusT);
        int accent = ClientAccent.accent(255.0F) & 16777215;
        R2D.rect(c, x, y, w, h, Ui.cornerRadius(4.0F, w, h), Colors.rgba(0, 0, 0, (40.0F + 12.0F * k) * a));
        float iconSize = 7.5F;
        float iconX = x + 5.0F;
        int iconColor = Colors.rgba(Colors.mixRgb(11842740, accent, k), (125.0F + 95.0F * k) * a);
        R2D.text(c, MsdfFont.Font.ICONS, "q", iconX, y + (h - iconSize) * 0.5F + 0.3F - iconSize * 0.03F, iconSize, iconColor);
        this.textStartX = iconX + iconSize + 4.0F;
        this.visibleW = Math.max(4.0F, x + w - 5.0F - this.textStartX);
        this.updateOffset();
        float tx = this.textStartX - this.xOffset;
        float ty = y + (h - 7.0F) * 0.5F - 0.8F;
        Clip.push(c, this.textStartX - 2.0F, y, this.textStartX + this.visibleW, y + h);
        if (this.typing && this.hasSelection()) {
            float s0 = tx + w(this.text.substring(0, this.selMin()));
            float s1 = tx + w(this.text.substring(0, this.selMax()));
            R2D.rect(c, s0, y + 2.5F, s1 - s0, h - 5.0F, 1.5F, Colors.rgba(60, 128, 240, 165.0F * a));
        }

        boolean animating = false;

        for (int i = 0; i < this.reveal.size(); i++) {
            float v = this.reveal.get(i);
            if (v < 1.0F) {
                this.reveal.set(i, Math.min(1.0F, v + dt * 3.6F));
                animating = true;
            }
        }

        if (!this.text.isEmpty()) {
            if (!animating) {
                Ui.text(c, this.text, tx, ty, 7.0F, Ui.white(235.0F * a));
            } else {
                for (int i = 0; i < this.text.length(); i++) {
                    float p = Anim.clamp01(i < this.reveal.size() ? this.reveal.get(i) : 1.0F);
                    if (!(p <= 0.0F)) {
                        float gx = tx + w(this.text.substring(0, i));
                        String ch = String.valueOf(this.text.charAt(i));
                        int col = Ui.white(235.0F * a * p);
                        if (p >= 1.0F) {
                            Ui.text(c, ch, gx, ty, 7.0F, col);
                        } else {
                            float s = Anim.easeOutBack(p);
                            float cx = gx + w(ch) * 0.5F;
                            float cy = ty + 3.5F;
                            c.pose().pushPose();
                            c.pose().translate(cx, cy, 0.0F);
                            c.pose().scale(s, s, 1.0F);
                            c.pose().translate(-cx, -cy, 0.0F);
                            Ui.text(c, ch, gx, ty, 7.0F, col);
                            c.pose().popPose();
                        }
                    }
                }
            }
        } else if (1.0F - this.focusT > 0.01F) {
            Ui.text(c, "Search...", tx, ty, 7.0F, Ui.white(95.0F * a * (1.0F - this.focusT)));
        }

        Clip.pop(c);
        float target = w(this.text.substring(0, this.cursor));
        if (!this.cursorSnap && this.typing) {
            this.cursorX = Anim.follow(this.cursorX, target, dt, 20.0F);
        } else {
            this.cursorX = target;
            this.cursorSnap = false;
        }

        if (this.focusT > 0.01F && !this.hasSelection()) {
            float blink = (float)(Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5);
            R2D.rect(c, tx + this.cursorX, y + 2.5F, 0.6F, h - 5.0F, 0.0F, Ui.white((70.0F + 185.0F * blink) * this.focusT * a));
        }

        if (this.dragging) {
            int idx = this.indexAt(mx);
            if (this.selStart == -1) {
                this.selStart = this.cursor;
            }

            this.cursor = this.selEnd = idx;
            if (this.selStart == this.selEnd) {
                this.clearSelection();
            }
        }
    }

    private void updateOffset() {
        float cursorPos = w(this.text.substring(0, this.cursor));
        float total = w(this.text);
        if (cursorPos - this.xOffset > this.visibleW) {
            this.xOffset = cursorPos - this.visibleW;
        }

        if (cursorPos - this.xOffset < 0.0F) {
            this.xOffset = cursorPos;
        }

        this.xOffset = Math.max(0.0F, Math.min(this.xOffset, Math.max(0.0F, total - this.visibleW)));
    }

    private int indexAt(float mx) {
        float rel = mx - (this.textStartX - this.xOffset);
        if (rel <= 0.0F) {
            return 0;
        }

        for (int i = 1; i <= this.text.length(); i++) {
            float mid = (w(this.text.substring(0, i - 1)) + w(this.text.substring(0, i))) / 2.0F;
            if (rel < mid) {
                return i - 1;
            }
        }

        return this.text.length();
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (!Ui.inside(mx, my, this.x, this.y, this.w, this.h)) {
            if (this.typing) {
                this.blur();
            }

            return false;
        } else {
            if (button == 1) {
                this.setText("");
                this.typing = true;
                return true;
            }

            this.typing = true;
            long now = System.currentTimeMillis();
            int idx = this.indexAt(mx);
            if (now - this.lastClick < 250L) {
                this.selStart = this.wordStart(idx);
                this.selEnd = this.wordEnd(idx);
                this.cursor = this.selEnd;
            } else {
                this.cursor = idx;
                this.clearSelection();
                if (Compat.shiftDown()) {
                    this.selStart = this.cursor;
                }

                this.dragging = true;
            }

            this.lastClick = now;
            return true;
        }
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public boolean charTyped(char chr) {
        if (this.typing && !Character.isISOControl(chr)) {
            this.deleteSelection();
            this.insert(String.valueOf(chr));
            Sounds.play("search_typing", 0.5F);
            return true;
        } else {
            return false;
        }
    }

    public boolean keyPressed(int key, int mods) {
        if (!this.typing) {
            return false;
        }

        boolean ctrl = (mods & 2) != 0;
        boolean shift = (mods & 1) != 0;
        Minecraft mc = Minecraft.getInstance();
        if (ctrl) {
            switch (key) {
                case 65:
                    this.selStart = 0;
                    this.selEnd = this.cursor = this.text.length();
                    return true;
                case 67:
                    if (this.hasSelection()) {
                        mc.keyboardHandler.setClipboard(this.text.substring(this.selMin(), this.selMax()));
                    }

                    return true;
                case 86:
                    String clip = mc.keyboardHandler.getClipboard();
                    if (clip != null) {
                        this.deleteSelection();
                        this.insert(clip.replaceAll("[\\r\\n]", ""));
                    }

                    return true;
                case 88:
                    if (this.hasSelection()) {
                        mc.keyboardHandler.setClipboard(this.text.substring(this.selMin(), this.selMax()));
                        this.deleteSelection();
                    }

                    return true;
                case 259:
                    if (!this.deleteSelection()) {
                        int s = this.wordStart(Math.max(0, this.cursor - 1));
                        this.remove(s, this.cursor);
                    }

                    return true;
                case 262:
                    this.move(this.wordEnd(Math.min(this.text.length(), this.cursor + 1)), shift);
                    return true;
                case 263:
                    this.move(this.wordStart(Math.max(0, this.cursor - 1)), shift);
                    return true;
            }
        }

        switch (key) {
            case 256:
                if (!this.text.isEmpty()) {
                    this.setText("");
                } else {
                    this.blur();
                }

                return true;
            case 257:
            case 335:
                this.blur();
                return true;
            case 259:
                if (!this.deleteSelection() && this.cursor > 0) {
                    this.remove(this.cursor - 1, this.cursor);
                }

                return true;
            case 261:
                if (!this.deleteSelection() && this.cursor < this.text.length()) {
                    this.remove(this.cursor, this.cursor + 1);
                }

                return true;
            case 262:
                this.move(this.hasSelection() && !shift ? this.selMax() : Math.min(this.text.length(), this.cursor + 1), shift);
                return true;
            case 263:
                this.move(this.hasSelection() && !shift ? this.selMin() : Math.max(0, this.cursor - 1), shift);
                return true;
            case 268:
                this.move(0, shift);
                return true;
            case 269:
                this.move(this.text.length(), shift);
                return true;
            default:
                return false;
        }
    }

    private void move(int to, boolean select) {
        if (select) {
            if (this.selStart == -1) {
                this.selStart = this.cursor;
            }

            this.selEnd = to;
        } else {
            this.clearSelection();
        }

        this.cursor = to;
    }

    private void insert(String s) {
        this.text = this.text.substring(0, this.cursor) + s + this.text.substring(this.cursor);

        for (int i = 0; i < s.length(); i++) {
            this.reveal.add(Math.min(this.reveal.size(), this.cursor + i), 0.0F);
        }

        this.cursor = this.cursor + s.length();
    }

    private void remove(int from, int to) {
        if (from < to) {
            this.text = this.text.substring(0, from) + this.text.substring(to);

            for (int i = from; i < to && from < this.reveal.size(); i++) {
                this.reveal.remove(from);
            }

            this.cursor = from;
        }
    }

    private boolean deleteSelection() {
        if (!this.hasSelection()) {
            return false;
        }

        int a = this.selMin();
        int b = this.selMax();
        this.clearSelection();
        this.remove(a, b);
        return true;
    }

    private boolean hasSelection() {
        return this.selStart != -1 && this.selEnd != -1 && this.selStart != this.selEnd;
    }

    private int selMin() {
        return Math.min(this.selStart, this.selEnd);
    }

    private int selMax() {
        return Math.max(this.selStart, this.selEnd);
    }

    private void clearSelection() {
        this.selStart = this.selEnd = -1;
    }

    private int wordStart(int i) {
        while (i > 0 && this.text.charAt(i - 1) != ' ') {
            i--;
        }

        return i;
    }

    private int wordEnd(int i) {
        while (i < this.text.length() && this.text.charAt(i) != ' ') {
            i++;
        }

        return i;
    }
}
