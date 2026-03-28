package app.morphe.patches.all.misc.addons

import java.util.logging.Logger

/**
 * Central registry that manages loaded addon modules throughout
 * the patching lifecycle.
 *
 * Modules are registered, configured, executed, and finalized
 * through this registry.
 */
object AddonModuleRegistry {

    private val logger = Logger.getLogger(AddonModuleRegistry::class.java.name)

    private val modules = mutableMapOf<String, AddonModule>()
    private val moduleConfigs = mutableMapOf<String, Map<String, Any>>()

    /** All currently registered modules. */
    val registeredModules: Collection<AddonModule>
        get() = modules.values

    /**
     * Register a module. Duplicate IDs are rejected.
     */
    fun register(module: AddonModule): Boolean {
        if (modules.containsKey(module.id)) {
            logger.warning("Addon module with id '${module.id}' is already registered. Skipping.")
            return false
        }
        modules[module.id] = module
        return true
    }

    /**
     * Register multiple modules at once.
     *
     * @return The number of modules successfully registered.
     */
    fun registerAll(addons: List<AddonModule>): Int {
        return addons.count { register(it) }
    }

    /**
     * Set configuration for a specific module by ID.
     */
    fun configure(moduleId: String, config: Map<String, Any>) {
        moduleConfigs[moduleId] = config
    }

    /**
     * Load (initialize) all registered modules with their configurations.
     */
    fun loadAll() {
        modules.values.forEach { module ->
            try {
                val config = moduleConfigs[module.id] ?: emptyMap()
                module.onLoad(config)
                logger.info("Initialized addon module: ${module.name}")
            } catch (e: Exception) {
                logger.warning("Failed to initialize addon module '${module.name}': ${e.message}")
            }
        }
    }

    /**
     * Execute all registered modules that are compatible with the target app.
     *
     * @param targetPackage The package name of the app being patched, or null.
     */
    fun executeAll(targetPackage: String?) {
        modules.values
            .filter { it.compatibleApps.isEmpty() || targetPackage in it.compatibleApps }
            .forEach { module ->
                try {
                    val config = moduleConfigs[module.id] ?: emptyMap()
                    val context = AddonContext(targetPackage, config)
                    module.onExecute(context)

                    context.messages.forEach { msg ->
                        logger.info("[${module.name}] $msg")
                    }
                } catch (e: Exception) {
                    logger.warning("Addon module '${module.name}' execution failed: ${e.message}")
                }
            }
    }

    /**
     * Finalize all registered modules.
     */
    fun finalizeAll() {
        modules.values.forEach { module ->
            try {
                module.onFinalize()
            } catch (e: Exception) {
                logger.warning("Addon module '${module.name}' finalize failed: ${e.message}")
            }
        }
    }

    /**
     * Get a module by its ID.
     */
    fun getModule(id: String): AddonModule? = modules[id]

    /**
     * Remove all registered modules and configurations.
     */
    fun clear() {
        modules.clear()
        moduleConfigs.clear()
    }
}
