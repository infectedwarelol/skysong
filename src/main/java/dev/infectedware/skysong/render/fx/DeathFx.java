package dev.infectedware.skysong.render.fx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Gpu;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.compat.Hooks;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import java.util.Random;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class DeathFx {
    public static final String[] EFFECTS = new String[]{"Shattered Glass", "Flatline", "Ascension", "Signal Lost", "Cinematic", "Swallowed", "Frozen", "Pixelated", "Ink Bleed", "Thermal", "Kaleidoscope", "Burn Away", "Drowned", "Rewind", "Ripple", "Stained Glass", "Ghost Echo", "Starry Void", "Random"};
    private static final int KINDS = 18;
    private static final java.util.List<Integer> randomBag = new java.util.ArrayList<>();
    private static int lastRandom = -1;
    private static final float[] BEATS = new float[]{0.05F, 0.75F};
    private static final DeathFx.Cue[][] CUES = new DeathFx.Cue[][]{
        {
                new DeathFx.Cue(0.0F, SoundEvents.GLASS_BREAK, 1.0F, 0.6F),
                new DeathFx.Cue(0.02F, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.9F),
                new DeathFx.Cue(0.72F, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.75F)
        },
        {
                new DeathFx.Cue(0.05F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.7F, 1.9F),
                new DeathFx.Cue(0.75F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.7F, 1.9F),
                new DeathFx.Cue(1.45F, SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.8F, 1.9F),
                new DeathFx.Cue(1.75F, SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.8F, 1.9F),
                new DeathFx.Cue(2.05F, SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.7F, 1.9F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.3F),
                new DeathFx.Cue(0.6F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F),
                new DeathFx.Cue(1.3F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.1F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.8F),
                new DeathFx.Cue(0.3F, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.8F, 0.7F),
                new DeathFx.Cue(0.9F, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.8F, 0.5F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F),
                new DeathFx.Cue(0.9F, SoundEvents.NOTE_BLOCK_BASS.value(), 0.8F, 0.45F),
                new DeathFx.Cue(2.6F, SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.6F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.4F),
                new DeathFx.Cue(0.5F, SoundEvents.PORTAL_TRAVEL, 0.35F, 1.6F),
                new DeathFx.Cue(3.2F, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.8F, 0.6F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.PLAYER_HURT_FREEZE, 1.0F, 0.7F),
                new DeathFx.Cue(0.6F, SoundEvents.POWDER_SNOW_BREAK, 1.0F, 0.6F),
                new DeathFx.Cue(1.8F, SoundEvents.GLASS_BREAK, 0.6F, 1.5F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 1.0F),
                new DeathFx.Cue(0.6F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.8F),
                new DeathFx.Cue(1.2F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.6F),
                new DeathFx.Cue(1.8F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F, 0.5F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.SCULK_CLICKING, 1.0F, 0.5F),
                new DeathFx.Cue(1.2F, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.6F),
                new DeathFx.Cue(2.6F, SoundEvents.SCULK_CLICKING, 0.8F, 0.4F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.8F),
                new DeathFx.Cue(0.8F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 1.4F),
                new DeathFx.Cue(2.8F, SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.2F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F),
                new DeathFx.Cue(1.0F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F),
                new DeathFx.Cue(2.0F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.9F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.FIRECHARGE_USE, 1.0F, 0.6F),
                new DeathFx.Cue(0.6F, SoundEvents.FIRE_AMBIENT, 1.0F, 0.8F),
                new DeathFx.Cue(2.4F, SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.7F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.PLAYER_SPLASH, 1.0F, 0.6F),
                new DeathFx.Cue(0.5F, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.0F, 0.8F),
                new DeathFx.Cue(2.5F, SoundEvents.DROWNED_AMBIENT_WATER, 0.6F, 0.6F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.6F),
                new DeathFx.Cue(0.3F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 0.5F),
                new DeathFx.Cue(0.6F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 0.45F),
                new DeathFx.Cue(0.9F, SoundEvents.NOTE_BLOCK_BIT.value(), 0.6F, 0.4F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.BELL_RESONATE, 1.0F, 0.8F),
                new DeathFx.Cue(1.5F, SoundEvents.BELL_RESONATE, 0.7F, 0.6F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.GLASS_BREAK, 0.8F, 1.4F),
                new DeathFx.Cue(0.8F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.7F),
                new DeathFx.Cue(1.6F, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.5F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.7F),
                new DeathFx.Cue(1.4F, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.5F),
                new DeathFx.Cue(2.8F, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.6F)
        },
        {
                new DeathFx.Cue(0.0F, SoundEvents.PORTAL_TRIGGER, 0.5F, 1.4F),
                new DeathFx.Cue(1.2F, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F),
                new DeathFx.Cue(3.0F, SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.5F)
        }
    };
    private static long diedAt = -1L;
    private static long previewUntil = -1L;
    private static boolean wasDead;
    private static int mode;
    private static int cue;
    private static TextureTarget scene;
    private static final Random RNG = new Random();

    private DeathFx() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(DeathFx::tick);
        Hooks.hud(DeathFx::heartMonitor);
    }

    static void forget() {
        diedAt = -1L;
        previewUntil = -1L;
        wasDead = false;
        cue = 0;
    }

    public static boolean active() {
        return Fx.allowed() && ModSettings.deathEffect;
    }

    public static float seconds() {
        return diedAt < 0L ? -1.0F : (float)(System.currentTimeMillis() - diedAt) / 1000.0F;
    }

    public static void preview(int type) {
        diedAt = System.currentTimeMillis();
        previewUntil = diedAt + 4000L;
        mode = type >= 0 && type < KINDS ? type : nextRandom();
        cue = 0;
    }

    static int nextRandom() {
        if (randomBag.isEmpty()) {
            for (int i = 0; i < KINDS; i++) {
                randomBag.add(i);
            }

            java.util.Collections.shuffle(randomBag, RNG);
            if (randomBag.size() > 1 && randomBag.getLast() == lastRandom) {
                java.util.Collections.swap(randomBag, 0, randomBag.size() - 1);
            }
        }

        lastRandom = randomBag.removeLast();
        return lastRandom;
    }

    private static void tick(Minecraft mc) {
        boolean dead = mc.player != null && mc.player.getHealth() <= 0.0F;
        if (dead && !wasDead && ModSettings.deathEffect) {
            diedAt = System.currentTimeMillis();
            int t = ModSettings.deathEffectType;
            mode = t >= 0 && t < KINDS ? t : nextRandom();
            cue = 0;
        }

        boolean previewing = previewUntil > System.currentTimeMillis();
        if (!previewing) {
            previewUntil = -1L;
        }

        if (!dead && !previewing) {
            diedAt = -1L;
        }

        wasDead = dead;
        if (diedAt >= 0L) {
            float t = seconds();

            for (DeathFx.Cue[] cues = CUES[mode]; cue < cues.length && t >= cues[cue].at(); cue++) {
                if (ModSettings.deathEffectSounds) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(cues[cue].sound(), cues[cue].pitch(), cues[cue].volume()));
                }
            }
        }
    }

    private static float[] tints() {
        int m = switch (ModSettings.deathEffectColor) {
            case 1 -> 0;
            case 2 -> 2;
            case 3 -> 3;
            default -> 1;
        };
        return Fx.echoTints(m, ModSettings.deathEffectCustomColor, ModSettings.deathEffectCustomColor2);
    }

    public static void apply() {
        if (diedAt >= 0L && active()) {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();

            try {
                float t = seconds();
                scene = Fx.ensure(scene, main.width, main.height, false, true);
                Fx.blitColor(main, scene);
                Gpu.bind(main);
                Fx.noBlend();
                Gpu.texture(0, Gpu.color(scene));
                float beat = mode != 0 && mode != 1 ? 0.0F : pulse(t, BEATS[0], 0.09F) + 0.8F * pulse(t, BEATS[1], 0.1F);
                float drained = 0.9F * smooth(1.4F, t);
                Fx.set(Fx.death, "Death", t, ModSettings.deathEffectCracks ? 1.0F : 0.0F, drained, beat);
                Fx.set(Fx.death, "Mode", mode, 0.0F, 0.0F, 0.0F);
                Fx.setTints(Fx.death, tints());
                Fx.setOutSize(Fx.death, main.width, main.height);
                Fx.draw(Fx.death);
            } catch (Throwable e) {
                Fx.fail("death effect", e);
            } finally {
                Fx.restore(main);
            }
        }
    }

    private static void heartMonitor(GuiGraphics c) {
        if (diedAt >= 0L && mode == 1 && active() && R2D.available()) {
            Minecraft mc = Minecraft.getInstance();
            float w = mc.getWindow().getGuiScaledWidth();
            float h = mc.getWindow().getGuiScaledHeight();
            float t = seconds();
            float speed = w / 1.9F;
            float head = Math.min(w, t * speed);
            float midY = h * 0.5F;
            float amp = Math.min(h * 0.16F, 46.0F);
            float[] tint = tints();
            float fadeIn = smooth(0.08F, t);
            float prevX = 0.0F;
            float prevY = midY;

            for (float x = 0.0F; x <= head; x += 1.5F) {
                float y = midY - amp * trace(x / speed);
                float age = (head - x) / w;
                float a = fadeIn * (0.25F + 0.75F * (1.0F - Math.min(1.0F, age * 1.4F)));
                float k = x / w;
                int col = Colors.rgba(
                    Math.round((tint[0] + (tint[3] - tint[0]) * k) * 255.0F),
                    Math.round((tint[1] + (tint[4] - tint[1]) * k) * 255.0F),
                    Math.round((tint[2] + (tint[5] - tint[2]) * k) * 255.0F),
                    255.0F * a
                );
                float top = Math.min(prevY, y) - 0.9F;
                float bottom = Math.max(prevY, y) + 0.9F;
                R2D.rect(c, prevX - 1.5F, top - 2.0F, x - prevX + 3.0F, bottom - top + 4.0F, 2.0F, Colors.multAlpha(col, 0.18F));
                R2D.rect(c, prevX, top, x - prevX + 1.0F, bottom - top, 0.8F, col);
                prevX = x;
                prevY = y;
            }

            if (head < w) {
                R2D.circle(c, head, prevY, 2.4F, Colors.rgba(255, 255, 255, 240.0F * fadeIn));
            }
        }
    }

    private static float trace(float s) {
        float v = 0.0F;

        for (float b : BEATS) {
            float d = s - b;
            v += 0.12F * gauss(d + 0.1F, 0.03F);
            v -= 0.25F * gauss(d - 0.015F, 0.008F);
            v += gauss(d - 0.04F, 0.011F);
            v -= 0.45F * gauss(d - 0.07F, 0.011F);
            v += 0.22F * gauss(d - 0.22F, 0.045F);
        }

        return v;
    }

    private static float gauss(float x, float w) {
        return (float)Math.exp(-(x * x) / (2.0F * w * w));
    }

    private static float pulse(float t, float at, float width) {
        float x = (t - at) / width;
        return (float)Math.exp(-x * x);
    }

    private static float smooth(float b, float x) {
        float t = Math.clamp((x - 0.0F) / (b - 0.0F), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record Cue(float at, SoundEvent sound, float volume, float pitch) {
    }
}
