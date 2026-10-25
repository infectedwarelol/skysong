package dev.infectedware.skysong.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.gui.click.SettingsPopup;
import dev.infectedware.skysong.hud.Notifications;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

public final class PartyClient {
    private static final long PING_SECONDS = 25L;
    private static final long MIN_RETRY_SECONDS = 5L;
    private static final long MAX_RETRY_SECONDS = 120L;
    private static final long SONG_REFRESH_MS = 20000L;
    private static final long SONG_DRIFT_MS = 2500L;
    private static final int MAX_FRAME = 262144;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ScheduledExecutorService IO = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Skysong party");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    private static volatile String status = "Off";
    private static volatile String myName = "";
    private static volatile boolean connected;
    private static volatile List<Friend> friends = List.of();
    private static volatile List<String> incoming = List.of();
    private static volatile List<String> outgoing = List.of();
    private static volatile String following;
    private static WebSocket socket;
    private static String activeUrl = "";
    private static boolean wanted;
    private static int attempt;
    private static long retryAt;
    private static boolean pinging;
    private static volatile String lastSongKey = "";
    private static long lastSongSent;
    private static long lastSongPosition;
    private static boolean lastSongPlaying;
    private static int tickCounter;

    public record Song(String title, String artist, String trackId, long positionMs, long durationMs, boolean playing, long receivedAt) {
        public long positionNow() {
            long position = this.playing ? this.positionMs + (System.currentTimeMillis() - this.receivedAt) : this.positionMs;
            return this.durationMs > 0L ? Math.min(position, this.durationMs) : position;
        }

        public String label() {
            return this.artist.isEmpty() ? this.title : this.title + " · " + this.artist;
        }
    }

    public record Friend(String uuid, String name, boolean online, Song song, boolean listeningToMe) {
    }

    private PartyClient() {
    }

    public static String status() {
        return status;
    }

    public static boolean isConnected() {
        return connected;
    }

    public static List<Friend> friends() {
        return friends;
    }

    public static List<String> incoming() {
        return incoming;
    }

    public static List<String> outgoing() {
        return outgoing;
    }

    public static Friend friend(String uuid) {
        for (Friend f : friends) {
            if (f.uuid().equals(uuid)) {
                return f;
            }
        }

        return null;
    }

    public static Friend followed() {
        String uuid = following;
        if (uuid == null) {
            return null;
        }

        for (Friend f : friends) {
            if (f.uuid().equals(uuid)) {
                return f;
            }
        }

        return null;
    }

    public static boolean isFollowing(String name) {
        Friend f = followed();
        return f != null && f.name().equalsIgnoreCase(name);
    }

    public static void tick() {
        if (++tickCounter % 10 != 0) {
            return;
        }

        boolean want = ModSettings.partyEnabled && !relayUrl().isEmpty();
        String url = relayUrl();
        IO.execute(() -> reconcile(want, url));
        if (connected) {
            publishSong(SkysongClient.getSpotifyManager());
            ListenAlong.tick(SkysongClient.getSpotifyManager());
        }
    }

    public static void shutdown() {
        IO.execute(() -> reconcile(false, ""));
        IO.shutdown();
    }

    public static void reconnect() {
        IO.execute(() -> {
            closeSocket();
            retryAt = 0L;
            attempt = 0;
        });
    }

    public static void addFriend(String name) {
        sendLater(message("add", "name", name.trim()));
    }

    public static void accept(String name) {
        sendLater(message("accept", "name", name));
    }

    public static void decline(String name) {
        sendLater(message("decline", "name", name));
    }

    public static void cancel(String name) {
        sendLater(message("cancel", "name", name));
    }

    public static void remove(String name) {
        sendLater(message("remove", "name", name));
    }

    public static void follow(String name) {
        sendLater(message("follow", "name", name));
    }

    public static void unfollow() {
        sendLater(message("unfollow", null, null));
    }

    private static String relayUrl() {
        String raw = ModSettings.partyRelayUrl == null ? "" : ModSettings.partyRelayUrl.trim();
        if (raw.isEmpty()) {
            return "";
        }

        String url = raw.replaceFirst("^https://", "wss://").replaceFirst("^http://", "ws://");
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            url = "wss://" + url;
        }

