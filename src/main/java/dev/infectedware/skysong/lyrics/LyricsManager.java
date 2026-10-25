package dev.infectedware.skysong.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsManager {
    private static final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();
    private static final String API = "https://lrclib.net/api/";
    private static final Pattern LRC_STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern WORD_STAMP = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern LRC_OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)\\]", 2);
    private volatile List<LyricsManager.LyricLine> currentLyrics = List.of();
    private volatile String currentTrackKey = "";
    private volatile String translationKey = "";

    public void fetchLyrics(String trackName, String artistName, long durationMs) {
        String key = trackName + "\u0000" + artistName;
        if (!key.equals(this.currentTrackKey)) {
            this.currentTrackKey = key;
            this.currentLyrics = List.of();
            if (!trackName.isEmpty()) {
                Thread t = new Thread(() -> {
                    List<LyricsManager.LyricLine> lines = find(trackName, artistName, durationMs);
                    if (key.equals(this.currentTrackKey)) {
                        this.currentLyrics = lines;
                        SkysongClient.LOGGER.info("[Skysong] {}{}", (lines.isEmpty() ? "No synced lyrics for: " : "Lyrics loaded for: "), trackName);
                    }
                }, "Skysong lyrics");
                t.setDaemon(true);
                t.start();
            }
        }
    }

    private static List<LyricsManager.LyricLine> find(String track, String artist, long durationMs) {
        try {
            JsonObject exact = getJson(
                "get?track_name=" + enc(track) + "&artist_name=" + enc(artist) + (durationMs > 0L ? "&duration=" + Math.round(durationMs / 1000.0) : "")
            );
            List<LyricsManager.LyricLine> lines = synced(exact);
            if (!lines.isEmpty()) {
                return lines;
            }

            lines = bestFromSearch("search?track_name=" + enc(track) + "&artist_name=" + enc(artist), durationMs);
            if (!lines.isEmpty()) {
                return lines;
            }

            String clean = cleanTitle(track);
            if (!clean.equalsIgnoreCase(track)) {
                lines = bestFromSearch("search?track_name=" + enc(clean) + "&artist_name=" + enc(artist), durationMs);
                if (!lines.isEmpty()) {
                    return lines;
                }
            }

            return bestFromSearch("search?q=" + enc(clean + " " + artist), durationMs);
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Lyrics lookup failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static List<LyricsManager.LyricLine> bestFromSearch(String query, long durationMs) throws Exception {
        String body = get(query);
        if (body == null) {
            return List.of();
        }

        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) {
            return List.of();
        }

        JsonArray results = parsed.getAsJsonArray();
        JsonObject best = null;
        double bestDiff = Double.MAX_VALUE;

        for (JsonElement el : results) {
            JsonObject r = el.getAsJsonObject();
            if (r.has("syncedLyrics") && !r.get("syncedLyrics").isJsonNull()) {
                double diff = durationMs > 0L && r.has("duration") && !r.get("duration").isJsonNull()
                    ? Math.abs(r.get("duration").getAsDouble() - durationMs / 1000.0)
                    : 0.0;
                if (diff < bestDiff) {
                    best = r;
                    bestDiff = diff;
                }
            }
        }

        return best != null && bestDiff <= 8.0 ? synced(best) : List.of();
    }

    private static List<LyricsManager.LyricLine> synced(JsonObject json) {
        return json != null && json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()
            ? parseLrc(json.get("syncedLyrics").getAsString())
            : List.of();
    }

    static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "");
        int dash = t.indexOf(" - ");
        if (dash > 0) {
            t = t.substring(0, dash);
        }

        t = t.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|with)\\s+.*$", "");
        return t.trim().isEmpty() ? title : t.trim();
    }

    private static JsonObject getJson(String path) throws Exception {
        String body = get(path);
        if (body == null) {
            return null;
        }

        JsonElement parsed = JsonParser.parseString(body);
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    private static String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://lrclib.net/api/" + path))
            .header("User-Agent", "Skysong (Minecraft mod)")
            .timeout(Duration.ofSeconds(10L))
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static List<LyricsManager.LyricLine> parseLrc(String lrc) {
        Matcher offsetTag = LRC_OFFSET.matcher(lrc);
        long offset = offsetTag.find() ? Long.parseLong(offsetTag.group(1)) : 0L;
        List<long[]> stamps = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<long[]> wordTimes = new ArrayList<>();

        for (String raw : lrc.split("\n")) {
            String line = raw.trim();
            Matcher m = LRC_STAMP.matcher(line);
            List<Long> lineTimes = new ArrayList<>();

            int pos;
            for (pos = 0; m.find() && m.start() == pos; pos = m.end()) {
                lineTimes.add(Math.max(0L, stampMs(m.group(1), m.group(2), m.group(3)) - offset));
            }

            if (!lineTimes.isEmpty()) {
                String body = line.substring(pos);
                texts.add(WORD_STAMP.matcher(body).replaceAll("").replaceAll("\\s+", " ").trim());
                wordTimes.add(lineTimes.size() == 1 ? parseWordTimes(body, offset) : null);

                for (long t : lineTimes) {
                    stamps.add(new long[]{t, texts.size() - 1});
                }
            }
        }

        stamps.sort((a, b) -> Long.compare(a[0], b[0]));
        List<LyricsManager.LyricLine> parsed = new ArrayList<>();

        for (int i = 0; i < stamps.size(); i++) {
            String text = texts.get((int)stamps.get(i)[1]);
            if (!text.isEmpty() && !text.equals("♪")) {
                long time = stamps.get(i)[0];
                long end = i + 1 < stamps.size() ? stamps.get(i + 1)[0] : time + 5000L;
                if (end > time) {
                    LyricsManager.LyricLine parsedLine = new LyricsManager.LyricLine(time, end, text);
                    parsedLine.wordTimes = wordTimes.get((int)stamps.get(i)[1]);
                    parsed.add(parsedLine);
                }
            }
        }

        return parsed;
    }

    private static long stampMs(String minutes, String seconds, String fraction) {
        long ms = fraction == null ? 0L : Long.parseLong(fraction) * (fraction.length() == 1 ? 100 : (fraction.length() == 2 ? 10 : 1));
        return Long.parseLong(minutes) * 60000L + Long.parseLong(seconds) * 1000L + ms;
    }

    static long[] parseWordTimes(String body, long offset) {
        Matcher stamp = WORD_STAMP.matcher(body);
        List<Long> times = new ArrayList<>();
        long segmentTime = -1L;
        int segmentStart = 0;
        boolean any = false;

        while (true) {
            boolean found = stamp.find();
            int segmentEnd = found ? stamp.start() : body.length();
            String segment = body.substring(segmentStart, segmentEnd).trim();
            if (!segment.isEmpty()) {
                if (segmentTime < 0L) {
                    return null;
                }

                for (String word : segment.split("\\s+")) {
                    if (!word.isEmpty()) {
                        times.add(segmentTime);
                    }
                }
            }

            if (!found) {
                break;
            }

            any = true;
            segmentTime = Math.max(0L, stampMs(stamp.group(1), stamp.group(2), stamp.group(3)) - offset);
            segmentStart = stamp.end();
        }

        if (!any || times.isEmpty()) {
            return null;
        }

        long[] out = new long[times.size()];

        for (int i = 0; i < out.length; i++) {
            out[i] = times.get(i);
        }

        return out;
    }

    public void tickTranslation() {
        if (!ModSettings.lyricTranslate) {
            this.translationKey = "";
            return;
        }

        List<LyricsManager.LyricLine> lines = this.currentLyrics;
        if (lines.isEmpty()) {
            return;
        }

        String code = LyricTranslator.languageCode(ModSettings.lyricTranslateLanguage);
        String key = this.currentTrackKey + "\u0000" + code;
        if (!key.equals(this.translationKey)) {
            this.translationKey = key;

            for (LyricsManager.LyricLine line : lines) {
                line.translation = null;
            }

            LyricTranslator.translateAsync(lines, code, () -> key.equals(this.translationKey) && ModSettings.lyricTranslate);
        }
    }

    public List<LyricsManager.LyricLine> lines() {
        return this.currentLyrics;
    }

    public LyricsManager.LyricLine getLineAtTime(long timeMs) {
        LyricsManager.LyricLine current = null;

        for (LyricsManager.LyricLine line : this.currentLyrics) {
            if (line.timeMs > timeMs) {
                break;
            }

            current = line;
        }

        return current != null && timeMs < current.endMs ? current : null;
    }

    public String getLyricAtTime(long timeMs) {
        LyricsManager.LyricLine line = this.getLineAtTime(timeMs);
        return line == null ? "" : line.text;
    }

    public boolean hasLyrics() {
        return !this.currentLyrics.isEmpty();
    }

    public static class LyricLine {
        public final long timeMs;
        public final long endMs;
        public final String text;
        public long[] wordTimes;
        public volatile String translation;

        public LyricLine(long timeMs, long endMs, String text) {
            this.timeMs = timeMs;
            this.endMs = endMs;
            this.text = text;
        }
    }
}
