package dev.infectedware.skysong.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.hud.Feedback;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class LyricTranslator {
    public static final String[] LANGUAGE_NAMES = new String[]{
        "English", "French", "Spanish", "German", "Italian", "Portuguese", "Dutch", "Polish", "Turkish", "Russian", "Japanese", "Korean", "Chinese", "Arabic"
    };
    private static final String[] LANGUAGE_CODES = new String[]{"en", "fr", "es", "de", "it", "pt", "nl", "pl", "tr", "ru", "ja", "ko", "zh-CN", "ar"};
    private static final String API = "https://api.mymemory.translated.net/get?langpair=autodetect%7C";
    private static final int MAX_CHUNK_BYTES = 450;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
    private static volatile boolean quotaReached;

    private LyricTranslator() {
    }

    public static String languageCode(int index) {
        return LANGUAGE_CODES[Math.floorMod(index, LANGUAGE_CODES.length)];
    }

    public static void translateAsync(List<LyricsManager.LyricLine> lines, String languageCode, BooleanSupplier stillWanted) {
        if (lines.isEmpty() || quotaReached) {
            return;
        }

        Thread thread = new Thread(() -> translate(lines, languageCode, stillWanted), "Skysong translation");
        thread.setDaemon(true);
        thread.start();
    }

    private static void translate(List<LyricsManager.LyricLine> lines, String languageCode, BooleanSupplier stillWanted) {
        List<List<LyricsManager.LyricLine>> chunks = chunk(lines);

        try {
            for (List<LyricsManager.LyricLine> chunk : chunks) {
                if (!stillWanted.getAsBoolean() || quotaReached) {
                    return;
                }

                StringBuilder joined = new StringBuilder();

                for (LyricsManager.LyricLine line : chunk) {
                    if (!joined.isEmpty()) {
                        joined.append('\n');
                    }

                    joined.append(line.text);
                }

                String translated = request(joined.toString(), languageCode);
                if (translated == null) {
                    continue;
                }

                String[] parts = translated.split("\n", -1);
                if (parts.length == chunk.size()) {
                    for (int i = 0; i < parts.length; i++) {
                        chunk.get(i).translation = cleanTranslation(parts[i], chunk.get(i).text);
                    }
                } else {
                    for (LyricsManager.LyricLine line : chunk) {
                        if (!stillWanted.getAsBoolean() || quotaReached) {
                            return;
                        }

                        String single = request(line.text, languageCode);
                        if (single != null) {
                            line.translation = cleanTranslation(single, line.text);
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Lyric translation failed: " + e.getMessage());
        }
    }

    private static List<List<LyricsManager.LyricLine>> chunk(List<LyricsManager.LyricLine> lines) {
        List<List<LyricsManager.LyricLine>> chunks = new ArrayList<>();
        List<LyricsManager.LyricLine> current = new ArrayList<>();
        int bytes = 0;

        for (LyricsManager.LyricLine line : lines) {
            int size = line.text.getBytes(StandardCharsets.UTF_8).length + 1;
            if (size > MAX_CHUNK_BYTES) {
                continue;
            }

            if (bytes + size > MAX_CHUNK_BYTES && !current.isEmpty()) {
                chunks.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }

            current.add(line);
            bytes += size;
        }

        if (!current.isEmpty()) {
            chunks.add(current);
        }

        return chunks;
    }

    private static String request(String text, String languageCode) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API + URLEncoder.encode(languageCode, StandardCharsets.UTF_8) + "&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)))
            .header("User-Agent", "Skysong (Minecraft mod)")
            .timeout(Duration.ofSeconds(10L))
            .GET()
            .build();
        HttpResponse<String> response = HTTP.send(request, BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            stopForToday();
            return null;
        }

        if (response.statusCode() != 200) {
            return null;
        }

        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) {
            return null;
        }

        JsonObject json = parsed.getAsJsonObject();
        if (json.has("quotaFinished") && json.get("quotaFinished").isJsonPrimitive() && json.get("quotaFinished").getAsBoolean()) {
            stopForToday();
            return null;
        }

        JsonElement data = json.get("responseData");
        if (data == null || !data.isJsonObject()) {
            return null;
        }

        JsonElement translated = data.getAsJsonObject().get("translatedText");
        return translated != null && translated.isJsonPrimitive() ? unescape(translated.getAsString()) : null;
    }

    private static void stopForToday() {
        if (!quotaReached) {
            quotaReached = true;
            Feedback.show("Lyric translation limit reached for today", 4000L);
        }
    }

    private static String cleanTranslation(String translated, String original) {
        String trimmed = translated.trim();
        return trimmed.isEmpty() || trimmed.equalsIgnoreCase(original.trim()) ? null : trimmed;
    }

    static String unescape(String text) {
        return text.replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
