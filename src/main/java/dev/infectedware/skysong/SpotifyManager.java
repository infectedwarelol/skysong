package dev.infectedware.skysong;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.media.LocalMediaSource;
import dev.infectedware.skysong.media.WindowsMediaReader;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

public class SpotifyManager {
    private static final String DEFAULT_CLIENT_ID = "e719b5b5423244d989964a16df70f95c";
    private static final String PREFIX = "§b[Skysong] ";
    private static final String GOOGLE_REDIRECT_URI = "https://google.com/";
    private static final String DASHBOARD_URL = "https://developer.spotify.com/dashboard";
    private static final File TOKEN_FILE = new File(Minecraft.getInstance().gameDirectory, "spotify_token.txt");
    private String accessToken = null;
    private String refreshToken = null;
    private long expiresAt = 0L;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile String status = "Not connected";
    private String lastProblemShown = null;
    private final LocalMediaSource local = new LocalMediaSource();
    private long lastApiSuccess = 0L;
    private long apiPausedUntil = 0L;
    private boolean usingLocal = false;
    private long localRawPosition = -1L;
    private static final long RESYNC_THRESHOLD_MS = 400L;
    private String currentTrackName = "";
    private String currentArtist = "";
    private long progressMs = 0L;
    private long durationMs = 0L;
    private boolean isPlaying = false;
    private int primaryColor = -14829228;
    private int secondaryColor = -15133676;
    private final HttpClient httpClient;
    private volatile String codeVerifier = null;
    private final AtomicBoolean exchanging = new AtomicBoolean(false);
    private boolean waitingForClipboard = false;
    private String redirectUri = "https://google.com/";
    private SpotifyAuthServer authServer;
    private boolean awaitingClientId = false;
    private String albumImageUrl = "";
    private Identifier albumArtId = null;
    private long lastUpdateTime = 0L;
    private static final String EXPIRED_MESSAGE = "§eYour Spotify login expired. Press §lRight Shift§r§e → Spotify → §lConnect Spotify§r§e.";
    private static final int ALBUM_ART_SIZE = 128;

    public long getSmoothedProgressMs() {
        if (this.isPlaying && this.lastUpdateTime != 0L) {
            long elapsed = System.currentTimeMillis() - this.lastUpdateTime;
            return Math.min(this.progressMs + elapsed, this.durationMs);
        } else {
            return this.progressMs;
        }
    }

    public SpotifyManager() {
        this.httpClient = HttpClient.newHttpClient();
        this.loadToken();
        if (ModSettings.useLocalMedia) {
            this.local.start();
        }
    }

    public void tickLocalSource() {
        if (!ModSettings.useLocalMedia) {
            this.local.stop();
            if (this.usingLocal) {
                this.stopUsingLocal();
            }
        } else {
            this.local.start();
            WindowsMediaReader.Snapshot snap = this.local.latest();
            if (snap == null) {
                if (this.usingLocal) {
                    this.stopUsingLocal();
                }
            } else {
                boolean wasUsingLocal = this.usingLocal;
                this.usingLocal = true;
                this.status = "Reading Spotify on this PC";
                boolean newTrack = !snap.title().equals(this.currentTrackName) || !snap.artist().equals(this.currentArtist);
                boolean playStateChanged = snap.playing() != this.isPlaying;
                this.currentTrackName = snap.title();
                this.currentArtist = snap.artist();
                this.durationMs = snap.durationMs();
                long now = System.currentTimeMillis();
                long reported = snap.positionNow();
                boolean positionMoved = snap.positionMs() != this.localRawPosition;
                boolean seeked = positionMoved && Math.abs(reported - this.getSmoothedProgressMs()) > 400L;
                if (!wasUsingLocal || newTrack || playStateChanged || seeked || !snap.playing()) {
                    this.progressMs = reported;
                    this.lastUpdateTime = now;
                }

                this.localRawPosition = snap.positionMs();
                this.isPlaying = snap.playing();
                if (newTrack) {
                    this.extractPaletteFromImage(this.currentTrackName);
                    this.fetchArtworkByName(snap.title(), snap.artist());
                }
            }
        }
    }

