package dev.infectedware.skysong;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import dev.infectedware.skysong.compat.Compat;
import dev.infectedware.skysong.compat.Ids;
import dev.infectedware.skysong.hud.Feedback;
import dev.infectedware.skysong.media.LocalMediaSource;
import dev.infectedware.skysong.media.MediaCommand;
import dev.infectedware.skysong.media.MediaSnapshot;
import dev.infectedware.skysong.theme.AlbumPalette;
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
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class SpotifyManager {
    private static final File TOKEN_FILE = new File(Minecraft.getInstance().gameDirectory, "spotify_token.txt");
    private String accessToken = null;
    private String refreshToken = null;
    private long expiresAt = 0L;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile String status = "Not connected";
    private String lastProblemShown = null;
    private final LocalMediaSource local = new LocalMediaSource();
    private long apiPausedUntil = 0L;
    private boolean usingLocal = false;
    private long localRawPosition = -1L;
    private String currentTrackName = "";
    private String currentArtist = "";
    private long progressMs = 0L;
    private long durationMs = 0L;
    private boolean isPlaying = false;
    private int primaryColor = -14829228;
    private int secondaryColor = -15133676;
    private volatile boolean hasAlbumPalette = false;
    private String paletteTrack = "";
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
    private static final String SCOPES = "user-read-playback-state user-read-currently-playing user-modify-playback-state user-library-read user-library-modify";
    private static final String API = "https://api.spotify.com/v1";
    private static final int VOLUME_STEP = 10;
    private volatile String currentTrackId = "";
    private static final Pattern TRACK_ID = Pattern.compile("[A-Za-z0-9]{22}");
    private static final int MAX_ART_BYTES = 8 * 1024 * 1024;
    private static final long MAX_ART_PIXELS = 4096L * 4096L;

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
            MediaSnapshot snap = this.local.latest();
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
                if (!snap.trackId().isEmpty()) {
                    this.currentTrackId = snap.trackId();
                }

                if (newTrack) {
                    this.extractPaletteFromImage(this.currentTrackName);
                    if (snap.trackId().isEmpty()) {
                        this.currentTrackId = "";
                    }

                    String art = snap.artUrl();
                    if (art.startsWith("https://")) {
                        if (!art.equals(this.albumImageUrl)) {
                            this.albumImageUrl = art;
                            this.fetchAlbumArt(art);
                        }
                    } else {
                        this.fetchArtworkByName(snap.title(), snap.artist());
                    }
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
                SkysongClient.LOGGER.debug("[Skysong] No artwork found for {}", title);
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

            String saved = Files.readString(TOKEN_FILE.toPath(), StandardCharsets.UTF_8).trim();
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
            SkysongClient.LOGGER.warn("[Skysong] Could not load saved Spotify login: {}", e.getMessage());
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
            try {
                Files.setPosixFilePermissions(TOKEN_FILE.toPath(), PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
            }
        } catch (Exception e) {
            SkysongClient.LOGGER.warn("[Skysong] Could not save Spotify login: {}", e.getMessage());
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
        } catch (Exception ignored) {
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
                + "&scope=" + URLEncoder.encode(SCOPES, StandardCharsets.UTF_8).replace("+", "%20");
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
            SkysongClient.LOGGER.warn("[Skysong] Can't open the login receiver on port 43821: {}", e.getMessage());
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
        this.sendChat("§b[Skysong] §a" + "Skysong will notice the Client ID and connect by itself.");
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
                SkysongClient.LOGGER.info("Token response status: {}", response.statusCode());
                if (response.statusCode() == 200) {
                    this.applyTokenResponse(JsonParser.parseString(response.body()).getAsJsonObject());
                    this.codeVerifier = null;
                    this.lastProblemShown = null;
                    SkysongClient.LOGGER.info("[Skysong] Spotify login complete");
                    this.sendChat("§b[Skysong] §a§lConnected to Spotify! §r§aYou won't need to log in again.");
                } else {
                    SkysongClient.LOGGER.error("Token exchange failed: {}", response.body());
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

                                try {
                                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                                    if (json.has("item") && !json.get("item").isJsonNull()) {
                                        JsonObject item = json.getAsJsonObject("item");
                                        this.currentTrackName = item.get("name").getAsString();
                                        this.currentArtist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
                                        this.currentTrackId = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
                                        this.progressMs = json.get("progress_ms").getAsLong();
                                        this.durationMs = item.get("duration_ms").getAsLong();
                                        this.isPlaying = json.get("is_playing").getAsBoolean();
                                        this.lastUpdateTime = System.currentTimeMillis();
                                        JsonObject album = item.getAsJsonObject("album");
                                        if (album.has("images") && !album.getAsJsonArray("images").isEmpty()) {
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

                                SkysongClient.LOGGER.warn("[Skysong] Spotify refused the request (403): {}", response.body());
                                if (ModSettings.useLocalMedia && LocalMediaSource.isSupported()) {
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
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            return;
        }

        HttpRequest req = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(10L)).GET().build();
        this.httpClient.sendAsync(req, BodyHandlers.ofByteArray()).thenAccept(res -> {
            if (res.statusCode() != 200 || res.body().length > MAX_ART_BYTES) {
                return;
            }

            BufferedImage buffered;
            try {
                buffered = readLimited(res.body());
            } catch (IOException e) {
                SkysongClient.LOGGER.warn("[Skysong] Could not read album art: {}", e.getMessage());
                return;
            }

            if (buffered == null) {
                return;
            }

            BufferedImage small = downscale(buffered);
            int[] palette = AlbumPalette.extract(small);
            int w = small.getWidth();
            int h = small.getHeight();
            int[] pixels = small.getRGB(0, 0, w, h, null, 0, w);
            Minecraft.getInstance().execute(() -> {
                try {
                    this.primaryColor = palette[0];
                    this.secondaryColor = palette[1];
                    this.hasAlbumPalette = true;
                    NativeImage image = new NativeImage(Format.RGBA, w, h, false);
                    for (int py = 0; py < h; py++) {
                        for (int px = 0; px < w; px++) {
                            int argb = pixels[py * w + px];
                            int abgr = argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
                            Compat.setPixelAbgr(image, px, py, abgr);
                        }
                    }

                    DynamicTexture texture = Compat.dynamicTexture("album art", image);
                    Identifier id = Ids.of("skysong", "album_art");
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                    this.albumArtId = id;
                } catch (RuntimeException e) {
                    SkysongClient.LOGGER.warn("[Skysong] Could not show album art: {}", e.getMessage());
                }
            });
        });
    }

    private static BufferedImage readLimited(byte[] data) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (in == null) {
                return null;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long)reader.getWidth(0) * reader.getHeight(0) > MAX_ART_PIXELS) {
                    return null;
                }

                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage downscale(BufferedImage src) {
        if (src.getWidth() <= 128 && src.getHeight() <= 128) {
            return src;
        }

        Image scaled = src.getScaledInstance(128, 128, 16);
        BufferedImage out = new BufferedImage(128, 128, 2);
        Graphics2D g = out.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return out;
    }

    private boolean localControl(MediaCommand command, long value, Runnable onDone) {
        if (!this.usingLocal || !this.local.supportsControls()) {
            return false;
        }

        return this.local.control(command, value, () -> Minecraft.getInstance().execute(onDone));
    }

    public void togglePlayback() {
        boolean pause = this.isPlaying;
        if (this.localControl(MediaCommand.PLAY_PAUSE, 0L, () -> Feedback.show(pause ? "Paused" : "Playing", 1200L))) {
            this.progressMs = this.getSmoothedProgressMs();
            this.lastUpdateTime = System.currentTimeMillis();
            this.isPlaying = !pause;
            return;
        }

        this.control("PUT", pause ? "/me/player/pause" : "/me/player/play", () -> {
            this.progressMs = this.getSmoothedProgressMs();
            this.lastUpdateTime = System.currentTimeMillis();
            this.isPlaying = !pause;
            Feedback.show(pause ? "Paused" : "Playing", 1200L);
        });
    }

    public void nextTrack() {
        if (this.localControl(MediaCommand.NEXT, 0L, () -> Feedback.show("Next track", 1200L))) {
            return;
        }

        this.control("POST", "/me/player/next", () -> Feedback.show("Next track", 1200L));
    }

    public void previousTrack() {
        if (this.localControl(MediaCommand.PREVIOUS, 0L, () -> Feedback.show("Previous track", 1200L))) {
            return;
        }

        this.control("POST", "/me/player/previous", () -> Feedback.show("Previous track", 1200L));
    }

    public void seekTo(long positionMs) {
        long clamped = Math.max(0L, this.durationMs > 0L ? Math.min(positionMs, this.durationMs) : positionMs);
        if (this.localControl(MediaCommand.SEEK_TO, clamped, () -> {
        })) {
            this.progressMs = clamped;
            this.lastUpdateTime = System.currentTimeMillis();
            return;
        }

        this.control("PUT", "/me/player/seek?position_ms=" + clamped, () -> {
            this.progressMs = clamped;
            this.lastUpdateTime = System.currentTimeMillis();
        });
    }

    public void changeVolume(int direction) {
        if (this.localControl(MediaCommand.VOLUME_STEP, direction, () -> Feedback.show(direction > 0 ? "Volume up" : "Volume down", 1200L))) {
            return;
        }

        if (!this.canControl()) {
            return;
        }

        this.send("GET", "/me/player").thenAccept(response -> {
            if (response == null || response.statusCode() != 200) {
                this.reportControlFailure(response);
                return;
            }

            try {
                JsonObject device = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("device");
                if (device == null || !device.has("volume_percent") || device.get("volume_percent").isJsonNull()) {
                    Feedback.show("This Spotify device has no volume control", 2000L);
                    return;
                }

                int volume = Mth.clamp(device.get("volume_percent").getAsInt() + Integer.signum(direction) * VOLUME_STEP, 0, 100);
                this.control("PUT", "/me/player/volume?volume_percent=" + volume, () -> Feedback.show("Volume " + volume + "%", 1200L));
            } catch (RuntimeException e) {
                SkysongClient.LOGGER.warn("[Skysong] Could not read Spotify volume: {}", e.getMessage());
            }
        });
    }

    public void toggleLike() {
        String id = this.currentTrackId;
        if (!this.canControl()) {
            return;
        }

        if (id.isEmpty()) {
            Feedback.show("This track can't be liked", 2000L);
            return;
        }

        this.send("GET", "/me/tracks/contains?ids=" + id).thenAccept(response -> {
            if (response == null || response.statusCode() != 200) {
                this.reportControlFailure(response);
                return;
            }

            try {
                boolean liked = JsonParser.parseString(response.body()).getAsJsonArray().get(0).getAsBoolean();
                this.control(liked ? "DELETE" : "PUT", "/me/tracks?ids=" + id, () -> Feedback.show(liked ? "Removed from Liked Songs" : "Added to Liked Songs", 2000L));
            } catch (RuntimeException e) {
                SkysongClient.LOGGER.warn("[Skysong] Could not read liked state: {}", e.getMessage());
            }
        });
    }

    private boolean canControl() {
        if (this.accessToken == null) {
            Feedback.show("Connect Spotify first: Right Shift → Spotify → Connect Spotify", 3500L);
            return false;
        }

        return true;
    }

    private void control(String method, String path, Runnable onSuccess) {
        if (this.canControl()) {
            this.send(method, path).thenAccept(response -> {
                if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                    Minecraft.getInstance().execute(onSuccess);
                    this.updatePlaybackState();
                } else {
                    this.reportControlFailure(response);
                }
            });
        }
    }

    private CompletableFuture<HttpResponse<String>> send(String method, String path) {
        return this.send(method, path, "");
    }

    private CompletableFuture<HttpResponse<String>> send(String method, String path, String body) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API + path))
            .header("Authorization", "Bearer " + this.accessToken)
            .header("Content-Type", "application/json")
            .method(method, method.equals("GET") ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
            .build();
        return this.httpClient.sendAsync(request, BodyHandlers.ofString()).exceptionally(error -> {
            SkysongClient.LOGGER.warn("[Skysong] Spotify request failed: {}", error.getMessage());
            return null;
        });
    }

    private void reportControlFailure(HttpResponse<String> response) {
        if (response == null) {
            Feedback.show("Couldn't reach Spotify", 2000L);
            return;
        }

        String body = response.body() == null ? "" : response.body().toLowerCase(Locale.ROOT);
        switch (response.statusCode()) {
            case 401 -> {
                this.refreshAccessToken();
                Feedback.show("Spotify login refreshed, try again", 2000L);
            }
            case 403 -> Feedback.show(
                body.contains("scope") ? "Reconnect Spotify to allow controls: Right Shift → Spotify" : "Spotify Premium is needed for playback controls", 3500L
            );
            case 404 -> Feedback.show("No active Spotify device: start playing first", 2500L);
            case 429 -> Feedback.show("Spotify says slow down, try again soon", 2000L);
            default -> Feedback.show("Spotify refused that (" + response.statusCode() + ")", 2000L);
        }
    }

    public Identifier getAlbumArtId() {
        return this.albumArtId;
    }

    public String getCurrentTrackId() {
        return this.currentTrackId;
    }

    public boolean playTrack(String trackId, long positionMs) {
        if (trackId == null || !TRACK_ID.matcher(trackId).matches()) {
            return false;
        }

        if (this.usingLocal && this.local.supportsControls() && this.local.openTrack(trackId, () -> {
        })) {
            return true;
        }

        if (this.accessToken != null) {
            JsonObject body = new JsonObject();
            JsonArray uris = new JsonArray();
            uris.add("spotify:track:" + trackId);
            body.add("uris", uris);
            body.addProperty("position_ms", Math.max(0L, positionMs));
            this.send("PUT", "/me/player/play", body.toString()).thenAccept(response -> {
                if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                    this.updatePlaybackState();
                } else {
                    this.reportControlFailure(response);
                }
            });
            return true;
        }

        Util.getPlatform().openUri("spotify:track:" + trackId);
        Feedback.show("Opened the song in Spotify: press play", 3000L);
        return false;
    }

    public String getCurrentTrackName() {
        return this.currentTrackName;
    }

    public String getCurrentArtist() {
        return this.currentArtist;
    }

    public long getDurationMs() {
        return this.durationMs;
    }

    public boolean isPlaying() {
        return this.isPlaying;
    }

    public int getSongColor() {
        return this.primaryColor;
    }

    public int getSecondaryColor() {
        return this.secondaryColor;
    }

    public boolean hasAlbumPalette() {
        return this.hasAlbumPalette;
    }

    private void extractPaletteFromImage(String trackId) {
        if (!trackId.equals(this.paletteTrack)) {
            this.paletteTrack = trackId;
            this.hasAlbumPalette = false;
            int[] colors = new int[]{-43213, -13369513, -13412353, -52235, -655565};
            this.primaryColor = colors[Math.abs(trackId.hashCode()) % colors.length];
        }
    }
}
