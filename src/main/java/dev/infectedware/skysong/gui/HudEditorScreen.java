package dev.infectedware.skysong.gui;

import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.VScreen;
import dev.infectedware.skysong.hud.DragSystem;
import dev.infectedware.skysong.hud.HudSpace;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends VScreen {
    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void draw(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        float s = HudSpace.scale();
        ctx.pose().pushPose();
        ctx.pose().translate(0.0F, 0.0F, 300.0F);
        ctx.pose().scale(s, s, 1.0F);
        float w = HudSpace.width();
        float h = HudSpace.height();
        R2D.rect(ctx, 0.0F, 0.0F, w, h, 0.0F, 855638032);
        String hint = "Drag HUD elements  ·  Right-click cancels  ·  R resets  ·  Esc saves";
        float tw = R2D.width(MsdfFont.Font.MEDIUM, hint, 7.0F);
        float pw = tw + 20.0F;
        float ph = 18.0F;
        float px = (w - pw) / 2.0F;
        float py = h - 60.0F;
        R2D.clientRectFixed(ctx, px, py, pw, ph, 8.0F, 1.0F);
        R2D.textCentered(ctx, MsdfFont.Font.MEDIUM, hint, px + 10.0F, py + ph / 2.0F, 7.0F, Colors.rgba(255, 255, 255, 235.0F));
        ctx.pose().popPose();
        this.drawWidgets(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean onKey(int key, int scanCode, int modifiers) {
        if (key == 82) {
            DragSystem.get().resetPositions();
            return true;
        } else {
            return super.onKey(key, scanCode, modifiers);
        }
    }

    @Override
    public void removed() {
        DragSystem.get().savePositions();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
