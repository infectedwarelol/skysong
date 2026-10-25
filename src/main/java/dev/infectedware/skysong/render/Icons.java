package dev.infectedware.skysong.render;

import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.ui.Theme;
import net.minecraft.resources.Identifier;

public final class Icons {
    public static final Identifier LOGO = Ids.of("skysong", "textures/gui/logo.png");

    private Icons() {
    }

    public static void logo(GuiGraphics ctx, float x, float y, float size, float opacity) {
        int a = Math.round(Math.clamp(opacity, 0.0F, 1.0F) * 255.0F);
        R2D.image(ctx, LOGO, x, y, size, size, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, a << 24 | 16777215, true);
    }

}
