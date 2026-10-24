package dev.infectedware.skysong.hud.components;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.hud.DragSystem;
import dev.infectedware.skysong.theme.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class PotionsComp extends ListHudComp {
    private static final Object[] PREVIEW = new Object[]{MobEffects.SPEED, MobEffects.JUMP_BOOST, MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE};
    private static final int NEGATIVE = Colors.lerp(-3355444, -53714, 0.32F);
    private long previewSwitch;
    private int previewIndex;

    public PotionsComp() {
        super("potions", "Potions", () -> ModSettings.potionsEnabled, 180.0F, 29.0F);
    }

    @Override
    protected String headerIcon() {
        return "s";
    }

    @Override
    protected List<ListHudComp.Row> collectRows() {
        List<ListHudComp.Row> rows = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                Object type = e.getEffect();
                String name = ChatFormatting.stripFormatting(Compat.effect(type).getDisplayName().getString());
                if (e.getAmplifier() > 0) {
                    name = name + " " + (e.getAmplifier() + 1);
                }

                int color = isNegative(type) ? NEGATIVE : 0;
                rows.add(new ListHudComp.Row(type, name, duration(e), (c, x, y, size, a) -> icon(c, type, x, y, size, a), pulse(e), color));
            }
        }

        if (rows.isEmpty() && DragSystem.get().dragMode()) {
            rows.add(this.preview());
        }

        return rows;
    }

    private static boolean isNegative(Object type) {
        MobEffect effect = Compat.effect(type);
        return effect.getCategory() == MobEffectCategory.HARMFUL || effect == Compat.effect(MobEffects.SLOW_FALLING);
    }

    private static String duration(MobEffectInstance e) {
        if (e.isInfiniteDuration()) {
            return "**:**";
        }

        int s = Math.max(0, e.getDuration()) / 20;
        return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private static void icon(GuiGraphics c, Object type, float x, float y, float size, float a) {
        float inset = (size - 8.0F) / 2.0F;
        HudItems.effect(c, type, x + inset, y + inset, 8.0F, a);
    }

    private static ListHudComp.AlphaPulse pulse(MobEffectInstance e) {
        return !e.isInfiniteDuration() && e.getDuration() <= 200 ? () -> {
            int d = Math.max(0, e.getDuration());
            if (d > 200) {
                return 1.0F;
            }

            float depth = smoothstep(1.0F - d / 200.0F) * 0.8F;
            float phase = (float)(System.currentTimeMillis() % 900L) / 900.0F;
            float wave = 0.5F - 0.5F * (float)Math.cos(phase * Math.PI * 2.0);
            return 1.0F - depth * wave;
        } : null;
    }

    private ListHudComp.Row preview() {
        long now = System.currentTimeMillis();
        if (now - this.previewSwitch >= 1000L) {
            this.previewIndex = (this.previewIndex + 1) % PREVIEW.length;
            this.previewSwitch = now;
        }

        Object type = PREVIEW[this.previewIndex];
        return new ListHudComp.Row("preview", "Example effect", "**:**", (c, x, y, size, a) -> icon(c, type, x, y, size, a));
    }
}