    private void stopUsingLocal() {
        this.usingLocal = false;
        this.isPlaying = false;
        this.currentTrackName = "";
        this.currentArtist = "";
        this.albumImageUrl = "";
        this.albumArtId = null;
        if (this.accessToken == null) {
            this.status = "Not connected";
        }
    }

    public boolean isUsingLocal() {
        return this.usingLocal;
    }

    private void fetchArtworkByName(String title, String artist) {
        String term = URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + term))
            .GET()
            .build();
        this.httpClient.sendAsync(req, BodyHandlers.ofString()).thenAccept(res -> {
            try {
                if (res.statusCode() != 200) {
                    return;
                }

                JsonArray results = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("results");
                if (results == null || results.isEmpty()) {
                    return;
                }

                String url = results.get(0).getAsJsonObject().get("artworkUrl100").getAsString().replace("100x100", "600x600");
                if (!url.equals(this.albumImageUrl) && title.equals(this.currentTrackName)) {
                    this.albumImageUrl = url;
                    this.fetchAlbumArt(url);
                }
            } catch (Exception e) {
                SkysongClient.LOGGER.debug("[Skysong] No artwork found for " + title);
            }
        });
    }

    private static String clientId() {
        String own = ModSettings.spotifyClientId == null ? "" : ModSettings.spotifyClientId.trim();
        return own.isEmpty() ? "e719b5b5423244d989964a16df70f95c" : own;
    }

    private void loadToken() {
        try {
            if (!TOKEN_FILE.exists()) {
                return;
            }

            String saved = new String(Files.readAllBytes(TOKEN_FILE.toPath()), StandardCharsets.UTF_8).trim();
            if (saved.startsWith("{")) {
                JsonObject json = JsonParser.parseString(saved).getAsJsonObject();
                if (json.has("client_id") && !json.get("client_id").getAsString().equals(clientId())) {
                    return;
                }

                this.accessToken = json.get("access_token").getAsString();
                this.refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                this.expiresAt = json.has("expires_at") ? json.get("expires_at").getAsLong() : 0L;
            } else if (!saved.isEmpty()) {
                this.accessToken = saved;
            }

            if (this.accessToken != null) {
                this.status = "Connected";
                SkysongClient.LOGGER.info("[Skysong] Loaded saved Spotify login");
            }
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not load saved Spotify login: " + e.getMessage());
        }
    }

    private void saveToken() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("client_id", clientId());
            json.addProperty("access_token", this.accessToken);
            if (this.refreshToken != null) {
                json.addProperty("refresh_token", this.refreshToken);
            }

            json.addProperty("expires_at", this.expiresAt);
            Files.writeString(TOKEN_FILE.toPath(), json.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not save Spotify login: " + e.getMessage());
        }
    }

    private void clearSession(String newStatus) {
        this.accessToken = null;
        this.refreshToken = null;
        this.expiresAt = 0L;
        this.isPlaying = false;
        this.currentTrackName = "";
        this.currentArtist = "";
        this.status = newStatus;

        try {
            Files.deleteIfExists(TOKEN_FILE.toPath());
        } catch (Exception var3) {
        }
    }

    public void setClientId(String id) {
        this.applyClientId(id, false);
    }

    private void applyClientId(String id, boolean autoConnect) {
        String cleaned = id == null ? "" : id.trim();
        if (!cleaned.isEmpty() && !cleaned.matches("[0-9a-fA-F]{32}")) {
            this.sendChat("§b[Skysong] §cThat doesn't look like a Client ID (it is 32 letters and numbers).");
        } else {
            ModSettings.spotifyClientId = cleaned;
            ModConfig.save();
            this.clearSession("Not connected");
            this.lastProblemShown = null;
            if (autoConnect) {
                this.sendChat("§b[Skysong] §aClient ID found! Opening Spotify login…");
                this.authenticate();
            } else {
                this.sendChat("§b[Skysong] §fClient ID saved. Now click §lConnect Spotify§r§f.");
            }
        }
    }

    public boolean usingOwnClientId() {
        return !clientId().equals("e719b5b5423244d989964a16df70f95c");
    }

    public String getStatus() {
        return this.status;
    }

    public void tickClipboardCheck() {
        if (this.awaitingClientId || this.waitingForClipboard && this.codeVerifier != null) {
            String text;
            try {
                text = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
            } catch (Exception e) {
                return;
            }

            if (this.awaitingClientId && text.matches("[0-9a-fA-F]{32}") && !text.equalsIgnoreCase(clientId())) {
                this.awaitingClientId = false;
                this.applyClientId(text, true);
            } else {
                if (this.waitingForClipboard && this.codeVerifier != null && text.contains("code=") && text.contains("google.com")) {
                    String code = null;

                    for (String param : text.split("[?&]")) {
                        if (param.startsWith("code=")) {
                            code = param.substring(5).trim();
                            break;
                        }
                    }

                    if (code != null && !code.isEmpty()) {
                        this.waitingForClipboard = false;
                        this.sendChat("§b[Skysong] §aLink detected! Connecting to Spotify…");
                        String finalCode = code;
                        new Thread(() -> this.exchangeCodeForToken(finalCode)).start();
                    }
                }
            }
        }
    }

    public void authenticate() {
        try {
            SecureRandom sr = new SecureRandom();
            byte[] codeBytes = new byte[32];
            sr.nextBytes(codeBytes);
            this.codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);
            byte[] stateBytes = new byte[16];
            sr.nextBytes(stateBytes);
            String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(this.codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            boolean automatic = this.usingOwnClientId() && this.startAuthServer(state);
            this.redirectUri = automatic ? "http://127.0.0.1:43821/callback" : "https://google.com/";
            String authUrl = "https://accounts.spotify.com/authorize?client_id="
                + clientId()
                + "&response_type=code&redirect_uri="
                + URLEncoder.encode(this.redirectUri, StandardCharsets.UTF_8)
                + "&state="
                + state
                + "&code_challenge_method=S256&code_challenge="
                + codeChallenge
                + "&scope=user-read-playback-state%20user-read-currently-playing";
            Util.getPlatform().openUri(URI.create(authUrl));
            this.status = "Waiting for login";
            if (automatic) {
                this.waitingForClipboard = false;
                this.sendChat("§b[Skysong] §fBrowser opened! Log in to Spotify and press §lAgree§r§f — Skysong connects by itself.");
            } else {
                this.waitingForClipboard = true;
                this.sendChat("§b[Skysong] §fBrowser opened! Log in to Spotify.");
                this.sendChat("§b[Skysong] §fWhen Google opens, copy its link (§lCtrl+L§r§f, then §lCtrl+C§r§f) — Skysong connects by itself.");
            }
        } catch (Exception e) {
            SkysongClient.LOGGER.error("Failed to start authentication", e);
            this.sendChat("§b[Skysong] §cError: " + e.getMessage());
        }
    }

    private boolean startAuthServer(String state) {
        if (this.authServer != null) {
            this.authServer.stop();
        }

        this.authServer = new SpotifyAuthServer(
            state, code -> new Thread(() -> this.exchangeCodeForToken(code)).start(), error -> Minecraft.getInstance().execute(() -> {
                this.status = "Not connected";
                this.sendChat("§b[Skysong] §eSpotify login was cancelled. Click §lConnect Spotify§r§e to try again.");
            })
        );

        try {
            this.authServer.start();
            return true;
        } catch (IOException e) {
            SkysongClient.LOGGER.warn("[Skysong] Can't open the login receiver on port 43821: " + e.getMessage());
            this.authServer = null;
            return false;
        }
    }

    public void startGuidedSetup() {
        Minecraft mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard("http://127.0.0.1:43821/callback");
        this.awaitingClientId = true;
        Util.getPlatform().openUri(URI.create("https://developer.spotify.com/dashboard"));
        this.sendChat("§b[Skysong] §fSpotify dashboard opened. Click §lCreate app§r§f:");
        this.sendChat("§7  1. Any name and description");
        this.sendChat("§7  2. Redirect URIs: press §fCtrl+V§7 (already copied), then §fAdd");
        this.sendChat("§7  3. Tick §fWeb API§7, then §fSave");
        this.sendChat("§7  4. Open the app's §fSettings§7 and copy the §fClient ID");
        this.sendChat("§b[Skysong] §aSkysong will notice the Client ID and connect by itself.");
    }

    public boolean handleChatCode(String message) {
        if (this.codeVerifier == null) {
            return false;
        }

        if (message.contains("code=") && message.contains("google.com")) {
            String code = null;
            String query = message.contains("?") ? message.split("\\?")[1] : message;

            for (String param : query.split("&")) {
                if (param.startsWith("code=")) {
                    code = param.substring(5).trim();
                    break;
                }
            }

            if (code != null && !code.isEmpty()) {
                this.waitingForClipboard = false;
                this.sendChat("§b[Skysong] §aLink received! Connecting…");
                String finalCode = code;
                new Thread(() -> this.exchangeCodeForToken(finalCode)).start();
                return true;
            }
        }

        return false;
    }

    public void exchangeCodeForTokenPublic(String code) {
        new Thread(() -> this.exchangeCodeForToken(code)).start();
    }

    private void exchangeCodeForToken(String code) {
        String verifier = this.codeVerifier;
        if (verifier == null) {
            if (this.accessToken == null) {
                this.sendChat("§b[Skysong] §eClick Connect Spotify first, then copy the google.com link.");
            }
        } else if (this.exchanging.compareAndSet(false, true)) {
            try {
                if (code.contains("#")) {
                    code = code.split("#")[0];
                }

                String requestBody = "client_id="
                    + clientId()
                    + "&grant_type=authorization_code&code="
                    + code
                    + "&redirect_uri="
                    + URLEncoder.encode(this.redirectUri, StandardCharsets.UTF_8)
                    + "&code_verifier="
                    + verifier;
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString(requestBody))
                    .build();
                HttpResponse<String> response = this.httpClient.send(request, BodyHandlers.ofString());
                SkysongClient.LOGGER.info("Token response status: " + response.statusCode());
                if (response.statusCode() == 200) {
                    this.applyTokenResponse(JsonParser.parseString(response.body()).getAsJsonObject());
                    this.codeVerifier = null;
                    this.lastProblemShown = null;
                    SkysongClient.LOGGER.info("[Skysong] Spotify login complete");
                    this.sendChat("§b[Skysong] §a§lConnected to Spotify! §r§aYou won't need to log in again.");
                } else {
                    SkysongClient.LOGGER.error("Token exchange failed: " + response.body());
                    this.sendChat("§b[Skysong] §cLogin failed: " + response.body());
                    this.codeVerifier = null;
                }
            } catch (Exception e) {
                SkysongClient.LOGGER.error("Token exchange exception", e);
                this.sendChat("§b[Skysong] §cError: " + e.getMessage());
                this.codeVerifier = null;
            } finally {
                this.exchanging.set(false);
            }
        }
    }

    private void sendChat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), false);
        }
    }

    private void applyTokenResponse(JsonObject json) {
        this.accessToken = json.get("access_token").getAsString();
        if (json.has("refresh_token")) {
            this.refreshToken = json.get("refresh_token").getAsString();
        }

        long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600L;
        this.expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        this.status = "Connected";
        this.saveToken();
    }

    private void refreshAccessToken() {
        if (this.refreshToken != null && this.refreshing.compareAndSet(false, true)) {
            String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(this.refreshToken, StandardCharsets.UTF_8) + "&client_id=" + clientId();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body))
                .build();
            this.httpClient.sendAsync(request, BodyHandlers.ofString()).whenComplete((response, error) -> {
                try {
                    if (error == null && response.statusCode() == 200) {
                        this.applyTokenResponse(JsonParser.parseString(response.body()).getAsJsonObject());
                        SkysongClient.LOGGER.info("[Skysong] Spotify login refreshed");
                    } else if (error == null && response.statusCode() == 400) {
                        this.clearSession("Login expired");
                        this.reportProblem("expired", "§eYour Spotify login expired. Press §lRight Shift§r§e → Spotify → §lConnect Spotify§r§e.");
                    }
                } finally {
                    this.refreshing.set(false);
                }
            });
        }
    }

    private static String shortReason(String body) {
        String b = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (b.contains("premium")) {
            return "the app's owner needs Spotify Premium";
        } else {
            return b.contains("not registered") ? "the account isn't on the app's user list" : "403";
        }
    }

    private void reportProblem(String key, String message) {
        if (!key.equals(this.lastProblemShown)) {
            this.lastProblemShown = key;
            Minecraft.getInstance().execute(() -> this.sendChat("§b[Skysong] " + message));
        }
    }

    public void updatePlaybackState() {
        if (this.accessToken != null && System.currentTimeMillis() >= this.apiPausedUntil) {
            if (this.refreshToken != null && this.expiresAt > 0L && System.currentTimeMillis() > this.expiresAt - 60000L) {
                this.refreshAccessToken();
            } else if (!this.usingLocal) {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                    .header("Authorization", "Bearer " + this.accessToken)
                    .GET()
                    .build();
                this.httpClient
                    .sendAsync(request, BodyHandlers.ofString())
                    .thenAccept(
                        response -> {
                            if (response.statusCode() == 200) {
                                this.lastApiSuccess = System.currentTimeMillis();

                                try {
                                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                    if (json.has("item") && !json.get("item").isJsonNull()) {
                                        JsonObject item = json.getAsJsonObject("item");
                                        this.currentTrackName = item.get("name").getAsString();
                                        this.currentArtist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
                                        this.progressMs = json.get("progress_ms").getAsLong();
                                        this.durationMs = item.get("duration_ms").getAsLong();
                                        this.isPlaying = json.get("is_playing").getAsBoolean();
                                        this.lastUpdateTime = System.currentTimeMillis();
                                        JsonObject album = item.getAsJsonObject("album");
                                        if (album.has("images") && album.getAsJsonArray("images").size() > 0) {
                                            String newImageUrl = album.getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString();
                                            if (!newImageUrl.equals(this.albumImageUrl)) {
                                                this.albumImageUrl = newImageUrl;
                                                this.fetchAlbumArt(this.albumImageUrl);
                                            }
                                        }

                                        this.extractPaletteFromImage(this.currentTrackName);
                                    }
                                } catch (Exception e) {
                                    SkysongClient.LOGGER.error("Failed to parse playback state", e);
                                }
                            } else if (response.statusCode() == 204 || response.statusCode() == 202) {
                                this.lastApiSuccess = System.currentTimeMillis();
                                this.isPlaying = false;
                                this.currentTrackName = "";
                                this.currentArtist = "";
                                this.albumImageUrl = "";
                                this.albumArtId = null;
                            } else if (response.statusCode() == 401) {
                                if (this.refreshToken != null) {
                                    this.refreshAccessToken();
                                } else {
                                    this.clearSession("Login expired");
                                    this.reportProblem("expired", "§eYour Spotify login expired. Press §lRight Shift§r§e → Spotify → §lConnect Spotify§r§e.");
                                }
                            } else if (response.statusCode() == 403) {
                                this.apiPausedUntil = System.currentTimeMillis() + 600000L;
                                if (!this.usingLocal) {
                                    this.status = "Blocked by Spotify";
                                }

                                SkysongClient.LOGGER.warn("[Skysong] Spotify refused the request (403): " + response.body());
                                if (ModSettings.useLocalMedia && WindowsMediaReader.isSupported()) {
                                    this.reportProblem(
                                        "blocked",
                                        "§eSpotify's online API refused this account ("
                                            + shortReason(response.body())
                                            + "). §aNo problem: Skysong reads Spotify from this PC instead, no login needed."
                                    );
                                } else {
                                    this.reportProblem("blocked", "§cSpotify refused this account: " + shortReason(response.body()));
                                }
                            }

                            if (response.statusCode() == 200 || response.statusCode() == 204) {
                                this.status = "Connected";
                                this.lastProblemShown = null;
                            }
                        }
                    );
            }
        }
    }

    private void fetchAlbumArt(String url) {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        this.httpClient.sendAsync(req, BodyHandlers.ofByteArray()).thenAccept(res -> {
            if (res.statusCode() == 200) {
                Minecraft.getInstance().execute(() -> {
                    try {
                        BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(res.body()));
                        if (buffered == null) {
                            SkysongClient.LOGGER.error("Album art ImageIO returned null");
                            return;
                        }

                        buffered = downscale(buffered, 128);
                        int w = buffered.getWidth();
                        int h = buffered.getHeight();
                        NativeImage image = new NativeImage(Format.RGBA, w, h, false);

                        for (int py = 0; py < h; py++) {
                            for (int px = 0; px < w; px++) {
                                int argb = buffered.getRGB(px, py);
                                int a = argb >> 24 & 0xFF;
                                int r = argb >> 16 & 0xFF;
                                int g = argb >> 8 & 0xFF;
                                int b = argb & 0xFF;
                                int abgr = a << 24 | b << 16 | g << 8 | r;
                                Compat.setPixelAbgr(image, px, py, abgr);
                            }
                        }

                        DynamicTexture texture = Compat.dynamicTexture("album art", image);
                        Identifier id = Ids.of("skysong", "album_art");
                        Minecraft.getInstance().getTextureManager().register(id, texture);
                        this.albumArtId = id;
                        SkysongClient.LOGGER.info("Album art loaded: " + w + "x" + h);
                    } catch (Exception e) {
                        SkysongClient.LOGGER.error("Failed to load album art", e);
                    }
                });
            }
        });
    }

    private static BufferedImage downscale(BufferedImage src, int size) {
        if (src.getWidth() <= size && src.getHeight() <= size) {
            return src;
        }

        Image scaled = src.getScaledInstance(size, size, 16);
        BufferedImage out = new BufferedImage(size, size, 2);
        Graphics2D g = out.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return out;
    }

    public Identifier getAlbumArtId() {
        return this.albumArtId;
    }

    public boolean isConnected() {
        return this.accessToken != null && !this.accessToken.isEmpty();
    }

    public String getCurrentTrackName() {
        return this.currentTrackName;
    }

    public String getCurrentArtist() {
        return this.currentArtist;
    }

    public long getProgressMs() {
        return this.progressMs;
    }

    public long getDurationMs() {
        return this.durationMs;
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public int getPrimaryColor() {
        return ModSettings.dynamicColors ? this.primaryColor : -14829228;
    }

    public int getSongColor() {
        return this.primaryColor;
    }

    public int getSecondaryColor() {
        return this.secondaryColor;
    }

    private void extractPaletteFromImage(String trackId) {
        int[] colors = new int[]{-43213, -13369513, -13412353, -52235, -655565};
        this.primaryColor = colors[Math.abs(this.currentTrackName.hashCode()) % colors.length];
    }
}
