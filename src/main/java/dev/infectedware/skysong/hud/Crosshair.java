package dev.infectedware.skysong.hud;

import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.Window;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult.Type;

public final class Crosshair {
    public static final String[] STYLES = new String[]{"Custom", "Cross", "Dot", "Circle", "X"};
    public static final String[] COLORS = new String[]{"White", "Theme", "Custom", "Two colors"};
    private static String parsedFrom;
    private static final boolean[] custom = new boolean[225];
    private static float react;
    private static long lastNs = System.nanoTime();

    private Crosshair() {
    }

    public static boolean[] pattern() {
        String raw = ModSettings.crosshairPixels;
        if (!raw.equals(parsedFrom)) {
            for (int i = 0; i < custom.length; i++) {
                custom[i] = i < raw.length() && raw.charAt(i) == '1';
            }

            parsedFrom = raw;
        }

        return custom;
    }

    public static void savePattern(boolean[] cells) {
        StringBuilder sb = new StringBuilder(225);

        for (boolean b : cells) {
            sb.append(b ? '1' : '0');
        }

        ModSettings.crosshairPixels = sb.toString();
    }

    public static boolean[] shape(int style) {
        if (style == 0) {
            return pattern();
        }

        boolean[] g = new boolean[225];
        switch (style) {
            case 2:
                for (int y = 6; y <= 8; y++) {
                    for (int x = 6; x <= 8; x++) {
                        if (Math.abs(x - 7) + Math.abs(y - 7) < 2) {
                            set(g, x, y);
                        }
                    }
                }
                break;
            case 3:
                for (int y = 0; y < 15; y++) {
                    for (int x = 0; x < 15; x++) {
                        double d = Math.hypot(x - 7, y - 7);
                        if (d > 3.4 && d < 4.6) {
                            set(g, x, y);
                        }
                    }
                }

                set(g, 7, 7);
                break;
            case 4:
                for (int i = 2; i < 13; i++) {
                    if (Math.abs(i - 7) >= 2) {
                        set(g, i, i);
                        set(g, 14 - i, i);
                    }
                }
                break;
            default:
                cross(g);
        }

        return g;
    }

    public static void cross(boolean[] g) {
        for (int i = 3; i <= 11; i++) {
            if (Math.abs(i - 7) != 1) {
                set(g, 7, i);
                set(g, i, 7);
            }
        }
    }

    public static String defaultPixels() {
        boolean[] g = new boolean[225];
        cross(g);
        StringBuilder sb = new StringBuilder();

        for (boolean b : g) {
            sb.append(b ? '1' : '0');
        }

        return sb.toString();
    }

    private static void set(boolean[] g, int x, int y) {
        if (x >= 0 && y >= 0 && x < 15 && y < 15) {
            g[y * 15 + x] = true;
        }
    }

    public static int color() {
        return switch (ModSettings.crosshairColor) {
            case 1 -> ClientAccent.gradientColor(0.5F, 255.0F);
            case 2, 3 -> ModSettings.crosshairCustomColor | 0xFF000000;
            default -> -1;
        };
    }

    public static int cellColor(int x, int y) {
        if (ModSettings.crosshairColor != 3) {
            return color();
        }

        float phase = (float)(System.currentTimeMillis() % 4000L) / 4000.0F;
        float k = (x + y) / 28.0F + phase;
        k -= (float)Math.floor(k);
        k = k < 0.5F ? k * 2.0F : 2.0F - k * 2.0F;
        return Colors.lerp(ModSettings.crosshairCustomColor | 0xFF000000, ModSettings.crosshairCustomColor2 | 0xFF000000, k);
    }

    public static boolean render(GuiGraphics c) {
        Minecraft mc = Minecraft.getInstance();
        if (!ModSettings.crosshair || mc.player == null || Compat.debugScreenShown()) {
            return false;
        }

        if (!mc.options.getCameraType().isFirstPerson()) {
            return true;
        }

        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return false;
        }

        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float)(now - lastNs) / 1.0E9F);
        lastNs = now;
        boolean onTarget = ModSettings.crosshairReact && mc.hitResult != null && mc.hitResult.getType() == Type.ENTITY;
        react = react + ((onTarget ? 1.0F : 0.0F) - react) * (1.0F - (float)Math.exp(-dt * 18.0F));
        Window w = mc.getWindow();
        float gui = w.getGuiScale();
        int cell = Math.max(1, Math.round(ModSettings.crosshairScale * gui));
        int fbW = w.getWidth();
        int fbH = w.getHeight();
        int x0 = fbW / 2 - 7 * cell - cell / 2;
        int y0 = fbH / 2 - 7 * cell - cell / 2;
        int col = Colors.lerp(color(), -46261, react);
        c.pose().pushPose();
        c.pose().translate(0.0F, 0.0F, -c.pose().last().pose().m32());
        c.pose().scale(1.0F / gui, 1.0F / gui, 1.0F);
        float pop = 1.0F + 0.18F * react;
        c.pose().translate(fbW / 2.0F, fbH / 2.0F, 0.0F);
        c.pose().scale(pop, pop, 1.0F);
        c.pose().translate(-fbW / 2.0F, -fbH / 2.0F, 0.0F);
        drawCells(c, shape(ModSettings.crosshairStyle), x0, y0, cell, col, ModSettings.crosshairOutline);
        c.pose().popPose();
        attackIndicator(c, mc, fbW, gui, y0 + 15 * cell);
        return true;
    }

    public static void drawCells(GuiGraphics c, boolean[] cells, int x0, int y0, int cell, int col, boolean outline) {
        if (outline) {
            int o = Math.max(1, cell / 3);
            int dark = Colors.rgba(0, 0, 0, 170.0F * ((col >>> 24) / 255.0F));

            for (int i = 0; i < cells.length; i++) {
                if (cells[i]) {
                    int x = x0 + i % 15 * cell;
                    int y = y0 + i / 15 * cell;
                    c.fill(x - o, y - o, x + cell + o, y + cell + o, dark);
                }
            }
        }

        for (int i = 0; i < cells.length; i++) {
            if (cells[i]) {
                int x = x0 + i % 15 * cell;
                int y = y0 + i / 15 * cell;
                int own = ModSettings.crosshairColor == 3 ? Colors.lerp(cellColor(i % 15, i / 15), col, react) : col;
                c.fill(x, y, x + cell, y + cell, own);
            }
        }
    }

    private static void attackIndicator(GuiGraphics c, Minecraft mc, int fbW, float gui, int belowY) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
            float progress = mc.player.getAttackStrengthScale(0.0F);
            if (!(progress >= 1.0F)) {
                c.pose().pushPose();
                c.pose().scale(1.0F / gui, 1.0F / gui, 1.0F);
                int bw = Math.round(16.0F * gui);
                int bh = Math.max(1, Math.round(1.5F * gui));
                int x = fbW / 2 - bw / 2;
                int y = belowY + Math.round(3.0F * gui);
                c.fill(x, y, x + bw, y + bh, 1711276032);
                c.fill(x, y, x + Math.round(bw * progress), y + bh, Colors.multAlpha(color(), 0.9F));
                c.pose().popPose();
            }
        }
    }
}
