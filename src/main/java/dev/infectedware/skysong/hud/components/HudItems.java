package dev.infectedware.skysong.hud.components;

import dev.infectedware.skysong.compat.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;

public final class HudItems {
    private HudItems() {
    }

    public static void item(GuiGraphics c, ItemStack stack, float cx, float cy, float size, boolean overlay) {
        if (stack != null && !stack.isEmpty() && !(size <= 0.1F)) {
            c.pose().pushPose();
            c.pose().translate(cx, cy, 0.0F);
            c.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
            c.pose().translate(-8.0F, -8.0F, 0.0F);
            c.renderItem(stack, 0, 0);
            if (overlay) {
                c.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
            }

            c.pose().popPose();
        }
    }

    public static void effect(GuiGraphics c, Holder<MobEffect> effect, float x, float y, float size, float alpha) {
        Identifier sprite = Gui.getMobEffectSprite(effect);
        c.pose().pushPose();
        c.pose().translate(x, y, 0.0F);
        c.pose().scale(size / 18.0F, size / 18.0F, 1.0F);
        c.blitSprite(sprite, 0, 0, 18, 18, Math.round(alpha * 255.0F) << 24 | 16777215);
        c.pose().popPose();
    }
}
