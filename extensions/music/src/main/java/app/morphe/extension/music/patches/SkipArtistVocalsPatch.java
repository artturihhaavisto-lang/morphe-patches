package app.morphe.extension.music.patches;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
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
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static volatile boolean skipPending;

    /**
     * Injection point. Called from MusicActivity.onCreate().
     */
    public static void init(Activity activity) {
        if (!Settings.SKIP_ARTIST_VOCALS.get()) {
            return;
        }
        registerSessionListener(activity);
    }

    private static void registerSessionListener(Context context) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return;
            }

            // getActiveSessions(null) works within the same app process for the
            // app's own sessions without requiring notification listener access.
            try {
                List<MediaController> controllers = manager.getActiveSessions(null);
                for (MediaController controller : controllers) {
                    attachCallback(controller, context);
                }
            } catch (SecurityException ignored) {
                // Expected if no notification listener permission.
            }

            try {
                manager.addOnActiveSessionsChangedListener(controllers -> {
                    if (controllers == null) return;
                    for (MediaController controller : controllers) {
                        attachCallback(controller, context);
                    }
                }, null);
            } catch (SecurityException ignored) {
                // Fall back: poll metadata on a timer.
                startPollingFallback(manager, context);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Failed to register media session listener", ex);
        }
    }

    /**
     * Fallback polling mechanism if MediaSessionManager listener registration
     * fails due to missing permissions.
     */
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
                        checkAndSkip(controller.getMetadata(), context);
                    }
                } catch (SecurityException ignored) {
                    // Cannot access sessions at all.
                }
                handler.postDelayed(this, 2000);
            }
        }, 2000);
    }

    private static void attachCallback(MediaController controller, Context context) {
        controller.registerCallback(new MediaController.Callback() {
            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                if (!Settings.SKIP_ARTIST_VOCALS.get()) {
                    return;
                }
                checkAndSkip(metadata, context);
            }
        }, handler);

        // Check the current metadata immediately.
        checkAndSkip(controller.getMetadata(), context);
    }

    private static void checkAndSkip(MediaMetadata metadata, Context context) {
        if (metadata == null) {
            return;
        }
        if (shouldSkip(metadata)) {
            skipToNext(context);
        }
    }

    private static boolean shouldSkip(MediaMetadata metadata) {
        // Check primary artist field.
        if (containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))) {
            return true;
        }
        // Check album artist (covers cases where sexmane is the album artist).
        if (containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))) {
            return true;
        }
        // Check display subtitle — YouTube Music uses this for "Artist • Album" text.
        // Catches featured credits like "Artist feat. sexmane".
        if (containsArtist(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))) {
            return true;
        }
        // Check display description for any remaining credits.
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

        // Small delay to let the player fully initialize the track before skipping.
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
}
