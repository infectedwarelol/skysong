package dev.infectedware.skysong.hud;

import net.minecraft.client.multiplayer.ClientLevel;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.HudLayer;
import dev.infectedware.skysong.hud.components.LyricsBarComp;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class InfoHud implements HudLayer {
    public static final String[] STYLES = new String[]{"Clean", "Glass", "Classic"};
    private static final String[] FACING = new String[]{"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};
    private static final String[] FACING_AXIS = new String[]{"+Z", "+Z -X", "-X", "-X -Z", "-Z", "-Z +X", "+X", "+X +Z"};
    private static final float[][] OUTLINE = new float[][]{
        {-1.0F, -1.0F}, {0.0F, -1.0F}, {1.0F, -1.0F}, {-1.0F, 0.0F}, {1.0F, 0.0F}, {-1.0F, 1.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}
    };
    private float fpsShown = -1.0F;
    private static final String[] COMPASS = new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    public static float reservedRight() {
        if (!ModSettings.infoPerformance && ModSettings.infoStyle != 2) {
            return 0.0F;
        }

        return switch (ModSettings.infoStyle) {
            case 1 -> 26.0F;
            case 2 -> 36.0F;
            default -> 40.5F;
        };
    }

    @Override
    public void render(GuiGraphics c) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (ModSettings.infoEnabled && player != null && mc.level != null && !mc.options.hideGui && R2D.available()) {
            float s = HudSpace.scale();
            float w = HudSpace.width();
            float h = HudSpace.height();
            c.pose().pushPose();
            c.pose().scale(s, s, 1.0F);
            switch (ModSettings.infoStyle) {
                case 1:
                    if (ModSettings.infoCoords) {
                        this.coordsCard(c, mc, player, h);
                    }

                    if (ModSettings.infoPerformance) {
                        this.performancePill(c, mc, w, h);
                    }
                    break;
                case 2:
                    classic(c, mc, player, w, h);
                    break;
                default:
                    if (ModSettings.infoCoords) {
                        this.cleanCoords(c, mc, player, h);
                    }

                    if (ModSettings.infoPerformance) {
                        this.cleanPerformance(c, mc, w, h);
                    }
            }

            c.pose().popPose();
        }
    }

    private void cleanCoords(GuiGraphics c, Minecraft mc, Player p, float h) {
        String[] axes = new String[]{"X", "Y", "Z"};
        int[] vals = new int[]{Mth.floor(p.getX()), Mth.floor(p.getY()), Mth.floor(p.getZ())};
        String extra = cleanExtra(mc, p);
        float extraH = extra.isEmpty() ? 0.0F : 8.0F;
        float blockH = 31.5F + extraH;
        float top = h - 5.0F - blockH;
        float x = 6.0F;
        R2D.accentFillVertical(c, x, top + 1.0F, 1.2F, 29.5F, 0.6F, 230.0F);
        float labelX = x + 5.0F;
        float valueX = labelX + 9.0F;
        float widest = 0.0F;

        for (int i = 0; i < 3; i++) {
            float cy = top + 10.5F * i + 5.25F;
            shadowed(c, MsdfFont.Font.SEMIBOLD, axes[i], labelX, cy, 6.0F, ClientAccent.gradientColor(i / 2.0F, 255.0F));
            String v = Integer.toString(vals[i]);
            shadowed(c, MsdfFont.Font.BOLD, v, valueX, cy, 8.5F, -1);
            widest = Math.max(widest, R2D.width(MsdfFont.Font.BOLD, v, 8.5F));
        }

        if (ModSettings.infoFacing) {
            int i = Math.floorMod(Math.round(Mth.wrapDegrees(p.getYRot()) / 45.0F), 8);
            float fx = valueX + widest + 9.0F;
            shadowed(c, MsdfFont.Font.BOLD, COMPASS[i], fx, top + 11.025F, 13.0F, -1);
            shadowed(c, MsdfFont.Font.SEMIBOLD, FACING_AXIS[i], fx, top + 24.675F, 5.2F, -6644568);
        }

        if (!extra.isEmpty()) {
            shadowed(c, MsdfFont.Font.SEMIBOLD, extra, labelX, top + 31.5F + 4.0F, 5.2F, -6644568);
        }
    }

    private static String cleanExtra(Minecraft mc, Player p) {
        ClientLevel level = mc.level;
        if (level == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (ModSettings.infoOtherDimension) {
            boolean nether = level.dimensionType().coordinateScale() > 1.0;
            double k = nether ? 8.0 : 0.125;
            sb.append(nether ? "OVERWORLD " : "NETHER ").append(Mth.floor(p.getX() * k)).append("  ").append(Mth.floor(p.getZ() * k));
        }

        if (ModSettings.infoSpeed) {
            if (!sb.isEmpty()) {
                sb.append("     ");
            }

            sb.append(bps(p)).append(" B/S");
        }

        return sb.toString();
    }

    private void cleanPerformance(GuiGraphics c, Minecraft mc, float w, float h) {
        int fps = mc.getFps();
        this.fpsShown = this.fpsShown < 0.0F ? fps : this.fpsShown + (fps - this.fpsShown) * 0.15F;
        double tps = ServerStats.tps();
        int ping = ServerStats.ping(mc.player);
        String[] vals = new String[]{Integer.toString(Math.round(this.fpsShown)), String.format(Locale.ROOT, "%.1f", tps), Integer.toString(ping)};
        String[] units = new String[]{"FPS", "TPS", "MS"};
        int[] cols = new int[]{
            fps >= 60 ? -1 : (fps >= 30 ? -340971 : -495247),
            tps >= 19.0 ? -1 : (tps >= 15.0 ? -340971 : -495247),
            ping <= 80 ? -1 : (ping <= 160 ? -340971 : -495247)
        };
        float unitW = 0.0F;

        for (String u : units) {
            unitW = Math.max(unitW, R2D.width(MsdfFont.Font.SEMIBOLD, u, 6.0F));
        }

        float right = w - 5.0F - 1.0F;
        float lineX = right - 1.2F;
        float unitX = lineX - 5.0F - unitW;
        float top = h - 5.0F - 31.5F;
        R2D.accentFillVertical(c, lineX, top + 1.0F, 1.2F, 29.5F, 0.6F, 230.0F);

        for (int i = 0; i < 3; i++) {
            float cy = top + 10.5F * i + 5.25F;
            float vw = R2D.width(MsdfFont.Font.BOLD, vals[i], 8.5F);
            shadowed(c, MsdfFont.Font.BOLD, vals[i], unitX - 4.0F - vw, cy, 8.5F, cols[i]);
            shadowed(c, MsdfFont.Font.SEMIBOLD, units[i], unitX, cy, 6.0F, ClientAccent.gradientColor(i / 2.0F, 255.0F));
        }
    }

    private static void shadowed(GuiGraphics c, MsdfFont.Font font, String s, float x, float cy, float size, int color) {
        int shadow = Colors.rgba(0, 0, 0, 110.0F * ((color >>> 24) / 255.0F));
        R2D.textCentered(c, font, s, x + 0.55F, cy + 0.55F, size, shadow);
        R2D.textCentered(c, font, s, x, cy, size, color);
    }

    private void coordsCard(GuiGraphics c, Minecraft mc, Player p, float h) {
        String[] axes = new String[]{"X", "Y", "Z"};
        String[] values = new String[]{Integer.toString(Mth.floor(p.getX())), Integer.toString(Mth.floor(p.getY())), Integer.toString(Mth.floor(p.getZ()))};
        float badge = 12.0F;
        float row1W = badge + 5.0F;

        for (int i = 0; i < 3; i++) {
            row1W += R2D.width(MsdfFont.Font.SEMIBOLD, axes[i], 8.0F) + 2.5F + R2D.width(MsdfFont.Font.BOLD, values[i], 8.0F);
            if (i < 2) {
                row1W += 7.0F;
            }
        }

        String sub = subline(mc, p);
        boolean two = !sub.isEmpty();
        float row2W = two ? R2D.width(MsdfFont.Font.MEDIUM, sub, 6.0F) : 0.0F;
        float cw = Math.max(row1W, row2W) + 14.0F;
        float ch = 19.0F + (two ? 9.5F : 0.0F) + 1.0F;
        float x = 5.0F;
        float y = h - 5.0F - ch;
        R2D.clientRect(c, x, y, cw, ch, 1.0F);
        float r1cy = y + 5.5F + 4.0F + 0.5F;
        float bcx = x + 7.0F + badge / 2.0F;
        R2D.circle(c, bcx, r1cy, badge / 2.0F, ClientAccent.gradientColor(0.0F, 70.0F));
        LyricsBarComp.navArrow(c, bcx, r1cy, p.getYRot(), -1, 0.85F);
        float tx = x + 7.0F + badge + 5.0F;

        for (int i = 0; i < 3; i++) {
            int axisColor = ClientAccent.gradientColor(i / 2.0F, 255.0F);
            R2D.textCentered(c, MsdfFont.Font.SEMIBOLD, axes[i], tx, r1cy, 8.0F, axisColor);
            tx += R2D.width(MsdfFont.Font.SEMIBOLD, axes[i], 8.0F) + 2.5F;
            R2D.textCentered(c, MsdfFont.Font.BOLD, values[i], tx, r1cy, 8.0F, -1);
            tx += R2D.width(MsdfFont.Font.BOLD, values[i], 8.0F) + 7.0F;
        }

        if (two) {
            float r2cy = y + 5.5F + 8.0F + 3.5F + 3.0F + 1.0F;
            R2D.textCentered(c, MsdfFont.Font.MEDIUM, sub, x + 7.0F, r2cy, 6.0F, -6644568);
        }
    }

    private static String subline(Minecraft mc, Player p) {
        ClientLevel level = mc.level;
        if (level == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (ModSettings.infoFacing) {
            int i = Math.floorMod(Math.round(Mth.wrapDegrees(p.getYRot()) / 45.0F), 8);
            sb.append(FACING[i]).append(' ').append(FACING_AXIS[i]);
        }

        if (ModSettings.infoOtherDimension) {
            if (!sb.isEmpty()) {
                sb.append("  ·  ");
            }

            boolean nether = level.dimensionType().coordinateScale() > 1.0;
            double k = nether ? 8.0 : 0.125;
            sb.append(nether ? "Overworld " : "Nether ").append(Mth.floor(p.getX() * k)).append(", ").append(Mth.floor(p.getZ() * k));
        }

        if (ModSettings.infoSpeed) {
            if (!sb.isEmpty()) {
                sb.append("  ·  ");
            }

            sb.append(bps(p)).append(" b/s");
        }

        return sb.toString();
    }

    private void performancePill(GuiGraphics c, Minecraft mc, float w, float h) {
        int fps = mc.getFps();
        this.fpsShown = this.fpsShown < 0.0F ? fps : this.fpsShown + (fps - this.fpsShown) * 0.15F;
        double tps = ServerStats.tps();
        int ping = ServerStats.ping(mc.player);
        String[] vals = new String[]{Integer.toString(Math.round(this.fpsShown)), String.format(Locale.ROOT, "%.1f", tps), Integer.toString(ping)};
        String[] labels = new String[]{"FPS", "TPS", "ms"};
        int[] dots = new int[]{
            fps >= 60 ? -11870592 : (fps >= 30 ? -340971 : -495247),
            tps >= 19.0 ? -11870592 : (tps >= 15.0 ? -340971 : -495247),
            ping <= 80 ? -11870592 : (ping <= 160 ? -340971 : -495247)
        };
        float dot = 4.0F;
        float gapIn = 3.5F;
        float gapLabel = 2.5F;
        float sep = 8.0F;
        float[] segW = new float[3];
        float total = 14.0F;

        for (int i = 0; i < 3; i++) {
            segW[i] = dot + gapIn + R2D.width(MsdfFont.Font.BOLD, vals[i], 8.0F) + gapLabel + R2D.width(MsdfFont.Font.MEDIUM, labels[i], 6.5F);
            total += segW[i] + (i < 2 ? sep * 2.0F + 0.6F : 0.0F);
        }

        float x = w - 5.0F - total;
        float y = h - 5.0F - 17.0F;
        float cy = y + 8.5F;
        R2D.clientRect(c, x, y, total, 17.0F, 1.0F);
        float tx = x + 7.0F;

        for (int i = 0; i < 3; i++) {
            R2D.circle(c, tx + dot / 2.0F, cy, dot, Colors.multAlpha(dots[i], 0.22F));
            R2D.circle(c, tx + dot / 2.0F, cy, dot / 2.0F, dots[i]);
            float vx = tx + dot + gapIn;
            R2D.textCentered(c, MsdfFont.Font.BOLD, vals[i], vx, cy, 8.0F, -1);
            float lx = vx + R2D.width(MsdfFont.Font.BOLD, vals[i], 8.0F) + gapLabel;
            R2D.textCentered(c, MsdfFont.Font.MEDIUM, labels[i], lx, cy + 0.4F, 6.5F, -6644568);
            tx += segW[i];
            if (i < 2) {
                tx += sep;
                R2D.rect(c, tx, y + 4.5F, 0.6F, 8.0F, 0.3F, Colors.rgba(255, 255, 255, 38.0F));
                tx += 0.6F + sep;
            }
        }
    }

    private static void classic(GuiGraphics c, Minecraft mc, Player p, float w, float h) {
        float bottom = h - 5.0F - 7.0F;
        float above = bottom - 10.0F;
        if (ModSettings.infoSpeed) {
            draw(c, 5.0F, above, new InfoHud.Seg("BPS ", -3618608), new InfoHud.Seg(bps(p), -11665561));
        }

        if (ModSettings.infoCoords) {
            draw(
                c,
                5.0F,
                bottom,
                new InfoHud.Seg("XYZ ", -3618608),
                new InfoHud.Seg(coords(p), -11665561),
                new InfoHud.Seg(ModSettings.infoOtherDimension ? "  " + otherDimension(mc, p) : "", -3618608)
            );
        }

        if (ModSettings.infoPerformance) {
            InfoHud.Seg[] fps = new InfoHud.Seg[]{new InfoHud.Seg("FPS ", -3618608), new InfoHud.Seg(Integer.toString(mc.getFps()), -45747)};
            InfoHud.Seg[] tps = new InfoHud.Seg[]{
                new InfoHud.Seg("TPS ", -3618608), new InfoHud.Seg(String.format(Locale.ROOT, "%.1f", ServerStats.tps()), -45747)
            };
            InfoHud.Seg[] ping = new InfoHud.Seg[]{new InfoHud.Seg("Ping ", -3618608), new InfoHud.Seg(ServerStats.ping(p) + "ms", -45747)};
            draw(c, w - 5.0F - width(fps), above - 10.0F, fps);
            draw(c, w - 5.0F - width(tps), above, tps);
            draw(c, w - 5.0F - width(ping), bottom, ping);
        }
    }

    private static float width(InfoHud.Seg[] segs) {
        float w = 0.0F;

        for (InfoHud.Seg s : segs) {
            w += R2D.width(MsdfFont.Font.SEMIBOLD, s.text(), 7.0F);
        }

        return w;
    }

    private static void draw(GuiGraphics c, float x, float y, InfoHud.Seg... segs) {
        for (float[] d : OUTLINE) {
            float cx = x;

            for (InfoHud.Seg s : segs) {
                R2D.text(c, MsdfFont.Font.SEMIBOLD, s.text(), cx + d[0] * 0.2F, y + d[1] * 0.2F, 7.0F, -16777216);
                cx += R2D.width(MsdfFont.Font.SEMIBOLD, s.text(), 7.0F);
            }
        }

        float cx = x;

        for (InfoHud.Seg s : segs) {
            R2D.text(c, MsdfFont.Font.SEMIBOLD, s.text(), cx, y, 7.0F, s.color());
            cx += R2D.width(MsdfFont.Font.SEMIBOLD, s.text(), 7.0F);
        }
    }

    private static String bps(Player p) {
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        return String.format(Locale.ROOT, "%.2f", Math.sqrt(dx * dx + dz * dz) * 20.0);
    }

    private static String coords(Player p) {
        return Mth.floor(p.getX()) + ", " + Mth.floor(p.getY()) + ", " + Mth.floor(p.getZ());
    }

    private static String otherDimension(Minecraft mc, Player p) {
        ClientLevel level = mc.level;
        if (level == null) {
            return "";
        }

        boolean nether = level.dimensionType().coordinateScale() > 1.0;
        double k = nether ? 8.0 : 0.125;
        return "[" + Mth.floor(p.getX() * k) + ", " + Mth.floor(p.getY()) + ", " + Mth.floor(p.getZ() * k) + "]";
    }

    private record Seg(String text, int color) {
    }
}
