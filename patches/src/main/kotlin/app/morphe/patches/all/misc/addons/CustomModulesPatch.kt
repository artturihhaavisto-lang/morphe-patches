package app.morphe.patches.all.misc.addons

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.misc.settings.preference.PreferenceCategory
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference
import app.morphe.patches.shared.misc.settings.preference.PreferenceScreenPreference.Sorting
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("CustomModulesPatch")

/**
 * Builds the addon modules preference category to insert into a settings screen.
 *
 * Uses a runtime [PreferenceCategory] backed by [AddonModulePreferenceGroup],
 * which dynamically discovers and displays loaded modules in the settings UI.
 *
 * Usage from an app-specific settings patch:
 * ```kotlin
 * PreferenceScreen.MISC.addPreferences(addonModulesPreferenceCategory())
 * ```
 */
fun addonModulesPreferenceCategory() = PreferenceCategory(
    key = "morphe_addon_modules",
    sorting = Sorting.UNSORTED,
    preferences = emptySet(), // Preferences are built at runtime by the custom class.
    tag = "app.morphe.extension.shared.addons.AddonModulePreferenceGroup",
)

/**
 * Patch that integrates the addon custom modules system into the patching pipeline.
 *
 * When included, this patch:
 * 1. Optionally loads addon module JARs at patch time
 * 2. Provides a runtime module loading system in the patched app
 * 3. Provides a preference category for the settings UI
 *
 * At runtime, the patched app will:
 * - Scan the app's external files addons directory for DEX/JAR module files
 * - Load and display modules in the settings UI
 * - Allow enabling/disabling modules with persistent state
 * - Support per-module key=value configuration
 *
 * Addon files are loaded via Android's DexClassLoader.
 * Each file must contain a class implementing
 * {@code app.morphe.extension.shared.addons.AddonModule}.
 * Name the file as the fully qualified class name
 * (e.g., {@code com.example.SkipArtistsModule.dex}).
 */
@Suppress("unused")
val customModulesPatch = bytecodePatch(
    name = "Custom modules",
    description = "Loads custom addon modules to extend app functionality.",
) {
    val addonsPath by stringOption(
        key = "addonsPath",
        default = "addons",
        title = "Addons directory",
        description = "Path to the directory containing addon module files (patch-time).",
        required = false,
    )

    execute {
        // Patch-time: optionally load modules from a local directory during patching.
        val addonsDir = File(addonsPath ?: "addons")
        if (addonsDir.isDirectory) {
            val loadedModules = AddonModuleLoader.loadFromDirectory(addonsDir)
            val registered = AddonModuleRegistry.registerAll(loadedModules)
            logger.info("Registered $registered patch-time addon module(s).")

            AddonModuleRegistry.loadAll()
            AddonModuleRegistry.executeAll(null)
        } else {
            logger.info("No patch-time addons directory found (this is normal).")
        }
    }

    finalize {
        AddonModuleRegistry.finalizeAll()
    }
}
