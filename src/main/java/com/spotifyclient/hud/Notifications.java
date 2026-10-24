package com.spotifyclient.hud;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.compat.HudLayer;
import com.spotifyclient.module.ClientModule;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.Colors;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

public final class Notifications implements HudLayer {
    private static final float SIZE = 7.0F;
    private static final float PAD_X = 7.0F;
    private static final float EDGE = 6.0F;
    private static final float GAP = 4.0F;
    private static final float H = 15.0F;
    private static final int MAX = 6;
    private static final int GREEN = -11141291;
    private static final int RED = -43691;
    private static final long IN_MS = 250L;
    private static final long OUT_MS = 250L;
    private static final long TOGGLE_HOLD = 1500L;
    private static final ConcurrentLinkedQueue<Notifications.Toast> PENDING = new ConcurrentLinkedQueue<>();
    private final List<Notifications.Toast> toasts = new ArrayList<>();
    private Notifications.Toast example;
    private float chatOffset;

    public static void notify(String text, long ms) {
        if (ModSettings.notificationsEnabled && text != null && !text.isBlank()) {
            PENDING.add(new Notifications.Toast(new Notifications.Seg[]{new Notifications.Seg(text, -1)}, 0L, Math.max(500L, ms)));
        }
    }

    public static void onToggle(ClientModule m) {
        if (ModSettings.notificationsEnabled && (!m.name().equals("Notifications") || m.isOn())) {
            boolean on = m.isOn();
            PENDING.add(
                new Notifications.Toast(
                    new Notifications.Seg[]{new Notifications.Seg(m.name(), -1), new Notifications.Seg(on ? " enabled" : " disabled", on ? -11141291 : -43691)},
                    0L,
                    1500L
                )
            );
        }
    }

    @Override
    public void render(GuiGraphics c, float tick) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        Notifications.Toast p;
        while ((p = PENDING.poll()) != null) {
            this.spawn(p.segs, p.hold, now, false);
        }

        if (!ModSettings.notificationsEnabled) {
            this.toasts.clear();
            this.example = null;
        } else {
            this.manageExample(mc, now);
            if (!this.toasts.isEmpty() && !mc.options.hideGui && R2D.available()) {
                float s = HudSpace.scale();
                float sw = HudSpace.width();
                float sh = HudSpace.height();
                boolean chat = mc.screen instanceof ChatScreen;
                float bottomMargin = ModSettings.infoEnabled ? Math.max(6.0F, InfoHud.reservedRight()) : 6.0F;
                this.chatOffset = this.chatOffset + ((chat && !ModSettings.infoEnabled ? 14.0F : 0.0F) - this.chatOffset) * 0.25F;
                float bottom = sh - bottomMargin - this.chatOffset;
                float right = sw - 6.0F;
                c.pose().pushPose();
                c.pose().scale(s, s, 1.0F);
                Iterator<Notifications.Toast> it = this.toasts.iterator();

                while (it.hasNext()) {
                    Notifications.Toast t = it.next();
                    long age = now - t.born;
                    if (!t.removing && !t.example && age >= t.hold) {
                        t.removing = true;
                        t.removeStart = now;
                    }

                    float in = clamp01((float)age / 250.0F);
                    float out = t.removing ? clamp01((float)(now - t.removeStart) / 250.0F) : 0.0F;
                    if (out >= 1.0F) {
                        it.remove();
                    } else {
                        float env = easeOutQuad(in) * (1.0F - out * out);
                        float slotBottom = bottom;
                        bottom -= 19.0F * env;
                        if (!(env <= 0.004F)) {
                            this.draw(c, t, right, slotBottom, env);
                        }
                    }
                }

                c.pose().popPose();
            }
        }
    }

    private void draw(GuiGraphics c, Notifications.Toast t, float right, float slotBottom, float env) {
        float textW = 0.0F;

        for (Notifications.Seg s : t.segs) {
            textW += R2D.width(MsdfFont.Font.BOLD, s.text(), 7.0F);
        }

        float w = textW + 14.0F;
        float x = right - w;
        float y = slotBottom - 15.0F;
        float r = Math.min(6.0F, Math.min(w, 15.0F) * 0.5F);
        c.pose().pushPose();
        c.pose().translate(0.0F, slotBottom, 0.0F);
        c.pose().scale(1.0F, Math.max(env, 1.0E-4F), 1.0F);
        c.pose().translate(0.0F, -slotBottom, 0.0F);
        R2D.glow(c, x, y, w, 15.0F, r, env);
        R2D.clientRectNoGlow(c, x, y, w, 15.0F, r, env);
        float[] bb = R2D.bounds(MsdfFont.Font.BOLD, "AHmT", 7.0F);
        float ty = y + 7.5F - (bb[1] + bb[3]) / 2.0F;
        float tx = x + 7.0F;

        for (Notifications.Seg s : t.segs) {
            int col = Colors.multAlpha(s.color(), env);
            int dark = Colors.darken(col, 0.25F);
            R2D.text(c, MsdfFont.Font.BOLD, s.text(), tx, ty + 0.5F, 7.0F, dark);
            R2D.text(c, MsdfFont.Font.BOLD, s.text(), tx, ty - 0.5F, 7.0F, dark);
            R2D.text(c, MsdfFont.Font.BOLD, s.text(), tx + 0.5F, ty, 7.0F, dark);
            R2D.text(c, MsdfFont.Font.BOLD, s.text(), tx - 0.5F, ty, 7.0F, dark);
            R2D.text(c, MsdfFont.Font.BOLD, s.text(), tx, ty, 7.0F, col);
            tx += R2D.width(MsdfFont.Font.BOLD, s.text(), 7.0F);
        }

        c.pose().popPose();
    }

    private void spawn(Notifications.Seg[] segs, long hold, long now, boolean isExample) {
        int live = 0;

        for (Notifications.Toast t : this.toasts) {
            if (!t.removing) {
                live++;
            }
        }

        for (int i = this.toasts.size() - 1; i >= 0 && live >= 6; i--) {
            Notifications.Toast t = this.toasts.get(i);
            if (!t.removing) {
                t.removing = true;
                t.removeStart = now;
                live--;
            }
        }

        Notifications.Toast t = new Notifications.Toast(segs, now, hold);
        t.example = isExample;
        this.toasts.add(0, t);
        if (isExample) {
            this.example = t;
        }
    }

    private void manageExample(Minecraft mc, long now) {
        boolean chat = mc.screen instanceof ChatScreen;
        int real = 0;

        for (Notifications.Toast t : this.toasts) {
            if (!t.example && !t.removing) {
                real++;
            }
        }

        boolean exampleAlive = this.example != null && this.toasts.contains(this.example) && !this.example.removing;
        if (chat && real == 0) {
            if (!exampleAlive) {
                this.spawn(new Notifications.Seg[]{new Notifications.Seg("Module", -1), new Notifications.Seg(" enabled", -11141291)}, 2500L, now, true);
            }
        } else if (exampleAlive) {
            this.example.removing = true;
            this.example.removeStart = now;
        }
    }

    private static float clamp01(float f) {
        return f < 0.0F ? 0.0F : Math.min(f, 1.0F);
    }

    private static float easeOutQuad(float f) {
        float u = 1.0F - clamp01(f);
        return 1.0F - u * u;
    }

    public record Seg(String text, int color) {
    }

    private static final class Toast {
        final Notifications.Seg[] segs;
        final long born;
        final long hold;
        boolean removing;
        boolean example;
        long removeStart;

        Toast(Notifications.Seg[] segs, long born, long hold) {
            this.segs = segs;
            this.born = born;
            this.hold = hold;
        }
    }
}
