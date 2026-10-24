package dev.infectedware.skysong.hud;

import dev.infectedware.skysong.ModSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Feedback {
    private Feedback() {
    }

    public static void show(String text, long ms) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (ModSettings.notificationsEnabled) {
            Notifications.notify(text, ms);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.gui.setOverlayMessage(Component.literal(text), false);
            }
        });
    }
}
