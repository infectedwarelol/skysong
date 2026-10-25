package dev.infectedware.skysong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
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
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("spotify_mod.json");
    private static final Class<?>[] HOLDERS = new Class<?>[]{ModSettings.class};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Field, Object> DEFAULTS = captureDefaults();
    private static final Set<String> PERSONAL = Set.of("spotifyClientId", "popupPositions", "shareMySong");

    private ModConfig() {
    }

    public static void load() {
        boolean migrate = !Files.exists(FILE) && Files.exists(LEGACY_FILE);
        Path source = migrate ? LEGACY_FILE : FILE;
        if (Files.exists(source)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();
                if (migrate && json.has("theme") && "VERSEFALL".equals(json.get("theme").getAsString())) {
                    json.addProperty("theme", "SKYSONG");
                }

                migrateEffects(json);
                apply(json, true);
                if (migrate) {
                    save();
                    SkysongClient.LOGGER.info("[Skysong] Imported settings from {}", LEGACY_FILE.getFileName());
                }
            } catch (Exception e) {
                SkysongClient.LOGGER.warn("[Skysong] Could not load config: {}", e.getMessage());
            }
        }
    }

    public static void migrateEffects(JsonObject json) {
        if (json == null || json.has("effectListVersion")) {
            return;
        }

        if (json.has("killEffectType") && json.get("killEffectType").isJsonPrimitive() && json.get("killEffectType").getAsInt() == 6) {
            json.addProperty("killEffectType", 11);
        }

        if (json.has("deathEffectType") && json.get("deathEffectType").isJsonPrimitive() && json.get("deathEffectType").getAsInt() == 4) {
            json.addProperty("deathEffectType", 8);
        }

        json.addProperty("effectListVersion", 2);
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

        try {
            for (Class<?> holder : HOLDERS) {
                for (Field f : holder.getFields()) {
                    if (isSetting(f) && (personal || !PERSONAL.contains(f.getName()))) {
                        Object v = f.get(null);
                        if (!onlyChanged || !Objects.equals(v, DEFAULTS.get(f))) {
                            if (v instanceof Boolean b) {
                                json.addProperty(f.getName(), b);
                            } else if (v instanceof Number n) {
                                json.addProperty(f.getName(), n);
                            } else if (v instanceof String str) {
                                json.addProperty(f.getName(), str);
                            }
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not read settings: {}", e.getMessage());
        }

        return json;
    }

    public static int apply(JsonObject json, boolean personal) {
        int count = 0;

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

                            count++;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return count;
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
                e.getKey().set(null, e.getValue());
            }
        } catch (IllegalAccessException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not reset config: {}", e.getMessage());
        }

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
