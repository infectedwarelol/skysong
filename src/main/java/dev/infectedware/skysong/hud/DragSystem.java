package dev.infectedware.skysong.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Hooks;
import dev.infectedware.skysong.compat.HudLayer;
import dev.infectedware.skysong.gui.HudEditorScreen;
import dev.infectedware.skysong.hud.components.ArmorComp;
import dev.infectedware.skysong.hud.components.ArrayListComp;
import dev.infectedware.skysong.hud.components.CooldownsComp;
import dev.infectedware.skysong.hud.components.HotkeysComp;
import dev.infectedware.skysong.hud.components.HpFocusComp;
import dev.infectedware.skysong.hud.components.InventoryComp;
import dev.infectedware.skysong.hud.components.KeyStrokesComp;
import dev.infectedware.skysong.hud.components.LyricsBarComp;
import dev.infectedware.skysong.hud.components.NowPlayingComp;
import dev.infectedware.skysong.hud.components.PotionsComp;
import dev.infectedware.skysong.hud.components.TargetHudComp;
import dev.infectedware.skysong.hud.components.WatermarkComp;
import dev.infectedware.skysong.render.r2d.Backdrop;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

public final class DragSystem implements HudLayer {
    private static final DragSystem INSTANCE = new DragSystem();
    private final List<Draggable> elements = new ArrayList<>();
    private final Set<String> failed = new HashSet<>();
    private Draggable active;
    private String loadedFrom;
    private final Map<String, float[]> origins = new HashMap<>();
    private boolean wasDragMode;
    private String lastClickId;
    private long lastClickMs;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final float OUTLINE_PAD = 3.0F;

    private DragSystem() {
    }

    public static DragSystem get() {
        return INSTANCE;
    }

