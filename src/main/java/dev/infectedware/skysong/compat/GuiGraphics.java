package dev.infectedware.skysong.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public final class GuiGraphics {
    private final net.minecraft.client.gui.GuiGraphics g;
    private final PoseStack pose = new PoseStack();

    public GuiGraphics(net.minecraft.client.gui.GuiGraphics g) {
        this.g = g;
        Compat.guiChanged();
    }

    public net.minecraft.client.gui.GuiGraphics vanilla() {
        return this.g;
    }

    public PoseStack pose() {
        return this.pose;
    }

    public int guiWidth() {
        return this.g.guiWidth();
    }

    public int guiHeight() {
        return this.g.guiHeight();
    }

    private void begin() {
        Compat.guiChanged();
        Matrix4f m = this.pose.last().pose();
        this.g.pose().pushMatrix();
        this.g.pose().set(m.m00(), m.m01(), m.m10(), m.m11(), m.m30(), m.m31());
    }

    private void end() {
        this.g.pose().popMatrix();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        this.begin();
        this.g.fill(x1, y1, x2, y2, color);
        this.end();
    }

    public void renderItem(ItemStack stack, int x, int y) {
        this.begin();
        this.g.renderItem(stack, x, y);
        this.end();
    }

    public void renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed) {
        this.begin();
        this.g.renderItem(entity, stack, x, y, seed);
        this.end();
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        this.begin();
        this.g.renderItemDecorations(font, stack, x, y);
        this.end();
    }

    public void blitSprite(Identifier sprite, int x, int y, int w, int h, int color) {
        this.begin();
        this.g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h, color);
        this.end();
    }

    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        this.begin();
        this.g.drawString(font, text, x, y, color, shadow);
        this.end();
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        this.begin();
        this.g.enableScissor(x1, y1, x2, y2);
        this.end();
        Gpu.pushGuiClip(this.pose.last().pose(), x1, y1, x2, y2);
    }

    public void disableScissor() {
        this.g.disableScissor();
        Gpu.popGuiClip();
    }
}
