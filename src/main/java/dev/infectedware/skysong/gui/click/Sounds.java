package dev.infectedware.skysong.gui.click;

import dev.infectedware.skysong.compat.Ids;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class Sounds {
    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static long lastTypingSound;

    private Sounds() {
    }

    public static void play(String id) {
        play(id, 1.0F);
    }

    public static void play(String id, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (id.equals("search_typing")) {
            long now = System.currentTimeMillis();
            if (now - lastTypingSound < 45L) {
                return;
            }

            lastTypingSound = now;
        }

        SoundEvent event = EVENTS.computeIfAbsent(id, k -> SoundEvent.createVariableRangeEvent(Ids.of("skysong", "ui." + k)));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, volume * 0.6F));
    }
}
