package dev.infectedware.skysong.hud.components;

import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.hud.DragSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemCooldowns.CooldownInstance;

public final class CooldownsComp extends ListHudComp {
    private static final Item[] PREVIEW = new Item[]{Items.ENDER_PEARL, Items.CHORUS_FRUIT, Items.MACE};
    private long previewSwitch;
    private int previewIndex;

    public CooldownsComp() {
        super("cooldowns", "Cooldowns", () -> ModSettings.cooldownsEnabled, 295.0F);
    }

    @Override
    protected String headerIcon() {
        return "i";
    }

    @Override
    protected List<ListHudComp.Row> collectRows() {
        List<ListHudComp.Row> rows = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ItemCooldowns cooldowns = player.getCooldowns();
            float partial = Compat.partialTick();

            for (Entry<Item, CooldownInstance> e : Compat.cooldowns(cooldowns)) {
                float ticksLeft = e.getValue().endTime - (cooldowns.tickCount + partial);
                if (!(ticksLeft <= 0.0F)) {
                    ItemStack stack = find(player, e.getKey());
                    String name = cleanName(stack.getHoverName().getString());
                    rows.add(
                        new ListHudComp.Row(
                            e.getKey(),
                            name,
                            format(ticksLeft * 50.0F),
                            (c, x, y, size, a) -> HudItems.item(c, stack, x + size / 2.0F, y + size / 2.0F - 0.5F, 8.0F * a, false)
                        )
                    );
                }
            }
        }

        if (rows.isEmpty() && DragSystem.get().dragMode()) {
            long now = System.currentTimeMillis();
            if (now - this.previewSwitch >= 1000L) {
                this.previewIndex = (this.previewIndex + 1) % PREVIEW.length;
                this.previewSwitch = now;
            }

            ItemStack stack = PREVIEW[this.previewIndex].getDefaultInstance();
            rows.add(
                new ListHudComp.Row(
                    "preview",
                    "Example cooldown",
                    "**:**",
                    (c, x, y, size, a) -> HudItems.item(c, stack, x + size / 2.0F, y + size / 2.0F - 0.5F, 8.0F * a, false)
                )
            );
        }

        return rows;
    }

    private static ItemStack find(LocalPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(item)) {
                return s;
            }
        }

        return item.getDefaultInstance();
    }

    private static String format(float ms) {
        float s = ms / 1000.0F;
        if (s >= 60.0F) {
            int n = Math.round(s);
            return n / 60 + ":" + String.format(Locale.ROOT, "%02d", n % 60);
        } else {
            return String.format(Locale.ROOT, "%.1fs", s);
        }
    }

    private static String cleanName(String s) {
        return s.replaceAll("\\[[^\\[\\]]*]", " ").replaceAll("<[^<>]*>", " ").replaceAll("\\{[^{}]*}", " ").replaceAll("\\s+", " ").trim();
    }
}
