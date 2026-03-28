package app.morphe.addon.skipartist;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.addons.AddonModule;

/**
 * Addon module that skips songs by blocked artists and seeks past
 * vocal segments where a blocked artist is featured.
 *
 * <h3>Configuration (key=value in Morphe settings)</h3>
 * <pre>
 * artists=sexmane
 * </pre>
 *
 * Multiple artists separated by {@code |}:
 * <pre>
 * artists=sexmane|another artist
 * </pre>
 *
 * Vocal segment timestamps (video ID or "artist|title" key, then start-end pairs in ms):
 * <pre>
 * segments.VIDEO_ID=62000-107000,190000-215000
 * segments.some artist|some song=30000-58000
 * </pre>
 */
public class SkipArtistVocalsModule implements AddonModule {

    private static final String TAG = "SkipArtistVocals";
    private static final long POLL_INTERVAL_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> blockedArtists = new HashSet<>();
    private final Map<String, long[][]> segments = new HashMap<>();

    private volatile boolean enabled;
    private volatile boolean skipPending;
    private volatile boolean polling;

    // region AddonModule interface

    @NonNull
    @Override
    public String getId() {
        return "skip-artist-vocals";
    }

    @NonNull
    @Override
    public String getName() {
        return "Skip Artist Vocals";
    }

    @NonNull
    @Override
    public String getDescription() {
        return "Skips songs by blocked artists and seeks past their vocal segments";
    }

    @NonNull
    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @NonNull
    @Override
    public Set<String> getCompatibleApps() {
        return Collections.singleton("com.google.android.apps.youtube.music");
    }

    @Override
    public void onEnable(@NonNull Map<String, String> config) {
        parseConfig(config);
        enabled = true;
        Log.i(TAG, "Enabled — blocking " + blockedArtists.size() + " artist(s), "
                + segments.size() + " segment track(s)");
        startMonitoring();
    }

    @Override
    public void onDisable() {
        enabled = false;
        polling = false;
        blockedArtists.clear();
        segments.clear();
        Log.i(TAG, "Disabled");
    }

    @Override
    public void onConfigChanged(@NonNull Map<String, String> config) {
        parseConfig(config);
        Log.i(TAG, "Config updated — blocking " + blockedArtists.size() + " artist(s), "
                + segments.size() + " segment track(s)");
    }

    // endregion

    // region Configuration parsing

    private void parseConfig(Map<String, String> config) {
        blockedArtists.clear();
        segments.clear();

        // Parse artist list: artists=sexmane|another artist
        String artistList = config.get("artists");
        if (artistList != null && !artistList.isEmpty()) {
            for (String artist : artistList.split("\\|")) {
                String trimmed = artist.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    blockedArtists.add(trimmed);
                }
            }
        }

        // Parse segment entries: segments.VIDEO_ID=start-end,start-end
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("segments.")) continue;

            String trackKey = key.substring("segments.".length());
            String value = entry.getValue();
            if (value == null || value.isEmpty()) continue;

            String[] pairs = value.split(",");
            long[][] parsed = new long[pairs.length][2];
            int count = 0;
            for (String pair : pairs) {
                String[] parts = pair.trim().split("-");
                if (parts.length == 2) {
                    try {
                        parsed[count][0] = Long.parseLong(parts[0].trim());
                        parsed[count][1] = Long.parseLong(parts[1].trim());
                        count++;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (count > 0) {
                long[][] trimmed = new long[count][2];
                System.arraycopy(parsed, 0, trimmed, 0, count);
                segments.put(trackKey, trimmed);
            }
        }
    }

    // endregion

    // region Monitoring

    private void startMonitoring() {
        if (polling) return;
        polling = true;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!enabled || !polling) return;

                try {
                    Context context = getAppContext();
                    if (context != null) {
                        pollSessions(context);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Poll error", ex);
                }

                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        });
    }

    private void pollSessions(Context context) {
        MediaSessionManager manager = (MediaSessionManager)
                context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) return;

        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(null);
        } catch (SecurityException ignored) {
            return;
        }

        for (MediaController controller : controllers) {
            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null) continue;

            // Full-track skip.
            if (shouldSkipTrack(metadata)) {
                skipToNext(context);
                return;
            }

            // Segment skip.
            checkAndSkipSegments(controller, metadata);
        }
    }

    // endregion

    // region Full-track skip

    private boolean shouldSkipTrack(MediaMetadata metadata) {
        if (blockedArtists.isEmpty()) return false;

        return matchesAnyArtist(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
                || matchesAnyArtist(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))
                || matchesAnyArtist(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))
                || matchesAnyArtist(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION));
    }

    private boolean matchesAnyArtist(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String artist : blockedArtists) {
            if (lower.contains(artist)) return true;
        }
        return false;
    }

    private void skipToNext(Context context) {
        if (skipPending) return;
        skipPending = true;

        handler.postDelayed(() -> {
            try {
                AudioManager am = (AudioManager)
                        context.getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;

                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(
                        new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_MEDIA_NEXT, 0));
                am.dispatchMediaKeyEvent(
                        new KeyEvent(now, now, KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_MEDIA_NEXT, 0));

                Log.i(TAG, "Skipped track by blocked artist");
            } catch (Exception ex) {
                Log.e(TAG, "Skip failed", ex);
            } finally {
                skipPending = false;
            }
        }, 400);
    }

    // endregion

    // region Segment skip

    private void checkAndSkipSegments(MediaController controller, MediaMetadata metadata) {
        if (segments.isEmpty()) return;

        PlaybackState state = controller.getPlaybackState();
        if (state == null || state.getState() != PlaybackState.STATE_PLAYING) return;

        String trackKey = resolveTrackKey(metadata);
        if (trackKey == null) return;

        long[][] trackSegments = segments.get(trackKey);
        if (trackSegments == null) return;

        long position = state.getPosition();
        for (long[] seg : trackSegments) {
            if (position >= seg[0] && position < seg[1]) {
                Log.i(TAG, "Seeking past vocal segment " + seg[0] + "ms -> " + seg[1] + "ms");
                controller.getTransportControls().seekTo(seg[1]);
                return;
            }
        }
    }

    private String resolveTrackKey(MediaMetadata metadata) {
        String mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if (mediaId != null && segments.containsKey(mediaId)) {
            return mediaId;
        }

        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (title != null && artist != null) {
            String compound = artist.toLowerCase(Locale.ROOT) + "|" + title.toLowerCase(Locale.ROOT);
            if (segments.containsKey(compound)) {
                return compound;
            }
        }
        return null;
    }

    // endregion

    // region Utilities

    private static Context getAppContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object currentApp = activityThread.getMethod("currentApplication").invoke(null);
            return (Context) currentApp;
        } catch (Exception e) {
            return null;
        }
    }

    // endregion
}
