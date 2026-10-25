package dev.infectedware.skysong.party;

import dev.infectedware.skysong.SpotifyManager;
import dev.infectedware.skysong.hud.Notifications;

final class ListenAlong {
    private static final long DRIFT_MS = 3000L;
    private static final long START_RETRY_MS = 8000L;
    private static final long SEEK_COOLDOWN_MS = 5000L;
    private static final long TOGGLE_COOLDOWN_MS = 3000L;
    private static final long WARN_COOLDOWN_MS = 30000L;
    private static String followedUuid;
    private static String startedTrack = "";
    private static String openedTrack = "";
    private static long startedAt;
    private static long seekedAt;
    private static long toggledAt;
    private static long warnedAt;
    private static boolean wasFollowing;
    private static boolean leaving;

    private ListenAlong() {
    }

    static void tick(SpotifyManager spotify) {
        PartyClient.Friend friend = PartyClient.followed();
        if (friend == null) {
            if (wasFollowing) {
                wasFollowing = false;
                followedUuid = null;
                startedTrack = "";
                openedTrack = "";
                leaving = false;
            }

            return;
        }

        if (!friend.uuid().equals(followedUuid)) {
            followedUuid = friend.uuid();
            startedTrack = "";
            openedTrack = "";
            wasFollowing = true;
            leaving = false;
            Notifications.notify("Listening along with " + friend.name(), 3000L);
        }

        if (!friend.online()) {
            if (!leaving) {
                leaving = true;
                Notifications.notify(friend.name() + " went offline", 3000L);
                PartyClient.unfollow();
            }

            return;
        }

        PartyClient.Song song = friend.song();
        if (spotify == null || song == null || song.title().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!sameTrack(spotify, song)) {
            if (song.trackId().isEmpty()) {
                if (now - warnedAt > WARN_COOLDOWN_MS) {
                    warnedAt = now;
                    Notifications.notify("Can't start " + friend.name() + "'s song: their Spotify doesn't share track links", 4000L);
                }

                return;
            }

            if (song.trackId().equals(openedTrack) || song.trackId().equals(startedTrack) && now - startedAt < START_RETRY_MS) {
                return;
            }

            startedTrack = song.trackId();
            startedAt = now;
            if (!spotify.playTrack(song.trackId(), song.positionNow())) {
                openedTrack = song.trackId();
            }

            return;
        }

        if (now - startedAt < TOGGLE_COOLDOWN_MS) {
            return;
        }

        if (song.playing() != spotify.isPlaying()) {
            if (now - toggledAt > TOGGLE_COOLDOWN_MS) {
                toggledAt = now;
                spotify.togglePlayback();
            }

            return;
        }

        if (song.playing() && Math.abs(spotify.getSmoothedProgressMs() - song.positionNow()) > DRIFT_MS && now - seekedAt > SEEK_COOLDOWN_MS) {
            seekedAt = now;
            spotify.seekTo(song.positionNow());
        }
    }

    private static boolean sameTrack(SpotifyManager spotify, PartyClient.Song song) {
        if (!song.trackId().isEmpty() && song.trackId().equals(spotify.getCurrentTrackId())) {
            return true;
        }

        return spotify.getCurrentTrackName().equalsIgnoreCase(song.title()) && spotify.getCurrentArtist().equalsIgnoreCase(song.artist());
    }
}
