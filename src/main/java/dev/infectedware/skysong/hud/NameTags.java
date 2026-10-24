package dev.infectedware.skysong.hud;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.HudLayer;
import dev.infectedware.skysong.compat.WorldCtx;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.scores.PlayerTeam;

public final class NameTags implements HudLayer {
    private static final double MAX_DISTANCE_SQ = 4096.0;
    private static final long VISIBILITY_REFRESH_MS = 150L;
    private static final long PRUNE_MS = 1000L;
    private static final int BACKGROUND = -1778384896;
    private static final int HEART = -42386;
    private static final int HEALTH = -5196096;
    private static final Pattern EMPTY_TOKEN = Pattern.compile("[<\\[({‹〈«＜]\\s*empty\\s*[>\\])}›〉»＞]", 2);
    private static final NameTags INSTANCE = new NameTags();
    private final List<NameTags.Tag> tags = new ArrayList<>();
    private final Map<Integer, NameTags.Seen> seen = new HashMap<>();
    private long lastFrame = System.currentTimeMillis();

    public static NameTags get() {
        return INSTANCE;
    }

    public static boolean anyVisible() {
        return false;
    }

    private static boolean tagged(Player p) {
        Minecraft mc = Minecraft.getInstance();
        return p == mc.player
            ? ModSettings.nameTagsSelf && !mc.options.getCameraType().isFirstPerson()
            : p.isAlive() && !p.isRemoved() && p.getHealth() > 0.0F && !p.isInvisible();
    }

    public static boolean hidesNameTagFor(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (ModSettings.nameTagsEnabled && e != null && mc.player != null && mc.level != null) {
            if (!(e instanceof Player p)) {
                for (Player p : mc.level.players()) {
                    if (tagged(p) && !(mc.player.distanceToSqr(p) > 4096.0)) {
                        double dx = e.getX() - p.getX();
                        double dz = e.getZ() - p.getZ();
                        double top = p.getY() + p.getBbHeight();
                        if (dx * dx + dz * dz <= 2.25 && e.getY() >= top - 1.0 && e.getY() <= top + 7.0) {
                            return true;
                        }
                    }
                }

                return false;
            } else {
                return tagged(p) && mc.player.distanceToSqr(p) <= 4096.0;
            }
        } else {
            return false;
        }
    }

    public static Component displayName(Player p) {
        PlayerTeam team = Compat.team(p);
        Component prefix = team != null ? team.getPlayerPrefix() : null;
        boolean hasPrefix = prefix != null && !missing(prefix.getString());
        String name = p.getName().getString();
        if (!missing(name)) {
            MutableComponent out = Component.empty();
            if (hasPrefix) {
                out.append(prefix.copy());
                if (!prefix.getString().endsWith(" ")) {
                    out.append(" ");
                }
            }

            out.append(Component.literal(EMPTY_TOKEN.matcher(name).replaceAll("").trim()).withStyle(ChatFormatting.WHITE));
            return out;
        } else if (hasPrefix) {
            return prefix.copy();
        } else {
            Component hologram = topHologram(p);
            if (hologram != null) {
                return hologram;
            } else {
                return p.getCustomName() != null && !missing(p.getCustomName().getString()) ? p.getCustomName().copy() : null;
            }
        }
    }

    private static boolean missing(String s) {
        if (s == null) {
            return true;
        }

        String t = EMPTY_TOKEN.matcher(s.replaceAll("(?i)§[0-9a-fk-or]", "").trim()).replaceAll("").trim();
        t = t.replaceAll("^[\\p{P}\\p{S}\\s]+", "").replaceAll("[\\p{P}\\p{S}\\s]+$", "");
        return t.isEmpty() || t.equalsIgnoreCase("empty");
    }

