package dev.infectedware.skysong.media;

import dev.infectedware.skysong.SkysongClient;

public final class LocalMediaSource {
    private static final long POLL_MS = 500L;
    private static final long RETRY_MS = 5000L;
    private volatile WindowsMediaReader.Snapshot latest;
    private volatile boolean running;
    private Thread thread;

    public void start() {
        if (!this.running && WindowsMediaReader.isSupported()) {
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

    public WindowsMediaReader.Snapshot latest() {
        return this.latest;
    }

    private void run() {
        WindowsMediaReader reader = new WindowsMediaReader();
        boolean warned = false;

        try {
            while (this.running) {
                if (!reader.open()) {
                    this.latest = null;
                    Thread.sleep(5000L);
                } else {
                    try {
                        while (this.running) {
                            this.latest = reader.read();
                            Thread.sleep(500L);
                        }
                    } catch (InterruptedException e) {
                        throw e;
                    } catch (Throwable t) {
                        if (!warned) {
                            SkysongClient.LOGGER.warn("[Skysong] Reading media from Windows failed: " + t);
                        }

                        warned = true;
                        this.latest = null;
                        reader.close();
                        Thread.sleep(5000L);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            reader.close();
        }
    }
}
