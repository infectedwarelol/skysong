package dev.infectedware.skysong.render;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.compat.Ids;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

public final class CustomFonts {
    public static final Path FONT_DIR = FabricLoader.getInstance().getConfigDir().resolve("skysong").resolve("fonts");
    private static final Path PACK_DIR = FabricLoader.getInstance().getConfigDir().resolve("skysong").resolve(".font-pack");
    private static final String PACK_ID = "skysong_custom_fonts";
    private static final int PACK_FORMAT = 75;
    private static final Gson GSON = new Gson();
    private static volatile Map<String, Identifier> fonts = Map.of();

    private CustomFonts() {
    }

    public static List<String> names() {
        return new ArrayList<>(fonts.keySet());
    }

    public static Identifier font(String name) {
        Map<String, Identifier> current = fonts;
        Identifier exact = name == null ? null : current.get(name);
        if (exact != null || current.isEmpty()) {
            return exact;
        }

        return current.values().iterator().next();
    }

    public static RepositorySource source() {
        return consumer -> {
            refresh();
            Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(PACK_ID, Component.literal("Skysong custom fonts"), PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(PACK_DIR),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
            );
            if (pack != null) {
                consumer.accept(pack);
            }
        };
    }

    public static synchronized void refresh() {
        Map<String, Identifier> found = new LinkedHashMap<>();

        try {
            Files.createDirectories(FONT_DIR);
            Path fontAssets = PACK_DIR.resolve("assets").resolve("skysong").resolve("font");
            Path fileDir = fontAssets.resolve("custom");
            deleteTree(fontAssets);
            Files.createDirectories(fileDir);
            Files.writeString(PACK_DIR.resolve("pack.mcmeta"), packMeta(), StandardCharsets.UTF_8);

            try (Stream<Path> files = Files.list(FONT_DIR)) {
                for (Path file : files.filter(CustomFonts::isFontFile).sorted().toList()) {
                    String fileName = file.getFileName().toString();
                    int dot = fileName.lastIndexOf('.');
                    String displayName = fileName.substring(0, dot);
                    String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
                    String id = uniqueId(sanitize(displayName), found);
                    Files.copy(file, fileDir.resolve(id + "." + extension), StandardCopyOption.REPLACE_EXISTING);
                    Files.writeString(fontAssets.resolve("custom_" + id + ".json"), fontDefinition(id + "." + extension), StandardCharsets.UTF_8);
                    found.put(uniqueName(displayName, found), Ids.of("skysong", "custom_" + id));
                }
            }
        } catch (IOException e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not prepare custom fonts: {}", e.getMessage());
        }

        fonts = Map.copyOf(found);
    }

    private static boolean isFontFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.endsWith(".ttf") || name.endsWith(".otf")) && name.lastIndexOf('.') > 0;
    }

    static String sanitize(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return id.isEmpty() ? "font" : id;
    }

    private static String uniqueId(String base, Map<String, Identifier> taken) {
        String id = base;

        for (int suffix = 2; containsId(taken, id); suffix++) {
            id = base + "_" + suffix;
        }

        return id;
    }

    private static boolean containsId(Map<String, Identifier> taken, String id) {
        for (Identifier identifier : taken.values()) {
            if (identifier.getPath().equals("custom_" + id)) {
                return true;
            }
        }

        return false;
    }

    private static String uniqueName(String base, Map<String, Identifier> taken) {
        String name = base;

        for (int suffix = 2; taken.containsKey(name); suffix++) {
            name = base + " (" + suffix + ")";
        }

        return name;
    }

    private static String packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Skysong custom fonts");
        pack.addProperty("min_format", PACK_FORMAT);
        pack.addProperty("max_format", PACK_FORMAT);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return GSON.toJson(root);
    }

    private static String fontDefinition(String file) {
        JsonObject ttf = new JsonObject();
        ttf.addProperty("type", "ttf");
        ttf.addProperty("file", "skysong:custom/" + file);
        ttf.addProperty("size", 11.0F);
        ttf.addProperty("oversample", 8.0F);
        JsonArray shift = new JsonArray();
        shift.add(0);
        shift.add(0);
        ttf.add("shift", shift);
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "reference");
        fallback.addProperty("id", "minecraft:default");
        JsonArray providers = new JsonArray();
        providers.add(ttf);
        providers.add(fallback);
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        return GSON.toJson(root);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
