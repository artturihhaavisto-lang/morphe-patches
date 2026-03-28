package app.morphe.patches.all.misc.addons

import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.logging.Logger

/**
 * Discovers and loads [AddonModule] implementations from external JAR files
 * using the [ServiceLoader] mechanism.
 *
 * Addon JARs must include a
 * `META-INF/services/app.morphe.patches.all.misc.addons.AddonModule`
 * file listing the fully qualified class names of their module implementations.
 */
object AddonModuleLoader {

    private val logger = Logger.getLogger(AddonModuleLoader::class.java.name)

    /**
     * Loads all addon modules found in the given directory.
     *
     * @param directory The directory containing addon JAR files.
     * @return List of successfully loaded addon modules.
     */
    fun loadFromDirectory(directory: File): List<AddonModule> {
        if (!directory.isDirectory) {
            logger.info("Addon directory does not exist: ${directory.absolutePath}")
            return emptyList()
        }

        val jarFiles = directory.listFiles { file ->
            file.extension == "jar"
        } ?: return emptyList()

        if (jarFiles.isEmpty()) {
            logger.info("No addon JARs found in: ${directory.absolutePath}")
            return emptyList()
        }

        return loadFromJars(jarFiles.toList())
    }

    /**
     * Loads all addon modules from the given JAR files.
     *
     * @param jarFiles List of JAR files to load modules from.
     * @return List of successfully loaded addon modules.
     */
    fun loadFromJars(jarFiles: List<File>): List<AddonModule> {
        val urls = jarFiles.map { it.toURI().toURL() }.toTypedArray()
        val classLoader = URLClassLoader(urls, AddonModule::class.java.classLoader)

        val modules = mutableListOf<AddonModule>()
        val serviceLoader = ServiceLoader.load(AddonModule::class.java, classLoader)

        for (module in serviceLoader) {
            try {
                logger.info("Loaded addon module: ${module.name} (${module.id}) v${module.version}")
                modules.add(module)
            } catch (e: Exception) {
                logger.warning("Failed to load addon module: ${e.message}")
            }
        }

        if (modules.isEmpty()) {
            logger.info("No addon modules found in provided JARs.")
        }

        return modules
    }
}
