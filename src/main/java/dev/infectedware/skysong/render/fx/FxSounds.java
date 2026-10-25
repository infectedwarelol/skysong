package dev.infectedware.skysong.render.fx;

import dev.infectedware.skysong.compat.Ids;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.sounds.SoundEvent;

final class FxSounds {
    private static final Map<String, SoundEvent> EVENTS = new ConcurrentHashMap<>();

    private FxSounds() {
    }

    static SoundEvent of(String prefix, String effectName) {
        String id = prefix + "_" + effectName.toLowerCase(Locale.ROOT).replace(' ', '_');
        return EVENTS.computeIfAbsent(id, key -> SoundEvent.createVariableRangeEvent(Ids.of("skysong", "fx." + key)));
    }
}
