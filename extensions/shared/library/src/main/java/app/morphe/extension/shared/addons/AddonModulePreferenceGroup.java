package app.morphe.extension.shared.addons;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

/**
 * Preference group that displays loaded addon modules in the settings UI.
 *
 * <p>Shows each loaded module as a toggleable switch with options to
 * view info and edit configuration. Also displays the addon directory
 * path for users to know where to place module files.</p>
 */
@SuppressWarnings({"unused", "deprecation"})
public class AddonModulePreferenceGroup extends PreferenceGroup {

    private boolean preferencesInitialized;

    public AddonModulePreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public AddonModulePreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AddonModulePreferenceGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    @SuppressLint("MissingSuperCall")
    protected View onCreateView(ViewGroup parent) {
        // No group title view needed.
        return new View(getContext());
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();

        if (preferencesInitialized) return;
        preferencesInitialized = true;

        try {
            buildPreferences();
        } catch (Exception e) {
            Logger.printException(() -> "Failed to build addon preferences", e);
        }
    }

    private void buildPreferences() {
        Context context = getContext();
        AddonModuleManager manager = AddonModuleManager.getInstance();

        // Initialize and discover modules.
        manager.discoverAndLoad();

        // Directory info preference.
        Preference dirInfo = new Preference(context);
        dirInfo.setTitle("Addon modules directory");
        dirInfo.setSummary(manager.getAddonsDirectory().getAbsolutePath());
        dirInfo.setSelectable(false);
        addPreference(dirInfo);

        // Refresh button.
        Preference refresh = new Preference(context);
        refresh.setTitle("Reload modules");
        refresh.setSummary("Rescan the addons directory for new or updated modules");
        refresh.setOnPreferenceClickListener(pref -> {
            preferencesInitialized = false;
            removeAll();
            buildPreferences();
            Utils.showToastShort("Modules reloaded");
            return true;
        });
        addPreference(refresh);

        List<AddonModuleManager.LoadedModule> loadedModules = manager.getLoadedModules();

        if (loadedModules.isEmpty()) {
            Preference empty = new Preference(context);
            empty.setTitle("No modules loaded");
            empty.setSummary("Place .dex or .jar files in the addons directory above");
            empty.setSelectable(false);
            addPreference(empty);
            return;
        }

        // Modules category.
        PreferenceCategory modulesCategory = new PreferenceCategory(context);
        modulesCategory.setTitle("Loaded modules (" + loadedModules.size() + ")");
        addPreference(modulesCategory);

        for (AddonModuleManager.LoadedModule loaded : loadedModules) {
            addModulePreference(context, modulesCategory, manager, loaded);
        }
    }

    private void addModulePreference(Context context, PreferenceCategory category,
                                     AddonModuleManager manager,
                                     AddonModuleManager.LoadedModule loaded) {
        AddonModule module = loaded.module;

        // Enable/disable toggle.
        SwitchPreference toggle = new SwitchPreference(context);
        toggle.setTitle(module.getName());
        toggle.setSummary(module.getDescription() + "\nv" + module.getVersion());
        toggle.setChecked(loaded.isEnabled());
        toggle.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean enabled = (Boolean) newValue;
            manager.setModuleEnabled(module.getId(), enabled);
            return true;
        });
        category.addPreference(toggle);

        // Config button (shown below the toggle).
        Preference configPref = new Preference(context);
        configPref.setTitle("  Configure " + module.getName());
        configPref.setSummary("Edit module configuration");
        configPref.setOnPreferenceClickListener(pref -> {
            showConfigDialog(context, manager, loaded);
            return true;
        });
        category.addPreference(configPref);

        // Remove button.
        Preference removePref = new Preference(context);
        removePref.setTitle("  Remove " + module.getName());
        removePref.setSummary("Delete module file and clear its settings");
        removePref.setOnPreferenceClickListener(pref -> {
            new AlertDialog.Builder(context)
                    .setTitle("Remove " + module.getName() + "?")
                    .setMessage("This will delete the module file and all its saved settings.")
                    .setPositiveButton("Remove", (dialog, which) -> {
                        manager.removeModule(module.getId());
                        preferencesInitialized = false;
                        removeAll();
                        buildPreferences();
                        Utils.showToastShort("Module removed");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
        category.addPreference(removePref);
    }

    /**
     * Shows a dialog to edit module configuration as key=value pairs.
     */
    private void showConfigDialog(Context context, AddonModuleManager manager,
                                  AddonModuleManager.LoadedModule loaded) {
        Map<String, String> currentConfig = loaded.getConfig();

        // Build current config as text.
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : currentConfig.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }

        EditText editText = new EditText(context);
        editText.setText(sb.toString());
        editText.setHint("key=value (one per line)");
        editText.setMinLines(4);
        editText.setTextSize(14);

        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, 0);

        TextView instructions = new TextView(context);
        instructions.setText("Enter configuration as key=value pairs, one per line.\n" +
                "Example:\nartists=ArtistA|ArtistB\nenabled=true");
        instructions.setTextSize(12);
        instructions.setPadding(0, 0, 0, padding);
        layout.addView(instructions);
        layout.addView(editText);
        scrollView.addView(layout);

        new AlertDialog.Builder(context)
                .setTitle("Configure " + loaded.module.getName())
                .setView(scrollView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String text = editText.getText().toString();
                    Map<String, String> newConfig = parseConfig(text);
                    manager.setModuleConfig(loaded.module.getId(), newConfig);
                    Utils.showToastShort("Configuration saved");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static Map<String, String> parseConfig(String text) {
        Map<String, String> config = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return config;

        for (String line : text.split("\n")) {
            line = line.trim();
            int eq = line.indexOf('=');
            if (eq > 0) {
                config.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return config;
    }
}
