package me.foesio.foJumpPads;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSound;
import me.foesio.core.sound.FoSoundMigrations;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.SoundCategory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FoJumpPads extends JavaPlugin {

    public static final String DISPLAY_NAME = "FoJumpPads";
    public static final String ADMIN_PERMISSION = "fojumppads.admin";
    public static final String MODRINTH_PROJECT_ID = "fojumppads";
    private static final int BSTATS_PLUGIN_ID = 32427;

    private final Map<PadLocation, PadData> pads = new ConcurrentHashMap<>();

    private FoCoreContext core;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private NamespacedKey presetKey;
    private FoMessageService messages;
    private GuiConfig guiConfig;
    private PresetRegistry presetRegistry;
    private PadStorage padStorage;
    private UpdateNoticeService updates;
    private FoReloadRegistry reloadRegistry;
    private EditorManager editorManager;
    private volatile Set<String> disabledWorlds = Set.of();
    private volatile boolean dropPresetItemOnBreak = true;
    private volatile long pathLockMaxMillis = 5000L;
    private final Set<UUID> nativeDialogWarnedPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        presetKey = new NamespacedKey(this, "preset");
        saveDefaultConfig();

        messages = FoMessageService.load(this, messageMigrations());
        guiConfig = new GuiConfig(this, messages);
        presetRegistry = new PresetRegistry(this);
        reloadRegistry = FoReloadRegistry.create()
                .add("config", this::reloadConfigState)
                .add("sounds", () -> sounds.reload())
                .addMessages(messages)
                .add("presets", presetRegistry::reload);
        reloadAll();
        migrateSprites();

        try {
            padStorage = new PadStorage(this);
            pads.putAll(padStorage.loadPads());
            logInfo("Loaded " + pads.size() + " jump pads from userdata.db.");
        } catch (Exception exception) {
            logWarn("Could not open userdata.db: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        editorManager = new EditorManager(this);

        JumpPadListener jumpPadListener = new JumpPadListener(this);
        Bukkit.getPluginManager().registerEvents(jumpPadListener, this);
        Bukkit.getPluginManager().registerEvents(editorManager, this);

        new AdminCommand(this).register();
    }

    @Override
    public void onDisable() {
        if (padStorage != null) {
            try {
                padStorage.close();
            } catch (SQLException exception) {
                logWarn("Could not close userdata.db: " + exception.getMessage());
            }
        }
        if (core != null) {
            core.close();
            core = null;
        }
        if (updates != null) {
            HandlerList.unregisterAll(updates);
        }
        updates = null;
        sounds = null;
        adminSounds = null;
        editorSounds = null;
    }

    public FoReloadResult reloadAll() {
        FoReloadResult result = reloadRegistry.reload();
        if (result.successful() && updates == null && core != null) {
            updates = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();
        }
        return result;
    }

    private void migrateSprites() {
        messages.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":slime_block:", null);
            changed |= FoMessageService.addMissingToken(config, "reload-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "reload-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "give-success", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "give-received", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "pad-saved", ":slime_block:");
            changed |= FoMessageService.addMissingToken(config, "pad-save-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "pad-removed", ":lava_bucket:");
            changed |= FoMessageService.addMissingToken(config, "pad-teleport-success", ":ender_pearl:");
            changed |= FoMessageService.addMissingToken(config, "editor-opened", ":book:");
            changed |= FoMessageService.addMissingToken(config, "setting-saved", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "setting-save-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "preset-added", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "preset-deleted", ":lava_bucket:");
            return true;
        });
    }

    private void reloadConfigState() {
        reloadConfig();
        NativeDialogConfigDefaults.addDefaults(getConfig());
        saveConfig();
        refreshCore();
        loadRuntimeSettings();
    }

    private void refreshCore() {
        if (core != null) {
            core.close();
        }
        if (updates != null) {
            HandlerList.unregisterAll(updates);
            updates = null;
        }
        core = FoPluginCore.create(this);
        sounds = core.createSounds(FoSoundMigrations.create()
                .add(service -> migrateLegacyPresetSounds(service))
                .build());
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        core.metrics(BSTATS_PLUGIN_ID);
        core.warnIfNativeDialogsUnavailable();
    }

    private boolean migrateLegacyPresetSounds(FoSoundService service) {
        ConfigurationSection presets = getConfig().getConfigurationSection("presets");
        if (presets == null) {
            return false;
        }

        boolean changed = false;
        for (String presetId : presets.getKeys(false)) {
            String sourcePath = "presets." + presetId + ".sound";
            String targetPath = "jump-pad.presets." + presetId.toLowerCase(Locale.ROOT);
            changed |= service.moveFromConfig(sourcePath, targetPath);
        }
        return changed;
    }

    private void loadRuntimeSettings() {
        disabledWorlds = getConfig().getStringList("settings.disabled-worlds").stream()
                .map(String::trim)
                .filter(world -> !world.isBlank())
                .map(world -> world.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        dropPresetItemOnBreak = getConfig().getBoolean("settings.drop-preset-item-on-break", true);
        pathLockMaxMillis = Math.max(1, getConfig().getInt("settings.path-lock-max-ticks", 100)) * 50L;
        nativeDialogWarnedPlayers.clear();
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .removeExact("unknown-command",
                        "{prefix}{bad}Unknown admin action. {muted}Use {theme}/fojumppadsadmin <version|reload|give|list|teleport|remove|editor>{muted}.")
                .build();
    }

    public boolean saveMainConfig() {
        try {
            getConfig().save(new File(getDataFolder(), "config.yml"));
            return true;
        } catch (IOException exception) {
            logWarn("Could not save config.yml: " + exception.getMessage());
            return false;
        }
    }

    public NamespacedKey presetKey() {
        return presetKey;
    }

    public FoMessageService messages() {
        return messages;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    public PresetRegistry presets() {
        return presetRegistry;
    }

    public PadStorage storage() {
        return padStorage;
    }

    public UpdateNoticeService updates() {
        return updates;
    }

    public FoSoundService sounds() {
        return sounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public SoundCategory soundCategory() {
        return SoundCategory.PLAYERS;
    }

    public Sound presetSound(String presetId, String legacyFallback) {
        String path = "jump-pad.presets." + presetId.toLowerCase(Locale.ROOT);
        String soundKey = sounds.find(path).map(FoSound::soundKey).orElse(legacyFallback);
        return me.foesio.core.sound.SoundTypes.resolve(soundKey).orElse(null);
    }

    public String presetSoundName(String presetId, String legacyFallback) {
        String configured = presetSoundKey(presetId, legacyFallback);
        return me.foesio.core.sound.SoundTypes.resolve(configured).map(Sound::name).orElse(configured);
    }

    public String presetSoundKey(String presetId, String legacyFallback) {
        String path = "jump-pad.presets." + presetId.toLowerCase(Locale.ROOT);
        return sounds.find(path).map(FoSound::soundKey).orElse(legacyFallback);
    }

    public boolean setPresetSound(String presetId, String soundKey) {
        return sounds.setSoundKey("jump-pad.presets." + presetId.toLowerCase(Locale.ROOT), soundKey);
    }

    public FoCoreContext core() {
        return core;
    }

    public EditorManager editor() {
        return editorManager;
    }

    public PadData padData(PadLocation location) {
        return pads.get(location);
    }

    public Map<PadLocation, PadData> padSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pads));
    }

    public void putPad(PadLocation location, PadData data) {
        pads.put(location, data);
    }

    public PadData removePad(PadLocation location) {
        return pads.remove(location);
    }

    public boolean isWorldDisabled(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean dropPresetItemOnBreak() {
        return dropPresetItemOnBreak;
    }

    public long pathLockMaxMillis() {
        return pathLockMaxMillis;
    }

    public void warnNativeDialogFallback(Player player) {
        warnNativeDialogFallback(player, false);
    }

    public void warnNativeDialogFallback(Player player, boolean force) {
        if (!core.nativeDialogs().configEnabled()
                || !core.nativeDialogs().warnOnFallback()
                || (!force && core.nativeDialogs().canUseNativeDialogs(player))) {
            return;
        }
        if (nativeDialogWarnedPlayers.add(player.getUniqueId())) {
            messages.send(player, "native-dialog-fallback");
        }
    }

    public void logInfo(String message) {
        getLogger().info("[" + DISPLAY_NAME + "] " + message);
    }

    public void logWarn(String message) {
        getLogger().warning("[" + DISPLAY_NAME + "] " + message);
    }
}
