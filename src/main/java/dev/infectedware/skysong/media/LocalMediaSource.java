package dev.infectedware.skysong.media;

import java.util.concurrent.TimeUnit;
import dev.infectedware.skysong.SkysongClient;

public final class LocalMediaSource {
    private static final long POLL_MS = 500L;
    private static final long RETRY_MS = 5000L;
    private volatile MediaSnapshot latest;
    private volatile MediaReader active;
    private volatile boolean running;
    private Thread thread;

    public static boolean isSupported() {
        return WindowsMediaReader.isSupported() || LinuxMprisReader.isSupported() || MacSpotifyReader.isSupported();
    }

    private static MediaReader createReader() {
        if (WindowsMediaReader.isSupported()) {
            return new WindowsMediaReader();
        }

        if (LinuxMprisReader.isSupported()) {
            return new LinuxMprisReader();
        }

        return MacSpotifyReader.isSupported() ? new MacSpotifyReader() : null;
    }

    public void start() {
        if (!this.running && isSupported()) {
            this.running = true;
            this.thread = new Thread(this::run, "Skysong local media");
            this.thread.setDaemon(true);
            this.thread.start();
        }
    }

    public void stop() {
        this.running = false;
        this.latest = null;
        if (this.thread != null) {
            this.thread.interrupt();
        }
    }

    public MediaSnapshot latest() {
        return this.latest;
    }

    public boolean control(MediaCommand command, long value, Runnable onDone) {
        MediaReader reader = this.active;
        if (reader == null || this.latest == null) {
            return false;
        }

        Thread worker = new Thread(() -> {
            if (reader.control(command, value)) {
                onDone.run();
            }
        }, "Skysong media control");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    public boolean supportsControls() {
        MediaReader reader = this.active;
        return reader != null && !(reader instanceof WindowsMediaReader);
    }

    private void run() {
        MediaReader reader = createReader();
        if (reader == null) {
            this.running = false;
            return;
        }

        boolean warned = false;

        try {
            while (this.running) {
                if (!reader.open()) {
                    this.latest = null;
                    this.active = null;
                    TimeUnit.MILLISECONDS.sleep(RETRY_MS);
                } else {
                    this.active = reader;

                    try {
                        while (this.running) {
                            this.latest = reader.read();
                            TimeUnit.MILLISECONDS.sleep(POLL_MS);
                        }
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Throwable t) {
                        if (!warned) {
                            SkysongClient.LOGGER.warn("[Skysong] Reading Spotify on this PC failed: {}", String.valueOf(t));
                        }

                        warned = true;
                        this.latest = null;
                        this.active = null;
                        reader.close();
                        TimeUnit.MILLISECONDS.sleep(RETRY_MS);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            this.active = null;
            reader.close();
        }
    }
}
