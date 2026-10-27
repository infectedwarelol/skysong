package dev.infectedware.skysong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.infectedware.skysong.module.ModuleRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

public final class ModConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skysong.json");
    private static final Class<?>[] HOLDERS = new Class<?>[]{ModSettings.class};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Field, Object> DEFAULTS = captureDefaults();
    private static final String EFFECT_LIST_KEY = "effectListVersion";
    private static final int EFFECT_LIST_VERSION = 4;
    private static final Set<String> PERSONAL = Set.of(
        "spotifyClientId", "popupPositions", "shareMySong", "partyEnabled", "partyShareSong", "partyRelayUrl", "partyKey"
    );
    private static final Set<String> KEPT_ON_RESET = Set.of("partyKey");

    private ModConfig() {
    }

    public static void load() {
        if (!Files.exists(FILE)) {
            ModuleRegistry.disableAll();
            save();
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            migrateEffects(json);
            apply(json, true);
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not load config: {}", e.getMessage());
        }
    }

    public static void migrateEffects(JsonObject json) {
        if (json == null) {
            return;
        }

        int version = json.has(EFFECT_LIST_KEY) && json.get(EFFECT_LIST_KEY).isJsonPrimitive() ? json.get(EFFECT_LIST_KEY).getAsInt() : 1;
        if (version < 2) {
            remap(json, "killEffectType", 6, 11);
            remap(json, "deathEffectType", 4, 8);
        }

        if (version < 3) {
            remap(json, "killEffectType", 11, 21);
            remap(json, "deathEffectType", 8, 18);
        }

        if (version < 4) {
            remap(json, "killEffectType", 21, 22);
        }

        json.addProperty(EFFECT_LIST_KEY, EFFECT_LIST_VERSION);
    }

    private static void remap(JsonObject json, String key, int from, int to) {
        if (json.has(key) && json.get(key).isJsonPrimitive() && json.get(key).getAsInt() == from) {
            json.addProperty(key, to);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(snapshot(false, true)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not save config: {}", e.getMessage());
        }
    }

    public static JsonObject snapshot(boolean onlyChanged, boolean personal) {
        JsonObject json = new JsonObject();
        json.addProperty(EFFECT_LIST_KEY, EFFECT_LIST_VERSION);

        try {
            for (Class<?> holder : HOLDERS) {
                for (Field f : holder.getFields()) {
                    if (isSetting(f) && (personal || !PERSONAL.contains(f.getName()))) {
                        Object v = f.get(null);
                        if (!onlyChanged || !Objects.equals(v, DEFAULTS.get(f))) {
                            put(json, f.getName(), v);
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not read settings: {}", e.getMessage());
        }

        return json;
    }


    private static void put(JsonObject json, String name, Object value) {
        if (value instanceof Boolean b) {
            json.addProperty(name, b);
        } else if (value instanceof Number n) {
            json.addProperty(name, n);
        } else if (value instanceof String str) {
            json.addProperty(name, str);
        }
    }

    public static void apply(JsonObject json, boolean personal) {
        for (Class<?> holder : HOLDERS) {
            for (Field f : holder.getFields()) {
                if (isSetting(f) && json.has(f.getName()) && (personal || !PERSONAL.contains(f.getName()))) {
                    try {
                        JsonElement v = json.get(f.getName());
                        if (v.isJsonPrimitive()) {
                            JsonPrimitive p = v.getAsJsonPrimitive();
                            if (f.getType() == boolean.class && p.isBoolean()) {
                                f.setBoolean(null, p.getAsBoolean());
                            } else if (f.getType() == int.class && p.isNumber()) {
                                f.setInt(null, p.getAsInt());
                            } else if (f.getType() == float.class && p.isNumber() && Float.isFinite(p.getAsFloat())) {
                                f.setFloat(null, p.getAsFloat());
                            } else {
                                if (f.getType() != String.class || !p.isString()) {
                                    continue;
                                }

                                f.set(null, p.getAsString());
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

    }

    public static List<Field> sharedFields() {
        List<Field> out = new ArrayList<>();

        for (Field f : DEFAULTS.keySet()) {
            if (!PERSONAL.contains(f.getName())) {
                out.add(f);
            }
        }

        out.sort(Comparator.comparing(Field::getName));
        return out;
    }

    public static Object defaultOf(Field f) {
        return DEFAULTS.get(f);
    }

    public static void resetShared() {
        try {
            for (Entry<Field, Object> e : DEFAULTS.entrySet()) {
                if (!PERSONAL.contains(e.getKey().getName())) {
                    e.getKey().set(null, e.getValue());
                }
            }
        } catch (IllegalAccessException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not reset shared settings: {}", e.getMessage());
        }
    }

    public static void resetToDefaults() {
        try {
            for (Entry<Field, Object> e : DEFAULTS.entrySet()) {
                if (!KEPT_ON_RESET.contains(e.getKey().getName())) {
                    e.getKey().set(null, e.getValue());
                }
            }
        } catch (IllegalAccessException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not reset config: {}", e.getMessage());
        }

        ModuleRegistry.disableAll();
        save();
    }

    private static Map<Field, Object> captureDefaults() {
        Map<Field, Object> defaults = new LinkedHashMap<>();

        for (Class<?> holder : HOLDERS) {
            for (Field f : holder.getFields()) {
                if (isSetting(f)) {
                    try {
                        defaults.put(f, f.get(null));
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
        }

        return defaults;
    }

    private static boolean isSetting(Field f) {
        int m = f.getModifiers();
        Class<?> t = f.getType();
        return Modifier.isStatic(m) && !Modifier.isFinal(m) && (t == boolean.class || t == int.class || t == float.class || t == String.class);
    }
}