        url = url.replaceAll("/+$", "");
        return url.endsWith("/ws") ? url : url + "/ws";
    }

    private static JsonObject message(String type, String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
        if (key != null) {
            o.addProperty(key, value);
        }

        return o;
    }

    private static void sendLater(JsonObject payload) {
        IO.execute(() -> send(payload.toString()));
    }

    private static void reconcile(boolean want, String url) {
        if (!want) {
            if (wanted || socket != null) {
                wanted = false;
                closeSocket();
            }

            setStatus(ModSettings.partyEnabled ? "Set the relay address below" : "Off");
            return;
        }

        if (!url.equals(activeUrl)) {
            closeSocket();
            activeUrl = url;
            retryAt = 0L;
            attempt = 0;
        }

        wanted = true;
        if (!pinging) {
            pinging = true;
            IO.scheduleAtFixedRate(() -> {
                if (socket != null) {
                    send("ping");
                }
            }, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
        }

        if (socket == null && System.currentTimeMillis() >= retryAt) {
            connect(url);
        }
    }

    private static void connect(String url) {
        retryAt = Long.MAX_VALUE;
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        String serverId = HexFormat.of().formatHex(RANDOM.generateSeed(16));
        setStatus("Signing in…");

        try {
            mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        } catch (AuthenticationException e) {
            failed("Minecraft login check failed: " + e.getMessage());
            return;
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            failed("That relay address isn't valid");
            return;
        }

        setStatus("Connecting…");
        HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10L)).buildAsync(uri, new Listener()).whenComplete((ws, error) -> IO.execute(() -> {
            if (error != null) {
                failed("Couldn't reach the relay");
            } else if (!wanted || !url.equals(activeUrl)) {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } else {
                socket = ws;
                JsonObject hello = message("hello", "name", user.getName());
                hello.addProperty("serverId", serverId);
                send(hello.toString());
            }
        }));
    }

    private static void failed(String reason) {
        setStatus(reason);
        clearState();
        long delay = Math.min(MAX_RETRY_SECONDS, MIN_RETRY_SECONDS << Math.min(attempt, 5));
        attempt++;
        retryAt = System.currentTimeMillis() + delay * 1000L;
    }

    private static void closeSocket() {
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }

        clearState();
    }

    private static void clearState() {
        boolean had = connected || !friends.isEmpty() || !incoming.isEmpty() || !outgoing.isEmpty();
        connected = false;
        friends = List.of();
        incoming = List.of();
        outgoing = List.of();
        following = null;
        lastSongKey = "";
        if (had) {
            rebuildMenu();
        }
    }

    private static void send(String text) {
        WebSocket ws = socket;
        if (ws == null) {
            return;
        }

        try {
            ws.sendText(text, true).get(10L, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            if (socket == ws) {
                socket = null;
                ws.abort();
                failed("Lost the relay connection");
            }
        }
    }

    private static void closed(WebSocket ws, String reason) {
        if (socket != ws) {
            return;
        }

        socket = null;
        failed(reason.isEmpty() ? "Disconnected from the relay" : reason);
    }

    private static void handle(String text) {
        if ("pong".equals(text)) {
            return;
        }

        JsonObject m;
        try {
            m = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }

        switch (string(m, "t")) {
            case "welcome" -> {
                myName = string(m, "name");
                connected = true;
                attempt = 0;
                lastSongKey = "";
                setStatus("Connected as " + myName);
                rebuildMenu();
            }
            case "state" -> applyState(m);
            case "song" -> applySong(string(m, "uuid"), m.get("song"));
            case "notice" -> notify(string(m, "message"));
            case "error" -> {
                String message = string(m, "message");
                notify(message);
                if (!connected) {
                    setStatus(message);
                }
            }
            default -> {
            }
        }
    }

    private static void applyState(JsonObject m) {
        String before = signature();
        List<Friend> list = new ArrayList<>();
        if (m.get("friends") instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e instanceof JsonObject f) {
                    list.add(new Friend(string(f, "uuid"), string(f, "name"), bool(f, "online"), song(f.get("song")), bool(f, "listening")));
                }
            }
        }

        list.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : a.name().compareToIgnoreCase(b.name()));
        friends = List.copyOf(list);
        incoming = names(m.get("incoming"));
        outgoing = names(m.get("outgoing"));
        following = m.get("following") != null && m.get("following").isJsonPrimitive() ? m.get("following").getAsString() : null;
        if (!before.equals(signature())) {
            rebuildMenu();
        }
    }

    private static void applySong(String uuid, JsonElement raw) {
        List<Friend> list = new ArrayList<>(friends);
        for (int i = 0; i < list.size(); i++) {
            Friend f = list.get(i);
            if (f.uuid().equals(uuid)) {
                list.set(i, new Friend(f.uuid(), f.name(), f.online(), song(raw), f.listeningToMe()));
                friends = List.copyOf(list);
                return;
            }
        }
    }

    private static String signature() {
        StringBuilder sb = new StringBuilder();
        for (Friend f : friends) {
            sb.append(f.uuid()).append(f.online()).append(f.listeningToMe()).append(';');
        }

        return sb.append(incoming).append(outgoing).append(following).toString();
    }

    private static Song song(JsonElement raw) {
        if (!(raw instanceof JsonObject s)) {
            return null;
        }

        long age = Math.max(0L, number(s, "ageMs"));
        return new Song(
            string(s, "title"), string(s, "artist"), string(s, "trackId"), number(s, "positionMs"), number(s, "durationMs"), bool(s, "playing"),
            System.currentTimeMillis() - age
        );
    }

    private static List<String> names(JsonElement raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            }
        }

        return List.copyOf(out);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0L;
    }

    private static void publishSong(SpotifyManager spotify) {
        if (spotify == null) {
            return;
        }

        boolean share = ModSettings.partyShareSong && !spotify.getCurrentTrackName().isEmpty();
        String title = share ? spotify.getCurrentTrackName() : "";
        String key = share ? (title + '\n' + spotify.getCurrentArtist() + '\n' + spotify.getCurrentTrackId() + '\n' + spotify.isPlaying()) : "";
        long now = System.currentTimeMillis();
        long position = share ? spotify.getSmoothedProgressMs() : 0L;
        long expected = lastSongPosition + (lastSongPlaying ? now - lastSongSent : 0L);
        boolean changed = !key.equals(lastSongKey);
        boolean drifted = share && Math.abs(position - expected) > SONG_DRIFT_MS;
        boolean stale = share && now - lastSongSent > SONG_REFRESH_MS;
        if (!changed && !drifted && !stale) {
            return;
        }

        lastSongKey = key;
        lastSongSent = now;
        lastSongPosition = position;
        lastSongPlaying = share && spotify.isPlaying();
        JsonObject song = message("song", "title", title);
        if (share) {
            song.addProperty("artist", spotify.getCurrentArtist());
            song.addProperty("trackId", spotify.getCurrentTrackId());
            song.addProperty("positionMs", position);
            song.addProperty("durationMs", spotify.getDurationMs());
            song.addProperty("playing", spotify.isPlaying());
        }

        sendLater(song);
    }

    private static void setStatus(String text) {
        if (!text.equals(status)) {
            status = text;
            rebuildMenu();
        }
    }

    private static void rebuildMenu() {
        Minecraft.getInstance().execute(SettingsPopup::rebuildSoon);
    }

    private static void notify(String text) {
        if (!text.isEmpty()) {
            Minecraft.getInstance().execute(() -> Notifications.notify(text, 4000L));
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1L);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            if (this.buffer.length() + data.length() <= MAX_FRAME) {
                this.buffer.append(data);
            }

            if (last) {
                String text = this.buffer.toString();
                this.buffer.setLength(0);
                IO.execute(() -> {
                    if (socket == ws) {
                        handle(text);
                    }
                });
            }

            ws.request(1L);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            IO.execute(() -> closed(ws, reason == null ? "" : reason));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            IO.execute(() -> closed(ws, "Lost the relay connection"));
        }
    }
}
