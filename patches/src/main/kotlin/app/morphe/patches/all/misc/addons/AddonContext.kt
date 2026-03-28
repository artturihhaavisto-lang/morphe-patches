package app.morphe.patches.all.misc.addons

/**
 * Context provided to addon modules during execution.
 *
 * Gives modules access to the current patching state and
 * a way to communicate back to the addon system.
 */
class AddonContext(
    /** The package name of the app being patched. */
    val targetPackage: String?,
    /** User-provided configuration for this module. */
    val config: Map<String, Any>,
) {
    private val _messages = mutableListOf<String>()

    /** Messages logged by the module during execution. */
    val messages: List<String> get() = _messages

    /** Log an informational message. */
    fun log(message: String) {
        _messages.add(message)
    }
}
