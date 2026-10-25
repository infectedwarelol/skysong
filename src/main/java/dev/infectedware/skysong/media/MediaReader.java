package dev.infectedware.skysong.media;

public interface MediaReader {
    boolean open();

    MediaSnapshot read() throws Exception;

    void close();

    default boolean control(MediaCommand command, long value) {
        return false;
    }

    default boolean openTrack(String trackId) {
        return false;
    }
}
