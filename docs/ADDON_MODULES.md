# Creating Addon Modules

Addon modules let you extend patched apps (YouTube, YouTube Music) with custom functionality at runtime — without modifying the core patches. Modules are loaded from DEX files placed on the device and managed through the Morphe settings UI.

## Quick Start

### 1. Create your module class

Implement the `AddonModule` interface:

```java
package com.example.myaddon;

import java.util.Map;
import java.util.Set;
import app.morphe.extension.shared.addons.AddonModule;

public class SkipArtistsModule implements AddonModule {

    private Set<String> blockedArtists;

    @Override
    public String getId() {
        return "skip-artists";
    }

    @Override
    public String getName() {
        return "Skip Artists";
    }

    @Override
    public String getDescription() {
        return "Skips playback for specific artists";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void onEnable(Map<String, String> config) {
        String artistList = config.getOrDefault("artists", "");
        blockedArtists = Set.of(artistList.split("\\|"));
        // Register your hooks here...
    }

    @Override
    public void onDisable() {
        blockedArtists = null;
        // Clean up hooks...
    }

    @Override
    public void onConfigChanged(Map<String, String> config) {
        // Called when the user edits config while the module is enabled.
        String artistList = config.getOrDefault("artists", "");
        blockedArtists = Set.of(artistList.split("\\|"));
    }
}
```

### 2. Compile to a DEX file

Compile your Java source and convert the resulting class files into a DEX file:

```bash
# Compile against the Android SDK and the morphe shared library
javac -source 17 -target 17 \
    -classpath "android.jar:morphe-extension-shared.jar" \
    com/example/myaddon/SkipArtistsModule.java

# Convert to DEX using d8 (from Android build tools)
d8 --output . com/example/myaddon/SkipArtistsModule.class

# Rename classes.dex to match the fully qualified class name
mv classes.dex com.example.myaddon.SkipArtistsModule.dex
```

Alternatively, if using Gradle with the Android SDK:

```kotlin
// build.gradle.kts
plugins {
    id("com.android.library")
}

android {
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(files("libs/morphe-extension-shared.jar"))
}
```

Then grab the DEX from `build/intermediates/dex/` after building.

### 3. Install the module

Copy the DEX file to the addons directory on your device:

```
/Android/data/<package-name>/files/addons/
```

Where `<package-name>` is:
- YouTube: `com.google.android.youtube` (or the patched package name)
- YouTube Music: `com.google.android.apps.youtube.music` (or the patched package name)

The exact path is displayed in the Morphe settings under **Misc > Addon modules**.

### 4. Enable the module

1. Open the patched app
2. Go to **Morphe Settings > Misc**
3. Scroll to the **Addon modules** section
4. Tap **Reload modules** if your module doesn't appear
5. Toggle the switch to enable it
6. Tap **Configure** to set key=value options

## AddonModule Interface

```java
public interface AddonModule {

    // Required — module metadata
    String getId();           // Unique identifier (e.g., "skip-artists")
    String getName();         // Display name (e.g., "Skip Artists")
    String getDescription();  // Short description shown in settings
    String getVersion();      // Version string (e.g., "1.0.0")

    // Optional — app filtering
    Set<String> getCompatibleApps();  // Package names, or empty for all apps

    // Optional — lifecycle callbacks
    void onEnable(Map<String, String> config);       // Module turned on
    void onDisable();                                 // Module turned off
    void onConfigChanged(Map<String, String> config); // Config edited while enabled
}
```

## File Naming

The module loader uses the file name to find the entry class. You have two options:

### Option A: Name the file as the class (recommended)

Name your DEX file as the fully qualified class name:

```
com.example.myaddon.SkipArtistsModule.dex
```

The loader strips the extension and loads `com.example.myaddon.SkipArtistsModule`.

### Option B: Use an .entry file

If you prefer a different file name, create a companion `.entry` text file containing the fully qualified class name:

```
addons/
  my-addon.dex
  my-addon.entry    ← contains: com.example.myaddon.SkipArtistsModule
```

