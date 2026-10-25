package dev.infectedware.skysong;

public class DiscordManager {
    private boolean isInitialized = false;

    public void init() {
        if (!this.isInitialized) {
            SkysongClient.LOGGER.info("Initializing Discord Rich Presence...");
            this.isInitialized = true;
        }
    }

    public void updatePresence(String trackName, String artist, boolean isPlaying) {
        if (this.isInitialized) {
            if (isPlaying && !trackName.isEmpty()) {
                SkysongClient.LOGGER.info("Discord RPC Update: Listening to {} by {}", trackName, artist);
            }
        }
    }
}