    public void init() {
        this.elements.add(new WatermarkComp());
        this.elements.add(new NowPlayingComp());
        this.elements.add(new LyricsBarComp());
        this.elements.add(new HotkeysComp());
        this.elements.add(new TargetHudComp());
        this.elements.add(new PotionsComp());
        this.elements.add(new CooldownsComp());
        this.elements.add(new ArrayListComp());
        this.elements.add(new ArmorComp());
        this.elements.add(new InventoryComp());
        this.elements.add(new HpFocusComp());
        this.elements.add(new KeyStrokesComp());
        Hooks.hud(this);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (isDragScreen(screen)) {
                ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !this.onClick(e.button()));
                ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !this.onRelease(e.button()));
            }
        });
    }

    public List<Draggable> all() {
        return this.elements;
    }

    public Draggable byId(String id) {
        for (Draggable d : this.elements) {
            if (d.id().equals(id)) {
                return d;
            }
        }

        return null;
    }

    private static boolean isDragScreen(Screen s) {
        return s instanceof ChatScreen || s instanceof HudEditorScreen;
    }

    public boolean dragMode() {
        return isDragScreen(Minecraft.getInstance().screen);
    }

    @Override
    public void render(GuiGraphics c, float tick) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui && mc.player != null && R2D.available()) {
            this.loadPositions();
            ClientAccent.beginFrame();
            HpFocusComp.captureIfNeeded();
            boolean drag = this.dragMode();
            if (!drag) {
                this.releaseInterrupted();
            }

            if (drag && !this.wasDragMode) {
                this.origins.clear();

                for (Draggable d : this.elements) {
                    this.origins.put(d.id(), new float[]{d.drag().targetX(), d.drag().targetY()});
                }
            } else if (!drag && this.wasDragMode) {
                this.origins.clear();
            }

            this.wasDragMode = drag;
            boolean anyVisible = false;

            for (Draggable d : this.elements) {
                anyVisible |= d.interactive();
            }

            if (anyVisible || NameTags.anyVisible()) {
                Backdrop.captureForHud(c, tick);
            }

            float s = HudSpace.scale();
            float mx = HudSpace.mouseX();
            float my = HudSpace.mouseY();
            boolean direct = ModSettings.hudDragStyle == 0;
            c.pose().pushPose();
            c.pose().scale(s, s, 1.0F);
            if (drag) {
                for (Draggable d : this.elements) {
                    if (d.interactive()) {
                        d.drag().tick(mx, my, d.overlayWidth(), d.overlayHeight(), direct);
                    }
                }
            }

            for (Draggable d : this.elements) {
                d.drag().updateTilt(mx, direct && ModSettings.dragTilt);
            }

            for (Draggable d : this.elements) {
                if (!d.drag().dragging()) {
                    d.drag().clamp(d.width(), d.height());
                }
            }

            if (drag && ModSettings.hudDragStyle != 2) {
                this.drawGhosts(c);
            }

            for (Draggable d : this.elements) {
                try {
                    d.renderWithTilt(c);
                } catch (Throwable t) {
                    if (this.failed.add(d.id())) {
                        SkysongClient.LOGGER.error("[Skysong] HUD element " + d.id() + " failed to draw", t);
                    }
                }
            }

            if (drag && ModSettings.hudDragStyle == 1 && ModSettings.dragWaves) {
                for (Draggable d : this.elements) {
                    if (d.drag().dragging()) {
                        drawWaves(c, d);
                    }
                }
            }

            if (drag) {
                for (Draggable d : this.elements) {
                    if (d.drag().outlineDragging()) {
                        drawOutline(c, d);
                    }
                }
            }

            c.pose().popPose();
        }
    }

    private void drawGhosts(GuiGraphics c) {
        for (Draggable d : this.elements) {
            float[] o = this.origins.get(d.id());
            if (o != null && d.interactive()) {
                float dx = d.drag().renderX() - o[0];
                float dy = d.drag().renderY() - o[1];
                if (!(dx * dx + dy * dy < 4.0F)) {
                    float w = d.width();
                    float h = d.height();
                    float a = Math.min(1.0F, (float)Math.sqrt(dx * dx + dy * dy) / 24.0F);

                    try {
                        Gpu.setColor(1.0F, 1.0F, 1.0F, 0.28F * a);
                        d.renderGhost(c, o[0], o[1]);
                    } catch (Throwable t) {
                        if (this.failed.add(d.id() + ":ghost")) {
                            SkysongClient.LOGGER.error("[Skysong] ghost of " + d.id() + " failed", t);
                        }
                    } finally {
                        Gpu.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    }

                    dashedFrame(c, o[0] - 2.0F, o[1] - 2.0F, w + 4.0F, h + 4.0F, ClientAccent.accent(150.0F * a));
                }
            }
        }
    }

    private static void dashedFrame(GuiGraphics c, float x, float y, float w, float h, int col) {
        float dash = 4.0F;
        float gap = 3.0F;
        float t = 0.8F;

        for (float i = 0.0F; i < w; i += dash + gap) {
            float len = Math.min(dash, w - i);
            R2D.rect(c, x + i, y, len, t, 0.0F, col);
            R2D.rect(c, x + i, y + h - t, len, t, 0.0F, col);
        }

        for (float i = 0.0F; i < h; i += dash + gap) {
            float len = Math.min(dash, h - i);
            R2D.rect(c, x, y + i, t, len, 0.0F, col);
            R2D.rect(c, x + w - t, y + i, t, len, 0.0F, col);
        }
    }

    private static void drawOutline(GuiGraphics c, Draggable d) {
        DragController g = d.drag();
        float px = R2D.pixel(c.pose().last().pose());
        float pop = Math.min(1.0F, (float)g.grabbedForMs() / 240.0F);
        float settle = 1.0F - (1.0F - pop) * (1.0F - pop) * (1.0F - pop);
        float pad = 3.0F;
        float ax = g.renderX() - pad;
        float ay = g.renderY() - pad;
        float aw = d.width() + pad * 2.0F;
        float ah = d.height() + pad * 2.0F;
        float bx = g.boxRenderX() - pad;
        float by = g.boxRenderY() - pad;
        float bw = d.overlayWidth() + pad * 2.0F;
        float bh = d.overlayHeight() + pad * 2.0F;
        float ar = outlineRadius(aw, ah);
        float br = outlineRadius(bw, bh);
        float grow = (1.0F - settle) * 2.5F;
        R2D.outline(c, ax - grow, ay - grow, aw + grow * 2.0F, ah + grow * 2.0F, ar + grow, px * (1.1F + (1.0F - settle) * 1.2F), white(0.95F - 0.5F * settle));
        float dx = bx - ax;
        float dy = by - ay;
        float away = Math.min(1.0F, (float)Math.sqrt(dx * dx + dy * dy) / 8.0F);
        if (!(away <= 0.01F)) {
            float ai = ar * 0.293F;
            float bi = br * 0.293F;
            int dashCol = white(0.6F * away);
            float t = px * 1.1F;
            dashedLine(c, ax + ai, ay + ai, bx + bi, by + bi, t, dashCol);
            dashedLine(c, ax + aw - ai, ay + ai, bx + bw - bi, by + bi, t, dashCol);
            dashedLine(c, ax + aw - ai, ay + ah - ai, bx + bw - bi, by + bh - bi, t, dashCol);
            dashedLine(c, ax + ai, ay + ah - ai, bx + bi, by + bh - bi, t, dashCol);
            R2D.outline(c, bx, by, bw, bh, br, px * 1.6F, white(0.9F * away));
            float inset = px * 2.4F;
            R2D.outline(c, bx + inset, by + inset, bw - inset * 2.0F, bh - inset * 2.0F, Math.max(0.0F, br - inset), px, white(0.3F * away));
        }
    }

    private static float outlineRadius(float w, float h) {
        return Math.min(ModSettings.rectCornerRadius + 3.0F, Math.min(w, h) / 2.0F);
    }

    private static int white(float a) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | 16777215;
    }

    private static void dashedLine(GuiGraphics c, float x0, float y0, float x1, float y1, float thickness, int col) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float)Math.sqrt(dx * dx + dy * dy);
        if (!(len < 1.0F)) {
            float dash = 4.5F;
            float gap = 3.5F;
            PoseStack m = c.pose();
            m.pushPose();
            m.translate(x0, y0, 0.0F);
            m.mulPose(Axis.ZP.rotation((float)Math.atan2(dy, dx)));

            for (float s = 0.0F; s < len; s += dash + gap) {
                R2D.rect(c, s, -thickness / 2.0F, Math.min(dash, len - s), thickness, 0.0F, col);
            }

            m.popPose();
        }
    }

    public Draggable outlineDragged() {
        for (Draggable d : this.elements) {
            if (d.drag().outlineDragging()) {
                return d;
            }
        }

        return null;
    }

    public void doubleClickForTest(String id) {
        Draggable d = this.byId(id);
        if (d != null) {
            d.slideHome();
            this.savePositions();
        }
    }

    public float[] originOf(String id) {
        return this.origins.get(id);
    }

    private static void drawWaves(GuiGraphics c, Draggable d) {
        float p = DragController.pulsePhase();
        float grow = 2.0F + p * 10.0F;
        float a = (1.0F - p) * 0.55F;
        float x = d.drag().renderX() - grow;
        float y = d.drag().renderY() - grow;
        float w = d.width() + grow * 2.0F;
        float h = d.height() + grow * 2.0F;
        float r = Math.min(ModSettings.rectCornerRadius + grow, Math.min(w, h) / 2.0F);
        int col = ClientAccent.accent(255.0F * a);
        R2D.outline(c, x, y, w, h, r, 0.8F, col);
    }

    private boolean onClick(int button) {
        float mx = HudSpace.mouseX();
        float my = HudSpace.mouseY();
        if (button == 1 && this.active != null) {
            this.active.drag().cancel();
            this.active = null;
            this.savePositions();
            return true;
        }

        if (button != 0) {
            return false;
        }

        for (int i = this.elements.size() - 1; i >= 0; i--) {
            Draggable d = this.elements.get(i);
            if (d.interactive() && d.drag().tryGrab(mx, my, d.width(), d.height())) {
                long now = System.currentTimeMillis();
                if (d.id().equals(this.lastClickId) && now - this.lastClickMs < 350L) {
                    d.slideHome();
                    this.lastClickId = null;
                    this.active = null;
                    this.savePositions();
                    return true;
                }

                this.lastClickId = d.id();
                this.lastClickMs = now;
                this.active = d;
                this.elements.remove(i);
                this.elements.add(d);
                return true;
            }
        }

        return false;
    }

    private boolean onRelease(int button) {
        if (button == 0 && this.active != null) {
            this.active.drag().release();
            this.active = null;
            this.savePositions();
            return true;
        } else {
            return false;
        }
    }

    private void releaseInterrupted() {
        boolean changed = false;

        for (Draggable d : this.elements) {
            if (d.drag().dragging()) {
                d.drag().release();
                changed = true;
            }
        }

        this.active = null;
        if (changed) {
            this.savePositions();
        }
    }

    private void loadPositions() {
        String raw = ModSettings.hudPositions;
        if (!raw.equals(this.loadedFrom)) {
            this.loadedFrom = raw;

            for (Draggable d : this.elements) {
                d.placed = false;
            }

            for (String entry : raw.split(";")) {
                int eq = entry.indexOf(61);
                if (eq > 0) {
                    Draggable d = this.byId(entry.substring(0, eq));
                    String[] xy = entry.substring(eq + 1).split(",");
                    if (d != null && xy.length == 2) {
                        try {
                            d.drag().setTarget(Float.parseFloat(xy[0]), Float.parseFloat(xy[1]));
                            d.drag().syncToTarget();
                            d.placed = true;
                        } catch (NumberFormatException var10) {
                        }
                    }
                }
            }

            for (Draggable d : this.elements) {
                if (!d.placed) {
                    d.reset();
                }
            }
        }
    }

    public void savePositions() {
        StringBuilder sb = new StringBuilder();

        for (Draggable d : this.elements) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }

            sb.append(d.id()).append('=').append(String.format(Locale.ROOT, "%.1f,%.1f", d.drag().targetX(), d.drag().targetY()));
            d.placed = true;
        }

        ModSettings.hudPositions = sb.toString();
        this.loadedFrom = ModSettings.hudPositions;
        ModConfig.save();
    }

    public void resetPositions() {
        for (Draggable d : this.elements) {
            d.reset();
        }

        this.savePositions();
    }
}
