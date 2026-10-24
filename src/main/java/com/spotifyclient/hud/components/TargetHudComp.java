package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.hud.Draggable;
import com.spotifyclient.hud.HudSpace;
import com.spotifyclient.hud.NameTags;
import com.spotifyclient.hud.Projection;
import com.spotifyclient.hud.ServerStats;
import com.spotifyclient.render.r2d.MsdfFont;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import com.spotifyclient.theme.Colors;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class TargetHudComp extends Draggable {
    private static final float PAD = 6.0F;
    private static final float HEAD = 22.0F;
    private static final float GAP = 7.0F;
    private static final float BAR_H = 3.0F;
    private static final float NAME_SIZE = 8.0F;
    private static final float INFO_SIZE = 6.0F;
    private static final float MIN_CONTENT = 74.0F;
    private static final float HEIGHT = 34.0F;
    private static final float NEW_HEIGHT = 45.0F;
    private static final long HOLD_MS = 1500L;
    private static final int INFO_COLOR = -3618608;
    private static final int BAR_BG = -14935006;
    private static final EquipmentSlot[] SLOTS = new EquipmentSlot[]{
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private final Smooth visibility = new Smooth(0.0F);
    private final List<TargetHudComp.Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final float[] slotAppear = new float[SLOTS.length];
    private boolean lastVisible;
    private LivingEntity target;
    private long lastSeen;
    private float health;
    private float hpGray = 1.0F;
    private float armor = 1.0F;
    private float width = 120.0F;
    private float lastPinnedWidth = Float.NaN;
    private long lastNs = System.nanoTime();
    private int particleEntity = Integer.MIN_VALUE;
    private int lastHurt = -1;
    private float followX;
    private float followY;

    public TargetHudComp() {
        super("targethud", 5.0F, 150.0F);
    }

    @Override
    public float width() {
        return this.width;
    }

    @Override
    public float height() {
        return ModSettings.targetHudMode == 1 ? 45.0F - 10.0F * (1.0F - easeInOutCubic(this.armor)) : 34.0F + 11.0F * easeInOutCubic(this.armor);
    }

    @Override
    public boolean interactive() {
        return ModSettings.targetHudEnabled;
    }

    @Override
    protected void render(GuiGraphics c) {
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        Minecraft mc = Minecraft.getInstance();
        boolean dragMode = DragSystem.get().dragMode();
        LivingEntity seen = dragMode ? mc.player : hovered();
        if (seen != null) {
            this.target = seen;
            this.lastSeen = System.currentTimeMillis();
        }

        boolean hidden = this.target != null && !dragMode && this.target.isInvisible();
        boolean show = ModSettings.targetHudEnabled && this.target != null && !hidden && (dragMode || System.currentTimeMillis() - this.lastSeen <= 1500L);
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.18 : 0.12);
            this.lastVisible = show;
        }

        this.updateFollow(dt, dragMode);
        float a = this.visibility.get();
        if (a <= 0.01F) {
            if (!show) {
                this.target = null;
            }
        } else if (this.target != null) {
            boolean moved = Math.abs(this.followX) > 0.01F || Math.abs(this.followY) > 0.01F;
            if (moved) {
                c.pose().pushPose();
                c.pose().translate(this.followX, this.followY, 0.0F);
            }

            if (ModSettings.targetHudMode == 1) {
                this.renderNew(c, dt, a);
            } else {
                this.renderClassic(c, dt, a);
            }

            if (moved) {
                c.pose().popPose();
            }
        }
    }

    private void renderClassic(GuiGraphics c, float dt, float a) {
        boolean armorOn = ModSettings.targetHudArmor && armorPieces(this.target) > 0;
        this.armor = clamp01(this.armor + ((armorOn ? 1.0F : 0.0F) - this.armor) * (1.0F - (float)Math.exp(-dt * (armorOn ? 6.0F : 10.0F))));
        float ae = easeInOutCubic(this.armor);
        float extra = 11.0F * ae;
        float h = 34.0F + extra;
        float headY = this.y() + 6.0F + extra * 0.5F;
        this.updateParticles(this.target, this.x() + 6.0F + 11.0F, headY + 11.0F);
        Component name = name(this.target);
        float maxHp = Math.max(1.0F, this.target.getMaxHealth());
        float hp = ServerStats.health(this.target);
        float pct = clamp01(hp / maxHp);
        this.health = this.health + (pct - this.health) * (1.0F - (float)Math.exp(-dt * 12.0F));
        String info = Math.round(pct * 100.0F) + "% - " + Math.round(distance(this.target)) + "m";
        float nameW = coloredWidth(name, 8.0F);
        float infoW = R2D.width(MsdfFont.Font.MEDIUM, info, 6.0F);
        float content = Math.max(74.0F, Math.max(nameW, infoW));
        this.width = 35.0F + content + 6.0F;
        this.pinCenter();
        float x = this.x();
        float y = this.y();
        pushScale(c, x + this.width / 2.0F, y + h / 2.0F, 0.94F + a * 0.06F);
        R2D.clientRect(c, x, y, this.width, h, a);
        float contentX = x + 6.0F + 22.0F + 7.0F;
        float mid = contentX + content / 2.0F;
        float armorRow = 8.0F * ae;
        float spacing = (22.0F + extra - 8.0F - armorRow - 3.0F - 6.0F) / (2.0F + ae);
        float nameY = y + 6.0F;
        float armorY = nameY + 8.0F + spacing * ae;
        float barY = armorY + armorRow + spacing;
        float infoY = barY + 3.0F + spacing;
        drawName(c, name, mid - nameW / 2.0F, nameY, 8.0F, 0.85F * a);
        float r = barRadius(3.0F);
        R2D.rect(c, contentX, barY + 1.0F, content, 3.0F, r, Colors.multAlpha(-14935006, a * 0.5F));
        barFill(c, contentX, barY + 1.0F, content * this.health, 3.0F, r, this.health, a);
        R2D.text(c, MsdfFont.Font.MEDIUM, info, mid - infoW / 2.0F, infoY, 6.0F, Colors.multAlpha(-3618608, a));
        drawHead(c, this.target, x + 6.0F + 1.0F, headY, 22.0F, 11.0F, a);
        this.drawParticles(c, a);
        if (this.armor > 0.02F) {
            this.drawArmorCentered(c, this.target, mid, armorY + 1.0F, a, this.armor);
        }

        c.pose().popPose();
    }

    private void renderNew(GuiGraphics c, float dt, float a) {
        boolean any = false;

        for (EquipmentSlot s : SLOTS) {
            any |= !this.target.getItemBySlot(s).isEmpty();
        }

        boolean armorOn = ModSettings.targetHudArmor && any;
        this.armor = clamp01(this.armor + ((armorOn ? 1.0F : 0.0F) - this.armor) * (1.0F - (float)Math.exp(-dt * (armorOn ? 6.0F : 10.0F))));
        float ae = easeInOutCubic(this.armor);
        float h = 45.0F - 10.0F * (1.0F - ae);
        Component name = name(this.target);
        float maxHp = Math.max(1.0F, this.target.getMaxHealth());
        float hp = ServerStats.health(this.target);
        float pct = clamp01(hp / maxHp);
        this.health = this.health + (pct - this.health) * (1.0F - (float)Math.exp(-dt * 12.0F));
        float slotsW = SLOTS.length * 8.0F + (SLOTS.length - 1) * 3.0F;
        float nameW = coloredWidth(name, 8.0F);
        float barY0 = this.y() + h - 5.0F - 8.0F;
        float headSize = barY0 - 3.0F - (this.y() + 3.0F);
        float armorReveal = easeOutCubic(clamp01(this.armor / 0.6F));
        float contentW = Math.max(nameW, (slotsW + 2.0F) * armorReveal);
        this.width = Math.max(84.0F, 3.0F + headSize + 3.0F + contentW + 3.0F);
        this.pinCenter();
        float x = this.x();
        float y = this.y();
        float barY = y + h - 5.0F - 8.0F;
        float hx = x + 3.0F;
        float hy = y + 3.0F;
        float headR = Math.max(4.5F, 6.0F * clamp01(headSize / 26.0F));
        if (ModSettings.rectCornerRadius > 6.0F) {
            headR = Math.min(ModSettings.rectCornerRadius, headSize / 2.0F);
        }

        this.updateParticles(this.target, hx + headSize / 2.0F, hy + headSize / 2.0F);
        pushScale(c, x + this.width / 2.0F, y + h / 2.0F, 0.94F + a * 0.06F);
        R2D.clientRect(c, x, y, this.width, h, a);
        float textX = hx + headSize + 3.0F;
        float slotY = hy + headSize - 10.0F;
        float centeredNameY = hy + headSize / 2.0F - 4.0F;
        float topNameY = (hy + slotY - 8.0F) / 2.0F;
        float nameY = centeredNameY + (topNameY - centeredNameY) * ae - 1.5F * (1.0F - ae);
        drawName(c, name, textX, nameY, 8.0F, 0.85F * a);
        float bx = x + 3.0F;
        float bw = this.width - 6.0F;
        float br = barRadius(8.0F);
        R2D.rect(c, bx, barY, bw, 8.0F, br, Colors.multAlpha(-14935006, a * 0.6F));
        float fill = bw * this.health;
        barFill(c, bx, barY, fill, 8.0F, br, this.health, a);
        String hpText = Math.round(Math.max(0.0F, hp)) + "HP";
        float hpW = R2D.width(MsdfFont.Font.PIXEL, hpText, 6.0F);
        float tx = Math.max(Math.min(bx + (bw - hpW) / 2.0F + 0.75F, bx + fill - hpW - 2.0F), bx + 2.0F);
        float covered = hpW <= 0.0F ? 0.0F : clamp01((bx + fill - tx) / hpW);
        int under = Colors.lerp(-14935006, barColorAt(clamp01((tx + hpW / 2.0F - bx) / Math.max(bw, 1.0F)), this.health), covered);
        float lum = (0.2126F * (under >> 16 & 0xFF) + 0.7152F * (under >> 8 & 0xFF) + 0.0722F * (under & 0xFF)) / 255.0F;
        this.hpGray = this.hpGray + (clamp01(1.0F - lum * 1.35F) - this.hpGray) * (1.0F - (float)Math.exp(-dt * 10.0F));
        int g = Math.round(clamp01(this.hpGray) * 255.0F);
        float[] pb = R2D.bounds(MsdfFont.Font.PIXEL, "0", 6.0F);
        R2D.text(c, MsdfFont.Font.PIXEL, hpText, tx, barY + 4.0F - (pb[1] + pb[3]) / 2.0F, 6.0F, Colors.multAlpha(0xFF000000 | g << 16 | g << 8 | g, a));
        float slotX = textX + 1.0F;
        float rightEdge = x + this.width - 2.0F;

        for (int i = 0; i < SLOTS.length; i++) {
            float delay = 0.35F + 0.25F * i / (SLOTS.length - 1);
            boolean fits = slotX + i * 11.0F - 1.0F + 10.0F <= rightEdge;
            boolean on = this.armor > delay && fits;
            float rate = on ? 14.0F : 30.0F;
            this.slotAppear[i] = clamp01(this.slotAppear[i] + ((on ? 1.0F : 0.0F) - this.slotAppear[i]) * (1.0F - (float)Math.exp(-dt * rate)));
        }

        if (this.armor > 0.02F) {
            for (int i = 0; i < SLOTS.length; i++) {
                float p = easeOutCubic(this.slotAppear[i]);
                if (p > 0.01F) {
                    R2D.rect(c, slotX + i * 11.0F - 1.0F, slotY, 10.0F, 10.0F, 2.0F * p, Math.round(36.0F * a * p) << 24);
                }
            }
        }

        drawHead(c, this.target, hx, hy, headSize, headR, a);
        this.drawParticles(c, a);

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = this.target.getItemBySlot(SLOTS[i]);
            float p = clamp01((this.slotAppear[i] - 0.35F) / 0.65F);
            if (!stack.isEmpty() && !(this.armor <= 0.02F) && !(p <= 0.01F)) {
                HudItems.item(c, stack, slotX + i * 11.0F + 4.0F, slotY + 5.0F, 8.0F * easeOutBack(p), true);
            }
        }

        c.pose().popPose();
    }

    private void drawArmorCentered(GuiGraphics c, LivingEntity e, float cx, float y, float a, float progress) {
        float total = SLOTS.length * 8.0F + (SLOTS.length - 1) * 3.0F;
        float start = cx - total / 2.0F;

        for (int i = 0; i < SLOTS.length; i++) {
            float p = easeOutCubic((progress - (0.35F + 0.25F * i / (SLOTS.length - 1))) / 0.3F);
            if (p > 0.01F) {
                R2D.rect(c, start + i * 11.0F - 1.0F, y - 1.0F, 10.0F, 10.0F, 2.0F * p, Math.round(36.0F * a * p) << 24);
            }
        }

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = e.getItemBySlot(SLOTS[i]);
            float p = clamp01((progress - (0.45F + 0.25F * i / (SLOTS.length - 1))) / 0.3F);
            if (!stack.isEmpty() && !(p <= 0.01F)) {
                HudItems.item(c, stack, start + i * 11.0F + 4.0F, y + 4.0F, 8.0F * easeOutBack(p) * a, false);
            }
        }
    }

    private static void drawHead(GuiGraphics c, LivingEntity e, float x, float y, float size, float r, float a) {
        if (e instanceof AbstractClientPlayer p) {
            int color = tintHurt(Math.round(a * 255.0F) << 24 | 16777215, e);
            Identifier skin = Compat.skin(p);
            R2D.image(c, skin, x, y, size, size, r, 0.125F, 0.125F, 0.25F, 0.25F, color, false);
            float inset = 0.009533897F;
            R2D.image(c, skin, x, y, size, size, r, 0.625F + inset, 0.125F + inset, 0.75F - inset, 0.25F - inset, color, false);
        } else {
            R2D.rect(c, x, y, size, size, r, Colors.multAlpha(-14935006, a * 0.5F));
            float qw = R2D.width(MsdfFont.Font.SEMIBOLD, "?", 8.0F);
            R2D.text(c, MsdfFont.Font.SEMIBOLD, "?", x + (size - qw) / 2.0F, y + (size - 8.0F) * 0.45F - 1.0F, 8.0F, Colors.multAlpha(-1, a * 0.7F));
        }
    }

    private static int tintHurt(int argb, LivingEntity e) {
        if (e.hurtTime <= 0) {
            return argb;
        }

        float f = Math.min(1.0F, e.hurtTime / 10.0F);
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * f * 0.35F));
        g = Math.round(g * (1.0F - f * 0.65F));
        b = Math.round(b * (1.0F - f * 0.65F));
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void barFill(GuiGraphics c, float x, float y, float w, float h, float r, float health, float a) {
        if (!(w <= 0.5F)) {
            r = Math.min(r, h / 2.0F);
            int alpha = Math.round(235.0F * clamp01(a)) << 24;
            switch (ModSettings.targetHudBarColor) {
                case 1:
                    R2D.rect(c, x, y, w, h, r, Math.round(215.0F * clamp01(a)) << 24 | 14145495);
                    break;
                case 2:
                    int ga = ClientAccent.gradientColor(0.0F, 235.0F * a);
                    int gb = ClientAccent.gradientColor(1.0F, 235.0F * a);
                    R2D.rect(c, x, y, w, h, r, ga, gb, gb, ga);
                    break;
                default:
                    int base = healthColor(health);
                    int dark = alpha | darken(base, 0.45F);
                    int light = alpha | base;
                    R2D.rect(c, x, y, w, h, r, dark, light, light, dark);
            }
        }
    }

    private static int barColorAt(float t, float health) {
        return switch (ModSettings.targetHudBarColor) {
            case 1 -> -2631721;
            case 2 -> 0xFF000000 | ClientAccent.gradientColor(t, 255.0F) & 16777215;
            default -> {
                int base = healthColor(health);
                yield 0xFF000000 | Colors.mixRgb(darken(base, 0.45F), base, t);
            }
        };
    }

    private static int healthColor(float f) {
        f = clamp01(f);
        int green = 3530826;
        int orange = 16751136;
        int red = 16721968;
        if (f >= 0.8F) {
            return green;
        } else {
            return f >= 0.4F ? Colors.mixRgb(orange, green, (f - 0.4F) / 0.4F) : Colors.mixRgb(red, orange, f / 0.4F);
        }
    }

    private static int darken(int rgb, float amount) {
        float k = 1.0F - amount;
        return Math.round((rgb >> 16 & 0xFF) * k) << 16 | Math.round((rgb >> 8 & 0xFF) * k) << 8 | Math.round((rgb & 0xFF) * k);
    }

    private static float barRadius(float h) {
        return Math.max(0.0F, Math.min(ModSettings.rectCornerRadius, h * 0.5F));
    }

    private void updateParticles(LivingEntity e, float cx, float cy) {
        if (e.getId() != this.particleEntity) {
            this.particleEntity = e.getId();
            this.lastHurt = e.hurtTime;
            this.particles.clear();
        }

        if (e.hurtTime > this.lastHurt && e.hurtTime > 0) {
            long now = System.currentTimeMillis();

            for (int i = 0; i < 12; i++) {
                float dist = (0.3333F + this.random.nextFloat() * 0.6667F) * 38.5F;
                float ang = (float)Math.toRadians(this.random.nextFloat() * 360.0F);
                long life = Math.max(120L, Math.round(dist / 36.0F * 1000.0F * (1.0F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.2F)));
                this.particles.add(new TargetHudComp.Particle(cx, cy, (float)Math.sin(ang) * dist, (float)(-Math.cos(ang)) * dist, now, life, i / 11.0F));
            }
        }

        this.lastHurt = e.hurtTime;
    }

    private void drawParticles(GuiGraphics c, float a) {
        long now = System.currentTimeMillis();
        Iterator<TargetHudComp.Particle> it = this.particles.iterator();

        while (it.hasNext()) {
            TargetHudComp.Particle p = it.next();
            float t = clamp01((float)(now - p.start()) / (float)p.life());
            if (t >= 1.0F) {
                it.remove();
            } else {
                float alpha = (t <= 0.15F ? t / 0.15F : clamp01(1.0F - (t - 0.15F) / 0.85F)) * a;
                if (!(alpha <= 0.004F)) {
                    float size = 0.85F + (1.0F - t) * 0.15F;
                    R2D.circle(c, p.x() + p.dx() * t, p.y() + p.dy() * t, size, ClientAccent.gradientColor(p.gradient(), 255.0F * alpha));
                }
            }
        }
    }

    private void pinCenter() {
        if (!Float.isNaN(this.lastPinnedWidth) && !this.drag().dragging() && Math.abs(this.width - this.lastPinnedWidth) > 1.0E-4F) {
            this.drag().setTargetX(this.drag().targetX() - (this.width - this.lastPinnedWidth) * 0.5F);
            this.drag().syncToTarget();
        }

        this.lastPinnedWidth = this.width;
    }

    private void updateFollow(float dt, boolean dragMode) {
        float tx = 0.0F;
        float ty = 0.0F;
        Minecraft mc = Minecraft.getInstance();
        if (ModSettings.targetHudFollow && this.target != null && this.target != mc.player && !dragMode) {
            float pt = Projection.tickDelta();
            Vec3 pos = this.target.getPosition(pt);
            float[] mid = Projection.project(pos.x, pos.y + (this.target.getBbHeight() + 0.4F) * 0.5F, pos.z);
            float[] top = Projection.project(pos.x, pos.y + this.target.getBbHeight() + 0.4F, pos.z);
            float sw = HudSpace.width();
            float sh = HudSpace.height();
            if (mid != null && mid[0] > -sw * 0.1F && mid[0] < sw * 1.1F && mid[1] > -sh * 0.1F && mid[1] < sh * 1.1F) {
                float halfH = Math.max(top == null ? 40.0F : Math.abs(mid[1] - top[1]), 14.0F);
                float side = mid[0] / sw;
                float t = Math.min(1.0F, Math.abs(side - 0.5F) / 0.15F);
                float w = this.width();
                float h = this.height();
                float sideX = side < 0.5F ? mid[0] + halfH * 0.45F + 10.0F : mid[0] - halfH * 0.45F - 10.0F - w;
                float belowX = mid[0] - w / 2.0F;
                float belowY = mid[1] + halfH + 8.0F;
                float fx = HudSpace.clampX(belowX + (sideX - belowX) * t, w);
                float fy = HudSpace.clampY(belowY + (mid[1] - h / 2.0F - belowY) * t, h);
                tx = fx - this.drag().targetX();
                ty = fy - this.drag().targetY();
            }
        }

        float k = 1.0F - (float)Math.exp(-dt * 12.0F);
        this.followX = this.followX + (tx - this.followX) * k;
        this.followY = this.followY + (ty - this.followY) * k;
    }

    private static LivingEntity hovered() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult hit) {
            if (!(hit.getEntity() instanceof LivingEntity e && e != mc.player)) {
                return null;
            } else {
                return e.isAlive() && !e.isRemoved() && !(e.getHealth() <= 0.0F) && !e.isInvisible() ? e : null;
            }
        } else {
            return null;
        }
    }

    private static int armorPieces(LivingEntity e) {
        int n = 0;

        for (EquipmentSlot s : SLOTS) {
            if (!e.getItemBySlot(s).isEmpty()) {
                n++;
            }
        }

        return n;
    }

    private static float distance(LivingEntity e) {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0.0F : p.distanceTo(e);
    }

    private static Component name(LivingEntity e) {
        if (e instanceof Player p) {
            Component t = NameTags.displayName(p);
            if (t != null) {
                return t;
            }
        }

        return Component.literal(e.getName().getString());
    }

    private static void drawName(GuiGraphics c, Component text, float x, float y, float size, float a) {
        float[] cursor = new float[]{x};
        StringBuilder run = new StringBuilder();
        int[] color = new int[]{-1};
        text.visit((style, s) -> {
            StringDecomposer.iterateFormatted(s, style, (i, st, cp) -> {
                int col = st.getColor() != null ? 0xFF000000 | st.getColor().getValue() : -1;
                if (col != color[0] && !run.isEmpty()) {
                    R2D.text(c, MsdfFont.Font.SEMIBOLD, run.toString(), cursor[0], y, size, Colors.multAlpha(color[0], a));
                    cursor[0] += R2D.width(MsdfFont.Font.SEMIBOLD, run.toString(), size);
                    run.setLength(0);
                }

                color[0] = col;
                run.appendCodePoint(cp);
                return true;
            });
            return Optional.empty();
        }, Style.EMPTY);
        if (!run.isEmpty()) {
            R2D.text(c, MsdfFont.Font.SEMIBOLD, run.toString(), cursor[0], y, size, Colors.multAlpha(color[0], a));
        }
    }

    private static float coloredWidth(Component text, float size) {
        String plain = ChatFormatting.stripFormatting(text.getString());
        return R2D.width(MsdfFont.Font.SEMIBOLD, plain == null ? "" : plain, size);
    }

    private static float clamp01(float f) {
        return f < 0.0F ? 0.0F : Math.min(f, 1.0F);
    }

    private static float easeInOutCubic(float f) {
        f = clamp01(f);
        return f < 0.5F ? 4.0F * f * f * f : 1.0F - (float)Math.pow(-2.0F * f + 2.0F, 3.0) / 2.0F;
    }

    private static float easeOutCubic(float f) {
        float u = 1.0F - clamp01(f);
        return 1.0F - u * u * u;
    }

    private static float easeOutBack(float f) {
        f = clamp01(f);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = f - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private record Particle(float x, float y, float dx, float dy, long start, long life, float gradient) {
    }
}
