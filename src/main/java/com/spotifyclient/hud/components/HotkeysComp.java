package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.module.Binds;
import com.spotifyclient.module.Category;
import com.spotifyclient.module.ClientModule;
import com.spotifyclient.module.ModuleRegistry;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.Colors;
import java.util.ArrayList;
import java.util.List;

public final class HotkeysComp extends ListHudComp {
    public HotkeysComp() {
        super("hotkeys", "Hotkeys", () -> ModSettings.hotkeysEnabled, 615.0F, 29.0F);
    }

    @Override
    protected String headerIcon() {
        return "u";
    }

    @Override
    protected float iconSlot() {
        return 6.0F;
    }

    @Override
    protected List<ListHudComp.Row> collectRows() {
        List<ListHudComp.Row> rows = new ArrayList<>();

        for (ClientModule m : ModuleRegistry.ALL) {
            if (!m.name().equals("Click GUI") && m.isOn() && Binds.isBound(m.name())) {
                rows.add(new ListHudComp.Row(m, m.name(), "[" + Binds.shortName(Binds.key(m.name())) + "]", icon(m.category())));
            }
        }

        if (rows.isEmpty() && DragSystem.get().dragMode()) {
            rows.add(new ListHudComp.Row("preview", "Example bind", "[B]", icon(Category.VISUALS)));
        }

        return rows;
    }

    private static ListHudComp.IconDrawer icon(Category category) {
        String glyph = category.hudIcon;
        return (c, x, y, size, a) -> {
            if (!(a <= 0.004F)) {
                float[] b = R2D.bounds(MsdfFont.Font.ICONS, glyph, size);
                float gx = x + (size - (b[2] - b[0])) / 2.0F - b[0];
                float gy = y + (size - (b[3] - b[1])) / 2.0F - b[1];
                R2D.text(c, MsdfFont.Font.ICONS, glyph, gx, gy, size, Colors.multAlpha(-3355444, a));
            }
        };
    }
}
