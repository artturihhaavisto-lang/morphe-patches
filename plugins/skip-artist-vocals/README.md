# Skip Artist Vocals — Addon Module

Skips songs by blocked artists and seeks past their vocal segments in YouTube Music.

## Install

1. Build the module to a DEX (see below)
2. Copy it to `/Android/data/<ytmusic-package>/files/addons/` on your device:
   ```
   app.morphe.addon.skipartist.SkipArtistVocalsModule.dex
   ```
3. Open YouTube Music → Morphe Settings → Misc → Addon modules
4. Tap **Reload modules**, then enable **Skip Artist Vocals**

## Configure

Tap **Configure** on the module in settings and enter key=value pairs:

```
artists=sexmane
```

Multiple artists (pipe-separated):
```
artists=sexmane|other artist
```

### Vocal segment timestamps

Skip specific timestamp ranges within a song (milliseconds):

```
segments.VIDEO_ID_HERE=62000-107000,190000-215000
segments.some artist|some song=30000-58000
```

- Key format: `segments.<video ID>` or `segments.<artist|title>` (lowercase)
- Value format: `start-end` pairs separated by commas

### Full example config

```
artists=sexmane
segments.dQw4w9WgXcQ=62000-107000,190000-215000
segments.other artist|collab track=30000-58000
```

## Build

```bash
# Compile against Android SDK + morphe shared addon interface
javac -source 17 -target 17 \
  -classpath "android.jar:morphe-extension-shared.jar" \
  src/app/morphe/addon/skipartist/SkipArtistVocalsModule.java

# Convert to DEX
d8 --output . src/app/morphe/addon/skipartist/SkipArtistVocalsModule.class

# Rename to match the fully qualified class name
mv classes.dex app.morphe.addon.skipartist.SkipArtistVocalsModule.dex
```

## How it works

- **Full-track skip**: Polls active MediaSessions every 500ms. If any metadata field
  (artist, album artist, subtitle, description) contains a blocked artist name, dispatches
  `KEYCODE_MEDIA_NEXT` to skip the track.

- **Segment skip**: When the playback position enters a defined timestamp range,
  calls `seekTo()` on the MediaController transport controls to jump past it.
