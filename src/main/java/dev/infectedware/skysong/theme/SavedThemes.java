package dev.infectedware.skysong.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.infectedware.skysong.ModConfig;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.hud.Feedback;
import dev.infectedware.skysong.render.fx.Fx;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class SavedThemes {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skysong_themes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_NAME = 24;
    private static final String CODE_PREFIX = "SKT1-";
    private static final int MAX_CODE_BYTES = 65536;
    private static final List<String> THEME_PREFIXES = List.of(
        "theme", "clientColorMode", "gui", "rainbow", "rect", "gradientStyle", "dynamic", "lyric", "shaderColor", "shaderGlass",
        "textDropShadows", "cinematicFades", "nowPlayingStyle", "hudLyricsStyle", "hudIcons"
    );
    private static final Set<String> NOT_THEME = Set.of(
        "lyricOffsetMs", "lyricSpawnDistMin", "lyricSpawnDistMax", "lyricFollowPlayer", "lyricThroughWalls", "lyricShowMode"
    );
    private static List<SavedThemes.Theme> themes;
    private static String active;

    private SavedThemes() {
    }

    public static boolean isActive(String name) {
        return name.equals(active);
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();

        for (SavedThemes.Theme theme : all()) {
            out.add(theme.name());
        }

        return out;
    }

    public static void save(String rawName) {
        String name = cleanName(rawName);
        if (name.isEmpty()) {
            name = "Theme " + (all().size() + 1);
        }

        SavedThemes.Theme theme = new SavedThemes.Theme(name, capture());
        int at = indexOf(name);
        boolean replaced = at >= 0;
        if (replaced) {
            all().set(at, theme);
        } else {
            all().add(theme);
        }

        write();
        active = name;
        Feedback.show((replaced ? "Updated theme " : "Saved theme ") + name, 2500L);
    }

    public static boolean load(String name) {
        int at = indexOf(name);
        if (at < 0) {
            Feedback.show("No theme named " + name, 2500L);
            return false;
        }

        ModConfig.apply(all().get(at).settings(), false);
        ModConfig.save();
        Fx.reset("theme changed");
        active = all().get(at).name();
        Feedback.show("Loaded theme " + active, 2500L);
        return true;
    }

    public static void delete(String name) {
        int at = indexOf(name);
        if (at >= 0) {
            all().remove(at);
            write();
            if (name.equalsIgnoreCase(active)) {
                active = null;
            }

            Feedback.show("Deleted theme " + name, 2500L);
        }
    }

    public static String exportCode() {
        JsonObject root = new JsonObject();
        root.addProperty("name", active == null ? "Shared theme" : active);
        root.add("settings", capture());
        byte[] json = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

        try {
            deflater.setInput(json);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }

            return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } finally {
            deflater.end();
        }
    }

    public static void copyCode() {
        Minecraft.getInstance().keyboardHandler.setClipboard(exportCode());
        Feedback.show("Theme code copied", 2500L);
    }

    public static boolean importClipboard() {
        return importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    public static boolean importCode(String text) {
        JsonObject root = decode(text);
        if (root == null) {
            Feedback.show("That is not a Skysong theme code", 2500L);
            return false;
        }

        String name = cleanName(root.get("name") != null && root.get("name").isJsonPrimitive() ? root.get("name").getAsString() : "");
        if (name.isEmpty()) {
            name = "Shared theme";
        }

        SavedThemes.Theme theme = new SavedThemes.Theme(name, onlyThemeFields(root.getAsJsonObject("settings")));
        int at = indexOf(name);
        if (at >= 0) {
            all().set(at, theme);
        } else {
            all().add(theme);
        }

        write();
        return load(name);
    }

    private static JsonObject decode(String text) {
        if (text == null) {
            return null;
        }

        String code = text.trim();
        if (!code.startsWith(CODE_PREFIX)) {
            return null;
        }

        Inflater inflater = new Inflater();

        try {
            inflater.setInput(Base64.getUrlDecoder().decode(code.substring(CODE_PREFIX.length())));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    return null;
                }

                out.write(buffer, 0, read);
                if (out.size() > MAX_CODE_BYTES) {
                    return null;
                }
            }

            JsonElement parsed = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return null;
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonElement settings = root.get("settings");
            return settings != null && settings.isJsonObject() ? root : null;
        } catch (IllegalArgumentException | DataFormatException | JsonParseException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    static boolean isThemeField(String name) {
        if (NOT_THEME.contains(name)) {
            return false;
        }

        if (name.contains("Color") || name.endsWith("MatchGui")) {
            return true;
        }

        for (String prefix : THEME_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private static JsonObject capture() {
        JsonObject everything = ModConfig.snapshot(false, false);
        JsonObject theme = new JsonObject();

        for (Field field : ModConfig.sharedFields()) {
            String name = field.getName();
            if (isThemeField(name) && everything.has(name)) {
                theme.add(name, everything.get(name));
            }
        }

        return theme;
    }

    private static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }

        String cleaned = raw.replaceAll("\\p{Cntrl}", "").trim();
        return cleaned.length() > MAX_NAME ? cleaned.substring(0, MAX_NAME).trim() : cleaned;
    }

    private static int indexOf(String name) {
        return indexIn(all(), name);
    }

    private static List<SavedThemes.Theme> all() {
        if (themes == null) {
            themes = read();
        }

        return themes;
    }

    private static List<SavedThemes.Theme> read() {
        List<SavedThemes.Theme> out = new ArrayList<>();
        if (!Files.exists(FILE)) {
            return out;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("themes");
            if (array == null) {
                return out;
            }

            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();
                JsonElement nameElement = entry.get("name");
                JsonElement settingsElement = entry.get("settings");
                if (nameElement == null || !nameElement.isJsonPrimitive() || settingsElement == null || !settingsElement.isJsonObject()) {
                    continue;
                }

                String name = cleanName(nameElement.getAsString());
                if (!name.isEmpty() && indexIn(out, name) < 0) {
                    out.add(new SavedThemes.Theme(name, onlyThemeFields(settingsElement.getAsJsonObject())));
                }
            }
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not read themes: " + e.getMessage());
        }

        return out;
    }

    private static int indexIn(List<SavedThemes.Theme> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }

        return -1;
    }

    private static JsonObject onlyThemeFields(JsonObject settings) {
        JsonObject filtered = new JsonObject();

        for (Entry<String, JsonElement> entry : settings.entrySet()) {
            if (isThemeField(entry.getKey())) {
                filtered.add(entry.getKey(), entry.getValue());
            }
        }

        return filtered;
    }

    private static void write() {
        JsonArray array = new JsonArray();

        for (SavedThemes.Theme theme : all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", theme.name());
            entry.add("settings", theme.settings());
            array.add(entry);
        }

        JsonObject root = new JsonObject();
        root.add("themes", array);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not save themes: " + e.getMessage());
        }
    }

    private record Theme(String name, JsonObject settings) {
    }
}
