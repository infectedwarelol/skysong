package dev.infectedware.skysong.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.infectedware.skysong.ModSettings;
import dev.infectedware.skysong.SkysongClient;
import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.gui.click.SettingsPopup;
import dev.infectedware.skysong.hud.Notifications;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

public final class PartyClient {
    public static final String DEFAULT_RELAY = "https://skysong-relay.developingjules.workers.dev";
    private static final long PING_SECONDS = 25L;
    private static final long MIN_RETRY_SECONDS = 5L;
    private static final long MAX_RETRY_SECONDS = 120L;
    private static final long SONG_REFRESH_MS = 20000L;
    private static final long SONG_DRIFT_MS = 2500L;
    private static final int MAX_FRAME = 262144;
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
    private static volatile List<Person> incoming = List.of();
    private static volatile List<Person> outgoing = List.of();
    private static final Map<String, String> VERIFIED = new ConcurrentHashMap<>();
    private static final Set<String> LOOKING = ConcurrentHashMap.newKeySet();
    private static ProfileKeyPair keys;
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

    public record Person(String uuid, String name) {
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

    public static List<Person> incoming() {
        return incoming;
    }

    public static List<Person> outgoing() {
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

    public static boolean isFollowing(String uuid) {
        return uuid.equals(following);
    }

    public static void tick() {
        if (++tickCounter % 10 != 0) {
            return;
        }

        boolean want = ModSettings.partyEnabled;
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

    public static void addFriend(String rawName) {
        String name = rawName.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            notify("That isn't a Minecraft name");
            return;
        }

        IO.execute(() -> {
            JsonObject profile = getJson("https://api.mojang.com/users/profiles/minecraft/" + name);
            if (profile == null || string(profile, "id").length() != 32) {
                notify("No Minecraft account named " + name);
                return;
            }

            String uuid = dashed(string(profile, "id"));
            VERIFIED.put(uuid, string(profile, "name"));
            send(message("add", "uuid", uuid).toString());
        });
    }

    public static void accept(String uuid) {
        sendLater(message("accept", "uuid", uuid));
    }

    public static void decline(String uuid) {
        sendLater(message("decline", "uuid", uuid));
    }

    public static void cancel(String uuid) {
        sendLater(message("cancel", "uuid", uuid));
    }

    public static void remove(String uuid) {
        sendLater(message("remove", "uuid", uuid));
    }

    public static void follow(String uuid) {
        sendLater(message("follow", "uuid", uuid));
    }

    private static String dashed(String id) {
        return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20);
    }

    private static JsonObject getJson(String url) {
        try {
            HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10L)).GET().build(), HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200 ? JsonParser.parseString(response.body()).getAsJsonObject() : null;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return null;
        }
    }

    private static String verifiedName(String uuid, String claimed) {
        String known = VERIFIED.get(uuid);
        if (known != null) {
            return known;
        }

        if (uuid.length() == 36 && LOOKING.add(uuid)) {
            IO.execute(() -> {
                JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replace("-", ""));
                if (profile != null && !string(profile, "name").isEmpty()) {
                    VERIFIED.put(uuid, string(profile, "name"));
                    renameAll();
                }
            });
        }

        return claimed;
    }

    private static void renameAll() {
        List<Friend> renamed = new ArrayList<>();
        for (Friend f : friends) {
            renamed.add(new Friend(f.uuid(), verifiedName(f.uuid(), f.name()), f.online(), f.song(), f.listeningToMe()));
        }

        friends = List.copyOf(renamed);
        incoming = renamePeople(incoming);
        outgoing = renamePeople(outgoing);
        rebuildMenu();
    }

    private static List<Person> renamePeople(List<Person> people) {
        List<Person> out = new ArrayList<>();
        for (Person p : people) {
            out.add(new Person(p.uuid(), verifiedName(p.uuid(), p.name())));
        }

        return List.copyOf(out);
    }

    public static void unfollow() {
        sendLater(message("unfollow", null, null));
    }

    private static String relayUrl() {
        String custom = ModSettings.partyRelayUrl == null ? "" : ModSettings.partyRelayUrl.trim();
        String raw = custom.isEmpty() ? DEFAULT_RELAY : custom;

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

            setStatus("Off");
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
        setStatus("Signing in…");

        try {
            keys = mc.getProfileKeyPairManager().prepareKeyPair().get(20L, TimeUnit.SECONDS).orElse(null);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            keys = null;
        }

        if (keys == null) {
            failed("Couldn't get your Minecraft chat key: play online with a Microsoft account");
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
            } else if (socket == null) {
                socket = ws;
            }
        }));
    }

    private static void sendHello(String nonce) {
        ProfileKeyPair pair = keys;
        if (pair == null || nonce.isEmpty()) {
            return;
        }

        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(pair.privateKey());
            signer.update(nonce.getBytes(StandardCharsets.UTF_8));
            ProfilePublicKey.Data data = pair.publicKey().data();
            User user = Minecraft.getInstance().getUser();
            JsonObject hello = message("hello", "name", user.getName());
            hello.addProperty("uuid", user.getProfileId().toString());
            hello.addProperty("key", Base64.getEncoder().encodeToString(data.key().getEncoded()));
            hello.addProperty("keySignature", Base64.getEncoder().encodeToString(data.keySignature()));
            hello.addProperty("expiresAt", data.expiresAt().toEpochMilli());
            hello.addProperty("signature", Base64.getEncoder().encodeToString(signer.sign()));
            send(hello.toString());
        } catch (GeneralSecurityException e) {
            failed("Couldn't sign in: " + e.getMessage());
        }
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
            case "challenge" -> sendHello(string(m, "nonce"));
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
                    String uuid = string(f, "uuid");
                    list.add(new Friend(uuid, verifiedName(uuid, string(f, "name")), bool(f, "online"), song(f.get("song")), bool(f, "listening")));
                }
            }
        }

        list.sort((a, b) -> a.online() != b.online() ? (a.online() ? -1 : 1) : a.name().compareToIgnoreCase(b.name()));
        friends = List.copyOf(list);
        incoming = people(m.get("incoming"));
        outgoing = people(m.get("outgoing"));
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

    private static List<Person> people(JsonElement raw) {
        List<Person> out = new ArrayList<>();
        if (raw instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e instanceof JsonObject p) {
                    String uuid = string(p, "uuid");
                    out.add(new Person(uuid, verifiedName(uuid, string(p, "name"))));
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
                    if (socket == null && wanted) {
                        socket = ws;
                    }

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
