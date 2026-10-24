package com.spotifyclient;

public class DiscordManager {
    private boolean isInitialized = false;

    public void init() {
        if (!this.isInitialized) {
            SpotifyModClient.LOGGER.info("Initializing Discord Rich Presence...");
            this.isInitialized = true;
        }
    }

    public void updatePresence(String trackName, String artist, long durationMs, long progressMs, boolean isPlaying) {
        if (this.isInitialized) {
            if (isPlaying && !trackName.isEmpty()) {
                SpotifyModClient.LOGGER.info("Discord RPC Update: Listening to " + trackName + " by " + artist);
            }
        }
    }
}
