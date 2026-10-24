package com.spotifyclient.hud;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.Compat;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.components.Smooth;
import com.spotifyclient.render.r2d.Backdrop;
import com.spotifyclient.render.r2d.R2D;
import com.spotifyclient.theme.ClientAccent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CustomHotbar {
    private static final Smooth selection = new Smooth(0.0F);
    private static int lastSlot = -1;

    private CustomHotbar() {
    }

    public static boolean render(GuiGraphics c, float tick) {
        Minecraft mc = Minecraft.getInstance();
        if (ModSettings.customHotbarEnabled && R2D.available() && mc.getCameraEntity() instanceof Player player) {
            Backdrop.captureForHud(c, tick);
            int var19 = Compat.selectedSlot(player);
            if (lastSlot < 0) {
                selection.set(var19);
            } else if (var19 != lastSlot) {
                selection.run(var19, 0.22);
            }

            lastSlot = var19;
            float center = c.guiWidth() / 2.0F - 1.0F;
            float y = c.guiHeight() - 22.0F;
            float x = center - 90.0F;
            float r = Math.min(ModSettings.rectCornerRadius, 11.0F);
            R2D.clientRect(c, x - 1.0F, y, 182.0F, 22.0F, 1.0F);
            float sx = center - 90.0F + selection.get() * 20.0F;
            float sy = y + 1.0F;
            float sr = Math.min(ModSettings.rectCornerRadius, 10.0F);
            R2D.clientRectNoGlow(c, sx, sy, 20.0F, 20.0F, sr, 0.9F);
            R2D.outline(
                c,
                sx,
                sy,
                20.0F,
                20.0F,
                sr,
                0.75F,
                ClientAccent.gradientColor(0.0F, 190.0F),
                ClientAccent.gradientColor(0.33F, 190.0F),
                ClientAccent.gradientColor(1.0F, 190.0F),
                ClientAccent.gradientColor(0.66F, 190.0F)
            );
            ItemStack offhand = player.getOffhandItem();
            boolean offhandLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
            if (!offhand.isEmpty()) {
                float ox = offhandLeft ? center - 120.0F : center + 97.0F;
                R2D.clientRect(c, ox, y - 1.0F, 24.0F, 24.0F, 1.0F);
                R2D.outline(
                    c,
                    ox,
                    y - 1.0F,
                    24.0F,
                    24.0F,
                    Math.min(ModSettings.rectCornerRadius, 12.0F),
                    0.5F,
                    ClientAccent.gradientColor(0.0F, 144.0F),
                    ClientAccent.gradientColor(0.33F, 144.0F),
                    ClientAccent.gradientColor(1.0F, 144.0F),
                    ClientAccent.gradientColor(0.66F, 144.0F)
                );
            }

            float partial = tick;
            int seed = 1;

            for (int i = 0; i < 9; i++) {
                int ix = (int)(center - 90.0F + i * 20.0F + 2.0F);
                int iy = (int)(y + 3.0F);
                item(c, player, player.getInventory().getItem(i), ix, iy, partial, seed++);
            }

            if (!offhand.isEmpty()) {
                int ox = (int)(offhandLeft ? center - 120.0F + 4.0F : center + 97.0F + 4.0F);
                item(c, player, offhand, ox, (int)(y + 3.0F), partial, seed);
            }

            return true;
        } else {
            return false;
        }
    }

    private static void item(GuiGraphics c, Player player, ItemStack stack, int x, int y, float partial, int seed) {
        if (!stack.isEmpty()) {
            float pop = stack.getPopTime() - partial;
            if (pop > 0.0F) {
                float s = 1.0F + pop / 5.0F;
                c.pose().pushPose();
                c.pose().translate(x + 8, y + 12, 0.0F);
                c.pose().scale(1.0F / s, (s + 1.0F) / 2.0F, 1.0F);
                c.pose().translate(-(x + 8), -(y + 12), 0.0F);
            }

            c.renderItem(player, stack, x, y, seed);
            if (pop > 0.0F) {
                c.pose().popPose();
            }

            c.renderItemDecorations(Minecraft.getInstance().font, stack, x, y);
        }
    }
}
