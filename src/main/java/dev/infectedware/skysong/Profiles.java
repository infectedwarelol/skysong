package dev.infectedware.skysong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.infectedware.skysong.hud.Notifications;
import dev.infectedware.skysong.render.fx.Fx;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

public final class Profiles {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skysong_profiles.json");
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("spotify_mod_profiles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Profiles.Profile> profiles;
    private static String status = "";
    private static String active;
    private static final Path BACKUP = FabricLoader.getInstance().getConfigDir().resolve("skysong_backup.json");
    private static JsonObject lastApplied;

    private Profiles() {
    }

    public static String status() {
        return status;
    }

    public static boolean isActive(String name) {
        return name.equals(active);
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();

        for (Profiles.Profile p : all()) {
            out.add(p.name());
        }

        return out;
    }

    public static void save(String rawName) {
        String name = cleanName(rawName);
        if (name.isEmpty()) {
            name = "Profile " + (all().size() + 1);
        }

        List<Profiles.Profile> list = all();
        Profiles.Profile p = new Profiles.Profile(name, ModConfig.snapshot(false, false));
        int at = indexOf(name);
        boolean replaced = at >= 0;
        if (replaced) {
            list.set(at, p);
        } else {
            list.add(p);
        }

        write();
        active = name;
        say((replaced ? "Updated profile " : "Saved profile ") + name);
    }

    public static void load(String name) {
        int at = indexOf(name);
        if (at < 0) {
            say("No profile named " + name);
            return;
        }

        backup();
        ModConfig.resetShared();
        ModConfig.migrateEffects(all().get(at).settings());
        ModConfig.apply(all().get(at).settings(), false);
        applied();
        active = name;
        say("Loaded profile " + name);
    }

    public static void delete(String name) {
        int at = indexOf(name);
        if (at >= 0) {
            all().remove(at);
            write();
            if (name.equals(active)) {
                active = null;
            }

            say("Deleted profile " + name);
        }
    }

    public static String exportCode() {
        return ShareCode.encode();
    }

    public static void copyCode() {
        String code = exportCode();
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        say(
            code.length() <= 256
                ? "Code copied (" + code.length() + " characters): send it to a friend"
                : "Code copied (" + code.length() + " characters): too long for Minecraft chat, send it on Discord or save it as a file"
        );
    }

    public static void importCode(String text) {
        ShareCode.Result r = ShareCode.decode(text);
        if (!r.ok()) {
            say(r.error());
        } else {
            JsonObject json = new JsonObject();
            r.settings().forEach((k, v) -> {
                if (v instanceof Boolean b) {
                    json.addProperty(k, b);
                } else if (v instanceof Number n) {
                    json.addProperty(k, n);
                } else if (v instanceof String str) {
                    json.addProperty(k, str);
                }
            });
            backup();
            ModConfig.resetShared();
            ModConfig.apply(json, false);
            applied();
            active = null;
            say("Code loaded: " + r.settings().size() + " settings (Undo brings yours back)");
        }
    }

    public static void importClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (!clip.isBlank()) {
            importCode(clip);
            return;
        }

        say("The clipboard is empty: copy the code first");
    }

    public static void saveCodeFile() {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("skysong");
            Files.createDirectories(dir);
            Path file = dir.resolve("skysong-settings.txt");
            Files.writeString(
                file,
                "Skysong settings. Drop this file on the Skysong menu in Minecraft, or copy the code:\n\n" + exportCode() + "\n",
                StandardCharsets.UTF_8
            );
            Util.getPlatform().openFile(dir.toFile());
            say("Saved skysong-settings.txt: send the file to a friend");
        } catch (Exception e) {
            say("Could not save the file: " + e.getMessage());
        }
    }

    public static void importFiles(List<Path> files) {
        for (Path f : files) {
            try {
                if (Files.isRegularFile(f) && Files.size(f) <= 1048576L) {
                    importCode(Files.readString(f, StandardCharsets.UTF_8));
                    return;
                }
            } catch (Exception e) {
                say("Could not read " + f.getFileName());
                return;
            }
        }

        say("That isn't a code file");
    }

    private static void backup() {
        JsonObject now = ModConfig.snapshot(false, false);
        if (!now.equals(lastApplied) || !canUndo()) {
            try {
                Files.writeString(BACKUP, GSON.toJson(ModConfig.snapshot(false, false)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                SkysongClient.LOGGER.warn("[Skysong] Could not back up settings: {}", e.getMessage());
            }
        }
    }

    public static boolean canUndo() {
        return Files.exists(BACKUP);
    }

    public static void undo() {
        if (!canUndo()) {
            say("Nothing to undo");
            return;
        }

        try {
            JsonObject before = JsonParser.parseString(Files.readString(BACKUP, StandardCharsets.UTF_8)).getAsJsonObject();
            Files.delete(BACKUP);
            ModConfig.resetShared();
            ModConfig.apply(before, false);
            applied();
            active = null;
            say("Your settings are back");
        } catch (Exception e) {
            say("Could not undo: " + e.getMessage());
        }
    }

    static JsonObject decodeV1(String code) {
        if (code == null) {
            return null;
        }

        code = code.replaceAll("\\s", "");
        if (!code.startsWith("VF1-")) {
            return null;
        }

        try {
            byte[] packed = Base64.getUrlDecoder().decode(code.substring("VF1-".length()));
            Inflater inf = new Inflater(true);
            inf.setInput(packed);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];

            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }

                out.write(buf, 0, n);
                if (out.size() > 262144) {
                    return null;
                }
            }

            inf.end();
            JsonObject root = JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            return root.has("mod") && "skysong".equals(root.get("mod").getAsString()) && root.get("s").isJsonObject() ? root.getAsJsonObject("s") : null;
        } catch (IllegalArgumentException | IllegalStateException | DataFormatException | UnsupportedOperationException | JsonParseException | NullPointerException e) {
            return null;
        }
    }

    private static void applied() {
        lastApplied = ModConfig.snapshot(false, false);
        ModConfig.save();
        Fx.reset("settings changed");
    }

    private static void say(String text) {
        status = text;
        Notifications.notify(text, 2500L);
    }

    private static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }

        String s = raw.replaceAll("\\p{Cntrl}", "").trim();
        return s.length() > 24 ? s.substring(0, 24).trim() : s;
    }

    private static int indexOf(String name) {
        List<Profiles.Profile> list = all();

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }

        return -1;
    }

    private static List<Profiles.Profile> all() {
        if (profiles == null) {
            profiles = read();
        }

        return profiles;
    }

    private static List<Profiles.Profile> read() {
        List<Profiles.Profile> out = new ArrayList<>();
        Path source = Files.exists(FILE) ? FILE : LEGACY_FILE;
        if (!Files.exists(source)) {
            return out;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();

            for (JsonElement e : root.getAsJsonArray("profiles")) {
                JsonObject o = e.getAsJsonObject();
                String name = cleanName(o.get("name").getAsString());
                if (!name.isEmpty() && o.get("settings").isJsonObject()) {
                    out.add(new Profiles.Profile(name, o.getAsJsonObject("settings")));
                }
            }
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not read profiles: {}", e.getMessage());
        }

        return out;
    }

    private static void write() {
        JsonArray arr = new JsonArray();

        for (Profiles.Profile p : all()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name());
            o.add("settings", p.settings());
            arr.add(o);
        }

        JsonObject root = new JsonObject();
        root.add("profiles", arr);

        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not save profiles: {}", e.getMessage());
        }
    }

    private record Profile(String name, JsonObject settings) {
    }
}
