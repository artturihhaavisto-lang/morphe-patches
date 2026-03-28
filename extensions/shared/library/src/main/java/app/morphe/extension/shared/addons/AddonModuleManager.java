package app.morphe.extension.shared.addons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

import dalvik.system.DexClassLoader;

/**
 * Manages the lifecycle of runtime addon modules.
 *
 * <p>Handles discovery, loading, enabling/disabling, and persistence of addon modules.
 * Modules are loaded from DEX files placed in the app's addon directory.</p>
 *
 * <p>Each addon DEX file must contain:</p>
 * <ul>
 *   <li>A class implementing {@link AddonModule}</li>
 *   <li>The fully qualified class name as the file name (e.g., {@code com.example.MyModule.dex}),
 *       or a {@code morphe-addon-entry} key in the DEX manifest</li>
 * </ul>
 *
 * <p>Module state (enabled/disabled and configuration) is persisted in SharedPreferences
 * and restored on app restart.</p>
 */
public final class AddonModuleManager {

    private static final String PREFS_NAME = "morphe_addon_modules";
    private static final String PREFS_KEY_ENABLED_PREFIX = "enabled_";
    private static final String PREFS_KEY_CONFIG_PREFIX = "config_";
    private static final String ADDON_DIR_NAME = "addons";

    @SuppressLint("StaticFieldLeak")
    private static volatile AddonModuleManager instance;

    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, LoadedModule> modules = new LinkedHashMap<>();
    private File addonsDir;

    /**
     * Holds a loaded module with its runtime state.
     */
    public static final class LoadedModule {
        @NonNull
        public final AddonModule module;
        @NonNull
        public final File sourceFile;
        private boolean enabled;
        @NonNull
        private Map<String, String> config;

        LoadedModule(@NonNull AddonModule module, @NonNull File sourceFile,
                     boolean enabled, @NonNull Map<String, String> config) {
            this.module = module;
            this.sourceFile = sourceFile;
            this.enabled = enabled;
            this.config = config;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @NonNull
        public Map<String, String> getConfig() {
            return Collections.unmodifiableMap(config);
        }
    }

    private AddonModuleManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.addonsDir = new File(this.context.getExternalFilesDir(null), ADDON_DIR_NAME);
    }

    /**
     * Get the singleton instance, initializing if needed.
     */
    @NonNull
    public static AddonModuleManager getInstance() {
        if (instance == null) {
            synchronized (AddonModuleManager.class) {
                if (instance == null) {
                    Context ctx = Utils.getContext();
                    if (ctx == null) {
                        throw new IllegalStateException("Context not available");
                    }
                    instance = new AddonModuleManager(ctx);
                }
            }
        }
        return instance;
    }

    /**
     * @return The directory where addon DEX files should be placed.
     */
    @NonNull
    public File getAddonsDirectory() {
        return addonsDir;
    }

