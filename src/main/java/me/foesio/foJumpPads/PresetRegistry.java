package me.foesio.foJumpPads;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PresetRegistry {

    private final FoJumpPads plugin;
    private final Map<String, JumpPadPreset> presets = new LinkedHashMap<>();

    public PresetRegistry(FoJumpPads plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        presets.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("presets");
        if (root == null) {
            plugin.logWarn("No presets found in config.yml.");
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section != null) {
                JumpPadPreset preset = JumpPadPreset.from(plugin, key.toLowerCase(Locale.ROOT), section);
                presets.put(preset.id(), preset);
            }
        }
    }

    public JumpPadPreset get(String id) {
        return id == null ? null : presets.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<JumpPadPreset> all() {
        return Collections.unmodifiableCollection(presets.values());
    }

    public List<String> ids() {
        return new ArrayList<>(presets.keySet());
    }

    public boolean contains(String id) {
        return get(id) != null;
    }
}
