package dev.infectedware.skysong.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinuxMprisReader implements MediaReader {
    private static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER = "org.mpris.MediaPlayer2.Player";
    private static final String PREFIX = "org.mpris.MediaPlayer2.";
    private static final long TIMEOUT_MS = 2000L;
    private static final double MICROS_PER_MS = 1000.0;
    private static final double VOLUME_STEP = 0.1;
    private static final Pattern NAME = Pattern.compile("'(org\\.mpris\\.MediaPlayer2\\.[^']+)'");
    private static final Pattern LENGTH = Pattern.compile("'mpris:length': <(?:u?int64 )?(\\d+)>");
    private static final Pattern POSITION = Pattern.compile("'Position': <(?:u?int64 )?(-?\\d+)>");
    private static final Pattern STATUS = Pattern.compile("'PlaybackStatus': <'(\\w+)'>");
    private static final Pattern SINGLE_NUMBER = Pattern.compile("<(?:u?int64 |double )?(-?[\\d.eE+-]+)>");
    private static volatile String gdbusPath;
    private volatile String destination;

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux") && gdbus() != null;
    }

    private static String gdbus() {
        String cached = gdbusPath;
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String found = findGdbus();
        gdbusPath = found == null ? "" : found;
        return found;
    }

    private static String findGdbus() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }

        for (String dir : path.split(":")) {
            Path candidate = Path.of(dir, "gdbus");
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }

        return null;
    }

    @Override
    public boolean open() {
        this.destination = findSpotify();
        return this.destination != null;
    }

    @Override
    public MediaSnapshot read() throws Exception {
        String dest = this.destination;
        if (dest == null) {
            return null;
        }

        String out = run("call", "--session", "--dest", dest, "--object-path", OBJECT_PATH, "--method", "org.freedesktop.DBus.Properties.GetAll", PLAYER);
        if (out == null) {
            throw new IOException("Spotify stopped answering on D-Bus");
        }

        long sampledAt = System.currentTimeMillis();
        String title = stringValue(out, "xesam:title");
        if (title.isEmpty()) {
            return null;
        }

        String artist = stringValue(out, "xesam:artist");
        String album = stringValue(out, "xesam:album");
        String artUrl = stringValue(out, "mpris:artUrl");
        String trackId = stringValue(out, "mpris:trackid");
        int slash = trackId.lastIndexOf('/');
        if (slash >= 0) {
            trackId = trackId.substring(slash + 1);
        }

        if (!trackId.matches("[0-9A-Za-z]{22}")) {
            trackId = "";
        }

        long durationMs = Math.round(longValue(LENGTH, out) / MICROS_PER_MS);
        long positionMs = Math.max(0L, Math.round(longValue(POSITION, out) / MICROS_PER_MS));
        Matcher status = STATUS.matcher(out);
        boolean playing = status.find() && status.group(1).equals("Playing");
        return new MediaSnapshot(title, artist, album, positionMs, durationMs, playing, sampledAt, artUrl, trackId);
    }

    @Override
    public void close() {
        this.destination = null;
    }

    @Override
    public boolean control(MediaCommand command, long value) {
        String dest = this.destination;
        if (dest == null) {
            return false;
        }

        return switch (command) {
            case PLAY_PAUSE -> call(dest, PLAYER + ".PlayPause");
            case NEXT -> call(dest, PLAYER + ".Next");
            case PREVIOUS -> call(dest, PLAYER + ".Previous");
            case SEEK_TO -> this.seekTo(dest, value);
            case VOLUME_STEP -> this.stepVolume(dest, value);
        };
    }

    private boolean seekTo(String dest, long targetMs) {
        String out = run("call", "--session", "--dest", dest, "--object-path", OBJECT_PATH, "--method", "org.freedesktop.DBus.Properties.Get", PLAYER, "Position");
        if (out == null) {
            return false;
        }

        long currentMicros = longValue(SINGLE_NUMBER, out);
        long offset = targetMs * 1000L - currentMicros;
        return call(dest, PLAYER + ".Seek", Long.toString(offset));
    }

    private boolean stepVolume(String dest, long direction) {
        String out = run("call", "--session", "--dest", dest, "--object-path", OBJECT_PATH, "--method", "org.freedesktop.DBus.Properties.Get", PLAYER, "Volume");
        if (out == null) {
            return false;
        }

        Matcher m = SINGLE_NUMBER.matcher(out);
        double volume;
        try {
            volume = m.find() ? Double.parseDouble(m.group(1)) : 0.5;
        } catch (NumberFormatException e) {
            volume = 0.5;
        }

        double next = Math.clamp(volume + Long.signum(direction) * VOLUME_STEP, 0.0, 1.0);
        String variant = "<" + String.format(Locale.ROOT, "%.3f", next) + ">";
        return run(
                "call", "--session", "--dest", dest, "--object-path", OBJECT_PATH, "--method", "org.freedesktop.DBus.Properties.Set", PLAYER, "Volume", variant
            )
            != null;
    }

    private static boolean call(String dest, String method, String... args) {
        List<String> command = new ArrayList<>(List.of("call", "--session", "--dest", dest, "--object-path", OBJECT_PATH, "--method", method));
        command.addAll(List.of(args));
        return run(command.toArray(String[]::new)) != null;
    }

    private static String findSpotify() {
        String out = run(
            "call", "--session", "--dest", "org.freedesktop.DBus", "--object-path", "/org/freedesktop/DBus", "--method", "org.freedesktop.DBus.ListNames"
        );
        if (out == null) {
            return null;
        }

        Matcher m = NAME.matcher(out);
        String fallback = null;

        while (m.find()) {
            String name = m.group(1);
            String player = name.substring(PREFIX.length()).toLowerCase(Locale.ROOT);
            if (player.equals("spotify") || player.startsWith("spotify.")) {
                return name;
            }

            if (fallback == null && (player.contains("spotify") || player.startsWith("ncspot"))) {
                fallback = name;
            }
        }

        return fallback;
    }

    private static long longValue(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) {
            return 0L;
        }

        try {
            return Math.round(Double.parseDouble(m.group(1)));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static String stringValue(String text, String key) {
        int at = text.indexOf("'" + key + "': <");
        if (at < 0) {
            return "";
        }

        int i = at + key.length() + 5;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '[' || text.charAt(i) == '@')) {
            if (text.charAt(i) == '@') {
                while (i < text.length() && text.charAt(i) != ' ') {
                    i++;
                }
            }

            i++;
        }

        if (i >= text.length()) {
            return "";
        }

        char quote = text.charAt(i);
        if (quote != '\'' && quote != '"') {
            return "";
        }

        StringBuilder value = new StringBuilder();

        for (int j = i + 1; j < text.length(); j++) {
            char c = text.charAt(j);
            if (c == '\\' && j + 1 < text.length()) {
                char next = text.charAt(++j);
                switch (next) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (j + 4 < text.length()) {
                            try {
                                value.append((char)Integer.parseInt(text.substring(j + 1, j + 5), 16));
                                j += 4;
                            } catch (NumberFormatException e) {
                                value.append(next);
                            }
                        }
                    }
                    default -> value.append(next);
                }
            } else {
                if (c == quote) {
                    return value.toString();
                }

                value.append(c);
            }
        }

        return value.toString();
    }

    private static String run(String... args) {
        String binary = gdbus();
        if (binary == null) {
            return null;
        }

        List<String> command = new ArrayList<>(args.length + 1);
        command.add(binary);
        command.addAll(List.of(args));
        if (args.length > 0 && args[0].equals("call")) {
            command.add(2, "--timeout=" + TIMEOUT_MS / 1000L);
        }
        Process process = null;

        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            byte[] output;
            try (InputStream in = process.getInputStream()) {
                output = in.readAllBytes();
            }

            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }

            return process.exitValue() == 0 ? new String(output, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();

            return null;
        }
    }
}