    /**
     * Copies a module file selected via the system file picker into the addons directory.
     *
     * <p>The destination file name is taken from the Uri's display name.  Call
     * {@link #discoverAndLoad()} (or tap Reload modules in settings) to make the new module
     * appear after a successful import.</p>
     *
     * @param uri Content Uri returned by {@code Intent.ACTION_OPEN_DOCUMENT}.
     * @return The copied file, or {@code null} if the copy failed.
     */
    @Nullable
    public File importModuleFromUri(@NonNull Uri uri) {
        try {
            // Resolve the display name from the content provider.
            String fileName = null;
            try (Cursor cursor = context.getContentResolver().query(
                    uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (col >= 0) fileName = cursor.getString(col);
                }
            }
            if (fileName == null) {
                String path = uri.getPath();
                fileName = path != null
                        ? path.substring(path.lastIndexOf('/') + 1)
                        : "addon_" + System.currentTimeMillis() + ".dex";
            }

            if (!addonsDir.exists()) {
                addonsDir.mkdirs();
            }

            File dest = new File(addonsDir, fileName);
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(dest)) {
                if (in == null) {
                    Logger.printException(() -> "Could not open input stream for Uri: " + uri);
                    return null;
                }
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }

            Logger.printInfo(() -> "Imported addon file: " + dest.getName());
            return dest;
        } catch (Exception e) {
            Logger.printException(() -> "Failed to import addon module", e);
            return null;
        }
    }

    /**
     * Scans the addons directory and loads all discovered modules.
     * Previously loaded modules are cleared first.
     */
    public void discoverAndLoad() {
        modules.clear();

        if (!addonsDir.exists()) {
            if (!addonsDir.mkdirs()) {
                Logger.printInfo(() -> "Created addons directory: " + addonsDir.getAbsolutePath());
            }
            return;
        }

        File[] files = addonsDir.listFiles((dir, name) ->
                name.endsWith(".dex") || name.endsWith(".jar") || name.endsWith(".apk"));

        if (files == null || files.length == 0) {
            Logger.printInfo(() -> "No addon files found in: " + addonsDir.getAbsolutePath());
            return;
        }

        File dexOutputDir = new File(context.getCacheDir(), "addon_dex");
        if (!dexOutputDir.exists()) {
            dexOutputDir.mkdirs();
        }

        for (File file : files) {
            try {
                loadModuleFromFile(file, dexOutputDir);
            } catch (Exception e) {
                Logger.printException(() -> "Failed to load addon: " + file.getName(), e);
            }
        }

        Logger.printInfo(() -> "Loaded " + modules.size() + " addon module(s)");
    }

    private void loadModuleFromFile(@NonNull File file, @NonNull File dexOutputDir) throws Exception {
        DexClassLoader classLoader = new DexClassLoader(
                file.getAbsolutePath(),
                dexOutputDir.getAbsolutePath(),
                null,
                context.getClassLoader()
        );

        // Try to find the entry class.
        // Convention: file named "com.example.MyModule.dex" → class "com.example.MyModule"
        String className = file.getName();
        int extIndex = className.lastIndexOf('.');
        if (extIndex > 0) {
            className = className.substring(0, extIndex);
        }

        Class<?> moduleClass;
        try {
            moduleClass = classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            // Fallback: try loading from a known entry point in the DEX.
            // Look for a class listed in a simple text file alongside the DEX.
            File manifest = new File(file.getParent(), className + ".entry");
            if (manifest.exists()) {
                String entryClassName = new java.util.Scanner(manifest).useDelimiter("\\A").next().trim();
                moduleClass = classLoader.loadClass(entryClassName);
            } else {
                throw new ClassNotFoundException(
                        "Cannot find module class '" + className + "' in " + file.getName() +
                        ". Name the file as the fully qualified class name, " +
                        "or create a .entry file with the class name.");
            }
        }

        if (!AddonModule.class.isAssignableFrom(moduleClass)) {
            throw new IllegalArgumentException(
                    moduleClass.getName() + " does not implement AddonModule");
        }

        AddonModule module = (AddonModule) moduleClass.getDeclaredConstructor().newInstance();
        String id = module.getId();

        if (modules.containsKey(id)) {
            Logger.printInfo(() -> "Duplicate addon module id: " + id + ", skipping: " + file.getName());
            return;
        }

        boolean enabled = prefs.getBoolean(PREFS_KEY_ENABLED_PREFIX + id, false);
        Map<String, String> config = loadConfig(id);

        LoadedModule loaded = new LoadedModule(module, file, enabled, config);
        modules.put(id, loaded);

        Logger.printInfo(() -> "Loaded addon: " + module.getName() + " v" + module.getVersion() +
                " (" + (enabled ? "enabled" : "disabled") + ")");

        if (enabled) {
            try {
                module.onEnable(config);
            } catch (Exception e) {
                Logger.printException(() -> "Failed to enable addon: " + module.getName(), e);
                loaded.enabled = false;
                saveEnabledState(id, false);
            }
        }
    }

    /**
     * @return An unmodifiable list of all loaded modules.
     */
    @NonNull
    public List<LoadedModule> getLoadedModules() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    /**
     * @return The loaded module with the given ID, or null.
     */
    @Nullable
    public LoadedModule getModule(@NonNull String id) {
        return modules.get(id);
    }

    /**
     * Enable or disable a module. State is persisted.
     */
    @SuppressLint("ApplySharedPref")
    public void setModuleEnabled(@NonNull String id, boolean enabled) {
        LoadedModule loaded = modules.get(id);
        if (loaded == null) return;

        if (loaded.enabled == enabled) return;

        loaded.enabled = enabled;
        saveEnabledState(id, enabled);

        try {
            if (enabled) {
                loaded.module.onEnable(loaded.config);
            } else {
                loaded.module.onDisable();
            }
        } catch (Exception e) {
            Logger.printException(() -> "Error toggling addon: " + loaded.module.getName(), e);
        }
    }

    /**
     * Update a module's configuration. State is persisted.
     */
    @SuppressLint("ApplySharedPref")
    public void setModuleConfig(@NonNull String id, @NonNull Map<String, String> config) {
        LoadedModule loaded = modules.get(id);
        if (loaded == null) return;

        loaded.config = new HashMap<>(config);
        saveConfig(id, config);

        if (loaded.enabled) {
            try {
                loaded.module.onConfigChanged(config);
            } catch (Exception e) {
                Logger.printException(() -> "Error updating addon config: " + loaded.module.getName(), e);
            }
        }
    }

    /**
     * Remove a module's file and clear its persisted state.
     */
    @SuppressLint("ApplySharedPref")
    public boolean removeModule(@NonNull String id) {
        LoadedModule loaded = modules.remove(id);
        if (loaded == null) return false;

        if (loaded.enabled) {
            try {
                loaded.module.onDisable();
            } catch (Exception e) {
                Logger.printException(() -> "Error disabling addon during removal", e);
            }
        }

        prefs.edit()
                .remove(PREFS_KEY_ENABLED_PREFIX + id)
                .remove(PREFS_KEY_CONFIG_PREFIX + id)
                .commit();

        return loaded.sourceFile.delete();
    }

    @SuppressLint("ApplySharedPref")
    private void saveEnabledState(@NonNull String id, boolean enabled) {
        prefs.edit().putBoolean(PREFS_KEY_ENABLED_PREFIX + id, enabled).commit();
    }

    @SuppressLint("ApplySharedPref")
    private void saveConfig(@NonNull String id, @NonNull Map<String, String> config) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        prefs.edit().putString(PREFS_KEY_CONFIG_PREFIX + id, sb.toString()).commit();
    }

    @NonNull
    private Map<String, String> loadConfig(@NonNull String id) {
        String raw = prefs.getString(PREFS_KEY_CONFIG_PREFIX + id, "");
        Map<String, String> config = new HashMap<>();
        if (raw == null || raw.isEmpty()) return config;

        for (String line : raw.split("\n")) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                config.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return config;
    }
}
