package app.morphe.extension.shared.addons;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Interface for runtime addon modules loaded in the patched app.
 *
 * <p>Modules are discovered from DEX files placed in the app's addon directory.
 * Each DEX must contain a class implementing this interface, declared in its
 * {@code addon.properties} manifest.</p>
 *
 * <p>Example module:</p>
 * <pre>
 * public class SkipArtistsModule implements AddonModule {
 *     public String getId() { return "skip-artists"; }
 *     public String getName() { return "Skip Artists"; }
 *     public String getDescription() { return "Skips specific artists during playback"; }
 *     public String getVersion() { return "1.0.0"; }
 *
 *     public void onEnable(Map&lt;String, String&gt; config) {
 *         String artists = config.get("artists");
 *         // Register playback hooks...
 *     }
 * }
 * </pre>
 */
public interface AddonModule {

    /** Unique identifier for this module. */
    @NonNull
    String getId();

    /** Human-readable display name. */
    @NonNull
    String getName();

    /** Short description of what this module does. */
    @NonNull
    String getDescription();

    /** Module version string. */
    @NonNull
    String getVersion();

    /**
     * Package names this module is compatible with.
     * Return empty set for universal compatibility.
     */
    @NonNull
    default Set<String> getCompatibleApps() {
        return Collections.emptySet();
    }

    /**
     * Called when the module is enabled. Initialize hooks and state here.
     *
     * @param config User-defined key-value configuration for this module.
     */
    default void onEnable(@NonNull Map<String, String> config) {}

    /**
     * Called when the module is disabled. Clean up hooks and state here.
     */
    default void onDisable() {}

    /**
     * Called when the module's configuration changes while it is enabled.
     *
     * @param config Updated configuration.
     */
    default void onConfigChanged(@NonNull Map<String, String> config) {}
}
