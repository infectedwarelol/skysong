package dev.infectedware.skysong.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.compat.WorldCtx;
import dev.infectedware.skysong.lyrics.LyricsManager;
import dev.infectedware.skysong.render.fx.StormFx;
import dev.infectedware.skysong.theme.ClientAccent;
import dev.infectedware.skysong.theme.Colors;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class WorldTextRenderer {
    private static final Random RNG = new Random();
    private static final Identifier UNIFORM_FONT = Ids.mc("uniform");
    private static final Identifier LYRIC_FONT = Ids.of("skysong", "lyrics");
    private static final Identifier LYRIC_FONT_BOLD = Ids.of("skysong", "lyrics_bold");
    private static final Identifier AQUIRE_FONT = Ids.of("skysong", "aquire");
    private static final Identifier AQUIRE_FONT_BOLD = Ids.of("skysong", "aquire_bold");
    private static final Identifier MONTSERRAT_FONT = Ids.of("skysong", "lyrics_montserrat");
    private static final Identifier MONTSERRAT_FONT_BOLD = Ids.of("skysong", "lyrics_montserrat_bold");
    private static final Identifier GALACTIC_FONT = Ids.mc("alt");
    private static final Identifier RUNIC_FONT = Ids.mc("illageralt");
    private static final double RAINBOW_CYCLE_MS = 6000.0;
    private static final float HIGHLIGHT_FADE_MS = 600.0F;
    private static final int FULL_BRIGHT = 15728880;
    private static final int APPEAR_MS = 280;
    private static final int FADE_MS = 550;
    private static final int KARAOKE_MS = 250;
    private static final double MAX_SYLLABLE_MS = 260.0;
    private static final long WORD_LEAD_MS = 80L;
    private static final int OUTLINE_COLOR = 461332;
    private static final int GLITCH_BUCKET_MS = 70;
    private final LyricLayers.Batch glowBuffers = new LyricLayers.Batch();
    private final LyricLayers.Batch outlineBuffers = new LyricLayers.Batch();
    private final LyricLayers.Batch mainBuffers = new LyricLayers.Batch();
    private static final int MAX_PIECES = 32;
    private static final float WIDE_STRETCH = 1.3F;
    private static final float SCALE_PER_SIZE = 0.28F;
    private static final int WORDS_ON_SCREEN = 4;
    private static final int MIN_WORD_LIFETIME_MS = 1200;
    private static final int MAX_WORD_LIFETIME_MS = 2400;
    private static final double MIN_GAP_DEGREES = 4.0;
    private static final int TEXT_COLOR = 16777215;
    private static final int KARAOKE_IDLE_COLOR = 14080742;
    private static final int GLOW_COLOR = 14543103;
    private static final float[][] GLOW_OFFSETS = buildGlowOffsets();
    private static final float[][] RING = ring(8, 1.0F);
    private final List<WorldTextRenderer.Piece> pieces = new ArrayList<>();
    private final List<WorldTextRenderer.Pending> pending = new ArrayList<>();
    public static volatile double lastPlacedDistance = -1.0;
    public static volatile double lastPlacedAngle = -1.0;
    public static volatile long lastPlacedAt;
    private static Vec3 followPos = Vec3.ZERO;
    private static long lastFollowNanos;
    private long currentLineKey = Long.MIN_VALUE;
    private CameraType lastPerspective;
    private int lastLayoutKey = -1;
    private float framePulse;
    private static final float TRANSLATION_SCALE = 0.6F;
    private static final float TRANSLATION_GAP = 1.25F;
    private static final int TRANSLATION_TINT = 12107984;
    private float frameDaylight = 1.0F;
    private static final double SKY_ARC_LIFT_DEGREES = 7.0;
    private static final float SKY_DRIFT_MS = 2600.0F;
    private static final double SKY_WIND_SPEED = 0.35;
    private static final double SKY_RISE_SPEED = 0.25;
    private static final float SKY_NIGHT_GLOW = 1.5F;
    private static final float STAR_SCALE = 0.28F;
    private static final float STAR_HALO_SCALE = 2.4F;
    private static final Component STAR = Component.literal("\u2726");
    private static final float TYPE_STEP_MS = 40.0F;
    private static final float TYPE_FADE_MS = 90.0F;
    private static final float FALL_STEP_MS = 30.0F;
    private static final float FALL_MS = 320.0F;
    private static final float FALL_HEIGHT = 12.0F;
    private static final long SHAKE_BUCKET_MS = 45L;
    private static final float SHAKE_AMOUNT = 0.9F;
    private static final int PARTICLES_PER_WORD = 5;
    private static final float PULSE_DECAY_MS = 200.0F;

    private static Vec3 worldPos(WorldTextRenderer.Piece p) {
        return !ModSettings.lyricFollowPlayer ? p.pos : p.pos.add(followPos.subtract(p.anchor));
    }

    private static void updateFollow(Minecraft mc, float tickDelta) {
        Vec3 target = mc.player.getPosition(tickDelta);
        long nowNs = System.nanoTime();
        float dt = lastFollowNanos == 0L ? 1.0F : Math.min(0.25F, (float)(nowNs - lastFollowNanos) / 1.0E9F);
        lastFollowNanos = nowNs;
        if (followPos != Vec3.ZERO && !(followPos.distanceToSqr(target) > 1024.0)) {
            double k = 1.0 - Math.exp(-dt * 9.0);
            followPos = followPos.add(target.subtract(followPos).scale(k));
        } else {
            followPos = target;
        }
    }

    public void setCurrentLine(LyricsManager.LyricLine line, long progressMs) {
        long key = line == null ? Long.MIN_VALUE : line.timeMs;
        Minecraft mc = Minecraft.getInstance();
        CameraType perspective = mc.options.getCameraType();
        int layoutKey = ModSettings.lyricCustomFont.hashCode() * 31
            ^ ModSettings.lyricShowMode | ModSettings.lyricLayout << 2 | ModSettings.lyricFont << 4 | (ModSettings.lyricUppercase ? 256 : 0)
            | ModSettings.lyricSkyStyle << 9
            | (ModSettings.lyricSkyArc ? 1024 : 0);
        if (key != this.currentLineKey || perspective != this.lastPerspective || layoutKey != this.lastLayoutKey) {
            this.currentLineKey = key;
            this.lastPerspective = perspective;
            this.lastLayoutKey = layoutKey;
            long now = System.currentTimeMillis();
            synchronized (this.pieces) {
                for (WorldTextRenderer.Piece p : this.pieces) {
                    p.fadeAt = Math.min(p.fadeAt, now);
                }

                this.pending.clear();
                if (line != null && mc.player != null && ModSettings.enable3DLyrics) {
                    if (followPos == Vec3.ZERO || followPos.distanceToSqr(mc.player.position()) > 1024.0) {
                        followPos = mc.player.position();
                    }

                    String text = ModSettings.lyricUppercase ? line.text.toUpperCase(Locale.ROOT) : line.text;
                    String[] words = text.trim().split("\\s+");
                    boolean[] emphasized = emphasized(line.text.trim().split("\\s+"), words.length);
                    if (words.length != 0 && !words[0].isEmpty()) {
                        long lineStart = now - (progressMs - line.timeMs);
                        long lineLength = line.endMs - line.timeMs;
                        long[] sungAt = ModSettings.lyricRealTiming && line.wordTimes != null && line.wordTimes.length == words.length
                            ? realWordTimes(line.wordTimes, now, progressMs)
                            : wordTimes(words, lineStart, lineLength);
                        Camera camera = mc.gameRenderer.getMainCamera();
                        Vec3 eye = Compat.camPos(camera);
                        float yaw = Compat.camYaw(camera);
                        float pitch = Compat.camPitch(camera);
                        boolean scatter = ModSettings.lyricLayout == ModSettings.LAYOUT_SCATTER;
                        boolean sky = ModSettings.lyricLayout == ModSettings.LAYOUT_SKY;
                        Vec3 anchor = null;
                        if (ModSettings.lyricShowMode == 1) {
                            Vec3 pos = sky
                                ? skyPos(eye, yaw)
                                : inFront(
                                    eye,
                                    scatter ? this.scatterPos(eye, yaw, pitch, words, sungAt[0], line.endMs - progressMs) : this.centerPos(eye, yaw, pitch)
                                );
                            this.addPiece(words, sungAt, pos, eye, yaw, scatter, 1.0F, 0.5F, sky).emphasized = emphasized;
                            anchor = pos;
                        } else if (scatter) {
                            long wordGap = words.length > 1 ? (sungAt[words.length - 1] - sungAt[0]) / (words.length - 1) : 300L;
                            long lifetime = Math.clamp(4L * wordGap, 1200L, 2400L);

                            for (int i = 0; i < words.length; i++) {
                                if (sungAt[i] + lifetime >= now) {
                                    this.pending
                                        .add(new WorldTextRenderer.Pending(words[i], sungAt[i], sungAt[i] + lifetime, i, colorT(i, words.length), emphasized[i]));
                                }
                            }
                        } else {
                            Font tr = mc.font;
                            float unit = worldScale() * stretch() * (sky ? skySizeMultiplier() : 1.0F);
                            float space = tr.width(styled(" ", false));
                            float total = -space;

                            for (String w : words) {
                                total += tr.width(styled(w, false)) + space;
                            }

                            Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
                            Vec3 center = sky ? skyPos(eye, yaw) : inFront(eye, this.centerPos(eye, yaw, pitch));
                            anchor = center;
                            boolean arc = sky && ModSettings.lyricSkyArc;
                            float rowFit = (float)Math.min(
                                1.0, 2.0 * center.distanceTo(eye) * Math.tan(Math.toRadians(horizontalFov() * (arc ? 0.55 : 0.38))) / Math.max(total * unit, 0.001)
                            );
                            unit *= rowFit;
                            float cursor = -total / 2.0F;

                            for (int i = 0; i < words.length; i++) {
                                float w = tr.width(styled(words[i], false));
                                Vec3 pos = arc
                                    ? arcPos(eye, yaw, (cursor + w / 2.0F) * unit, total * unit / 2.0F)
                                    : center.add(right.scale((cursor + w / 2.0F) * unit));
                                this.addPiece(new String[]{words[i]}, new long[]{sungAt[i]}, pos, eye, yaw, false, rowFit, colorT(i, words.length), sky).emphasized = new boolean[]{
                                    emphasized[i]
                                };
                                cursor += w + space;
                            }
                        }

                        this.addTranslation(line, anchor, eye, yaw, pitch, sky, sungAt[0]);

                        while (this.pieces.size() > 32) {
                            this.pieces.remove(0);
                        }
                    }
                }
            }
        }
    }

    private void addTranslation(LyricsManager.LyricLine line, Vec3 anchor, Vec3 eye, float yaw, float pitch, boolean sky, long showAt) {
        String translation = line.translation;
        if (!ModSettings.lyricTranslate || translation == null || translation.isBlank()) {
            return;
        }

        String text = ModSettings.lyricUppercase ? translation.toUpperCase(Locale.ROOT) : translation;
        Vec3 base = anchor != null ? anchor : inFront(eye, this.centerPos(eye, yaw, pitch));
        double gap = 9.0F * worldScale() * (sky ? skySizeMultiplier() : 1.0F) * TRANSLATION_GAP;
        WorldTextRenderer.Piece piece = this.addPiece(new String[]{text}, new long[]{showAt}, base.add(0.0, -gap, 0.0), eye, yaw, false, 1.0F, 0.5F, sky);
        piece.translation = true;
        piece.textScale = TRANSLATION_SCALE;
    }

    static boolean[] emphasized(String[] originalWords, int count) {
        boolean[] out = new boolean[count];

        for (int i = 0; i < count && i < originalWords.length; i++) {
            out[i] = isLoud(originalWords[i]);
        }

        return out;
    }

    static boolean isLoud(String word) {
        if (word.endsWith("!")) {
            return true;
        }

        int letters = 0;

        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                if (!Character.isUpperCase(c)) {
                    return false;
                }

                letters++;
            }
        }

        return letters >= 2;
    }

    static long[] realWordTimes(long[] songTimes, long now, long progressMs) {
        long[] at = new long[songTimes.length];

        for (int i = 0; i < songTimes.length; i++) {
            at[i] = now - (progressMs - songTimes[i]) - WORD_LEAD_MS;
        }

        return at;
    }

    public static long[] wordTimes(String[] words, long lineStart, long lineLength) {
        int[] syllables = new int[words.length];
        int total = 0;

        for (int i = 0; i < words.length; i++) {
            syllables[i] = syllables(words[i]);
            total += syllables[i];
        }

        double perSyllable = Math.min(lineLength * 0.85 / Math.max(1, total), 260.0);
        long[] at = new long[words.length];
        int before = 0;

        for (int i = 0; i < words.length; i++) {
            at[i] = lineStart - 80L + Math.round(before * perSyllable);
            before += syllables[i];
        }

        return at;
    }

    static int syllables(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        int groups = 0;
        int letters = 0;
        boolean prevVowel = false;

        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
            }

            boolean vowel = "aeiouyàáâäãåèéêëìíîïòóôöõùúûüæœ".indexOf(c) >= 0;
            if (vowel && !prevVowel) {
                groups++;
            }

            prevVowel = vowel;
        }

        if (groups > 1 && w.endsWith("e") && !w.endsWith("le")) {
            groups--;
        }

        if (groups == 0) {
            groups = Math.max(1, letters / 3);
        }

        return Math.min(groups, 5);
    }

    private static float colorT(int index, int count) {
        return count > 1 ? (float)index / (count - 1) : 0.5F;
    }

    private static Vec3 skyPos(Vec3 eye, float yaw) {
        return eye.add(Vec3.directionFromRotation(-ModSettings.lyricSkyAngle, yaw).scale(ModSettings.lyricSkyDistance));
    }

    private static Vec3 arcPos(Vec3 eye, float yaw, double offset, double halfSpan) {
        double distance = ModSettings.lyricSkyDistance;
        double normalized = halfSpan > 0.0 ? Mth.clamp(offset / halfSpan, -1.0, 1.0) : 0.0;
        float yawOffset = (float)Math.toDegrees(offset / distance);
        float lift = (float)(SKY_ARC_LIFT_DEGREES * (1.0 - normalized * normalized));
        return eye.add(Vec3.directionFromRotation(-(ModSettings.lyricSkyAngle + lift), yaw + yawOffset).scale(distance));
    }

    private static boolean constellation() {
        return ModSettings.lyricLayout == ModSettings.LAYOUT_SKY && ModSettings.lyricSkyStyle == ModSettings.LYRIC_SKY_CONSTELLATION;
    }

    private static float fadeDuration(WorldTextRenderer.Piece p) {
        return p.sky && ModSettings.lyricSkyDrift ? SKY_DRIFT_MS : 550.0F;
    }

    private static Vec3 skyDrift(WorldTextRenderer.Piece p, long now) {
        if (!p.sky || !ModSettings.lyricSkyDrift || now <= p.fadeAt) {
            return Vec3.ZERO;
        }

        double seconds = (now - p.fadeAt) / 1000.0;
        Vec3 wind = Vec3.directionFromRotation(0.0F, p.yaw + 90.0F).scale(SKY_WIND_SPEED * seconds * p.sizeMultiplier);
        return wind.add(0.0, SKY_RISE_SPEED * seconds * p.sizeMultiplier, 0.0);
    }

    private static float skySizeMultiplier() {
        float groundDistance = (ModSettings.lyricSpawnDistMin + ModSettings.lyricSpawnDistMax) / 2.0F;
        return ModSettings.lyricSkyDistance / Math.max(1.0F, groundDistance) * ModSettings.lyricSkyScale;
    }

    private WorldTextRenderer.Piece addPiece(
        String[] words, long[] sungAt, Vec3 pos, Vec3 eye, float playerYaw, boolean tilted, float maxFit, float colorT, boolean sky
    ) {
        float sizeMultiplier = sky ? skySizeMultiplier() : 1.0F;
        double dist = pos.distanceTo(eye);
        double width = textWidth(words) * worldScale() * stretch() * sizeMultiplier;
        double halfFov = Math.toRadians(horizontalFov() * 0.38);
        float fit = (float)Math.min(maxFit, 2.0 * dist * Math.tan(halfFov) / Math.max(width, 0.001));
        double halfWidth = Math.toDegrees(Math.atan(width * fit / (2.0 * dist))) + 2.0;
        double halfHeight = Math.toDegrees(Math.atan(9.0F * worldScale() * sizeMultiplier * fit / (2.0 * dist))) + 2.0;
        float yaw;
        float pitch;
        float roll;
        if (sky) {
            Vec3 d = pos.subtract(eye);
            yaw = (float)Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
            pitch = (float)(-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
            roll = 0.0F;
        } else if (tilted) {
            float amount = words.length > 1 ? 0.4F : 1.0F;
            Vec3 d = pos.subtract(eye);
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            yaw = (float)Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F + (RNG.nextFloat() - 0.5F) * 50.0F * amount;
            pitch = (float)(-Math.toDegrees(Math.atan2(d.y, horiz)));
            roll = (RNG.nextFloat() - 0.5F) * 16.0F * amount;
        } else {
            yaw = playerYaw;
            pitch = 0.0F;
            roll = 0.0F;
        }

        WorldTextRenderer.Piece piece = new WorldTextRenderer.Piece(
            words, sungAt, pos, yaw, pitch, roll, fit, halfWidth, halfHeight, colorT, sky, sizeMultiplier
        );
        this.pieces.add(piece);
        return piece;
    }

    private void placePending(Minecraft mc, WorldCtx context, long now) {
        if (!this.pending.isEmpty()) {
            Camera camera = context.camera();
            Vec3 eye = Compat.camPos(camera);
            Iterator<WorldTextRenderer.Pending> it = this.pending.iterator();

            while (it.hasNext()) {
                WorldTextRenderer.Pending w = it.next();
                if (now >= w.sungAt() - 60L) {
                    it.remove();
                    if (now <= w.fadeAt()) {
                        long lifetime = w.fadeAt() - w.sungAt();
                        Vec3 pos = frontCamera()
                            ? this.scatterPos(eye, Compat.camYaw(camera), Compat.camPitch(camera), new String[]{w.word()}, w.sungAt(), lifetime)
                            : this.crosshairScatter(eye, Compat.camYaw(camera), Compat.camPitch(camera), w.word(), w.index(), w.sungAt(), lifetime);
                        Vec3 placed = inFront(eye, pos);
                        float keepSize = (float)Math.min(1.0, placed.distanceTo(eye) / Math.max(0.01, pos.distanceTo(eye)) * 1.1);
                        WorldTextRenderer.Piece p = this.addPiece(
                            new String[]{w.word()}, new long[]{w.sungAt()}, placed, eye, Compat.camYaw(camera), true, keepSize, w.colorT(), false
                        );
                        p.fadeAt = w.fadeAt();
                        p.emphasized = new boolean[]{w.emphasized()};
                        Vec3 look = Vec3.directionFromRotation(Compat.camPitch(camera), Compat.camYaw(camera));
                        lastPlacedDistance = placed.distanceTo(eye);
                        lastPlacedAngle = Math.toDegrees(Math.acos(Mth.clamp(placed.subtract(eye).normalize().dot(look), -1.0, 1.0)));
                        lastPlacedAt = now;
                    }
                }
            }

            while (this.pieces.size() > 32) {
                this.pieces.remove(0);
            }
        }
    }

    private Vec3 crosshairScatter(Vec3 eye, float yaw, float pitch, String word, int index, long showAt, long lifetime) {
        double fovX = horizontalFov();
        double fovY = verticalFov();
        double spreadX = Math.min(fovX * 0.21, 18.0);
        double spreadY = Math.min(fovY * 0.17, 10.0);
        int slot = index % 4;
        double preferX = -spreadX + slot * (2.0 * spreadX / 3.0);
        double preferY = (index % 2 == 0 ? 0.5 : -0.3) * spreadY;
        float min = ModSettings.lyricSpawnDistMin;
        float max = Math.max(min + 0.5F, ModSettings.lyricSpawnDistMax);
        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int attempt = 0; attempt < 28; attempt++) {
            double reach = 3.0 + attempt * 0.6;
            double dx = preferX + RNG.nextGaussian() * reach;
            double dy = preferY + RNG.nextGaussian() * reach * 0.6;
            dx = Mth.clamp(dx, -spreadX * 1.25, spreadX * 1.25);
            dy = Mth.clamp(dy, -spreadY * 1.25, spreadY * 1.25);
            double dist = min + RNG.nextDouble() * (max - min);
            double width = textWidth(new String[]{word}) * worldScale() * stretch();
            double fit = Math.min(1.0, 2.0 * dist * Math.tan(Math.toRadians(fovX * 0.38)) / Math.max(width, 0.001));
            double halfW = Math.toDegrees(Math.atan(width * fit / (2.0 * dist))) + 2.0;
            double halfH = Math.toDegrees(Math.atan(9.0F * worldScale() * fit / (2.0 * dist))) + 2.0;
            Vec3 candidate = aboveFloor(eye.add(Vec3.directionFromRotation(pitch + (float)dy, yaw + (float)dx).scale(dist)));
            double gap = this.nearestGap(eye, candidate, halfW, halfH, showAt, showAt + lifetime);
            double score = Math.min(gap, 6.0) * 10.0 - Math.hypot(dx - preferX, dy - preferY) * 0.3;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }

        return best;
    }

    private static Vec3 inFront(Vec3 eye, Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && pos != null) {
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, pos, Block.COLLIDER, Fluid.NONE, mc.player));
            if (hit.getType() == Type.MISS) {
                return pos;
            }

            double full = pos.distanceTo(eye);
            double d = Math.max(1.3, hit.getLocation().distanceTo(eye) - 0.8);
            return d >= full ? pos : eye.add(pos.subtract(eye).scale(d / full));
        } else {
            return pos;
        }
    }

    private static double floorBelow(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        MutableBlockPos b = new MutableBlockPos();
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);

        for (int y = Mth.floor(at.y); y > Mth.floor(at.y) - 16; y--) {
            b.set(x, y, z);
            FluidState fluid = mc.level.getFluidState(b);
            if (!fluid.isEmpty()) {
                return y + fluid.getHeight(mc.level, b);
            }

            VoxelShape shape = mc.level.getBlockState(b).getCollisionShape(mc.level, b);
            if (!shape.isEmpty()) {
                return y + shape.max(Axis.Y);
            }
        }

        return at.y - 2.0;
    }

    private Vec3 scatterPos(Vec3 eye, float yaw, float pitch, String[] words, long showAt, long lifetime) {
        float min = ModSettings.lyricSpawnDistMin;
        float max = Math.max(min + 0.5F, ModSettings.lyricSpawnDistMax);
        Vec3 best = null;
        double bestGap = Double.NEGATIVE_INFINITY;
        double fovX = horizontalFov();
        double fovY = verticalFov();

        for (int attempt = 0; attempt < 32; attempt++) {
            double dist = screenDist(min + RNG.nextDouble() * (max - min));
            double width = textWidth(words) * worldScale() * stretch();
            double fit = Math.min(1.0, 2.0 * dist * Math.tan(Math.toRadians(fovX * 0.38)) / Math.max(width, 0.001));
            double halfW = Math.toDegrees(Math.atan(width * fit / (2.0 * dist))) + 2.0;
            double halfH = Math.toDegrees(Math.atan(9.0F * worldScale() * fit / (2.0 * dist))) + 2.0;
            float dx = (float)((RNG.nextDouble() * 2.0 - 1.0) * Math.max(0.0, fovX * 0.42 - halfW));
            float dy = (float)((RNG.nextDouble() * 2.0 - 1.0) * Math.max(0.0, fovY * 0.38 - halfH));
            if (frontCamera()) {
                float edge = (float)Math.max(12.0, fovY * 0.38 - halfH);
                dy = (RNG.nextBoolean() ? 1 : -1) * (12.0F + RNG.nextFloat() * Math.max(0.0F, edge - 12.0F));
            }

            Vec3 candidate = eye.add(Vec3.directionFromRotation(pitch + dy, yaw + dx).scale(dist));
            candidate = aboveFloor(candidate);
            double gap = this.nearestGap(eye, candidate, halfW, halfH, showAt, showAt + lifetime);
            if (gap > bestGap) {
                best = candidate;
                bestGap = gap;
            }

            if (gap > 4.0) {
                break;
            }
        }

        return best;
    }

    private double nearestGap(Vec3 eye, Vec3 pos, double halfW, double halfH, long start, long end) {
        Vec3 dir = pos.subtract(eye).normalize();
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        double pitch = Math.toDegrees(Math.atan2(-dir.y, Math.hypot(dir.x, dir.z)));
        double nearest = 180.0;

        for (WorldTextRenderer.Piece p : this.pieces) {
            boolean overlapsInTime = p.sungAt[0] < end + 550L && (p.fadeAt == Long.MAX_VALUE || start < p.fadeAt + 550L);
            if (overlapsInTime) {
                Vec3 other = worldPos(p).subtract(eye).normalize();
                double otherYaw = Math.toDegrees(Math.atan2(-other.x, other.z));
                double otherPitch = Math.toDegrees(Math.atan2(-other.y, Math.hypot(other.x, other.z)));
                double dx = Math.abs(Mth.wrapDegrees(yaw - otherYaw)) - halfW - p.halfWidth;
                double dy = Math.abs(pitch - otherPitch) - halfH - p.halfHeight;
                nearest = Math.min(nearest, Math.max(dx, dy));
            }
        }

        return nearest;
    }

    private Vec3 centerPos(Vec3 eye, float yaw, float pitch) {
        float dist = (ModSettings.lyricSpawnDistMin + ModSettings.lyricSpawnDistMax) / 2.0F;
        if (frontCamera()) {
            pitch -= (float)Math.min(16.0, verticalFov() * 0.28);
        } else {
            pitch = Mth.clamp(pitch, -12.0F, 5.0F);
        }

        Vec3 pos = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(screenDist(dist)));
        if (!frontCamera()) {
            pos = pos.add(0.0, 0.8, 0.0);
        }

        return aboveFloor(pos);
    }

    private static Vec3 aboveFloor(Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        double floor = mc.player.getY() + 1.35;
        double y = Math.max(pos.y, floor);

        for (int i = 0; i < 12; i++) {
            BlockPos block = BlockPos.containing(pos.x, y, pos.z);
            if (mc.level.getBlockState(block).getCollisionShape(mc.level, block).isEmpty()) {
                break;
            }

            y = block.getY() + 1.35;
        }

        return new Vec3(pos.x, y, pos.z);
    }

    private static double screenDist(double configured) {
        return frontCamera() ? Math.min(configured, 2.5) : configured;
    }

    private static boolean frontCamera() {
        return Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
    }

    private static int textWidth(String[] words) {
        Font tr = Minecraft.getInstance().font;
        int width = 0;

        for (String word : words) {
            width += tr.width(styled(word, false)) + tr.width(" ");
        }

        return width;
    }

    private static double verticalFov() {
        return Math.min(60, Minecraft.getInstance().options.fov().get());
    }

    private static double horizontalFov() {
        Window window = Minecraft.getInstance().getWindow();
        double aspect = AspectRatio.aspect((float)window.getWidth() / window.getHeight());
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(verticalFov() / 2.0)) * aspect));
    }

    public void afterEntities(WorldCtx context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            long now = System.currentTimeMillis();
            Vec3 eye = mc.player.getEyePosition();
            synchronized (this.pieces) {
                updateFollow(mc, context.partialTick());
                this.placePending(mc, context, now);
                this.pieces.removeIf(px -> now - px.fadeAt > fadeDuration(px) || worldPos(px).distanceToSqr(eye) > 4096.0);
                if (!ModSettings.enable3DLyrics || this.pieces.isEmpty()) {
                    return;
                }

                this.framePulse = this.pulse(now);
                boolean walls = ModSettings.lyricThroughWalls;
                boolean smooth = smoothFont();
                float daylight = 1.0F - Mth.clamp(mc.level.getSkyDarken() / 11.0F, 0.0F, 1.0F);
                this.frameDaylight = daylight;
                WorldTextRenderer.Passes passes = new WorldTextRenderer.Passes(
                    LyricLayers.remap(this.glowBuffers, LyricLayers.Pass.GLOW, walls, smooth),
                    LyricLayers.remap(this.outlineBuffers, LyricLayers.Pass.OUTLINE, walls, smooth),
                    LyricLayers.remap(this.mainBuffers, LyricLayers.Pass.MAIN, walls, smooth),
                    ModSettings.lyricOutline * (0.4F + 0.6F * daylight)
                );

                for (WorldTextRenderer.Piece p : this.pieces) {
                    this.renderPiece(context, mc.font, p, now, passes);
                }
            }

            StormFx.beforeLyrics();
            Gpu.bind(mc.getMainRenderTarget());
            this.glowBuffers.endBatch();
            this.outlineBuffers.endBatch();
            this.mainBuffers.endBatch();
            this.renderMirror(context, mc, now);
        }
    }

    private float pulse(long now) {
        float amount = ModSettings.lyricPulseAmount;
        if (amount <= 0.0F) {
            return 0.0F;
        }

        if (ModSettings.lyricPulseMode == ModSettings.LYRIC_PULSE_WORDS) {
            long lastOnset = Long.MIN_VALUE;

            for (WorldTextRenderer.Piece p : this.pieces) {
                for (long sung : p.sungAt) {
                    if (sung <= now && sung > lastOnset) {
                        lastOnset = sung;
                    }
                }
            }

            return lastOnset == Long.MIN_VALUE ? 0.0F : amount * (float)Math.exp(-(now - lastOnset) / PULSE_DECAY_MS);
        }

        if (ModSettings.lyricPulseMode == ModSettings.LYRIC_PULSE_TEMPO) {
            SpotifyManager spotify = SkysongClient.getSpotifyManager();
            if (spotify == null || !spotify.isPlaying()) {
                return 0.0F;
            }

            double beatMs = 60000.0 / Math.max(1.0F, ModSettings.lyricBpm);
            long progress = spotify.getSmoothedProgressMs() + ModSettings.lyricOffsetMs;
            double sinceBeat = Math.floorMod(progress, Math.max(1L, Math.round(beatMs)));
            return amount * (float)Math.exp(-sinceBeat / PULSE_DECAY_MS);
        }

        return 0.0F;
    }

    private void renderMirror(WorldCtx context, Minecraft mc, long now) {
        if (StormFx.beginMirror()) {
            try {
                boolean smooth = smoothFont();
                WorldTextRenderer.Passes passes = new WorldTextRenderer.Passes(
                    LyricLayers.remap(this.glowBuffers, LyricLayers.Pass.GLOW, true, smooth),
                    LyricLayers.remap(this.outlineBuffers, LyricLayers.Pass.OUTLINE, true, smooth),
                    LyricLayers.remap(this.mainBuffers, LyricLayers.Pass.MAIN, true, smooth),
                    ModSettings.lyricOutline
                );
                synchronized (this.pieces) {
                    for (WorldTextRenderer.Piece p : this.pieces) {
                        if (!p.sky) {
                            this.renderPiece(context, mc.font, p, now, passes, true);
                        }
                    }
                }

                this.glowBuffers.endBatch();
                this.outlineBuffers.endBatch();
                this.mainBuffers.endBatch();
            } finally {
                StormFx.endMirror();
            }
        }
    }

    private void renderPiece(WorldCtx context, Font tr, WorldTextRenderer.Piece p, long now, WorldTextRenderer.Passes passes) {
        this.renderPiece(context, tr, p, now, passes, false);
    }

    private void renderPiece(WorldCtx context, Font tr, WorldTextRenderer.Piece p, long now, WorldTextRenderer.Passes passes, boolean mirror) {
        long appearAt = p.sungAt[0];
        if (now >= appearAt) {
            float appear = RenderUtil.clamp01((float)(now - appearAt) / 280.0F);
            float fade = now < p.fadeAt ? 0.0F : RenderUtil.clamp01((float)(now - p.fadeAt) / fadeDuration(p));
            float alpha = RenderUtil.easeOutCubic(appear) * (1.0F - fade) * ModSettings.lyricOpacity;
            if (!(alpha < 0.02F)) {
                float bounce = ModSettings.lyricBounce;
                float blur = ModSettings.lyricBlur * Math.max(1.0F - appear, fade);
                float popScale = 1.0F - 0.6F * bounce * (1.0F - RenderUtil.easeOutBack(appear, 2.0F));
                popScale *= 1.0F - 0.15F * fade;
                popScale *= 1.0F + 0.12F * this.framePulse;
                double rise = -0.4 * bounce * (1.0F - RenderUtil.easeOutCubic(appear)) * p.sizeMultiplier;
                double drift = Math.sin(now / 700.0 + p.driftSeed) * 0.05 * bounce * p.sizeMultiplier;
                Vec3 cam = Compat.camPos(context.camera());
                PoseStack m = context.poseStack();
                m.pushPose();
                Vec3 at = worldPos(p).add(skyDrift(p, now));
                if (mirror) {
                    double floor = floorBelow(at);
                    double y = at.y + rise + drift;
                    m.translate(at.x - cam.x, 2.0 * floor - y - cam.y, at.z - cam.z);
                    m.scale(1.0F, -1.0F, 1.0F);
                } else {
                    m.translate(at.x - cam.x, at.y - cam.y + rise + drift, at.z - cam.z);
                }

                m.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-p.yaw));
                m.mulPose(com.mojang.math.Axis.XP.rotationDegrees(p.pitch));
                m.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.roll));
                float s = worldScale() * p.sizeMultiplier * p.textScale * p.fit * popScale;
                m.scale(-s * stretch(), -s, s);
                boolean karaoke = p.words.length > 1;
                int current = -1;
                if (karaoke) {
                    for (int i = 0; i < p.words.length; i++) {
                        if (now >= p.sungAt[i]) {
                            current = i;
                        }
                    }
                }

                Component[] texts = new Component[p.words.length];

                for (int i = 0; i < p.words.length; i++) {
                    texts[i] = styled(p.words[i], i == current);
                }

                float space = tr.width(styled(" ", false));
                float total = -space;

                for (Component t : texts) {
                    total += tr.width(t) + space;
                }

                float x = -total / 2.0F;
                float y = -9 / 2.0F;

                for (int i = 0; i < texts.length; i++) {
                    float w = tr.width(texts[i]);
                    float wordAlpha = alpha;
                    float wordPop = 1.0F;
                    int base = textColor(p, i, now);
                    int color = base;
                    if (karaoke && i != current) {
                        wordAlpha *= 0.85F;
                        color = ModSettings.lyricColorMode == ModSettings.LYRIC_COLOR_WHITE ? 14080742 : Colors.darken(base, 0.84F) & 16777215;
                    } else if (ModSettings.lyricHighlight) {
                        int highlight = ModSettings.lyricHighlightColor & 16777215;
                        color = karaoke ? highlight : Colors.mixRgb(highlight, base, (float)(now - p.sungAt[i]) / HIGHLIGHT_FADE_MS);
                    }

                    if (p.translation) {
                        color = Colors.mixRgb(base, TRANSLATION_TINT, 0.4F);
                        wordAlpha *= 0.85F;
                    }

                    if (karaoke && i == current) {
                        float sung = RenderUtil.clamp01((float)(now - p.sungAt[i]) / 250.0F);
                        if (sung < 1.0F) {
                            wordPop += 0.12F * bounce * (float)Math.sin(sung * Math.PI);
                        }
                    }

                    if (this.framePulse > 0.0F) {
                        color = Colors.mixRgb(color, 16777215, 0.3F * this.framePulse);
                    }

                    float glowStrength = ModSettings.lyricGlow * (1.0F + this.framePulse);
                    if (p.sky && ModSettings.lyricSkyNightGlow) {
                        glowStrength *= 1.0F + SKY_NIGHT_GLOW * (1.0F - this.frameDaylight);
                    }

                    long wordStart = karaoke ? p.sungAt[i] : p.sungAt[0];
                    boolean shake = ModSettings.lyricEffect == ModSettings.LYRIC_EFFECT_SHAKE
                        || ModSettings.lyricShakeEmphasis && i < p.emphasized.length && p.emphasized[i];
                    float glitchAmount = glitch(now, p.driftSeed + i * 1.7F);
                    m.pushPose();
                    m.translate(x + w / 2.0F, 0.0F, 0.0F);
                    m.scale(wordPop, wordPop, 1.0F);
                    if (p.sky && constellation() && !p.translation) {
                        this.drawConstellation(tr, p.words[i], i == current, -w / 2.0F, y, wordAlpha, color, m, passes, glowStrength, now, p.driftSeed + i);
                    } else if (p.translation || ModSettings.lyricEffect == ModSettings.LYRIC_EFFECT_NONE && !shake) {
                        this.drawWord(tr, texts[i], -w / 2.0F, y, wordAlpha, blur, color, m.last().pose(), passes, glitchAmount, glowStrength);
                    } else {
                        this.drawLetters(
                            tr, p.words[i], i == current, -w / 2.0F, y, wordAlpha, blur, color, m, passes, glitchAmount, glowStrength, wordStart, now, shake,
                            p.driftSeed + i
                        );
                    }

                    m.popPose();
                    if (!mirror && !p.translation && ModSettings.lyricParticles != ModSettings.LYRIC_PARTICLES_NONE && i >= p.particleWords && now >= wordStart) {
                        p.particleWords = i + 1;
                        spawnParticles(p, at, x + w / 2.0F, s);
                    }

                    x += w + space;
                }

                m.popPose();
            }
        }
    }

    private void drawLetters(
        Font tr,
        String word,
        boolean bold,
        float x,
        float y,
        float alpha,
        float blur,
        int color,
        PoseStack m,
        WorldTextRenderer.Passes passes,
        float glitch,
        float glowStrength,
        long wordStart,
        long now,
        boolean shake,
        float seed
    ) {
        int effect = ModSettings.lyricEffect;
        float cursor = x;
        int letter = 0;

        for (int offset = 0; offset < word.length(); letter++) {
            int codePoint = word.codePointAt(offset);
            offset += Character.charCount(codePoint);
            Component glyph = styled(new String(Character.toChars(codePoint)), bold);
            float glyphWidth = tr.width(glyph);
            float dx = 0.0F;
            float dy = 0.0F;
            float letterAlpha = alpha;
            long since = now - wordStart;
            switch (effect) {
                case ModSettings.LYRIC_EFFECT_TYPEWRITER -> letterAlpha *= RenderUtil.clamp01((since - letter * TYPE_STEP_MS) / TYPE_FADE_MS);
                case ModSettings.LYRIC_EFFECT_FALL -> {
                    float t = RenderUtil.clamp01((since - letter * FALL_STEP_MS) / FALL_MS);
                    dy = -FALL_HEIGHT * (1.0F - RenderUtil.easeOutBack(t, 1.7F));
                    letterAlpha *= RenderUtil.easeOutCubic(t);
                }
                case ModSettings.LYRIC_EFFECT_WAVE -> dy = (float)Math.sin(now / 160.0 - letter * 0.7) * 1.6F;
                default -> {
                }
            }

            if (shake) {
                long bucket = now / SHAKE_BUCKET_MS;
                dx += noise(bucket, letter, seed) * SHAKE_AMOUNT;
                dy += noise(bucket, letter, seed + 31.0F) * SHAKE_AMOUNT;
            }

            if (letterAlpha >= 0.02F) {
                this.drawWord(tr, glyph, cursor + dx, y + dy, letterAlpha, blur, color, m.last().pose(), passes, glitch, glowStrength);
            }

            cursor += glyphWidth;
        }
    }

    private void drawConstellation(
        Font tr,
        String word,
        boolean bold,
        float x,
        float y,
        float alpha,
        int color,
        PoseStack m,
        WorldTextRenderer.Passes passes,
        float glowStrength,
        long now,
        float seed
    ) {
        float cursor = x;
        float starWidth = tr.width(STAR);

        for (int offset = 0; offset < word.length(); ) {
            int codePoint = word.codePointAt(offset);
            offset += Character.charCount(codePoint);
            Component glyph = styled(new String(Character.toChars(codePoint)), bold);
            float advance = tr.width(glyph);
            boolean[] grid = StarGlyphs.glyph(codePoint);
            if (grid == null) {
                if (!Character.isWhitespace(codePoint)) {
                    this.drawWord(tr, glyph, cursor, y, alpha, 0.0F, color, m.last().pose(), passes, 0.0F, glowStrength);
                }
            } else {
                for (int gy = 0; gy < StarGlyphs.GRID; gy++) {
                    for (int gx = 0; gx < StarGlyphs.GRID; gx++) {
                        if (grid[gy * StarGlyphs.GRID + gx] && (gx + gy & 1) == 0) {
                            float twinkle = 0.55F + 0.45F * (float)Math.sin(now / 260.0 + seed * 3.1 + codePoint * 0.7 + gx * 1.3 + gy * 2.1);
                            this.drawStar(tr, cursor + gx + 0.5F, y + gy + 0.5F, alpha * twinkle, color, starWidth, m, passes, glowStrength);
                        }
                    }
                }
            }

            cursor += advance;
        }
    }

    private void drawStar(
        Font tr, float x, float y, float alpha, int color, float starWidth, PoseStack m, WorldTextRenderer.Passes passes, float glowStrength
    ) {
        m.pushPose();
        m.translate(x, y, 0.0F);
        m.scale(STAR_SCALE, STAR_SCALE, 1.0F);
        int main = argb(alpha, color);
        if (main != 0) {
            tr.drawInBatch(STAR, -starWidth / 2.0F, -4.5F, main, false, m.last().pose(), passes.main(), DisplayMode.NORMAL, 0, 15728880);
        }

        if (glowStrength > 0.02F) {
            int halo = argb(alpha * 0.22F * Math.min(glowStrength, 2.0F), color);
            if (halo != 0) {
                m.scale(STAR_HALO_SCALE, STAR_HALO_SCALE, 1.0F);
                tr.drawInBatch(STAR, -starWidth / 2.0F, -4.5F, halo, false, m.last().pose(), passes.glow(), DisplayMode.NORMAL, 0, 15728880);
            }
        }

        m.popPose();
    }

    private static float noise(long bucket, int letter, float seed) {
        long h = bucket * -7046029254386353131L ^ (letter + 1L) * -4417276706812531889L ^ Float.floatToIntBits(seed) * 1609587929392839161L;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        return (float)(h & 65535L) / 32768.0F - 1.0F;
    }

    private static void spawnParticles(WorldTextRenderer.Piece p, Vec3 at, float fontX, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Matrix4f local = new Matrix4f()
            .rotateY((float)Math.toRadians(-p.yaw))
            .rotateX((float)Math.toRadians(p.pitch))
            .rotateZ((float)Math.toRadians(p.roll))
            .scale(-scale * stretch(), -scale, scale);
        Vector3f offset = local.transformPosition(fontX, 0.0F, 0.0F, new Vector3f());
        double spread = scale * 9.0F;

        for (int i = 0; i < PARTICLES_PER_WORD; i++) {
            double px = at.x + offset.x + (RNG.nextDouble() - 0.5) * spread * 2.0;
            double py = at.y + offset.y + (RNG.nextDouble() - 0.5) * spread;
            double pz = at.z + offset.z + (RNG.nextDouble() - 0.5) * spread * 2.0;
            switch (ModSettings.lyricParticles) {
                case ModSettings.LYRIC_PARTICLES_FIRE -> mc.level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.02 + RNG.nextDouble() * 0.02, 0.0);
                case ModSettings.LYRIC_PARTICLES_ICE -> mc.level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, 0.0, -0.01, 0.0);
                case ModSettings.LYRIC_PARTICLES_NOTES -> mc.level.addParticle(ParticleTypes.NOTE, px, py, pz, RNG.nextDouble(), 0.0, 0.0);
                case ModSettings.LYRIC_PARTICLES_SPARKLES -> mc.level
                    .addParticle(ParticleTypes.END_ROD, px, py, pz, (RNG.nextDouble() - 0.5) * 0.02, 0.01, (RNG.nextDouble() - 0.5) * 0.02);
                default -> {
                    return;
                }
            }
        }
    }

    private static float glitch(long now, float seed) {
        if (!ModSettings.lyricFlicker) {
            return 0.0F;
        }

        long bucket = now / 70L;
        long h = bucket * -7046029254386353131L ^ Float.floatToIntBits(seed) * -4417276706812531889L;
        h ^= h >>> 29;
        h *= -4658895280553007687L;
        h ^= h >>> 32;
        float r = (float)(h & 65535L) / 65536.0F;
        return r < 0.12F ? 0.15F + r / 0.12F * 0.85F : 0.0F;
    }

    private void drawWord(
        Font tr,
        Component text,
        float x,
        float y,
        float alpha,
        float blur,
        int color,
        Matrix4f mat,
        WorldTextRenderer.Passes passes,
        float glitch,
        float glowStrength
    ) {
        DisplayMode layer = DisplayMode.NORMAL;
        if (glitch > 0.0F) {
            x += (glitch - 0.55F) * 4.0F;
            alpha *= 0.55F + 0.3F * glitch;
        }

        float glow = glowStrength;
        if (glow > 0.02F) {
            int glowRgb = switch (ModSettings.lyricGlowMode) {
                case ModSettings.LYRIC_GLOW_MATCH -> color;
                case ModSettings.LYRIC_GLOW_CUSTOM -> ModSettings.lyricGlowColor & 16777215;
                default -> 14543103;
            };
            int c = argb(alpha * (0.1F + 0.05F * glow), glowRgb);
            float spread = 0.5F + 0.5F * glow;
            if (c != 0) {
                for (float[] o : GLOW_OFFSETS) {
                    tr.drawInBatch(text, x + o[0] * spread, y + o[1] * spread, c, false, mat, passes.glow(), layer, 0, 15728880);
                }
            }
        }

        if (blur > 0.01F) {
            for (int k = 1; k <= 2; k++) {
                int ghost = argb(alpha * 0.3F / k, color);
                if (ghost != 0) {
                    float off = k * 1.6F * blur;
                    tr.drawInBatch(text, x - off, y, ghost, false, mat, passes.glow(), layer, 0, 15728880);
                    tr.drawInBatch(text, x + off, y, ghost, false, mat, passes.glow(), layer, 0, 15728880);
                }
            }
        }

        if (glitch > 0.0F) {
            float split = 1.0F + 2.0F * glitch;
            int cyan = argb(alpha * 0.6F, 6087167);
            int pink = argb(alpha * 0.6F, 16732088);
            if (cyan != 0) {
                tr.drawInBatch(text, x - split, y, cyan, false, mat, passes.glow(), layer, 0, 15728880);
            }

            if (pink != 0) {
                tr.drawInBatch(text, x + split, y, pink, false, mat, passes.glow(), layer, 0, 15728880);
            }
        }

        float outline = passes.outlineStrength();
        if (outline > 0.02F) {
            int outlineRgb = ModSettings.lyricOutlineColor & 16777215;
            int near = argb(alpha * 0.34F * outline, outlineRgb);
            int far = argb(alpha * 0.16F * outline, outlineRgb);

            for (int i = 0; i < 8; i++) {
                float[] o = RING[i];
                if (near != 0) {
                    tr.drawInBatch(text, x + o[0] * 0.55F, y + o[1] * 0.55F, near, false, mat, passes.outline(), layer, 0, 15728880);
                }

                if (far != 0) {
                    tr.drawInBatch(text, x + o[0] * 1.1F, y + o[1] * 1.1F, far, false, mat, passes.outline(), layer, 0, 15728880);
                }
            }
        }

        int main = argb(alpha * (1.0F - 0.55F * blur), color);
        if (main != 0) {
            tr.drawInBatch(text, x, y, main, false, mat, passes.main(), layer, 0, 15728880);
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = (int)(RenderUtil.clamp01(alpha) * 255.0F);
        return a < 26 ? 0 : a << 24 | rgb;
    }

    private static int textColor(WorldTextRenderer.Piece p, int wordIndex, long now) {
        float t = p.words.length > 1 ? (float)wordIndex / (p.words.length - 1) : p.colorT;
        return switch (ModSettings.lyricColorMode) {
            case ModSettings.LYRIC_COLOR_CLIENT -> ClientAccent.gradientColor(t, 255.0F) & 16777215;
            case ModSettings.LYRIC_COLOR_CUSTOM -> ModSettings.lyricColor & 16777215;
            case ModSettings.LYRIC_COLOR_GRADIENT -> Colors.mixRgb(ModSettings.lyricColor, ModSettings.lyricColor2, t);
            case ModSettings.LYRIC_COLOR_RAINBOW -> rainbow(t, now);
            case ModSettings.LYRIC_COLOR_ALBUM -> albumColor(t);
            default -> 16777215;
        };
    }

    private static int albumColor(float t) {
        SpotifyManager spotify = SkysongClient.getSpotifyManager();
        if (spotify == null || !spotify.hasAlbumPalette()) {
            return 16777215;
        }

        return Colors.mixRgb(spotify.getSongColor(), spotify.getSecondaryColor(), t);
    }

    private static int rainbow(float offset, long now) {
        double phase = now * Math.max(0.1F, ModSettings.rainbowSpeed) / RAINBOW_CYCLE_MS + offset * 0.35F;
        float hue = (float)(phase - Math.floor(phase));
        return Color.HSBtoRGB(hue, ModSettings.rainbowSaturation, 1.0F) & 16777215;
    }

    private static Component styled(String word, boolean bold) {
        Style style = switch (constellation() ? ModSettings.FONT_MINECRAFT : ModSettings.lyricFont) {
            case ModSettings.FONT_MINECRAFT -> Style.EMPTY.withBold(bold);
            case ModSettings.FONT_SMOOTH -> Compat.withFont(Style.EMPTY, UNIFORM_FONT).withBold(bold);
            case ModSettings.FONT_AQUIRE -> Compat.withFont(Style.EMPTY, bold ? AQUIRE_FONT_BOLD : AQUIRE_FONT);
            case ModSettings.FONT_MONTSERRAT -> Compat.withFont(Style.EMPTY, bold ? MONTSERRAT_FONT_BOLD : MONTSERRAT_FONT);
            case ModSettings.FONT_GALACTIC -> Compat.withFont(Style.EMPTY, GALACTIC_FONT).withBold(bold);
            case ModSettings.FONT_RUNIC -> Compat.withFont(Style.EMPTY, RUNIC_FONT).withBold(bold);
            case ModSettings.FONT_CUSTOM -> {
                Identifier custom = CustomFonts.font(ModSettings.lyricCustomFont);
                yield custom == null ? Compat.withFont(Style.EMPTY, bold ? LYRIC_FONT_BOLD : LYRIC_FONT) : Compat.withFont(Style.EMPTY, custom).withBold(bold);
            }
            default -> Compat.withFont(Style.EMPTY, bold ? LYRIC_FONT_BOLD : LYRIC_FONT);
        };
        return Component.literal(word).setStyle(style);
    }

    private static boolean smoothFont() {
        int font = ModSettings.lyricFont;
        return font != ModSettings.FONT_MINECRAFT && font != ModSettings.FONT_GALACTIC && font != ModSettings.FONT_RUNIC;
    }

    private static float stretch() {
        return ModSettings.lyricFont == ModSettings.FONT_WIDE ? 1.3F : 1.0F;
    }

    private static float worldScale() {
        float fontFactor = ModSettings.lyricFont == ModSettings.FONT_WIDE ? 0.75F : 1.0F;
        return ModSettings.lyricSize * 0.28F * fontFactor;
    }

    private static float[][] ring(int count, float radius) {
        float[][] ring = new float[count][];

        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count;
            ring[i] = new float[]{(float)Math.cos(a) * radius, (float)Math.sin(a) * radius};
        }

        return ring;
    }

    private static float[][] buildGlowOffsets() {
        float[][] offsets = new float[16][];

        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            offsets[i] = new float[]{(float)Math.cos(a) * 0.6F, (float)Math.sin(a) * 0.6F};
            offsets[i + 8] = new float[]{(float)Math.cos(a) * 1.3F, (float)Math.sin(a) * 1.3F};
        }

        return offsets;
    }

    private record Passes(MultiBufferSource glow, MultiBufferSource outline, MultiBufferSource main, float outlineStrength) {
    }

    private record Pending(String word, long sungAt, long fadeAt, int index, float colorT, boolean emphasized) {
    }

    private static final class Piece {
        final String[] words;
        final long[] sungAt;
        final Vec3 pos;
        final Vec3 anchor = WorldTextRenderer.followPos;
        final float yaw;
        final float pitch;
        final float roll;
        final float fit;
        final double halfWidth;
        final double halfHeight;
        final float colorT;
        final boolean sky;
        final float sizeMultiplier;
        final float driftSeed = WorldTextRenderer.RNG.nextFloat() * 6.28F;
        long fadeAt = Long.MAX_VALUE;
        boolean[] emphasized = new boolean[0];
        int particleWords;
        boolean translation;
        float textScale = 1.0F;

        Piece(
            String[] words,
            long[] sungAt,
            Vec3 pos,
            float yaw,
            float pitch,
            float roll,
            float fit,
            double halfWidth,
            double halfHeight,
            float colorT,
            boolean sky,
            float sizeMultiplier
        ) {
            this.words = words;
            this.sungAt = sungAt;
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            this.fit = fit;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
            this.colorT = colorT;
            this.sky = sky;
            this.sizeMultiplier = sizeMultiplier;
        }
    }
}
