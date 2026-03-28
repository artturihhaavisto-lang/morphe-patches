# Skip Artist Vocals Plugin

Automatically skips songs by **sexmane** and seeks past vocal segments where sexmane is featured.

## Features

- **Full-track skip** — Any song where sexmane is the artist or featured artist is skipped entirely.
- **Segment skip** — SponsorBlock-style timestamp ranges let you skip just the parts of a song where sexmane sings, keeping the rest of the track.

## Adding vocal segments

Open `extension/ArtistVocalSegments.java` and add entries to the `SEGMENTS` map:

```java
// Skip his verse from 1:02–1:47 and outro from 3:10–3:35
SEGMENTS.put("VIDEO_ID_HERE", new long[][]{
    {mins(1, 2), mins(1, 47)},
    {mins(3, 10), mins(3, 35)},
});
```

Keys can be a YouTube video ID or a lowercase `"artist|title"` string.

## Installation

Upload this plugin through the Morphe app's plugin loader. The toggle appears under **Settings → Player → Skip sexmane vocals**.

## Files

| File | Purpose |
|------|---------|
| `plugin.json` | Plugin metadata and settings definition |
| `patch/SkipArtistVocalsPatch.kt` | Bytecode patch — hooks MusicActivity.onCreate() |
| `extension/SkipArtistVocalsPatch.java` | Runtime logic — metadata monitoring + skip |
| `extension/ArtistVocalSegments.java` | Timestamp registry for segment skipping |
