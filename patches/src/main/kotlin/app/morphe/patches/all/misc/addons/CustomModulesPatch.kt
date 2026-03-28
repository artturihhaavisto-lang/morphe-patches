package app.morphe.patches.all.misc.addons

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import java.io.File
import java.util.logging.Logger

private val logger = Logger.getLogger("CustomModulesPatch")

/**
 * Patch that integrates the addon custom modules system into the patching pipeline.
 *
 * When included, this patch will:
 * 1. Scan the configured addons directory for module JARs
 * 2. Load and register discovered modules
 * 3. Execute compatible modules during the patch phase
 * 4. Finalize modules after all patches complete
 *
 * Addon JARs are loaded via Java's [java.util.ServiceLoader] mechanism.
 * Each JAR must declare its [AddonModule] implementations in
 * `META-INF/services/app.morphe.patches.all.misc.addons.AddonModule`.
 */
@Suppress("unused")
val customModulesPatch = bytecodePatch(
    name = "Custom modules",
    description = "Loads custom addon modules from external JARs to extend patching functionality.",
) {
    val addonsPath by stringOption(
        key = "addonsPath",
        default = "addons",
        title = "Addons directory",
        description = "Path to the directory containing addon module JAR files.",
        required = false,
    )

    val moduleConfigs by stringOption(
        key = "moduleConfigs",
        default = "",
        title = "Module configurations",
        description = "Semicolon-separated module configurations in the format: " +
                "moduleId:key=value,key=value;moduleId2:key=value. " +
                "Example: skip-artists:artists=ArtistA|ArtistB;my-filter:enabled=true",
        required = false,
    )

    execute {
        val addonsDir = File(addonsPath ?: "addons")
        logger.info("Loading addon modules from: ${addonsDir.absolutePath}")

        // Discover and load modules.
        val loadedModules = AddonModuleLoader.loadFromDirectory(addonsDir)
        val registered = AddonModuleRegistry.registerAll(loadedModules)
        logger.info("Registered $registered addon module(s).")

        // Parse and apply module configurations.
        parseModuleConfigs(moduleConfigs ?: "").forEach { (moduleId, config) ->
            AddonModuleRegistry.configure(moduleId, config)
        }

        // Initialize all modules.
        AddonModuleRegistry.loadAll()

        // Execute compatible modules.
        // Target package is not available at bytecode patch level,
        // so modules that need it should check in their onExecute.
        AddonModuleRegistry.executeAll(null)
    }

    finalize {
        AddonModuleRegistry.finalizeAll()
        logger.info("Addon modules finalized.")
    }
}

/**
 * Parses a semicolon-separated configuration string into per-module config maps.
 *
 * Format: `moduleId:key=value,key=value;moduleId2:key=value`
 */
internal fun parseModuleConfigs(raw: String): Map<String, Map<String, Any>> {
    if (raw.isBlank()) return emptyMap()

    return raw.split(";")
        .filter { it.contains(":") }
        .associate { entry ->
            val (moduleId, pairs) = entry.split(":", limit = 2)
            val config = pairs.split(",")
                .filter { it.contains("=") }
                .associate { pair ->
                    val (key, value) = pair.split("=", limit = 2)
                    key.trim() to (value.trim() as Any)
                }
            moduleId.trim() to config
        }
}
