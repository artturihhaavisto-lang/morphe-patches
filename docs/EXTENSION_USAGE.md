# Using the Included Extension in Patches

Extensions (`.mpe` files) are compiled DEX modules that get merged into the target app at patch time. They contain the Java/Kotlin code that actually runs inside the patched app. Patches themselves are build-time tools — they locate methods and inject Smali call instructions that invoke the extension code at runtime.

## How it works

```
Extension (.mpe)  ──merged into──▶  Patched APK
     ▲                                   ▲
     │ provides classes                  │ calls injected by
     │                                   │
Patch (.kt)  ─────────────────▶  Bytecode patch
```

The extension is built under `extensions/` and produces a `.mpe` file. Patches load it via `extendWith("extensions/<name>.mpe")`. The patcher merges the extension's DEX classes directly into the target app so they are available at runtime.

## Declaring a dependency on the shared extension

Most patches depend on the shared extension rather than calling `extendWith` directly. Use `sharedExtensionPatch` as a dependency:

```kotlin
// For a YouTube or YouTube Music patch
val myPatch = bytecodePatch(
    name = "My patch",
    description = "Does something useful.",
) {
    dependsOn(
        sharedExtensionPatch,  // the app-specific instance from youtube/misc/extension
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        // inject calls to your extension class here
    }
}
```

The `sharedExtensionPatch` values for each app are defined at:

| App | File |
|-----|------|
| YouTube | `patches/src/main/kotlin/app/morphe/patches/youtube/misc/extension/SharedExtensionPatch.kt` |
| YouTube Music | `patches/src/main/kotlin/app/morphe/patches/music/misc/extension/` |

Each of those instances wires up the `ExtensionHook`s that pass the Android `Context` to the extension on app startup.

## Bundling a patch-specific extension

If your patch ships its own extension file (in addition to the shared one), use the two-argument form:

```kotlin
// patches/src/main/kotlin/app/morphe/patches/youtube/ad/general/HideAdsPatch.kt
private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/patches/components/AdsFilter;"

val hideAdsResourcePatch = resourcePatch {
    dependsOn(
        sharedExtensionPatch,
        // other dependencies ...
    )
    // ...
}
```

For patches that ship their own named extension, call the factory function from `shared/misc/extension/SharedExtensionPatch.kt`:

```kotlin
// sharedExtensionPatch(extensionName, isYouTubeOrYouTubeMusic, vararg hooks)
val mySharedExtension = sharedExtensionPatch(
    extensionName = "youtube",        // loads extensions/youtube.mpe
    isYouTubeOrYouTubeMusic = true,
    applicationInitHook,
    applicationInitOnCrateHook,
)
```

## Calling extension methods from a patch

Once the extension is declared as a dependency, reference its classes by their Smali descriptor and inject calls using the patcher instruction helpers:

```kotlin
private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/patches/components/AdsFilter;"

execute {
    SomeFingerprint.method.apply {
        val insertIndex = 0

        addInstructions(
            insertIndex,
            """
                invoke-static {}, $EXTENSION_CLASS_DESCRIPTOR->hideAds()Z
                move-result v0
                if-eqz v0, :show
                return-void
                :show
                nop
            """
        )
    }
}
```

Common helpers (imported from `patcher.extensions.InstructionExtensions`):

| Helper | Use |
|--------|-----|
| `addInstruction(index, smali)` | Insert a single Smali instruction |
| `addInstructions(index, smali)` | Insert multiple Smali instructions |
| `addInstructionsWithLabels(index, smali)` | Insert instructions that contain jump labels |
| `replaceInstruction(index, smali)` | Replace an existing instruction |

## Adding context hooks

The extension's `Utils.setContext()` must be called before any extension code that needs an Android `Context`. This is handled automatically by `sharedExtensionPatch` via `ExtensionHook`s.

To add a hook for a specific activity:

```kotlin
// Hook a known, non-obfuscated activity class
val myHook = activityOnCreateExtensionHook(
    activityClassType = "Lcom/example/MyActivity;",
    targetBundleMethod = true,  // hooks onCreate(Bundle), false hooks onCreate()
)

// Or build a custom hook from any Fingerprint
val myCustomHook = ExtensionHook(
    fingerprint = MyFingerprint,
    insertIndexResolver = { method -> 0 },         // where to inject the call
    contextRegisterResolver = { method -> "p0" },  // register holding the Context
)
```

Pass the hooks when constructing the shared extension patch:

```kotlin
val sharedExtensionPatch = sharedExtensionPatch(
    "youtube",
    true,
    myHook,
    myCustomHook,
)
```

## Extension source layout

```
extensions/
  shared/           ← shared across all apps (Utils, settings helpers, etc.)
    library/        ← pure-Java library module (AddonModule interface lives here)
    src/main/java/
  shared-youtube/   ← shared between YouTube and YouTube Music
  youtube/          ← YouTube-only extension code
  music/            ← YouTube Music-only extension code
  reddit/           ← Reddit extension code
```

Each sub-module produces one `.mpe` file that is referenced from the corresponding patch by name (e.g. `extendWith("extensions/youtube.mpe")`).