    private static Component topHologram(Player p) {
        Minecraft mc = Minecraft.getInstance();
        Component best = null;
        double bestY = Double.NEGATIVE_INFINITY;
        double top = p.getY() + p.getBbHeight();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e != p && !(e instanceof Player)) {
                double dx = e.getX() - p.getX();
                double dz = e.getZ() - p.getZ();
                if (!(dx * dx + dz * dz > 2.25) && !(e.getY() < top - 1.0) && !(e.getY() > top + 7.0)) {
                    Component t = e instanceof TextDisplay d ? d.getText() : e.getCustomName();
                    if (t != null) {
                        String line = t.getString().split("\n")[0];
                        if (!missing(line) && !(e.getY() <= bestY)) {
                            best = Component.literal(line);
                            bestY = e.getY();
                        }
                    }
                }
            }
        }

        return best;
    }

    public void afterWorld(WorldCtx ctx) {
        Projection.capture(ctx);
        this.tags.clear();
        Minecraft mc = Minecraft.getInstance();
        if (ModSettings.nameTagsEnabled && mc.player != null && mc.level != null) {
            long now = System.currentTimeMillis();
            long frameMs = Math.max(1L, Math.min(now - this.lastFrame, 100L));
            this.lastFrame = now;
            float partial = ctx.partialTick();
            float k = ModSettings.nameTagsScale;
            float nameSize = Math.max(1, Math.round(7.0F * k));
            float healthSize = Math.max(1, Math.round(6.0F * k));
            float padX = Math.round(3.0F * k);
            float padY = Math.round(2.0F * k);
            float gap = Math.round(2.0F * k);
            float healthGap = Math.round(3.0F * k);
            float radius = Math.round(4.0F * k);
            float nudge = Math.max(1, Math.round(k));

            for (Player p : mc.level.players()) {
                if (tagged(p) && !(mc.player.distanceToSqr(p) > 4096.0)) {
                    NameTags.Seen s = this.seen.computeIfAbsent(p.getId(), id -> new NameTags.Seen());
                    if (now - s.checkedAt >= 150L) {
                        s.visible = p == mc.player || visible(mc, p, partial);
                        s.checkedAt = now;
                    }

                    s.touchedAt = now;
                    s.anim = Mth.clamp(s.anim + (s.visible ? 1 : -1) * ((float)frameMs / 150.0F), 0.0F, 1.0F);
                    s.alpha = s.anim * s.anim * (3.0F - 2.0F * s.anim);
                    s.scale = 0.85F + 0.15F * s.alpha;
                    if (!(s.alpha <= 0.01F)) {
                        Vec3 pos = p.getPosition(partial);
                        float[] screen = Projection.project(pos.x, pos.y + p.getBbHeight() + 0.35, pos.z);
                        if (screen != null) {
                            Component name = displayName(p);
                            if (name == null) {
                                name = Component.literal("NPC");
                            }

                            List<NameTags.Run> runs = runs(name);
                            float nameW = 0.0F;

                            for (NameTags.Run r : runs) {
                                nameW += R2D.width(MsdfFont.Font.SEMIBOLD, r.text(), nameSize);
                            }

                            String health = ModSettings.nameTagsHealth ? Integer.toString(Mth.ceil(ServerStats.health(p))) : null;
                            float heartW = health == null ? 0.0F : R2D.width(MsdfFont.Font.HEART, "A", healthSize);
                            float healthW = health == null ? 0.0F : R2D.width(MsdfFont.Font.MEDIUM, health, healthSize);
                            float healthPart = health == null ? 0.0F : healthGap + heartW + gap + healthW;
                            float w = padX * 2.0F + nameW + healthPart;
                            float h = nameSize + padY * 2.0F;
                            this.tags
                                .add(
                                    new NameTags.Tag(
                                        runs,
                                        nameW,
                                        health,
                                        heartW,
                                        healthW,
                                        nameSize,
                                        healthSize,
                                        padX,
                                        padY,
                                        gap,
                                        healthGap,
                                        radius,
                                        screen[0] - w / 2.0F,
                                        screen[1] - h,
                                        w,
                                        h,
                                        s.alpha,
                                        s.scale,
                                        screen[2]
                                    )
                                );
                        }
                    }
                }
            }

            this.tags.sort((a, b) -> Float.compare(b.depth(), a.depth()));
            this.seen.values().removeIf(sx -> now - sx.touchedAt > 1000L);
        }
    }

    private static boolean visible(Minecraft mc, Player p, float partial) {
        Vec3 eye = mc.player.getEyePosition(partial);
        return clear(mc, eye, p.getEyePosition(partial), p) || clear(mc, eye, p.getPosition(partial).add(0.0, p.getBbHeight() * 0.5, 0.0), p);
    }

    private static boolean clear(Minecraft mc, Vec3 from, Vec3 to, Entity e) {
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, Block.VISUAL, Fluid.NONE, e));
        return hit == null || hit.getType() == Type.MISS;
    }

    private static List<NameTags.Run> runs(Component text) {
        List<NameTags.Run> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int[] color = new int[]{-1};
        text.visit((style, s) -> {
            StringDecomposer.iterateFormatted(s, style, (i, st, cp) -> {
                int col = st.getColor() != null ? 0xFF000000 | st.getColor().getValue() : -1;
                if (col != color[0] && !sb.isEmpty()) {
                    out.add(new NameTags.Run(sb.toString(), color[0]));
                    sb.setLength(0);
                }

                color[0] = col;
                sb.appendCodePoint(cp);
                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        if (!sb.isEmpty()) {
            out.add(new NameTags.Run(sb.toString(), color[0]));
        }

        while (!out.isEmpty() && out.get(0).text().isBlank()) {
            out.remove(0);
        }

        while (!out.isEmpty() && out.get(out.size() - 1).text().isBlank()) {
            out.remove(out.size() - 1);
        }

        if (!out.isEmpty()) {
            out.set(0, new NameTags.Run(out.get(0).text().stripLeading(), out.get(0).color()));
            int last = out.size() - 1;
            out.set(last, new NameTags.Run(out.get(last).text().stripTrailing(), out.get(last).color()));
        }

        return out;
    }

    @Override
    public void render(GuiGraphics c, float tick) {
        Minecraft mc = Minecraft.getInstance();
        if (ModSettings.nameTagsEnabled && !this.tags.isEmpty() && !mc.options.hideGui && R2D.available()) {
            float s = HudSpace.scale();
            c.pose().pushPose();
            c.pose().scale(s, s, 1.0F);

            for (NameTags.Tag t : this.tags) {
                boolean scaling = Math.abs(t.scale() - 1.0F) > 0.001F;
                if (scaling) {
                    float cx = t.x() + t.w() / 2.0F;
                    float cy = t.y() + t.h() / 2.0F;
                    c.pose().pushPose();
                    c.pose().translate(cx, cy, 0.0F);
                    c.pose().scale(t.scale(), t.scale(), 1.0F);
                    c.pose().translate(-cx, -cy, 0.0F);
                }

                R2D.rect(c, t.x(), t.y(), t.w(), t.h(), t.radius(), Colors.multAlpha(-1778384896, t.alpha()));
                float tx = t.x() + t.padX();
                float ty = t.y() + t.padY() - 1.0F;
                float[] nb = R2D.bounds(MsdfFont.Font.SEMIBOLD, "H", t.nameSize());
                float nameY = t.y() + t.h() / 2.0F - (nb[1] + nb[3]) / 2.0F;

                for (NameTags.Run r : t.runs()) {
                    R2D.text(c, MsdfFont.Font.SEMIBOLD, r.text(), tx, nameY, t.nameSize(), Colors.multAlpha(r.color(), t.alpha()));
                    tx += R2D.width(MsdfFont.Font.SEMIBOLD, r.text(), t.nameSize());
                }

                if (t.health() != null) {
                    float hx = t.x() + t.padX() + t.nameW() + t.healthGap();
                    float[] hb = R2D.bounds(MsdfFont.Font.HEART, "A", t.healthSize());
                    R2D.text(
                        c, MsdfFont.Font.HEART, "A", hx, t.y() + t.h() / 2.0F - (hb[1] + hb[3]) / 2.0F, t.healthSize(), Colors.multAlpha(-42386, t.alpha())
                    );
                    float[] vb = R2D.bounds(MsdfFont.Font.MEDIUM, "0", t.healthSize());
                    R2D.text(
                        c,
                        MsdfFont.Font.MEDIUM,
                        t.health(),
                        hx + t.heartW() + t.gap(),
                        t.y() + t.h() / 2.0F - (vb[1] + vb[3]) / 2.0F,
                        t.healthSize(),
                        Colors.multAlpha(-5196096, t.alpha())
                    );
                }

                if (scaling) {
                    c.pose().popPose();
                }
            }

            c.pose().popPose();
        }
    }

    private record Run(String text, int color) {
    }

    private static final class Seen {
        boolean visible;
        float anim;
        float alpha;
        float scale;
        long checkedAt;
        long touchedAt;
    }

    private record Tag(
        List<NameTags.Run> runs,
        float nameW,
        String health,
        float heartW,
        float healthW,
        float nameSize,
        float healthSize,
        float padX,
        float padY,
        float gap,
        float healthGap,
        float radius,
        float x,
        float y,
        float w,
        float h,
        float alpha,
        float scale,
        float depth
    ) {
    }
}
