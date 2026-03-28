package app.morphe.extension.music.patches;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.List;
import java.util.Locale;

import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public class SkipArtistVocalsPatch {

    private static final String ARTIST_NAME = "sexmane";
    private static final long POLL_INTERVAL_MS = 500;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static volatile boolean skipPending;
    private static volatile boolean initialized;

    /**
     * Injection point. Called from MusicActivity.onCreate().
     */
    public static void init(Activity activity) {
        if (!Settings.SKIP_ARTIST_VOCALS.get() || initialized) {
            return;
        }
        initialized = true;
        registerSessionListener(activity);
    }

    private static void registerSessionListener(Context context) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return;
            }

            try {
                List<MediaController> controllers = manager.getActiveSessions(null);
                for (MediaController controller : controllers) {
                    attachCallback(controller, context);
                }
            } catch (SecurityException ignored) {
            }

            try {
                manager.addOnActiveSessionsChangedListener(controllers -> {
                    if (controllers == null) return;
                    for (MediaController controller : controllers) {
                        attachCallback(controller, context);
                    }
                }, null);
            } catch (SecurityException ignored) {
                startPollingFallback(manager, context);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to register media session listener", ex);
        }
    }

    private static void startPollingFallback(MediaSessionManager manager, Context context) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!Settings.SKIP_ARTIST_VOCALS.get()) {
                    handler.postDelayed(this, 2000);
                    return;
                }
                try {
                    List<MediaController> controllers = manager.getActiveSessions(null);
                    for (MediaController controller : controllers) {
                        checkAndSkipTrack(controller.getMetadata(), context);
                        checkAndSkipSegments(controller);
                    }
                } catch (SecurityException ignored) {
                }
                handler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private static void attachCallback(MediaController controller, Context context) {
        startSegmentPoller(controller, context);

        controller.registerCallback(new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                if (!Settings.SKIP_ARTIST_VOCALS.get()) {
                    return;
                }
                checkAndSkipTrack(metadata, context);
            }
        }, handler);

        checkAndSkipTrack(controller.getMetadata(), context);
    }

    /**
     * Polls playback position and seeks past vocal segments
     * defined in {@link ArtistVocalSegments}.
     */
    private static void startSegmentPoller(MediaController controller, Context context) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!Settings.SKIP_ARTIST_VOCALS.get()) {
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                    return;
                }
                checkAndSkipSegments(controller);
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }, POLL_INTERVAL_MS);
    }

    private static void checkAndSkipSegments(MediaController controller) {
        try {
            MediaMetadata metadata = controller.getMetadata();
            PlaybackState state = controller.getPlaybackState();
            if (metadata == null || state == null) {
                return;
            }
            if (state.getState() != PlaybackState.STATE_PLAYING) {
                return;
            }

            String mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            if (mediaId == null) {
                String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (title != null && artist != null) {
                    mediaId = artist.toLowerCase(Locale.ROOT) + "|" + title.toLowerCase(Locale.ROOT);
                }
            }
            if (mediaId == null) {
                return;
            }

            long[][] segments = ArtistVocalSegments.getSegments(mediaId);
            if (segments == null) {
                return;
            }

            long position = state.getPosition();
            for (long[] segment : segments) {
                long start = segment[0];
                long end = segment[1];
                if (position >= start && position < end) {
                    Logger.printInfo(() -> "Seeking past vocal segment at " + position
                            + "ms -> " + end + "ms");
                    controller.getTransportControls().seekTo(end);
                    return;
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Segment skip check failed", ex);
        }
    }

    // region Full-track skip

    private static void checkAndSkipTrack(MediaMetadata metadata, Context context) {
        if (metadata == null) {
            return;
        }
        if (shouldSkipTrack(metadata)) {
            skipToNext(context);
        }
    }

    private static boolean shouldSkipTrack(MediaMetadata metadata) {
        if (containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))) {
            return true;
        }
        if (containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))) {
            return true;
        }
        if (containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))) {
            return true;
        }
        return containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION));
    }

    private static boolean containsArtist(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(ARTIST_NAME);
    }

    private static void skipToNext(Context context) {
        if (skipPending) {
            return;
        }
        skipPending = true;

        handler.postDelayed(() -> {
            try {
                AudioManager audioManager = (AudioManager)
                        context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager == null) return;

                long now = SystemClock.uptimeMillis();
                audioManager.dispatchMediaKeyEvent(
                        new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_MEDIA_NEXT, 0));
                audioManager.dispatchMediaKeyEvent(
                        new KeyEvent(now, now, KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_MEDIA_NEXT, 0));

                Logger.printInfo(() -> "Skipped track by blocked artist");
            } catch (Exception ex) {
                Logger.printException(() -> "Failed to skip track", ex);
            } finally {
                skipPending = false;
            }
        }, 400);
    }

    // endregion
}
