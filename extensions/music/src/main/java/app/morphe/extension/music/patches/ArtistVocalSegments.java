package app.morphe.extension.music.patches;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry of vocal segments to skip for specific tracks.
 *
 * Each entry maps a track key to an array of [start_ms, end_ms] timestamp pairs.
 * When playback enters any of these ranges the player seeks past it.
 *
 * <h3>How to add segments</h3>
 * <ol>
 *   <li>Find the track's media ID (YouTube video ID) <b>or</b> use
 *       {@code "artist|title"} (lowercase) as the key.</li>
 *   <li>Add an entry to the {@code SEGMENTS} map below with the timestamp
 *       ranges in <b>milliseconds</b>.</li>
 * </ol>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Skip sexmane's verse from 1:02 to 1:47 and his outro from 3:10 to 3:35
 * SEGMENTS.put("dQw4w9WgXcQ", new long[][]{
 *     {62_000, 107_000},
 *     {190_000, 215_000},
 * });
 *
 * // Same track but keyed by artist|title (fallback when media ID is unavailable)
 * SEGMENTS.put("some artist|some song title", new long[][]{
 *     {62_000, 107_000},
 *     {190_000, 215_000},
 * });
 * }</pre>
 */
public final class ArtistVocalSegments {

    private static final Map<String, long[][]> SEGMENTS = new HashMap<>();

    static {
        // =====================================================================
        // Add vocal segments to skip below.
        //
        // Format:
        //   SEGMENTS.put("<videoId or artist|title>", new long[][]{
        //       {start_ms, end_ms},   // segment 1
        //       {start_ms, end_ms},   // segment 2
        //   });
        //
        // Timestamps are in milliseconds. Use helpers:
        //   mins(m, s)       -> milliseconds  (e.g. mins(1, 30) = 90_000)
        //   secs(s)          -> milliseconds  (e.g. secs(45)    = 45_000)
        // =====================================================================

        // -- Example entries (uncomment and adjust as needed) --

        // SEGMENTS.put("VIDEO_ID_HERE", new long[][]{
        //     {mins(1, 2), mins(1, 47)},   // sexmane verse
        //     {mins(3, 10), mins(3, 35)},   // sexmane outro
        // });
    }

    /**
     * Returns the skip segments for the given track, or {@code null} if none are defined.
     *
     * @param trackKey Either a YouTube video/media ID or a lowercase {@code "artist|title"} key.
     */
    @Nullable
    public static long[][] getSegments(String trackKey) {
        if (trackKey == null) {
            return null;
        }
        long[][] result = SEGMENTS.get(trackKey);
        if (result != null) {
            return result;
        }
        // Try lowercase lookup for artist|title keys.
        return SEGMENTS.get(trackKey.toLowerCase(Locale.ROOT));
    }

    /** Convert minutes + seconds to milliseconds. */
    public static long mins(int minutes, int seconds) {
        return (minutes * 60L + seconds) * 1000L;
    }

    /** Convert seconds to milliseconds. */
    public static long secs(int seconds) {
        return seconds * 1000L;
    }

    private ArtistVocalSegments() {
    }
}
