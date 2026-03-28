package app.morphe.patches.all.misc.addons

/**
 * Interface for custom addon modules that can be loaded alongside patches.
 *
 * Addon modules provide a way to extend the patching process with custom logic,
 * such as skipping specific artists, filtering content, or applying conditional modifications.
 *
 * Example usage:
 * ```kotlin
 * class SkipArtistModule : AddonModule {
 *     override val id = "skip-artists"
 *     override val name = "Skip Artists"
 *     override val description = "Skips playback for specific artists"
 *     override val version = "1.0.0"
 *
 *     override fun onLoad(config: Map<String, Any>) {
 *         // Initialize with user-provided artist list
 *     }
 *
 *     override fun onExecute(context: AddonContext) {
 *         // Apply the filtering logic
 *     }
 * }
 * ```
 */
interface AddonModule {
    /** Unique identifier for this module. */
    val id: String

    /** Human-readable name. */
    val name: String

    /** Short description of what this module does. */
    val description: String

    /** Module version string. */
    val version: String

    /**
     * Compatible target apps this module can work with.
     * Empty set means compatible with all apps.
     */
    val compatibleApps: Set<String>
        get() = emptySet()

    /**
     * Called when the module is loaded. Use this to initialize state
     * and read configuration values.
     *
     * @param config Key-value configuration passed by the user.
     */
    fun onLoad(config: Map<String, Any>) {}

    /**
     * Called during patch execution. This is where the module applies
     * its custom logic.
     *
     * @param context Provides access to patching context and utilities.
     */
    fun onExecute(context: AddonContext) {}

    /**
     * Called after all patches have been applied. Use this for cleanup
     * or final modifications.
     */
    fun onFinalize() {}
}