## Configuration

Users configure modules through the settings UI by entering key=value pairs, one per line:

```
artists=Drake|Kanye West|Travis Scott
action=skip
notify=true
```

Your module receives these as a `Map<String, String>` in `onEnable()` and `onConfigChanged()`.

### Configuration tips

- Use `|` as a delimiter for lists within a single value
- All values are strings — parse numbers/booleans in your code
- Configuration is persisted automatically across app restarts
- Provide sensible defaults in your code for when a key is missing

## Persistence

The addon system automatically persists:
- **Enabled/disabled state** per module
- **Configuration** per module

Both survive app restarts. Modules that were enabled will be re-enabled on next app launch with their saved configuration.

## Supported File Formats

The loader accepts these file extensions:
- `.dex` — Standalone DEX file (simplest)
- `.jar` — JAR containing classes.dex
- `.apk` — APK containing classes.dex

## Example Modules

### Minimal module

```java
package com.example;

import java.util.Map;
import app.morphe.extension.shared.addons.AddonModule;

public class HelloModule implements AddonModule {
    public String getId() { return "hello"; }
    public String getName() { return "Hello World"; }
    public String getDescription() { return "A minimal example module"; }
    public String getVersion() { return "1.0.0"; }

    public void onEnable(Map<String, String> config) {
        android.util.Log.i("HelloModule", "Module enabled!");
    }
}
```

### YouTube-only module

```java
package com.example;

import java.util.Map;
import java.util.Set;
import app.morphe.extension.shared.addons.AddonModule;

public class YouTubeOnlyModule implements AddonModule {
    public String getId() { return "yt-only"; }
    public String getName() { return "YouTube Only Module"; }
    public String getDescription() { return "Only loads in YouTube"; }
    public String getVersion() { return "1.0.0"; }

    public Set<String> getCompatibleApps() {
        return Set.of("com.google.android.youtube");
    }

    public void onEnable(Map<String, String> config) {
        // YouTube-specific logic
    }
}
```

### Module with configuration

```java
package com.example;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import app.morphe.extension.shared.addons.AddonModule;

public class ContentFilterModule implements AddonModule {
    private final Set<String> blockedKeywords = new HashSet<>();

    public String getId() { return "content-filter"; }
    public String getName() { return "Content Filter"; }
    public String getDescription() { return "Filters content by keywords"; }
    public String getVersion() { return "1.0.0"; }

    public void onEnable(Map<String, String> config) {
        loadKeywords(config);
    }

    public void onConfigChanged(Map<String, String> config) {
        loadKeywords(config);
    }

    public void onDisable() {
        blockedKeywords.clear();
    }

    private void loadKeywords(Map<String, String> config) {
        blockedKeywords.clear();
        String keywords = config.getOrDefault("keywords", "");
        if (!keywords.isEmpty()) {
            for (String keyword : keywords.split("\\|")) {
                blockedKeywords.add(keyword.trim().toLowerCase());
            }
        }
    }

    public boolean shouldBlock(String text) {
        String lower = text.toLowerCase();
        for (String keyword : blockedKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
}
```

User config for the above:
```
keywords=clickbait|spam|giveaway
```

## Troubleshooting

**Module doesn't appear in settings**
- Verify the file is in the correct addons directory (path shown in settings)
- Check the file extension is `.dex`, `.jar`, or `.apk`
- Tap **Reload modules** in settings
- Check logcat for errors: `adb logcat | grep -i addon`

**ClassNotFoundException**
- Ensure the file name matches the fully qualified class name (e.g., `com.example.MyModule.dex`)
- Or create a `.entry` file with the correct class name

**Module fails to enable**
- Check that your class has a public no-argument constructor
- Check that it implements `AddonModule` correctly
- Look at logcat for the exception details

**Configuration not saving**
- Configuration is saved when you tap **Save** in the config dialog
- Ensure your key=value pairs are one per line with no extra whitespace around the `=`
