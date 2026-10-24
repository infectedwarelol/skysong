package com.spotifyclient.hud.components;

import com.spotifyclient.ModSettings;
import com.spotifyclient.compat.GuiGraphics;
import com.spotifyclient.hud.DragSystem;
import com.spotifyclient.hud.Draggable;
import com.spotifyclient.hud.HudSpace;
import com.spotifyclient.render.r2d.R2D;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ArmorComp extends Draggable {
    private static final float ITEM = 16.0F;
    private static final float PAD = 2.0F;
    private static final float ENTER = 16.0F;
    private static final float EXIT = 26.0F;
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Smooth visibility = new Smooth(0.0F);
    private int side;
    private int previewSide;
    private int sideBeforeDrag;
    private boolean wasDragging;
    private boolean resolved;
    private boolean lastVisible;
    private float w = 20.0F;
    private float h = 20.0F;
    private float previewW = 20.0F;
    private float previewH = 20.0F;
    private List<ItemStack> shown = List.of();

    public ArmorComp() {
        super("armor", 180.0F, 5.0F);
    }

    @Override
    public float width() {
        return this.w;
    }

    @Override
    public float height() {
        return this.h;
    }

    @Override
    public float overlayWidth() {
        return this.previewW;
    }

    @Override
    public float overlayHeight() {
        return this.previewH;
    }

    @Override
    public boolean interactive() {
        return ModSettings.armorEnabled;
    }

    @Override
    protected void render(GuiGraphics c) {
        boolean on = ModSettings.armorEnabled;
        List<ItemStack> items = on ? worn() : List.of();
        if (items.isEmpty() && on && DragSystem.get().dragMode()) {
            items = preview();
        }

        if (!items.isEmpty()) {
            this.shown = items;
        }

        boolean show = on && !items.isEmpty();
        if (show != this.lastVisible) {
            this.visibility.run(show ? 1.0F : 0.0F, show ? 0.18 : 0.12);
            this.lastVisible = show;
        }

        float a = this.visibility.get();
        if (!(a <= 0.01F) && !this.shown.isEmpty()) {
            float length = 4.0F + this.shown.size() * 16.0F;
            float thick = 20.0F;
            float screenW = Math.max(1.0F, HudSpace.width());
            this.updateSide(this.drag().dragging(), length <= screenW - 10.0F, thick, screenW);
            this.w = this.side != 0 ? thick : length;
            this.h = this.side != 0 ? length : thick;
            this.previewW = this.previewSide != 0 ? thick : length;
            this.previewH = this.previewSide != 0 ? length : thick;
            boolean vertical = this.side != 0;
            float x = this.x();
            float y = this.y();
            pushScale(c, x + this.w / 2.0F, y + this.h / 2.0F, 0.96F + a * 0.04F);
            R2D.clientRect(c, x, y, this.w, this.h, a);
            float offset = 0.0F;

            for (ItemStack s : this.shown) {
                float ix = x + 2.0F + (vertical ? 0.0F : offset);
                float iy = y + 2.0F + (vertical ? offset : 0.0F);
                HudItems.item(c, s, ix + 8.0F, iy + 8.0F, 16.0F * Math.min(1.0F, a), true);
                offset += 16.0F;
            }

            c.pose().popPose();
        }
    }

    private static List<ItemStack> worn() {
        List<ItemStack> out = new ArrayList<>();
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            for (EquipmentSlot s : ARMOR) {
                if (!p.getItemBySlot(s).isEmpty()) {
                    out.add(p.getItemBySlot(s));
                }
            }
        }

        return out;
    }

    private static List<ItemStack> preview() {
        return List.of(
            Items.DIAMOND_HELMET.getDefaultInstance(),
            Items.DIAMOND_CHESTPLATE.getDefaultInstance(),
            Items.DIAMOND_LEGGINGS.getDefaultInstance(),
            Items.DIAMOND_BOOTS.getDefaultInstance()
        );
    }

    private void updateSide(boolean dragging, boolean canDock, float thick, float screenW) {
        boolean started = !this.wasDragging && dragging;
        boolean ended = this.wasDragging && !dragging;
        this.wasDragging = dragging;
        if (started) {
            this.sideBeforeDrag = this.side;
        }

        if (dragging) {
            int next = canDock ? edgeSide(HudSpace.mouseX(), screenW, this.previewSide) : 0;
            if (next != 0 != (this.previewSide != 0)) {
                this.drag().swapGrabOffset();
            }

            this.previewSide = next;
            this.resolved = true;
        } else {
            if (ended) {
                this.side = this.drag().wasCancelled() ? this.sideBeforeDrag : this.previewSide;
            } else if (!this.resolved) {
                float x = this.drag().targetX();
                this.side = !canDock ? 0 : (x >= screenW - thick - 6.0F ? 1 : (x <= 6.0F ? -1 : 0));
                this.resolved = true;
            }

            this.previewSide = this.side;
            if (this.side > 0) {
                this.drag().setTargetX(screenW - thick - 5.0F);
            } else if (this.side < 0) {
                this.drag().setTargetX(5.0F);
            }
        }
    }

    private static int edgeSide(float mx, float screenW, int current) {
        if (current > 0) {
            return mx < screenW - 26.0F ? 0 : 1;
        } else if (current < 0) {
            return mx > 26.0F ? 0 : -1;
        } else if (mx >= screenW - 16.0F) {
            return 1;
        } else {
            return mx <= 16.0F ? -1 : 0;
        }
    }
}
