package dev.infectedware.skysong.compat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public abstract class VScreen extends Screen {
    private int mods;

    protected VScreen(Component title) {
        super(title);
    }

    protected abstract void draw(GuiGraphics var1, int var2, int var3, float var4);

    protected boolean scrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return false;
    }

    public final boolean scroll(double mouseX, double mouseY, double horizontal, double vertical) {
        return this.scrolled(mouseX, mouseY, horizontal, vertical);
    }

    protected final boolean scrolledVanilla(double mouseX, double mouseY, double horizontal, double vertical) {
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    protected final void drawWidgets(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(Compat.vanilla(g), mouseX, mouseY, delta);
    }

    public boolean onClick(double mouseX, double mouseY, int button) {
        return super.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, this.mods)), false);
    }

    public boolean onRelease(double mouseX, double mouseY, int button) {
        return super.mouseReleased(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, this.mods)));
    }

    public boolean onDrag(double mouseX, double mouseY, int button, double dx, double dy) {
        return super.mouseDragged(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, this.mods)), dx, dy);
    }

    public boolean onKey(int key, int scanCode, int modifiers) {
        return super.keyPressed(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean onKeyUp(int key, int scanCode, int modifiers) {
        return super.keyReleased(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean onChar(char chr, int modifiers) {
        return super.charTyped(new CharacterEvent(chr, modifiers));
    }

    @Override
    public final boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        this.mods = e.modifiers();
        return this.onClick(e.x(), e.y(), e.button());
    }

    @Override
    public final boolean mouseReleased(MouseButtonEvent e) {
        this.mods = e.modifiers();
        return this.onRelease(e.x(), e.y(), e.button());
    }

    @Override
    public final boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        this.mods = e.modifiers();
        return this.onDrag(e.x(), e.y(), e.button(), dx, dy);
    }

    @Override
    public final boolean keyPressed(KeyEvent e) {
        this.mods = e.modifiers();
        return this.onKey(e.key(), e.scancode(), e.modifiers());
    }

    @Override
    public final boolean keyReleased(KeyEvent e) {
        this.mods = e.modifiers();
        return this.onKeyUp(e.key(), e.scancode(), e.modifiers());
    }

    @Override
    public final boolean charTyped(CharacterEvent e) {
        boolean used = false;

        for (char c : Character.toChars(e.codepoint())) {
            used |= this.onChar(c, this.mods);
        }

        return used;
    }

    @Override
    public final void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        this.draw(Compat.gui(g), mouseX, mouseY, delta);
    }

    @Override
    public final void renderBackground(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public final boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return this.scrolled(mouseX, mouseY, horizontal, vertical);
    }
}
