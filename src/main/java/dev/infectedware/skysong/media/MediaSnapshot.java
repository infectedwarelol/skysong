package dev.infectedware.skysong.media;

public record MediaSnapshot(
    String title, String artist, String album, long positionMs, long durationMs, boolean playing, long sampledAt, String artUrl, String trackId
) {
    public long positionNow() {
        long p = this.playing ? this.positionMs + (System.currentTimeMillis() - this.sampledAt) : this.positionMs;
        return Math.max(0L, this.durationMs > 0L ? Math.min(p, this.durationMs) : p);
    }
}
