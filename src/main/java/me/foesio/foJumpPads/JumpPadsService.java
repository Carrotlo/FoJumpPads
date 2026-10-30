package me.foesio.foJumpPads;

import me.foesio.core.editor.CursorItemEditor;
import me.foesio.foJumpPads.api.ChangeResult;
import me.foesio.foJumpPads.api.JumpPadsApi;
import me.foesio.foJumpPads.api.LaunchResult;
import me.foesio.foJumpPads.api.PadSnapshot;
import me.foesio.foJumpPads.api.PlayerStatus;
import me.foesio.foJumpPads.api.PresetSnapshot;
import me.foesio.foJumpPads.api.PresetSpec;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Internal implementation of the plugin-owned integration contract. */
final class JumpPadsService implements JumpPadsApi {
    private final FoJumpPads plugin;

    JumpPadsService(FoJumpPads plugin) { this.plugin = plugin; }

    @Override public Optional<PadSnapshot> findPad(Block block) {
        if (!plugin.isEnabled() || block == null) return Optional.empty();
        PadLocation location = PadLocation.from(block);
        PadData data = plugin.padData(location);
        return data == null ? Optional.empty() : Optional.of(snapshot(location, data));
    }

    @Override public List<PadSnapshot> listPads() {
        if (!plugin.isEnabled()) return List.of();
        return plugin.padSnapshot().entrySet().stream()
                .map(entry -> snapshot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PadSnapshot::world).thenComparingInt(PadSnapshot::x)
                        .thenComparingInt(PadSnapshot::y).thenComparingInt(PadSnapshot::z))
                .toList();
    }

    @Override public Optional<PresetSnapshot> findPreset(String id) {
        if (!plugin.isEnabled()) return Optional.empty();
        JumpPadPreset preset = plugin.presets().get(id);
        return preset == null ? Optional.empty() : Optional.of(snapshot(preset));
    }

    @Override public List<PresetSnapshot> listPresets() {
        if (!plugin.isEnabled()) return List.of();
        return plugin.presets().all().stream().map(this::snapshot)
                .sorted(Comparator.comparing(PresetSnapshot::id)).toList();
    }

    @Override public Optional<ItemStack> createPresetItem(String id, Player viewer, int amount) {
        if (!plugin.isEnabled() || amount < 1 || amount > 64) return Optional.empty();
        JumpPadPreset preset = plugin.presets().get(id);
        return preset == null ? Optional.empty() : Optional.of(preset.createItem(plugin, viewer, amount));
    }

    @Override public Optional<String> identifyPresetItem(ItemStack item) {
        if (!plugin.isEnabled() || item == null || !item.hasItemMeta()) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        String id = meta.getPersistentDataContainer().get(plugin.presetKey(), PersistentDataType.STRING);
        JumpPadPreset preset = plugin.presets().get(id);
        return preset == null ? Optional.empty() : Optional.of(preset.id());
    }

    @Override public ChangeResult registerPad(Block block, String presetId) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        if (block == null) return ChangeResult.INVALID_INPUT;
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) return ChangeResult.NOT_FOUND;
        if (!PadMaterialRules.isCompatible(preset.material(), block.getType())) return ChangeResult.MATERIAL_MISMATCH;
        return registerPad(PadLocation.from(block), new PadData(preset.id(), block.getType()));
    }

    ChangeResult registerPad(PadLocation location, PadData data) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        PadData old = plugin.padData(location);
        if (data.equals(old)) return ChangeResult.UNCHANGED;
        try {
            plugin.storage().savePad(location, data);
            plugin.putPad(location, data);
            return old == null ? ChangeResult.CREATED : ChangeResult.UPDATED;
        } catch (SQLException exception) {
            plugin.logWarn("API could not register pad: " + exception.getMessage());
            return ChangeResult.STORAGE_ERROR;
        }
    }

    @Override public ChangeResult removePad(Block block) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        if (block == null) return ChangeResult.INVALID_INPUT;
        return removePad(PadLocation.from(block));
    }

    ChangeResult removePad(PadLocation location) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        if (plugin.padData(location) == null) return ChangeResult.NOT_FOUND;
        try {
            plugin.storage().deletePad(location);
            plugin.removePad(location);
            return ChangeResult.REMOVED;
        } catch (SQLException exception) {
            plugin.logWarn("API could not remove pad: " + exception.getMessage());
            return ChangeResult.STORAGE_ERROR;
        }
    }

    @Override public LaunchResult launchPlayer(Player player, String presetId) {
        if (!plugin.isEnabled()) return LaunchResult.PLUGIN_DISABLED;
        if (player == null || !player.isOnline()) return LaunchResult.PLAYER_OFFLINE;
        if (plugin.isWorldDisabled(player.getWorld().getName())) return LaunchResult.WORLD_DISABLED;
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) return LaunchResult.PRESET_NOT_FOUND;
        if (!preset.enabled()) return LaunchResult.PRESET_DISABLED;
        return plugin.jumpPadListener().tryLaunch(player, preset, null);
    }

    @Override public ChangeResult createPreset(PresetSpec spec) { return savePreset(spec, false); }
    @Override public ChangeResult updatePreset(PresetSpec spec) { return savePreset(spec, true); }

    @Override public ChangeResult setPresetSound(String id, String soundKey) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        JumpPadPreset preset = plugin.presets().get(id);
        if (preset == null) return ChangeResult.NOT_FOUND;
        if (soundKey == null || soundKey.isBlank()) return ChangeResult.INVALID_INPUT;
        if (soundKey.equals(plugin.presetSoundKey(preset.id(), ""))) return ChangeResult.UNCHANGED;
        if (!plugin.setPresetSound(preset.id(), soundKey)) return ChangeResult.STORAGE_ERROR;
        return plugin.reloadAll().successful() ? ChangeResult.UPDATED : ChangeResult.STORAGE_ERROR;
    }

    @Override public ChangeResult setPresetItem(String id, ItemStack item) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        JumpPadPreset preset = plugin.presets().get(id);
        if (preset == null) return ChangeResult.NOT_FOUND;
        Optional<ItemStack> copy = CursorItemEditor.cloneItem(item);
        if (copy.isEmpty() || !copy.get().getType().isBlock()) return ChangeResult.INVALID_INPUT;
        String serialized;
        try {
            serialized = CursorItemEditor.serializeBase64(copy.get());
        } catch (RuntimeException exception) {
            return ChangeResult.INVALID_INPUT;
        }
        if (serialized.equals(preset.serializedItem())) return ChangeResult.UNCHANGED;
        String basePath = "presets." + configPresetKey(preset.id()) + ".";
        plugin.getConfig().set(basePath + "material", copy.get().getType().name());
        plugin.getConfig().set(basePath + "item-stack", serialized);
        ItemMeta meta = copy.get().getItemMeta();
        if (meta != null && meta.hasDisplayName()) plugin.getConfig().set(basePath + "display-name", meta.getDisplayName());
        if (meta != null && meta.hasLore()) plugin.getConfig().set(basePath + "lore", meta.getLore());
        plugin.getConfig().set(basePath + "custom-model-data",
                meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : null);
        return persistPresetChange(ChangeResult.UPDATED);
    }

    @Override public ChangeResult deletePreset(String id) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        JumpPadPreset old = plugin.presets().get(id);
        if (old == null) return ChangeResult.NOT_FOUND;
        plugin.getConfig().set("presets." + configPresetKey(old.id()), null);
        return persistPresetChange(ChangeResult.REMOVED);
    }

    @Override public PlayerStatus playerStatus(Player player) {
        if (!plugin.isEnabled() || player == null) return new PlayerStatus(0, 0);
        return plugin.jumpPadListener().status(player);
    }

    PadSnapshot snapshot(PadLocation location, PadData data) {
        JumpPadPreset preset = plugin.presets().get(data.presetId());
        boolean active = preset != null && preset.enabled() && !plugin.isWorldDisabled(location.world());
        return new PadSnapshot(location.world(), location.x(), location.y(), location.z(),
                data.presetId(), data.material(), active);
    }

    private PresetSnapshot snapshot(JumpPadPreset p) {
        return new PresetSnapshot(p.id(), p.enabled(), p.material(), p.displayName(), p.lore(),
                p.horizontalVelocity(), p.verticalVelocity(), p.cooldownTicks(),
                p.fallDamageImmunityTicks(), p.directionMode().name(), p.pathLock(),
                p.particle(), p.customModelData());
    }

    private ChangeResult savePreset(PresetSpec spec, boolean update) {
        if (!plugin.isEnabled()) return ChangeResult.PLUGIN_DISABLED;
        if (!valid(spec)) return ChangeResult.INVALID_INPUT;
        String id = spec.id().toLowerCase(Locale.ROOT);
        JumpPadPreset old = plugin.presets().get(id);
        if (!update && old != null) return ChangeResult.ALREADY_EXISTS;
        if (update && old == null) return ChangeResult.NOT_FOUND;
        if (update && Objects.equals(snapshot(old), snapshot(spec))) return ChangeResult.UNCHANGED;

        String configKey = update ? configPresetKey(id) : id;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("presets." + configKey);
        if (section == null) section = plugin.getConfig().createSection("presets." + configKey);
        section.set("enabled", spec.enabled());
        section.set("material", spec.material().name());
        section.set("display-name", spec.displayName());
        section.set("lore", spec.lore());
        section.set("velocity-horizontal", spec.horizontalVelocity());
        section.set("velocity-vertical", spec.verticalVelocity());
        section.set("cooldown-ticks", spec.cooldownTicks());
        section.set("fall-damage-immunity-ticks", spec.fallDamageImmunityTicks());
        section.set("direction-mode", spec.directionMode());
        section.set("path-lock", spec.pathLock());
        section.set("particle", spec.particle() == null ? "" : spec.particle().name());
        section.set("custom-model-data", spec.customModelData());
        if (!update) section.set("sound", "ENTITY_FIREWORK_ROCKET_LAUNCH");
        return persistPresetChange(update ? ChangeResult.UPDATED : ChangeResult.CREATED);
    }

    private PresetSnapshot snapshot(PresetSpec p) {
        return new PresetSnapshot(p.id().toLowerCase(Locale.ROOT), p.enabled(), p.material(), p.displayName(),
                p.lore(), p.horizontalVelocity(), p.verticalVelocity(), p.cooldownTicks(),
                p.fallDamageImmunityTicks(), p.directionMode(), p.pathLock(), p.particle(), p.customModelData());
    }

    private ChangeResult persistPresetChange(ChangeResult success) {
        if (!plugin.saveMainConfig()) {
            plugin.reloadAll();
            return ChangeResult.STORAGE_ERROR;
        }
        return plugin.reloadAll().successful() ? success : ChangeResult.STORAGE_ERROR;
    }

    private String configPresetKey(String id) {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("presets");
        if (root == null) return id;
        return root.getKeys(false).stream().filter(key -> key.equalsIgnoreCase(id)).findFirst().orElse(id);
    }

    private boolean valid(PresetSpec p) {
        if (p == null || p.id() == null || !p.id().matches("[a-z0-9_-]{1,32}")
                || p.material() == null || p.material() == Material.AIR
                || !p.material().isBlock() || !p.material().isItem()
                || p.displayName() == null || p.displayName().isBlank() || p.lore() == null
                || !Double.isFinite(p.horizontalVelocity()) || p.horizontalVelocity() < 0 || p.horizontalVelocity() > 5
                || !Double.isFinite(p.verticalVelocity()) || p.verticalVelocity() < 0 || p.verticalVelocity() > 5
                || p.cooldownTicks() < 0 || p.cooldownTicks() > 200
                || p.fallDamageImmunityTicks() < 0 || p.fallDamageImmunityTicks() > 400
                || p.directionMode() == null) return false;
        try { DirectionMode.valueOf(p.directionMode()); } catch (IllegalArgumentException exception) { return false; }
        return true;
    }
}
