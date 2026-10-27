package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class CardFx {
    public static final String[] COLORS = new String[]{"Theme", "Rainbow", "Custom", "Two colors"};
    private static final int CELL_W = 24;
    private static final int CELL_H = 32;
    private static final int FACES = 8;
    private static final int BACK = 8;
    private static final int GLOW = 9;
    private static final int WHITE = 10;
    private static final int CELLS = 11;
    private static final float CARD_H = 0.42F;
    private static final float CARD_W = CARD_H * 20.0F / 28.0F;
    static final float KILL_GATHER = 0.3F;
    private static final float GRAVITY = 9.0F;
    private static final String[][] RANKS = new String[][]{
        {".#.", "#.#", "###", "#.#", "#.#"},
        {"#.#", "##.", "#..", "##.", "#.#"},
        {".#.", "#.#", "#.#", "##.", ".##"},
        {"..#", "..#", "..#", "#.#", ".#."},
        {"###", "..#", ".#.", ".#.", ".#."},
        {".#.", "#.#", ".##", "..#", ".#."}
    };
    private static final String[][] SMALL_SUITS = new String[][]{
        {"..#..", ".###.", "#####", "..#..", ".###."},
        {".#.#.", "#####", "#####", ".###.", "..#.."},
        {"..#..", ".###.", "#####", ".###.", "..#.."},
        {".###.", "#####", "#####", "..#..", ".###."}
    };
    private static final String[][] BIG_SUITS = new String[][]{
        {"...#...", "..###..", ".#####.", "#######", "#######", ".#.#.#.", "..###.."},
        {".##.##.", "#######", "#######", "#######", ".#####.", "..###..", "...#..."},
        {"...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."},
        {"..###..", "..###..", "##.#.##", "#######", "##.#.##", "...#...", "..###.."}
    };
    private static final int[][] DECK = new int[][]{{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 0}, {5, 2}, {0, 1}, {1, 3}};
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static Identifier atlas;

    private CardFx() {
    }

    private static final class Burst {
        final boolean kill;
        final int entity;
        final long born;
        final long seed;
        final float life;
        final int count;
        double x;
        double y;
        double z;
        float height;

        Burst(boolean kill, int entity, Vec3 at, float height, float life, int count) {
            this.kill = kill;
            this.entity = entity;
            this.born = System.currentTimeMillis();
            this.seed = RNG.nextLong();
            this.life = life;
            this.count = count;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.height = height;
        }
    }

    public static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player && entity instanceof LivingEntity target && target.isAlive()) {
                hit(target);
            }

            return InteractionResult.PASS;
        });
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> render(context.matrices(), context.commandQueue()));
        PiecesFx.init();
    }

    private static void hit(LivingEntity target) {
        if (!ModSettings.targetCards || ModSettings.targetCardsPlayersOnly && !(target instanceof Player)) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Burst b : BURSTS) {
            if (!b.kill && b.entity == target.getId() && now - b.born < ModSettings.targetCardsRepeat * 1000.0F) {
                return;
            }
        }

        BURSTS.add(new Burst(false, target.getId(), target.position(), target.getBbHeight(), ModSettings.targetCardsHold, ModSettings.targetCardsCount));
    }

    static void killBurst(Vec3 at, float height) {
        BURSTS.add(new Burst(true, -1, at, height, KILL_GATHER + 2.2F + ModSettings.killPiecesLie, 14));
    }

    static Identifier atlas() {
        if (atlas == null) {
            atlas = Ids.of("skysong", "fx/cards");
            Minecraft.getInstance().getTextureManager().register(atlas, Compat.dynamicTexture("cards", paintAtlas()));
        }

        return atlas;
    }

    static float whiteU() {
        return (WHITE * CELL_W + CELL_W / 2.0F) / (CELLS * CELL_W);
    }

    static float whiteV() {
        return 0.5F;
    }

    private static void render(PoseStack pose, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        if (BURSTS.isEmpty()) {
            return;
        }

        if (mc.level == null || !Fx.allowed()) {
            BURSTS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        float pt = Compat.partialTick();
        Vec3 cam = Compat.camPos(mc.gameRenderer.getMainCamera());
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst b = it.next();
            float t = (now - b.born) / 1000.0F;
            if (t > b.life || !b.kill && !ModSettings.targetCards) {
                it.remove();
                continue;
            }

            if (!b.kill && mc.level.getEntity(b.entity) instanceof Entity e) {
                Vec3 at = e.getPosition(pt);
                b.x = at.x;
                b.y = at.y;
                b.z = at.z;
            }

            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(b.x, b.y + b.height * 0.5, b.z));
            pose.pushPose();
            pose.translate(b.x - cam.x, b.y - cam.y, b.z - cam.z);
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(atlas()), (p, out) -> {
                if (b.kill) {
                    killCards(b, t, p, out, light);
                } else {
                    orbitCards(b, t, p, out, light);
                }
            });
            if (!b.kill && ModSettings.targetCardsGroundGlow) {
                float glow = envelope(t, 0.2F, b.life);
                collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(atlas()), (p, out) -> groundGlow(b, glow, p, out));
            }

            pose.popPose();
        }
    }

    private static float envelope(float t, float in, float life) {
        return Mth.clamp(t / in, 0.0F, 1.0F) * Mth.clamp((life - t) / 0.3F, 0.0F, 1.0F);
    }

    private static void orbitCards(Burst b, float t, PoseStack.Pose base, VertexConsumer out, int light) {
        Random r = new Random(b.seed);
        float env = envelope(t, 0.18F, b.life);
        float radius = ModSettings.targetCardsRadius;
        float speed = ModSettings.targetCardsSpeed;
        float size = ModSettings.targetCardsSize * (0.35F + 0.65F * env);
        PoseStack stack = new PoseStack();
        stack.last().set(base);
        for (int i = 0; i < b.count; i++) {
            float offset = r.nextFloat();
            float tilt = (r.nextFloat() - 0.5F) * 1.2F;
            int face = r.nextInt(FACES);
            float phase = Mth.TWO_PI * i / b.count + t * speed * 2.4F + offset;
            float spread = radius * (0.45F + 0.55F * Mth.clamp(t / 0.3F, 0.0F, 1.0F)) * (1.0F + 0.15F * Mth.sin(t * 2.0F + i));
            float y = 0.15F + (i * 0.618F + offset * 0.3F) % 1.0F * Math.max(0.3F, b.height - 0.2F) + Mth.sin(t * 3.0F + i) * 0.06F;
            stack.pushPose();
            stack.translate(Mth.cos(phase) * spread, y, Mth.sin(phase) * spread);
            stack.mulPose(Axis.YP.rotation(-phase + Mth.HALF_PI + t * speed * 3.0F * (i % 2 == 0 ? 1.0F : -1.0F)));
            stack.mulPose(Axis.XP.rotation(tilt + Mth.sin(t * 4.0F + i) * 0.4F));
            stack.mulPose(Axis.ZP.rotation(offset * Mth.TWO_PI));
            card(stack.last(), out, face, size, tint(i / (float)b.count, false), light);
            stack.popPose();
        }
    }

    private static void killCards(Burst b, float t, PoseStack.Pose base, VertexConsumer out, int light) {
        Random r = new Random(b.seed);
        PoseStack stack = new PoseStack();
        stack.last().set(base);
        float cy = b.height * 0.55F;
        for (int i = 0; i < b.count; i++) {
            float angle = Mth.TWO_PI * i / b.count + r.nextFloat() * 0.4F;
            float startR = 2.4F + r.nextFloat() * 1.2F;
            float startY = cy + (r.nextFloat() - 0.3F) * 1.6F;
            float vx = Mth.cos(angle + 0.6F) * (2.0F + r.nextFloat() * 2.5F);
            float vz = Mth.sin(angle + 0.6F) * (2.0F + r.nextFloat() * 2.5F);
            float vy = 2.0F + r.nextFloat() * 2.5F;
            float spin = (6.0F + r.nextFloat() * 10.0F) * (r.nextBoolean() ? 1.0F : -1.0F);
            float yaw = r.nextFloat() * Mth.TWO_PI;
            int face = r.nextInt(FACES);
            float px;
            float py;
            float pz;
            float pitch;
            float roll;
            boolean flat = false;
            if (t < KILL_GATHER) {
                float k = t / KILL_GATHER;
                float e = k * k;
                float rad = startR * (1.0F - e) + 0.15F;
                px = Mth.cos(angle) * rad;
                pz = Mth.sin(angle) * rad;
                py = Mth.lerp(e, startY, cy);
                pitch = 0.0F;
                roll = k * 4.0F;
                yaw = -angle + Mth.HALF_PI;
            } else {
                float tt = t - KILL_GATHER;
                float land = (vy + (float)Math.sqrt(vy * vy + 2.0F * GRAVITY * cy)) / GRAVITY;
                float ft = Math.min(tt, land);
                px = Mth.cos(angle) * 0.15F + vx * ft;
                pz = Mth.sin(angle) * 0.15F + vz * ft;
                py = Math.max(0.02F + i * 0.001F, cy + vy * ft - 0.5F * GRAVITY * ft * ft);
                pitch = spin * ft;
                roll = spin * 0.6F * ft;
                flat = tt >= land;
            }

            float fade = Mth.clamp((b.life - t) / 0.4F, 0.0F, 1.0F);
            stack.pushPose();
            stack.translate(px, py, pz);
            stack.mulPose(Axis.YP.rotation(yaw));
            if (flat) {
                stack.mulPose(Axis.XP.rotation(Mth.HALF_PI));
            } else {
                stack.mulPose(Axis.XP.rotation(pitch));
                stack.mulPose(Axis.ZP.rotation(roll));
            }

            card(stack.last(), out, face, 1.1F * fade, tint(i / (float)b.count, true), light);
            stack.popPose();
        }
    }

    private static void groundGlow(Burst b, float alpha, PoseStack.Pose pose, VertexConsumer out) {
        if (alpha <= 0.01F) {
            return;
        }

        float rad = ModSettings.targetCardsRadius * 1.7F;
        int c = tint(0.5F, false);
        int color = Math.round(alpha * 200.0F) << 24 | c & 0xFFFFFF;
        float u0 = (float)(GLOW * CELL_W) / (CELLS * CELL_W);
        float u1 = (float)((GLOW + 1) * CELL_W) / (CELLS * CELL_W);
        vertex(out, pose, -rad, 0.03F, -rad, color, u0, 0.0F, 15728880);
        vertex(out, pose, -rad, 0.03F, rad, color, u0, 1.0F, 15728880);
        vertex(out, pose, rad, 0.03F, rad, color, u1, 1.0F, 15728880);
        vertex(out, pose, rad, 0.03F, -rad, color, u1, 0.0F, 15728880);
    }

    private static int tint(float t, boolean kill) {
        if (kill) {
            return switch (ModSettings.killEffectColor) {
                case 1 -> -1;
                case 2 -> 0xFF000000 | ModSettings.killEffectCustomColor;
                case 3 -> 0xFF000000 | Colors.lerp(ModSettings.killEffectCustomColor, ModSettings.killEffectCustomColor2, t);
                default -> ClientAccent.gradientColor(t, 255.0F);
            };
        }

        return switch (ModSettings.targetCardsColorMode) {
            case 1 -> 0xFF000000 | Color.HSBtoRGB((System.currentTimeMillis() % 4000L) / 4000.0F + t * 0.5F, ModSettings.rainbowSaturation, 1.0F);
            case 2 -> 0xFF000000 | ModSettings.targetCardsColor;
            case 3 -> 0xFF000000 | Colors.lerp(ModSettings.targetCardsColor, ModSettings.targetCardsColor2, t);
            default -> ClientAccent.gradientColor(t, 255.0F);
        };
    }

    private static void card(PoseStack.Pose pose, VertexConsumer out, int face, float size, int back, int light) {
        if (size <= 0.01F) {
            return;
        }

        float w = CARD_W * size / 2.0F;
        float h = CARD_H * size / 2.0F;
        float fu0 = (float)(face * CELL_W + 2) / (CELLS * CELL_W);
        float fu1 = (float)(face * CELL_W + 22) / (CELLS * CELL_W);
        float bu0 = (float)(BACK * CELL_W + 2) / (CELLS * CELL_W);
        float bu1 = (float)(BACK * CELL_W + 22) / (CELLS * CELL_W);
        float v0 = 2.0F / CELL_H;
        float v1 = 30.0F / CELL_H;
        vertex(out, pose, -w, h, 0.004F, -1, fu0, v0, light, 0.0F, 0.0F, 1.0F);
        vertex(out, pose, -w, -h, 0.004F, -1, fu0, v1, light, 0.0F, 0.0F, 1.0F);
        vertex(out, pose, w, -h, 0.004F, -1, fu1, v1, light, 0.0F, 0.0F, 1.0F);
        vertex(out, pose, w, h, 0.004F, -1, fu1, v0, light, 0.0F, 0.0F, 1.0F);
        vertex(out, pose, w, h, -0.004F, back, bu0, v0, light, 0.0F, 0.0F, -1.0F);
        vertex(out, pose, w, -h, -0.004F, back, bu0, v1, light, 0.0F, 0.0F, -1.0F);
        vertex(out, pose, -w, -h, -0.004F, back, bu1, v1, light, 0.0F, 0.0F, -1.0F);
        vertex(out, pose, -w, h, -0.004F, back, bu1, v0, light, 0.0F, 0.0F, -1.0F);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, int light) {
        vertex(out, pose, x, y, z, color, u, v, light, 0.0F, 1.0F, 0.0F);
    }

    static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, int color, float u, float v, int light, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static NativeImage paintAtlas() {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, CELLS * CELL_W, CELL_H, false);
        image.fillRect(0, 0, CELLS * CELL_W, CELL_H, 0);
        for (int f = 0; f < FACES; f++) {
            int ox = f * CELL_W;
            int rank = DECK[f][0];
            int suit = DECK[f][1];
            int ink = suit == 1 || suit == 2 ? 0xFFD81E2A : 0xFF1A1A1A;
            cardShape(image, ox, 0xFFFFFFFF, 0xFFB8BEC8);
            glyph(image, RANKS[rank], ox + 4, 4, ink, false);
            glyph(image, SMALL_SUITS[suit], ox + 3, 10, ink, false);
            glyph(image, BIG_SUITS[suit], ox + 8, 12, ink, false);
            glyph(image, RANKS[rank], ox + 17, 23, ink, true);
        }

        int bx = BACK * CELL_W;
        cardShape(image, bx, 0xFFC8C8C8, 0xFFFFFFFF);
        for (int y = 4; y < 28; y++) {
            for (int x = 4; x < 20; x++) {
                if ((x + y) % 4 == 0 || (x - y + 64) % 4 == 0) {
                    image.setPixel(bx + x, y, 0xFFFFFFFF);
                }
            }
        }

        int gx = GLOW * CELL_W;
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                float dx = (x + 0.5F) / CELL_W * 2.0F - 1.0F;
                float dy = (y + 0.5F) / CELL_H * 2.0F - 1.0F;
                float a = Math.clamp(1.0F - (float)Math.sqrt(dx * dx + dy * dy), 0.0F, 1.0F);
                image.setPixel(gx + x, y, Math.round(a * a * 255.0F) << 24 | 0xFFFFFF);
            }
        }

        image.fillRect(WHITE * CELL_W, 0, CELL_W, CELL_H, -1);
        return image;
    }

    private static void cardShape(NativeImage image, int ox, int fill, int border) {
        for (int y = 2; y < 30; y++) {
            for (int x = 2; x < 22; x++) {
                boolean corner = (x == 2 || x == 21) && (y == 2 || y == 29);
                if (corner) {
                    continue;
                }

                boolean edge = x == 2 || x == 21 || y == 2 || y == 29;
                image.setPixel(ox + x, y, edge ? border : fill);
            }
        }
    }

    private static void glyph(NativeImage image, String[] rows, int x, int y, int color, boolean flip) {
        int h = rows.length;
        int w = rows[0].length();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                char ch = flip ? rows[h - 1 - r].charAt(w - 1 - c) : rows[r].charAt(c);
                if (ch == '#') {
                    image.setPixel(x + c, y + r, color);
                }
            }
        }
    }
}
