package dev.infectedware.skysong.hud.components;

import net.minecraft.util.Mth;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.GuiGraphics;
import dev.infectedware.skysong.hud.DragSystem;
import dev.infectedware.skysong.hud.Draggable;
import dev.infectedware.skysong.render.r2d.MsdfFont;
import dev.infectedware.skysong.render.r2d.R2D;
import dev.infectedware.skysong.theme.Colors;
import dev.infectedware.skysong.ui.Clip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventoryComp extends Draggable {
    private static final String TITLE = "Inventory";
    private static final float TITLE_SIZE = 8.2F;
    private static final float PAD_X = 9.0F;
    private static final float HEADER_H = 22.0F;
    private static final float CELL = 11.0F;
    private static final float ITEM = 8.0F;
    private static final float ORIGIN_X = 4.0F;
    private static final float ORIGIN_Y = 28.0F;
    private static final float PANEL_W = 107.0F;
    private final Smooth visibility = new Smooth(0.0F);
    private final Smooth widthAnim = new Smooth(107.0F);
    private final Smooth divider = new Smooth(0.0F);
    private final Smooth extent = new Smooth(0.0F);
    private final Smooth headerIcon = new Smooth(0.0F);
    private final float[] slot = new float[27];
    private final ItemStack[] shownStacks = new ItemStack[27];
    private List<ItemStack> stacks = new ArrayList<>();
    private float w = 107.0F;
    private float h = 64.0F;
    private boolean everHad;
    private boolean lastVisible;
    private long collapsedAt;
    private long lastNs = System.nanoTime();

    public InventoryComp() {
        super("inventory", 5.0F, 88.0F);
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
    public boolean interactive() {
        return ModSettings.inventoryEnabled;
    }

    private float titleMinWidth() {
        float base = R2D.width(MsdfFont.Font.SEMIBOLD, "Inventory", 8.2F) + 14.0F;
        float p = this.headerIcon.get();
        if (p <= 0.001F) {
            return base;
        }

        float[] tb = R2D.bounds(MsdfFont.Font.SEMIBOLD, "Inventory", 8.2F);
        float[] ib = R2D.bounds(MsdfFont.Font.ICONS, "n", 7.0F);
        return base + Math.max(0.0F, 18.0F + (tb[2] - tb[0]) + 6.0F + (ib[2] - ib[0]) - base) * p;
    }

    @Override
    protected void render(GuiGraphics c) {
        boolean on = ModSettings.inventoryEnabled;
        boolean dragMode = DragSystem.get().dragMode();
        this.stacks = on ? read() : new ArrayList<>();
        boolean hasItems = false;

        for (ItemStack s : this.stacks) {
            if (!s.isEmpty()) {
                hasItems = true;
                break;
            }
        }

        boolean chat = Minecraft.getInstance().screen instanceof ChatScreen;
        boolean previewRows = on && (dragMode || chat) && !hasItems;
        int lastRow = -1;

        for (int r = 0; r < 3; r++) {
            if (previewRows || this.rowHasItem(r) || this.rowPresence(r, false) > 0.02F) {
                lastRow = r;
            }
        }

        this.extent.target(lastRow + 1, lastRow + 1 >= this.extent.get() ? 0.22 : 0.18);
        float ext = this.extent.get();
        boolean content = on && (hasItems || dragMode || chat);
        boolean divOn = hasItems || previewRows;
        this.divider.target(divOn ? 1.0F : 0.0F, divOn ? 0.24 : 0.14);
        float dv = ListHudComp.smoothstep(this.divider.get());
        float full = 28.0F + ext * 11.0F + 3.0F;
        this.h = 22.0F + (full - 22.0F) * Mth.clamp(ext, dv, 1.0F);
        this.headerIcon.target(ModSettings.hudIcons ? 1.0F : 0.0F, ModSettings.hudIcons ? 0.25 : 0.2);
        float minW = this.titleMinWidth();
        boolean collapsed = ext < 0.05F && dv < 0.05F;
        this.widthAnim.target(!content && collapsed ? minW : 107.0F, 0.24);
        this.w = Math.max(minW, this.widthAnim.get());
        boolean visible = this.visible(content, minW);
        if (visible != this.lastVisible) {
            this.visibility.run(visible ? 1.0F : 0.0F, visible ? 0.18 : 0.12);
            this.lastVisible = visible;
        }

        float a = this.visibility.get();
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (float)(now - this.lastNs) / 1.0E9F);
        this.lastNs = now;
        boolean itemsReady = a >= 0.9F && this.w >= 106.0F;
        float k = 1.0F - (float)Math.exp(-dt * 13.0F);

        for (int i = 0; i < 27; i++) {
            ItemStack s = i < this.stacks.size() ? this.stacks.get(i) : ItemStack.EMPTY;
            if (!s.isEmpty()) {
                this.shownStacks[i] = s;
            }

            this.slot[i] = this.slot[i] + ((!s.isEmpty() && itemsReady ? 1.0F : 0.0F) - this.slot[i]) * k;
        }

        if (!(a <= 0.01F)) {
            float x = this.x();
            float y = this.y();
            pushScale(c, x + this.w / 2.0F, y + this.h / 2.0F, 0.96F + a * 0.04F);
            R2D.clientRect(c, x, y, this.w, this.h, a);
            this.drawHeader(c, x, y, a);
            float dw = Math.max(0.0F, this.w - 18.0F) * dv;
            if (dw > 0.25F) {
                R2D.rect(c, x + (this.w - dw) / 2.0F, y + 22.0F, dw, 0.5F, 0.0F, Colors.multAlpha(-1997220937, a * dv));
            }

            Clip.push(c, x, y, x + this.w, y + this.h);
            this.drawSeparators(c, x, y, a, lastRow, previewRows);

            for (int i = 0; i < 27; i++) {
                float p = this.slot[i];
                ItemStack s = this.shownStacks[i];
                if (!(p <= 0.02F) && s != null && !s.isEmpty()) {
                    float size = 8.0F * easeOutBack(p) * Math.min(1.0F, a * p);
                    HudItems.item(c, s, cellX(x, i) + 0.75F + 4.0F, cellY(y, i) + 0.75F + 4.0F, size, true);
                }
            }

            Clip.pop(c);
            c.pose().popPose();
        }
    }

    private void drawHeader(GuiGraphics c, float x, float y, float a) {
        float ip = this.headerIcon.get();
        float[] tb = R2D.bounds(MsdfFont.Font.SEMIBOLD, "Inventory", 8.2F);
        float tw = tb[2] - tb[0];
        float cy = y + 11.0F;
        float ty = cy - (tb[1] + tb[3]) / 2.0F;
        float centered = x + (this.w - tw) / 2.0F - tb[0];
        float left = x + 9.0F - tb[0];
        R2D.text(c, MsdfFont.Font.SEMIBOLD, "Inventory", centered + (left - centered) * ip, ty, 8.2F, Colors.multAlpha(ListHudComp.titleColor(), a));
        if (ip > 0.004F) {
            float[] ib = R2D.bounds(MsdfFont.Font.ICONS, "n", 7.0F);
            R2D.text(
                c,
                MsdfFont.Font.ICONS,
                "n",
                x + this.w - 9.0F - ib[2] + 5.0F * (1.0F - ip),
                cy - (ib[1] + ib[3]) / 2.0F,
                7.0F,
                Colors.multAlpha(ListHudComp.titleColor(), a * ip * 0.8F)
            );
        }
    }

    private void drawSeparators(GuiGraphics c, float x, float y, float a, int lastRow, boolean preview) {
        for (int r = 0; r <= lastRow; r++) {
            float rowP = r == lastRow ? this.rowPresence(r, preview) : 1.0F;
            if (!(rowP <= 0.02F)) {
                int vertical = Colors.multAlpha(-1, a * 0.12F * rowP);

                for (int col = 0; col < 8; col++) {
                    int i = r * 9 + col;
                    R2D.rect(c, cellX(x, i) + 10.0F, cellY(y, i), 0.5F, 9.0F, 0.0F, vertical);
                }

                if (r < lastRow) {
                    float nextP = r + 1 == lastRow ? this.rowPresence(r + 1, preview) : 1.0F;
                    int horizontal = Colors.multAlpha(-1, a * 0.12F * Math.min(rowP, nextP));

                    for (int col = 0; col < 9; col++) {
                        int i = r * 9 + col;
                        R2D.rect(c, cellX(x, i) - 0.5F, cellY(y, i) + 10.0F, 9.0F, 0.5F, 0.0F, horizontal);
                    }
                }
            }
        }
    }

    private boolean visible(boolean content, float minW) {
        if (content) {
            this.everHad = true;
            this.collapsedAt = 0L;
            return true;
        }

        if (!this.everHad) {
            return false;
        }

        boolean animating = this.extent.get() > 0.02F || this.divider.get() > 0.004F || this.h > 22.5F || this.widthAnim.get() > minW + 0.5F;
        if (animating) {
            this.collapsedAt = 0L;
            return true;
        }

        if (this.collapsedAt == 0L) {
            this.collapsedAt = System.currentTimeMillis();
        }

        return System.currentTimeMillis() - this.collapsedAt < 100L;
    }

    private float rowPresence(int r, boolean preview) {
        if (preview) {
            return 1.0F;
        }

        float max = 0.0F;

        for (int i = r * 9; i < r * 9 + 9; i++) {
            max = Math.max(max, this.slot[i]);
        }

        return max;
    }

    private boolean rowHasItem(int r) {
        for (int i = r * 9; i < r * 9 + 9 && i < this.stacks.size(); i++) {
            if (!this.stacks.get(i).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static List<ItemStack> read() {
        List<ItemStack> out = new ArrayList<>(27);
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            for (int i = 9; i < 36; i++) {
                out.add(p.getInventory().getItem(i));
            }
        }

        return out;
    }

    private static float cellX(float x, int i) {
        return x + 4.0F + 1.0F + i % 9 * 11.0F;
    }

    private static float cellY(float y, int i) {
        return y + 28.0F + 1.0F + (float)(i / 9) * 11.0F;
    }

    private static float easeOutBack(float f) {
        f = Math.clamp(f, 0.0F, 1.0F);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = f - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }
}
