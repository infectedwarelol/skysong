package dev.infectedware.skysong.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MacSpotifyReader implements MediaReader {
    private static final String OSASCRIPT = "/usr/bin/osascript";
    private static final long TIMEOUT_MS = 2000L;
    private static final int VOLUME_STEP = 10;
    private static final String SEPARATOR = "\u001f";
    private static final String READ_SCRIPT = String.join(
        "\n",
        "if application \"Spotify\" is running then",
        "tell application \"Spotify\"",
        "set d to character id 31",
        "if player state is stopped then return \"\"",
        "set t to current track",
        "return (name of t) & d & (artist of t) & d & (album of t) & d & (duration of t) & d & (player position as string) & d & (player state as string) & d & (artwork url of t) & d & (id of t)",
        "end tell",
        "end if",
        "return \"\""
    );

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    @Override
    public boolean open() {
        return isSupported();
    }

    @Override
    public MediaSnapshot read() throws Exception {
        String out = run(READ_SCRIPT);
        if (out == null) {
            throw new IOException("osascript failed");
        }

        long sampledAt = System.currentTimeMillis();
        String[] parts = out.strip().split(SEPARATOR, -1);
        if (parts.length < 8 || parts[0].isEmpty()) {
            return null;
        }

        long durationMs = parseLong(parts[3]);
        long positionMs = Math.round(parseDouble(parts[4]) * 1000.0);
        boolean playing = parts[5].trim().equalsIgnoreCase("playing");
        String id = parts[7].trim();
        String trackId = id.startsWith("spotify:track:") ? id.substring("spotify:track:".length()) : "";
        return new MediaSnapshot(parts[0], parts[1], parts[2], Math.max(0L, positionMs), durationMs, playing, sampledAt, parts[6].trim(), trackId);
    }

    @Override
    public void close() {
    }

    @Override
    public boolean control(MediaCommand command, long value) {
        String action = switch (command) {
            case PLAY_PAUSE -> "playpause";
            case NEXT -> "next track";
            case PREVIOUS -> "previous track";
            case SEEK_TO -> "set player position to " + String.format(Locale.ROOT, "%.3f", Math.max(0L, value) / 1000.0);
            case VOLUME_STEP -> "set sound volume to ((sound volume) + (" + Long.signum(value) * VOLUME_STEP + "))";
        };
        return run("if application \"Spotify\" is running then\ntell application \"Spotify\" to " + action + "\nend if") != null;
    }

    @Override
    public boolean openTrack(String trackId) {
        return run("if application \"Spotify\" is running then\ntell application \"Spotify\" to play track \"spotify:track:" + trackId + "\"\nend if") != null;
    }

    private static long parseLong(String text) {
        try {
            return Math.round(Double.parseDouble(text.trim().replace(',', '.')));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String run(String script) {
        Process process = null;

        try {
            process = new ProcessBuilder(OSASCRIPT, "-e", script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process running = process;
            Thread killer = Thread.ofVirtual().start(() -> {
                try {
                    if (!running.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        running.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    running.destroyForcibly();
                }
            });
            byte[] output;
            try (InputStream in = process.getInputStream()) {
                output = in.readAllBytes();
            }

            killer.join(TIMEOUT_MS);
            return process.isAlive() || process.exitValue() != 0 ? null : new String(output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();

            return null;
        }
    }
}
