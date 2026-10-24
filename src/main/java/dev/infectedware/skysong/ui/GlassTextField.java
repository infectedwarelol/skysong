package dev.infectedware.skysong.ui;

import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.render.GlassRenderer;
import dev.infectedware.skysong.render.UiText;
import net.minecraft.client.Minecraft;

public final class GlassTextField {
    private static final int SIZE = 8;
    private final int maxLength;
    private String text = "";
    private int cursor;
    private boolean allSelected;
    private boolean focused;
    private float x;
    private float y;
    private float w;
    private float h;
    private float scroll;
    private float focusAnim;
    private Runnable onChange = () -> {};

    public GlassTextField(int maxLength) {
        this.maxLength = maxLength;
    }

    public void onChange(Runnable r) {
        this.onChange = r;
    }

    public String getText() {
        return this.text;
    }

    public boolean isFocused() {
        return this.focused;
    }

    public void setFocused(boolean f) {
        this.focused = f;
        this.allSelected = false;
    }

    public void setText(String t) {
        this.text = t.length() > this.maxLength ? t.substring(0, this.maxLength) : t;
        this.cursor = this.text.length();
        this.allSelected = false;
        this.onChange.run();
    }

    public void render(GuiGraphics ctx, float x, float y, float w, float h, float leftInset, String placeholder, float dt) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.focusAnim = Theme.approach(this.focusAnim, this.focused ? 1.0F : 0.0F, dt, 14.0F);
        GlassRenderer.fillRounded(ctx, x, y, w, h, 5.0F, 939919902);
        if (this.focusAnim > 0.01F) {
            GlassRenderer.strokeRounded(ctx, x, y, w, h, 5.0F, 0.5F, Theme.alpha(Theme.guiAccent(), 0.8F * this.focusAnim));
        }

        float tx = x + leftInset;
        float cy = y + h / 2.0F;
        float visibleW = w - leftInset - 6.0F;
        if (this.text.isEmpty() && !this.focused) {
            UiText.drawCentered(ctx, placeholder, tx, cy, -6445370, 8, false);
        }

        float caretX = UiText.width(this.text.substring(0, this.cursor), 8, false);
        if (caretX - this.scroll > visibleW) {
            this.scroll = caretX - visibleW;
        }

        if (caretX - this.scroll < 0.0F) {
            this.scroll = caretX;
        }

        Clip.push(ctx, x + leftInset - 1.0F, y, x + w - 4.0F, y + h);
        if (this.allSelected && !this.text.isEmpty()) {
            GlassRenderer.fillRounded(ctx, tx - this.scroll, cy - 5.0F, UiText.width(this.text, 8, false), 10.0F, 2.0F, Theme.alpha(Theme.guiAccent(), 0.45F));
        }

        if (!this.text.isEmpty()) {
            UiText.drawCentered(ctx, this.text, tx - this.scroll, cy, -723201, 8, false);
        }

        boolean blinkOn = System.currentTimeMillis() / 530L % 2L == 0L;
        if (this.focused && blinkOn) {
            GlassRenderer.fillRounded(ctx, tx + caretX - this.scroll, cy - 4.5F, 1.0F, 9.0F, 0.5F, -723201);
        }

        Clip.pop(ctx);
    }

    public boolean mouseClicked(double mx, double my) {
        boolean inside = mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        this.setFocused(inside);
        if (inside) {
            this.cursor = this.text.length();
        }

        return inside;
    }

    public boolean charTyped(char c) {
        if (this.focused && c >= ' ' && c != 127) {
            this.insert(String.valueOf(c));
            return true;
        } else {
            return false;
        }
    }

    public boolean keyPressed(int key) {
        if (!this.focused) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (Compat.isSelectAll(key)) {
            this.allSelected = true;
            return true;
        }

        if (Compat.isPaste(key)) {
            this.insert(mc.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", ""));
            return true;
        }

        if (Compat.isCopy(key)) {
            mc.keyboardHandler.setClipboard(this.text);
            return true;
        }

        switch (key) {
            case 256:
                return false;
            case 257:
            case 258:
            case 260:
            case 264:
            case 265:
            case 266:
            case 267:
            default:
                return key != 257 && key != 258;
            case 259:
                if (this.allSelected) {
                    this.setText("");
                } else if (this.cursor > 0) {
                    this.replace(this.cursor - 1, this.cursor, "");
                }
                break;
            case 261:
                if (this.allSelected) {
                    this.setText("");
                } else if (this.cursor < this.text.length()) {
                    this.replace(this.cursor, this.cursor + 1, "");
                }
                break;
            case 262:
                this.cursor = Math.min(this.text.length(), this.cursor + 1);
                this.allSelected = false;
                break;
            case 263:
                this.cursor = Math.max(0, this.cursor - 1);
                this.allSelected = false;
                break;
            case 268:
                this.cursor = 0;
                this.allSelected = false;
                break;
            case 269:
                this.cursor = this.text.length();
                this.allSelected = false;
        }

        return true;
    }

    private void insert(String s) {
        if (this.allSelected) {
            this.text = "";
            this.cursor = 0;
            this.allSelected = false;
        }

        this.replace(this.cursor, this.cursor, s);
    }

    private void replace(int from, int to, String s) {
        String next = this.text.substring(0, from) + s + this.text.substring(to);
        if (next.length() <= this.maxLength) {
            this.text = next;
            this.cursor = from + s.length();
            this.allSelected = false;
            this.onChange.run();
        }
    }
}
