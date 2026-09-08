package me.foesio.foJumpPads;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.CursorItemEditor;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.selector.TriStateSelectionActionType;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionEntry;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.core.text.PromptNormalizer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EditorManager implements Listener {

    private static final int INVALID_INT = Integer.MIN_VALUE;
    private static final List<Integer> DEFAULT_ENTRY_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );
    private static final int CORE_SELECTOR_SEARCH_OFFSET = 2;
    private static final int CORE_SELECTOR_CLEAR_SEARCH_OFFSET = 6;
    private static final List<String> SOUND_VALUES = Arrays.stream(Sound.values())
            .map(Sound::name)
            .sorted()
            .toList();
    private static final List<String> PARTICLE_VALUES = Arrays.stream(Particle.values())
            .map(Particle::name)
            .sorted()
            .toList();

    private final FoJumpPads plugin;
    private final Map<UUID, EditorView> views = new ConcurrentHashMap<>();
    private final Map<UUID, Prompt> chatPrompts = new ConcurrentHashMap<>();
    private final Set<UUID> opening = ConcurrentHashMap.newKeySet();
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();

    public EditorManager(FoJumpPads plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        openMain(player, true);
    }

    private void openMain(Player player, boolean playSound) {
        Inventory inventory = Bukkit.createInventory(player,
                plugin.guiConfig().size("main", 27),
                plugin.guiConfig().title("main", Map.of()));
        plugin.guiConfig().fill(inventory);
        setButton(player, inventory, "main.items.settings", Map.of());
        setButton(player, inventory, "main.items.presets", Map.of());
        open(player, inventory, new EditorView(ViewType.MAIN, null, 0, ""));
        if (playSound) {
            plugin.editorSounds().open(player);
        }
    }

    private void openSettings(Player player) {
        Map<String, String> placeholders = settingsPlaceholders();
        Inventory inventory = Bukkit.createInventory(player,
                plugin.guiConfig().size("settings", 36),
                plugin.guiConfig().title("settings", placeholders));
        plugin.guiConfig().fill(inventory);
        setButton(player, inventory, "settings.items.global-cooldown", placeholders);
        setButton(player, inventory, "settings.items.fall-immunity", placeholders);
        setButton(player, inventory, "settings.items.drop-on-break", placeholders);
        setSharedButton(player, inventory, "settings.items.disabled-worlds", worldsButton(player));
        setBackButton(player, inventory);
        open(player, inventory, new EditorView(ViewType.SETTINGS, null, 0, ""));
    }

    private void openWorldSelector(Player player, int page) {
        openWorldSelector(player, page, "");
    }

    private void openWorldSelector(Player player, int page, String search) {
        TriStateSelectionRequest request = worldSelectorRequest(page, search);
        Inventory inventory = TriStateSelectionMenus.createInventory(player, request);
        applyCoreSelectorLayout(inventory, request);
        UUID uuid = player.getUniqueId();
        opening.add(uuid);
        chatPrompts.remove(uuid);
        views.put(uuid, new EditorView(ViewType.WORLDS, null, request.page(), request.filter()));
        try {
            player.openInventory(inventory);
        } finally {
            clearOpeningLater(uuid);
        }
    }

    private void openPresetBrowser(Player player, int page, String search) {
        String query = search == null ? "" : search;
        List<JumpPadPreset> presets = filteredPresets(query);
        List<EntryBrowserRequest.Entry> entries = presets.stream()
                .map(preset -> EntryBrowserRequest.Entry.of(preset.id(), presetEntryItem(player, preset)))
                .toList();
        EntryBrowserRequest request = EntryBrowserRequest.builder()
                .title(plugin.guiConfig().title("preset-browser", Map.of()))
                .entries(entries)
                .page(page)
                .filter(query)
                .buttons(buttons)
                .showBack(true)
                .addButton(presetAddButton(player))
                .context(new PresetBrowserContext())
                .build();
        UUID uuid = player.getUniqueId();
        opening.add(uuid);
        chatPrompts.remove(uuid);
        views.put(uuid, new EditorView(ViewType.BROWSER, null, Math.max(0, page), query));
        try {
            EntryBrowserMenus.open(player, request);
        } finally {
            clearOpeningLater(uuid);
        }
    }

    private void openPresetEdit(Player player, String presetId, int page, String search) {
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) {
            plugin.messages().send(player, "preset-not-found", Map.of("preset", presetId));
            openPresetBrowser(player, page, search);
            return;
        }
        Map<String, String> placeholders = preset.placeholders(plugin.messages());
        Inventory inventory = Bukkit.createInventory(player,
                plugin.guiConfig().size("preset-edit", 45),
                plugin.guiConfig().title("preset-edit", placeholders));
        plugin.guiConfig().fill(inventory);
        setButton(player, inventory, "preset-edit.items.enabled", placeholders);
        setButton(player, inventory, "preset-edit.items.direction", placeholders);
        setButton(player, inventory, "preset-edit.items.horizontal", placeholders);
        setButton(player, inventory, "preset-edit.items.vertical", placeholders);
        setButton(player, inventory, "preset-edit.items.cooldown", placeholders);
        setButton(player, inventory, "preset-edit.items.sound", placeholders);
        setButton(player, inventory, "preset-edit.items.particle", placeholders);
        setButton(player, inventory, "preset-edit.items.path-lock", placeholders);
        setPresetBlockButton(player, inventory, preset, placeholders);
        setButton(player, inventory, "preset-edit.items.delete", placeholders);
        setBackButton(player, inventory);
        open(player, inventory, new EditorView(ViewType.EDIT, preset.id(), page, search == null ? "" : search));
    }

    private void openSoundSelector(Player player, String presetId, int page, String search) {
        openSoundSelector(player, presetId, page, search, "");
    }

    private void openSoundSelector(Player player, String presetId, int page, String parentSearch, String selectorSearch) {
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) {
            plugin.messages().send(player, "preset-not-found", Map.of("preset", presetId));
            openPresetBrowser(player, page, parentSearch);
            return;
        }
        openValueSelector(player, preset, SOUND_VALUES, selectorEntrySlots("sound-selector"), page, selectorSearch,
                "sound-selector", ViewType.SOUND_SELECTOR, preset.sound() == null ? "none" : preset.sound(), parentSearch);
    }

    private void openParticleSelector(Player player, String presetId, int page, String search) {
        openParticleSelector(player, presetId, page, search, "");
    }

    private void openParticleSelector(Player player, String presetId, int page, String parentSearch, String selectorSearch) {
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) {
            plugin.messages().send(player, "preset-not-found", Map.of("preset", presetId));
            openPresetBrowser(player, page, parentSearch);
            return;
        }
        openValueSelector(player, preset, PARTICLE_VALUES, selectorEntrySlots("particle-selector"), page, selectorSearch,
                "particle-selector", ViewType.PARTICLE_SELECTOR, preset.particle() == null ? "none" : preset.particle().name(), parentSearch);
    }

    private void openValueSelector(Player player, JumpPadPreset preset, List<String> values, List<Integer> slots,
                                   int page, String search, String path, ViewType type, String currentValue, String parentSearch) {
        String query = search == null ? "" : search;
        List<String> filteredValues = filteredValues(values, query);
        int entriesPerPage = Math.max(1, slots.size());
        int pages = Math.max(1, (int) Math.ceil(filteredValues.size() / (double) entriesPerPage));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        Map<String, String> placeholders = selectorPlaceholders(preset, safePage, pages);
        Inventory inventory = Bukkit.createInventory(player,
                plugin.guiConfig().size(path, 54),
                plugin.guiConfig().title(path, placeholders));
        plugin.guiConfig().fill(inventory);

        int start = safePage * entriesPerPage;
        for (int index = 0; index < slots.size(); index++) {
            int valueIndex = start + index;
            if (valueIndex >= filteredValues.size()) {
                break;
            }
            String value = filteredValues.get(valueIndex);
            setValueEntry(player, inventory, slots.get(index), path, value, value.equals(currentValue));
        }

        setSearchButton(inventory, buttons.search(player, query));
        if (!query.isBlank()) {
            setClearSearchButton(inventory, buttons.clearSearch(player, path.equals("sound-selector") ? "sounds" : "particles"));
        }
        if (safePage > 0) {
            setSharedButton(player, inventory, path + ".items.previous", buttons.previousPage(player, safePage, pages - 1));
        }
        if (safePage < pages - 1) {
            setSharedButton(player, inventory, path + ".items.next", buttons.nextPage(player, safePage, pages - 1));
        }
        setBackButton(player, inventory);
        open(player, inventory, new EditorView(type, preset.id(), safePage, query, parentSearch == null ? "" : parentSearch));
    }

    private void openConfirmDelete(Player player, EditorView previous) {
        JumpPadPreset preset = plugin.presets().get(previous.presetId());
        if (preset == null) {
            plugin.messages().send(player, "preset-delete-missing", Map.of("preset", previous.presetId()));
            openPresetBrowser(player, previous.page(), previous.search());
            return;
        }
        Map<String, String> placeholders = preset.placeholders(plugin.messages());
        Inventory inventory = Bukkit.createInventory(player, 27, plugin.guiConfig().title("confirm-delete", placeholders));
        plugin.guiConfig().fill(inventory);
        setButton(player, inventory, "confirm-delete.items.confirm", placeholders);
        setButton(player, inventory, "confirm-delete.items.cancel", placeholders);
        open(player, inventory, new EditorView(ViewType.CONFIRM_DELETE, preset.id(), previous.page(), previous.search()));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder holder) {
            event.setCancelled(true);
            if (!player.hasPermission(FoJumpPads.ADMIN_PERMISSION)) {
                player.closeInventory();
                plugin.messages().send(player, "no-permission");
                return;
            }
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                handleEntryBrowserClick(player, holder, event);
            }
            return;
        }
        EditorView view = views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        if (!player.hasPermission(FoJumpPads.ADMIN_PERMISSION)) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        switch (view.type()) {
            case MAIN -> clickMain(player, slot);
            case SETTINGS -> clickSettings(player, slot);
            case WORLDS -> clickWorlds(player, slot, event.getView().getTopInventory());
            case EDIT -> clickEdit(player, view, slot, event.getCursor());
            case SOUND_SELECTOR -> clickSoundSelector(player, view, slot);
            case PARTICLE_SELECTOR -> clickParticleSelector(player, view, slot);
            case CONFIRM_DELETE -> clickConfirm(player, view, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder)
                && !views.containsKey(player.getUniqueId())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void handleEntryBrowserClick(Player player, EntryBrowserHolder holder, InventoryClickEvent event) {
        EntryBrowserClick click = EntryBrowserMenus.handleClick(event.getRawSlot(), holder, event.getClick());
        if (!(holder.request().context() instanceof PresetBrowserContext)) {
            return;
        }
        int page = holder.request().page();
        String search = holder.request().filter();
        switch (click.action()) {
            case ENTRY -> {
                if (click.entryId() != null) {
                    openPresetEdit(player, click.entryId(), page, search);
                    plugin.editorSounds().open(player);
                }
            }
            case ADD -> startPrompt(player, new Prompt(PromptType.ADD_PRESET, null, page, search), Map.of());
            case SEARCH -> {
                plugin.editorSounds().search(player);
                startPrompt(player, new Prompt(PromptType.SEARCH, null, page, search), Map.of());
            }
            case CLEAR_SEARCH -> {
                plugin.editorSounds().clearSearch(player);
                plugin.messages().send(player, "search-cleared");
                openPresetBrowser(player, 0, "");
            }
            case PREVIOUS_PAGE -> {
                plugin.editorSounds().previousPage(player);
                openPresetBrowser(player, page - 1, search);
            }
            case NEXT_PAGE -> {
                plugin.editorSounds().nextPage(player);
                openPresetBrowser(player, page + 1, search);
            }
            case BACK -> {
                plugin.editorSounds().back(player);
                openMain(player, false);
            }
            case EXTRA, NONE -> {
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && plugin.core().inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }
        UUID uuid = event.getPlayer().getUniqueId();
        if (opening.contains(uuid)) {
            return;
        }
        EditorView view = views.remove(uuid);
        if (view != null && view.type() == ViewType.CONFIRM_DELETE
                && event.getPlayer() instanceof Player player
                && player.isOnline()
                && plugin.isEnabled()) {
            plugin.core().scheduler().runForPlayer(player, () -> {
                if (player.isOnline() && player.hasPermission(FoJumpPads.ADMIN_PERMISSION)) {
                    openPresetEdit(player, view.presetId(), view.page(), view.search());
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        views.remove(uuid);
        chatPrompts.remove(uuid);
        opening.remove(uuid);
        try {
            plugin.core().dialogInputs().clear(event.getPlayer());
        } catch (Throwable throwable) {
            plugin.logWarn("Dialog cleanup failed: " + throwable.getMessage());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Prompt prompt = chatPrompts.remove(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.core().scheduler().runForPlayer(player, () -> {
            if (player.isOnline()) {
                handlePrompt(player, prompt, message);
            }
        });
    }

    private void clickMain(Player player, int slot) {
        if (slot == configuredSlot("main.items.settings", 11)) {
            openSettings(player);
            plugin.editorSounds().open(player);
        } else if (slot == configuredSlot("main.items.presets", 15)) {
            openPresetBrowser(player, 0, "");
            plugin.editorSounds().open(player);
        }
    }

    private void clickSettings(Player player, int slot) {
        if (slot == configuredSlot("settings.items.global-cooldown", 10)) {
            startPrompt(player, new Prompt(PromptType.GLOBAL_COOLDOWN, null, 0, ""),
                    Map.of("setting", "global cooldown ticks", "min", "0", "max", "200"));
        } else if (slot == configuredSlot("settings.items.fall-immunity", 12)) {
            startPrompt(player, new Prompt(PromptType.FALL_IMMUNITY, null, 0, ""),
                    Map.of("setting", "fall immunity ticks", "min", "0", "max", "400"));
        } else if (slot == configuredSlot("settings.items.drop-on-break", 14)) {
            boolean next = !plugin.getConfig().getBoolean("settings.drop-preset-item-on-break", true);
            plugin.getConfig().set("settings.drop-preset-item-on-break", next);
            if (saveAndReload(player, "Drop Item On Break", next ? "Enabled" : "Disabled")) {
                plugin.editorSounds().toggle(player, next);
            }
            openSettings(player);
        } else if (slot == configuredSlot("settings.items.disabled-worlds", 16)) {
            openWorldSelector(player, 0);
            plugin.editorSounds().open(player);
        } else if (slot == backSlot(plugin.guiConfig().size("settings", 36))) {
            plugin.editorSounds().back(player);
            openMain(player, false);
        }
    }

    private void clickWorlds(Player player, int slot, Inventory inventory) {
        if (!(inventory.getHolder() instanceof TriStateSelectionHolder holder)) {
            openSettings(player);
            return;
        }

        int remappedSlot = remapCoreSelectorClickSlot(slot, holder);
        if (remappedSlot == INVALID_INT) {
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(remappedSlot, holder);
        if (click.action() == TriStateSelectionActionType.NONE) {
            return;
        }
        if (click.action() == TriStateSelectionActionType.BACK) {
            plugin.editorSounds().back(player);
            openSettings(player);
            return;
        }
        if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE
                || click.action() == TriStateSelectionActionType.NEXT_PAGE) {
            if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE) {
                plugin.editorSounds().previousPage(player);
            } else {
                plugin.editorSounds().nextPage(player);
            }
            openWorldSelector(player, click.nextRequest().page(), click.nextRequest().filter());
            return;
        }
        if (click.action() == TriStateSelectionActionType.SEARCH) {
            plugin.editorSounds().search(player);
            startPrompt(player, new Prompt(PromptType.WORLD_SEARCH, null, click.page(), click.filter()), Map.of());
            return;
        }
        if (click.action() == TriStateSelectionActionType.CLEAR_SEARCH) {
            plugin.editorSounds().clearSearch(player);
            plugin.messages().send(player, "search-cleared");
            openWorldSelector(player, 0, "");
            return;
        }
        if (click.action() == TriStateSelectionActionType.TOGGLE) {
            saveWorldStates(player, click);
        }
    }

    private TriStateSelectionRequest worldSelectorRequest(int page, String search) {
        List<String> disabledWorlds = plugin.getConfig().getStringList("settings.disabled-worlds");
        Set<String> disabledKeys = disabledWorlds.stream()
                .map(world -> world.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        List<TriStateSelectionEntry> entries = WorldSelectionEntries.loadedAndConfigured(plugin, disabledWorlds);
        Map<String, TriStateSelectionState> states = new LinkedHashMap<>();
        for (TriStateSelectionEntry entry : entries) {
            states.put(entry.key(), disabledKeys.contains(entry.key().toLowerCase(Locale.ROOT))
                    ? TriStateSelectionState.DISABLED
                    : TriStateSelectionState.ENABLED);
        }
        return TriStateSelectionRequest.builder()
                .worldSelection()
                .entries(entries)
                .states(states)
                .cycleOrder(List.of(TriStateSelectionState.ENABLED, TriStateSelectionState.DISABLED))
                .page(page)
                .filter(search)
                .showSearch(true)
                .showBack(true)
                .buttons(buttons)
                .enabledLabel("Enabled")
                .disabledLabel("Disabled")
                .clickHint("Click to toggle.")
                .emptyTitle("No Worlds")
                .emptyLore(List.of("No worlds are loaded or configured."))
                .build();
    }

    private void saveWorldStates(Player player, TriStateSelectionClick click) {
        List<String> disabledWorlds = TriStateSelections.disabledKeys(click.nextRequest());
        plugin.getConfig().set("settings.disabled-worlds", disabledWorlds);
        if (saveAndReload(player, "World", click.displayName(), false)) {
            plugin.editorSounds().cycle(player);
            plugin.messages().send(player,
                    click.newState() == TriStateSelectionState.DISABLED ? "world-disabled" : "world-enabled",
                    Map.of("world", click.displayName()));
        }
        openWorldSelector(player, click.nextRequest().page(), click.nextRequest().filter());
    }

    private void clickEdit(Player player, EditorView view, int slot, ItemStack cursor) {
        JumpPadPreset preset = plugin.presets().get(view.presetId());
        if (preset == null) {
            plugin.messages().send(player, "preset-not-found", Map.of("preset", view.presetId()));
            openPresetBrowser(player, view.page(), view.search());
            return;
        }
        if (slot == configuredSlot("preset-edit.items.enabled", 10)) {
            boolean next = !preset.enabled();
            if (setValue(player, preset.id(), "enabled", next, "Enabled", next ? "Enabled" : "Disabled")) {
                plugin.editorSounds().toggle(player, next);
            }
            openPresetEdit(player, preset.id(), view.page(), view.search());
        } else if (slot == configuredSlot("preset-edit.items.direction", 12)) {
            DirectionMode next = preset.directionMode().next();
            if (setValue(player, preset.id(), "direction-mode", next.name(), "Direction Mode", next.displayName())) {
                plugin.editorSounds().cycle(player);
            }
            openPresetEdit(player, preset.id(), view.page(), view.search());
        } else if (slot == configuredSlot("preset-edit.items.horizontal", 14)) {
            startNumberPrompt(player, PromptType.HORIZONTAL, preset.id(), view, "horizontal launch", "0", "5");
        } else if (slot == configuredSlot("preset-edit.items.vertical", 16)) {
            startNumberPrompt(player, PromptType.VERTICAL, preset.id(), view, "vertical launch", "0", "5");
        } else if (slot == configuredSlot("preset-edit.items.cooldown", 20)) {
            startNumberPrompt(player, PromptType.COOLDOWN, preset.id(), view, "cooldown ticks", "0", "200");
        } else if (slot == configuredSlot("preset-edit.items.sound", 22)) {
            openSoundSelector(player, preset.id(), 0, view.search());
            plugin.editorSounds().open(player);
        } else if (slot == configuredSlot("preset-edit.items.particle", 24)) {
            openParticleSelector(player, preset.id(), 0, view.search());
            plugin.editorSounds().open(player);
        } else if (slot == configuredSlot("preset-edit.items.path-lock", 28)) {
            boolean next = !preset.pathLock();
            if (setValue(player, preset.id(), "path-lock", next, "Path Lock", next ? "Enabled" : "Disabled")) {
                plugin.editorSounds().toggle(player, next);
            }
            openPresetEdit(player, preset.id(), view.page(), view.search());
        } else if (slot == configuredSlot("preset-edit.items.item", 31)) {
            copyCursorItem(player, preset.id(), cursor, view);
        } else if (slot == configuredSlot("preset-edit.items.delete", 40)) {
            openConfirmDelete(player, view);
            plugin.editorSounds().open(player);
        } else if (slot == backSlot(plugin.guiConfig().size("preset-edit", 54))) {
            plugin.editorSounds().back(player);
            openPresetBrowser(player, view.page(), view.search());
        }
    }

    private void clickSoundSelector(Player player, EditorView view, int slot) {
        clickValueSelector(player, view, slot, "sound-selector", SOUND_VALUES, "sound");
    }

    private void clickParticleSelector(Player player, EditorView view, int slot) {
        clickValueSelector(player, view, slot, "particle-selector", PARTICLE_VALUES, "particle");
    }

    private void clickValueSelector(Player player, EditorView view, int slot, String path, List<String> values, String configKey) {
        List<Integer> slots = selectorEntrySlots(path);
        List<String> filteredValues = filteredValues(values, view.search());
        int entryIndex = slots.indexOf(slot);
        if (entryIndex >= 0) {
            int valueIndex = view.page() * Math.max(1, slots.size()) + entryIndex;
            if (valueIndex < filteredValues.size()) {
                String value = filteredValues.get(valueIndex);
                if (setValue(player, view.presetId(), configKey, value, configKey.equals("sound") ? "Sound" : "Particle", value)) {
                    plugin.editorSounds().cycle(player);
                }
                openPresetEdit(player, view.presetId(), 0, view.parentSearch());
            }
            return;
        }
        int size = plugin.guiConfig().size(path, 54);
        int pages = Math.max(1, (int) Math.ceil(filteredValues.size() / (double) Math.max(1, slots.size())));
        if (slot == configuredSlot(path + ".items.previous", 45) && view.page() > 0) {
            plugin.editorSounds().previousPage(player);
            openSelectorByType(player, view, view.page() - 1);
        } else if (slot == configuredSlot(path + ".items.next", 53) && view.page() < pages - 1) {
            plugin.editorSounds().nextPage(player);
            openSelectorByType(player, view, view.page() + 1);
        } else if (slot == searchSlot(size)) {
            plugin.editorSounds().search(player);
            PromptType searchType = path.equals("sound-selector") ? PromptType.SOUND_SEARCH : PromptType.PARTICLE_SEARCH;
            startPrompt(player, new Prompt(searchType, view.presetId(), view.page(), view.search(), view.parentSearch()), Map.of());
        } else if (slot == clearSearchSlot(size) && !view.search().isBlank()) {
            plugin.editorSounds().clearSearch(player);
            plugin.messages().send(player, "search-cleared");
            openSelectorByType(player, new EditorView(view.type(), view.presetId(), 0, "", view.parentSearch()), 0);
        } else if (slot == backSlot(size)) {
            plugin.editorSounds().back(player);
            openPresetEdit(player, view.presetId(), 0, view.parentSearch());
        }
    }

    private void openSelectorByType(Player player, EditorView view, int page) {
        if (view.type() == ViewType.SOUND_SELECTOR) {
            openSoundSelector(player, view.presetId(), page, view.parentSearch(), view.search());
        } else {
            openParticleSelector(player, view.presetId(), page, view.parentSearch(), view.search());
        }
    }

    private void clickConfirm(Player player, EditorView view, int slot) {
        if (slot == configuredSlot("confirm-delete.items.confirm", 11)) {
            if (!plugin.presets().contains(view.presetId())) {
                plugin.messages().send(player, "preset-delete-missing", Map.of("preset", view.presetId()));
                openPresetBrowser(player, view.page(), view.search());
                return;
            }
            plugin.getConfig().set("presets." + view.presetId(), null);
            if (saveAndReload(player, "Preset", view.presetId(), false)) {
                plugin.editorSounds().delete(player);
                plugin.messages().send(player, "preset-deleted", Map.of("preset", view.presetId()));
            }
            openPresetBrowser(player, view.page(), view.search());
        } else if (slot == configuredSlot("confirm-delete.items.cancel", 15)) {
            plugin.editorSounds().back(player);
            openPresetEdit(player, view.presetId(), view.page(), view.search());
        }
    }

    private void startNumberPrompt(Player player, PromptType type, String presetId, EditorView view,
                                   String setting, String min, String max) {
        startPrompt(player, new Prompt(type, presetId, view.page(), view.search()),
                Map.of("setting", setting, "min", min, "max", max));
    }

    private void startPrompt(Player player, Prompt prompt, Map<String, String> placeholders) {
        UUID uuid = player.getUniqueId();
        views.remove(uuid);
        chatPrompts.remove(uuid);
        TextDialogRequest request = promptRequest(prompt, placeholders);
        try {
            boolean opened = EditorDialogInputs.openTextFromInventory(plugin,
                    plugin.core().inventoryCloseSuppressor(),
                    plugin.core().dialogService(),
                    player,
                    request,
                    input -> handlePrompt(player, prompt, input),
                    () -> cancelPrompt(player, prompt));
            if (!opened) {
                plugin.warnNativeDialogFallback(player, true);
                startChatPrompt(player, prompt, request);
                return;
            }
            plugin.editorSounds().open(player);
            plugin.warnNativeDialogFallback(player);
        } catch (Throwable throwable) {
            plugin.core().inventoryCloseSuppressor().clear(player);
            plugin.core().nativeDialogs().disableForSession("input failed: " + throwable.getClass().getSimpleName());
            plugin.logWarn("Dialog input failed, using chat fallback: " + throwable.getMessage());
            plugin.warnNativeDialogFallback(player, true);
            startChatPrompt(player, prompt, request);
        }
    }

    private void startChatPrompt(Player player, Prompt prompt, TextDialogRequest request) {
        plugin.core().inventoryCloseSuppressor().clear(player);
        player.closeInventory();
        chatPrompts.put(player.getUniqueId(), prompt);
        player.sendMessage(color("{prefix}{theme}Type input in chat. {muted}Type {bad}cancel{muted} to cancel."));
        request.body().stream()
                .filter(line -> line != null && !line.isBlank())
                .forEach(line -> player.sendMessage(color("{prefix}{muted}{line}", Map.of("line", line))));
        if (!request.initialValue().isBlank()) {
            player.sendMessage(color("{prefix}{muted}Current: {theme}{value}", Map.of("value", request.initialValue())));
        }
    }

    private void handlePrompt(Player player, Prompt prompt, String input) {
        if (!player.hasPermission(FoJumpPads.ADMIN_PERMISSION)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        String normalized = PromptNormalizer.normalize(input);
        if (PromptNormalizer.isCancel(input)) {
            plugin.editorSounds().back(player);
            plugin.messages().send(player, "prompt-cancelled");
            returnToPromptSource(player, prompt);
            return;
        }
        switch (prompt.type()) {
            case ADD_PRESET -> addPreset(player, prompt, normalized);
            case SEARCH -> applySearch(player, normalized);
            case WORLD_SEARCH -> applyWorldSearch(player, normalized);
            case SOUND_SEARCH -> applySoundSearch(player, prompt, normalized);
            case PARTICLE_SEARCH -> applyParticleSearch(player, prompt, normalized);
            case HORIZONTAL -> saveDoublePrompt(player, prompt, normalized, "velocity-horizontal", "Horizontal Launch", 0D, 5D);
            case VERTICAL -> saveDoublePrompt(player, prompt, normalized, "velocity-vertical", "Vertical Launch", 0D, 5D);
            case COOLDOWN -> saveIntPrompt(player, prompt, normalized, "cooldown-ticks", "Cooldown", 0, 200);
            case GLOBAL_COOLDOWN -> saveSettingIntPrompt(player, normalized, "settings.global-cooldown-ticks", "Global Cooldown", 0, 200);
            case FALL_IMMUNITY -> saveSettingIntPrompt(player, normalized, "settings.fall-damage-immunity-ticks", "Fall Immunity", 0, 400);
        }
    }

    private void cancelPrompt(Player player, Prompt prompt) {
        plugin.editorSounds().back(player);
        plugin.messages().send(player, "prompt-cancelled");
        returnToPromptSource(player, prompt);
    }

    private void addPreset(Player player, Prompt prompt, String input) {
        String id = input.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            plugin.messages().send(player, "invalid-input", Map.of("hint", "Use lowercase letters, numbers, _ or -."));
            openPresetBrowser(player, prompt.page(), prompt.search());
            return;
        }
        if (plugin.presets().contains(id)) {
            plugin.messages().send(player, "invalid-input", Map.of("hint", "Preset already exists."));
            openPresetBrowser(player, prompt.page(), prompt.search());
            return;
        }
        ConfigurationSection section = plugin.getConfig().createSection("presets." + id);
        section.set("enabled", true);
        section.set("material", Material.LIGHT_WEIGHTED_PRESSURE_PLATE.name());
        section.set("display-name", "{theme}" + id + " Jump Pad");
        section.set("lore", List.of("{white}Place to create a jump pad.", "{muted}Launch: {theme}{horizontal}x {muted}/ {theme}{vertical}y"));
        section.set("velocity-horizontal", 1.0D);
        section.set("velocity-vertical", 1.0D);
        section.set("direction-mode", DirectionMode.PLAYER_FACING.name());
        section.set("path-lock", false);
        section.set("cooldown-ticks", plugin.getConfig().getInt("settings.global-cooldown-ticks", 10));
        section.set("fall-damage-immunity-ticks", plugin.getConfig().getInt("settings.fall-damage-immunity-ticks", 80));
        section.set("particle", "CLOUD");
        plugin.sounds().setDefinition(
                "jump-pad.presets." + id,
                "ENTITY_FIREWORK_ROCKET_LAUNCH",
                plugin.soundCategory(),
                1.0f,
                1.0f,
                true
        );
        if (saveAndReload(player, "Preset", id, false)) {
            plugin.editorSounds().add(player);
            plugin.messages().send(player, "preset-added", Map.of("preset", id));
            openPresetEdit(player, id, prompt.page(), prompt.search());
        } else {
            openPresetBrowser(player, prompt.page(), prompt.search());
        }
    }

    private void applySearch(Player player, String input) {
        plugin.messages().send(player, "search-applied", Map.of("query", input));
        openPresetBrowser(player, 0, input);
    }

    private void applyWorldSearch(Player player, String input) {
        plugin.messages().send(player, "search-applied", Map.of("query", input));
        openWorldSelector(player, 0, input);
    }

    private void applySoundSearch(Player player, Prompt prompt, String input) {
        plugin.messages().send(player, "search-applied", Map.of("query", input));
        openSoundSelector(player, prompt.presetId(), 0, prompt.parentSearch(), input);
    }

    private void applyParticleSearch(Player player, Prompt prompt, String input) {
        plugin.messages().send(player, "search-applied", Map.of("query", input));
        openParticleSelector(player, prompt.presetId(), 0, prompt.parentSearch(), input);
    }

    private void saveDoublePrompt(Player player, Prompt prompt, String input, String key, String label, double min, double max) {
        double value = parseFiniteDouble(input, Double.NaN);
        if (!Double.isFinite(value) || value < min || value > max) {
            invalidNumber(player, min, max);
        } else if (setValue(player, prompt.presetId(), key, value, label, JumpPadPreset.format(value))) {
            plugin.editorSounds().save(player);
        }
        openPresetEdit(player, prompt.presetId(), prompt.page(), prompt.search());
    }

    private void saveIntPrompt(Player player, Prompt prompt, String input, String key, String label, int min, int max) {
        int value = parseInt(input, INVALID_INT);
        if (value < min || value > max) {
            invalidWholeNumber(player, min, max);
        } else if (setValue(player, prompt.presetId(), key, value, label, String.valueOf(value))) {
            plugin.editorSounds().save(player);
        }
        openPresetEdit(player, prompt.presetId(), prompt.page(), prompt.search());
    }

    private void saveSettingIntPrompt(Player player, String input, String path, String label, int min, int max) {
        int value = parseInt(input, INVALID_INT);
        if (value < min || value > max) {
            invalidWholeNumber(player, min, max);
            openSettings(player);
            return;
        }
        plugin.getConfig().set(path, value);
        if (saveAndReload(player, label, String.valueOf(value))) {
            plugin.editorSounds().save(player);
        }
        openSettings(player);
    }

    private int parseInt(String input, int fallback) {
        return LargeNumberParser.parse(input)
                .filter(value -> value.signum() >= 0)
                .filter(value -> value.compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0)
                .flatMap(value -> {
                    try {
                        return java.util.Optional.of(value.intValueExact());
                    } catch (ArithmeticException exception) {
                        return java.util.Optional.empty();
                    }
                })
                .orElse(fallback);
    }

    private double parseFiniteDouble(String input, double fallback) {
        return LargeNumberParser.parseDouble(input)
                .orElse(fallback);
    }

    private void invalidNumber(Player player, double min, double max) {
        plugin.sounds().play(player, "jump-pad.error");
        plugin.messages().send(player, "invalid-input", Map.of(
                "hint", "Use a number from " + JumpPadPreset.format(min) + " to " + JumpPadPreset.format(max) + "."
        ));
    }

    private void invalidWholeNumber(Player player, int min, int max) {
        plugin.sounds().play(player, "jump-pad.error");
        plugin.messages().send(player, "invalid-input", Map.of(
                "hint", "Use a whole number from " + min + " to " + max + "."
        ));
    }

    private void copyCursorItem(Player player, String presetId, ItemStack cursor, EditorView view) {
        ItemStack item = CursorItemEditor.cloneInputItem(player, cursor).orElse(null);
        if (item == null || !item.getType().isBlock()) {
            plugin.editorSounds().addItemError(player);
            plugin.messages().send(player, "item-invalid");
            openPresetEdit(player, presetId, view.page(), view.search());
            return;
        }
        String basePath = "presets." + presetId + ".";
        plugin.getConfig().set(basePath + "material", item.getType().name());
        plugin.getConfig().set(basePath + "item-stack", CursorItemEditor.serializeBase64(item));
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            plugin.getConfig().set(basePath + "display-name", meta.getDisplayName());
        }
        if (meta != null && meta.hasLore()) {
            plugin.getConfig().set(basePath + "lore", meta.getLore());
        }
        if (meta != null && meta.hasCustomModelData()) {
            plugin.getConfig().set(basePath + "custom-model-data", meta.getCustomModelData());
        } else {
            plugin.getConfig().set(basePath + "custom-model-data", null);
        }
        if (saveAndReload(player, "Item", presetId, false)) {
            plugin.editorSounds().addItem(player);
            plugin.messages().send(player, "item-saved", Map.of("preset", presetId));
        }
        openPresetEdit(player, presetId, view.page(), view.search());
    }

    private boolean setValue(Player player, String presetId, String key, Object value, String label, String displayValue) {
        if (key.equals("sound")) {
            plugin.setPresetSound(presetId, String.valueOf(value));
        } else {
            plugin.getConfig().set("presets." + presetId + "." + key, value);
        }
        return saveAndReload(player, label, displayValue);
    }

    private boolean saveAndReload(Player player, String setting, String value) {
        return saveAndReload(player, setting, value, true);
    }

    private boolean saveAndReload(Player player, String setting, String value, boolean sendSuccess) {
        if (!plugin.saveMainConfig()) {
            plugin.reloadAll();
            plugin.sounds().play(player, "jump-pad.error");
            plugin.messages().send(player, "setting-save-failed", Map.of("setting", setting));
            return false;
        }
        plugin.reloadAll();
        if (sendSuccess) {
            plugin.messages().send(player, "setting-saved", Map.of("setting", setting, "value", value));
        }
        return true;
    }

    private void returnToPromptSource(Player player, Prompt prompt) {
        if (prompt.type() == PromptType.ADD_PRESET || prompt.type() == PromptType.SEARCH) {
            openPresetBrowser(player, prompt.page(), prompt.search());
        } else if (prompt.type() == PromptType.WORLD_SEARCH) {
            openWorldSelector(player, prompt.page(), prompt.search());
        } else if (prompt.type() == PromptType.SOUND_SEARCH) {
            openSoundSelector(player, prompt.presetId(), prompt.page(), prompt.parentSearch(), prompt.search());
        } else if (prompt.type() == PromptType.PARTICLE_SEARCH) {
            openParticleSelector(player, prompt.presetId(), prompt.page(), prompt.parentSearch(), prompt.search());
        } else if (prompt.type() == PromptType.GLOBAL_COOLDOWN || prompt.type() == PromptType.FALL_IMMUNITY) {
            openSettings(player);
        } else {
            openPresetEdit(player, prompt.presetId(), prompt.page(), prompt.search());
        }
    }

    private TextDialogRequest promptRequest(Prompt prompt, Map<String, String> placeholders) {
        String current = currentPromptValue(prompt);
        return switch (prompt.type()) {
            case ADD_PRESET -> new TextDialogRequest(
                    color("{theme}Add Preset"),
                    List.of(color("{muted}Use lowercase letters, numbers, _ or -.")),
                    color("{theme}Preset ID"),
                    "",
                    "",
                    DialogButton.confirm(color("{good}Create")),
                    DialogButton.cancel(color("{bad}Cancel")),
                    320,
                    300,
                    32,
                    true,
                    true,
                    false
            );
            case SEARCH, WORLD_SEARCH, SOUND_SEARCH, PARTICLE_SEARCH -> new TextDialogRequest(
                    "Search",
                    List.of(color("{muted}Enter search text.")),
                    color("{theme}Search"),
                    current,
                    "",
                    DialogButton.search(color("{good}Search")),
                    DialogButton.cancel(color("{bad}Cancel")),
                    320,
                    300,
                    48,
                    true,
                    true,
                    false
            );
            case HORIZONTAL, VERTICAL, COOLDOWN, GLOBAL_COOLDOWN, FALL_IMMUNITY -> new TextDialogRequest(
                    color("{theme}{setting}", placeholders),
                    List.of(color("{muted}Enter a value from {theme}{min}{muted} to {theme}{max}{muted}.", placeholders)),
                    color("{theme}Value"),
                    current,
                    "",
                    DialogButton.save(color("{good}Save")),
                    DialogButton.cancel(color("{bad}Cancel")),
                    320,
                    300,
                    12,
                    true,
                    true,
                    false
            );
        };
    }

    private String color(String value) {
        return plugin.messages().renderTemplate(value, Map.of());
    }

    private String color(String value, Map<String, String> placeholders) {
        return plugin.messages().renderTemplate(value, placeholders);
    }

    private String currentPromptValue(Prompt prompt) {
        JumpPadPreset preset = prompt.presetId() == null ? null : plugin.presets().get(prompt.presetId());
        return switch (prompt.type()) {
            case ADD_PRESET -> "";
            case SEARCH, WORLD_SEARCH, SOUND_SEARCH, PARTICLE_SEARCH -> prompt.search() == null ? "" : prompt.search();
            case HORIZONTAL -> preset == null ? "" : JumpPadPreset.format(preset.horizontalVelocity());
            case VERTICAL -> preset == null ? "" : JumpPadPreset.format(preset.verticalVelocity());
            case COOLDOWN -> preset == null ? "" : String.valueOf(preset.cooldownTicks());
            case GLOBAL_COOLDOWN -> String.valueOf(plugin.getConfig().getInt("settings.global-cooldown-ticks", 10));
            case FALL_IMMUNITY -> String.valueOf(plugin.getConfig().getInt("settings.fall-damage-immunity-ticks", 80));
        };
    }

    private List<JumpPadPreset> filteredPresets(String search) {
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT);
        return plugin.presets().all().stream()
                .filter(preset -> query.isBlank() || preset.id().contains(query))
                .sorted(Comparator.comparing(JumpPadPreset::id))
                .toList();
    }

    private List<String> filteredValues(List<String> values, String search) {
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return values;
        }
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private List<Integer> selectorEntrySlots(String path) {
        List<Integer> slots = new ArrayList<>(plugin.guiConfig().intList(path + ".entry-slots"));
        if (slots.isEmpty()) {
            slots.addAll(DEFAULT_ENTRY_SLOTS);
        }
        return slots;
    }

    private ItemStack presetEntryItem(Player player, JumpPadPreset preset) {
        Map<String, String> placeholders = preset.placeholders(plugin.messages());
        List<String> lore = plugin.guiConfig().stringList("preset-browser.entry.lore");
        if (lore.isEmpty()) {
            lore = List.of(
                    "Enabled: {enabled}",
                    "Horizontal: {horizontal}",
                    "Vertical: {vertical}"
            );
        }
        String label = plugin.messages().renderTemplate(
                plugin.guiConfig().string("preset-browser.entry.name", "{theme}{preset}"), placeholders);
        return EditorItemFactory.button(player, preset.material(), "{theme}", label,
                colorList(lore, placeholders), "edit this preset");
    }

    private ItemStack presetAddButton(Player player) {
        return EditorItemFactory.button(player, Material.ANVIL, "{theme}", "Add Preset",
                List.of("Create a new preset."), "create a preset");
    }

    private void setValueEntry(Player player, Inventory inventory, int slot, String path, String value, boolean selected) {
        if (!validSlot(inventory, slot)) {
            return;
        }
        Material material = selected
                ? plugin.guiConfig().material(path + ".entry.selected-material", "LIME_DYE")
                : plugin.guiConfig().material(path + ".entry.material", "PAPER");
        Map<String, String> placeholders = Map.of(
                "value", value,
                "selected", selected ? color("{good}Selected") : color("{muted}No")
        );
        List<String> lore = plugin.guiConfig().stringList(path + ".entry.lore");
        if (lore.isEmpty()) {
            lore = List.of("Current: {selected}");
        }
        String label = plugin.messages().renderTemplate(
                plugin.guiConfig().string(path + ".entry.name", "{theme}{value}"), placeholders);
        ItemStack item = EditorItemFactory.button(player, material,
                selected ? "{good}" : "{theme}",
                plugin.messages().renderTemplate(label, Map.of()),
                colorList(lore, placeholders), "select this value");
        inventory.setItem(slot, item);
    }

    private void setPresetBlockButton(Player player, Inventory inventory, JumpPadPreset preset, Map<String, String> placeholders) {
        int slot = configuredSlot("preset-edit.items.item", 31);
        if (!validSlot(inventory, slot)) {
            return;
        }
        inventory.setItem(slot, plugin.guiConfig().namedItem(player,
                preset.material(),
                "preset-edit.items.item.name",
                "preset-edit.items.item.lore",
                placeholders
        ));
    }

    private Map<String, String> settingsPlaceholders() {
        return Map.of(
                "global_cooldown", String.valueOf(plugin.getConfig().getInt("settings.global-cooldown-ticks", 10)),
                "fall_immunity", String.valueOf(plugin.getConfig().getInt("settings.fall-damage-immunity-ticks", 80)),
                "drop_on_break", plugin.getConfig().getBoolean("settings.drop-preset-item-on-break", true)
                        ? color("{good}Enabled")
                        : color("{bad}Disabled"),
                "disabled_worlds", String.valueOf(plugin.getConfig().getStringList("settings.disabled-worlds").size())
        );
    }

    private List<String> colorList(List<String> values, Map<String, String> placeholders) {
        return values.stream()
                .map(value -> plugin.messages().renderTemplate(value, placeholders))
                .toList();
    }

    private Map<String, String> selectorPlaceholders(JumpPadPreset preset, int page, int pages) {
        Map<String, String> placeholders = new LinkedHashMap<>(preset.placeholders(plugin.messages()));
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(pages));
        return placeholders;
    }

    private void setButton(Player player, Inventory inventory, String sectionPath, Map<String, String> placeholders) {
        int slot = configuredSlot(sectionPath, -1);
        if (validSlot(inventory, slot)) {
            inventory.setItem(slot, plugin.guiConfig().item(player, sectionPath, placeholders));
        }
    }

    private void setSharedButton(Player player, Inventory inventory, String sectionPath, ItemStack item) {
        int slot = configuredSlot(sectionPath, -1);
        if (validSlot(inventory, slot)) {
            inventory.setItem(slot, item);
        }
    }

    private void setBackButton(Player player, Inventory inventory) {
        inventory.setItem(backSlot(inventory.getSize()), buttons.back(player));
    }

    private void setSearchButton(Inventory inventory, ItemStack item) {
        inventory.setItem(searchSlot(inventory.getSize()), item);
    }

    private void setClearSearchButton(Inventory inventory, ItemStack item) {
        inventory.setItem(clearSearchSlot(inventory.getSize()), item);
    }

    private int configuredSlot(String sectionPath, int fallback) {
        return plugin.guiConfig().slot(sectionPath, fallback);
    }

    private boolean validSlot(Inventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }

    private int backSlot(int inventorySize) {
        return GuiSlots.bottomMiddleSlot(rows(inventorySize));
    }

    private int searchSlot(int inventorySize) {
        return backSlot(inventorySize) + 2;
    }

    private int clearSearchSlot(int inventorySize) {
        return searchSlot(inventorySize) + 1;
    }

    private int rows(int inventorySize) {
        return Math.max(1, inventorySize / GuiSlots.ROW_SIZE);
    }

    private ItemStack worldsButton(Player player) {
        List<String> disabledWorlds = plugin.getConfig().getStringList("settings.disabled-worlds");
        int disabledCount = disabledWorlds.size();
        int totalWorlds = WorldSelectionEntries.loadedAndConfigured(plugin, disabledWorlds).size();
        return EditorItemFactory.worlds(player, Math.max(0, totalWorlds - disabledCount), disabledCount, "Enabled");
    }

    private void applyCoreSelectorLayout(Inventory inventory, TriStateSelectionRequest request) {
        int bottom = GuiSlots.bottomRowStart(rows(inventory.getSize()));
        ItemStack search = inventory.getItem(bottom + CORE_SELECTOR_SEARCH_OFFSET);
        ItemStack clearSearch = inventory.getItem(bottom + CORE_SELECTOR_CLEAR_SEARCH_OFFSET);

        inventory.setItem(bottom + CORE_SELECTOR_SEARCH_OFFSET, EditorItemFactory.filler());
        if (!request.filter().isBlank()) {
            inventory.setItem(bottom + CORE_SELECTOR_CLEAR_SEARCH_OFFSET, EditorItemFactory.filler());
        }
        if (search != null) {
            inventory.setItem(searchSlot(inventory.getSize()), search);
        }
        if (!request.filter().isBlank() && clearSearch != null) {
            inventory.setItem(clearSearchSlot(inventory.getSize()), clearSearch);
        }
    }

    private int remapCoreSelectorClickSlot(int slot, TriStateSelectionHolder holder) {
        int size = GuiSlots.sizeForRows(holder.rows());
        int bottom = GuiSlots.bottomRowStart(holder.rows());
        if (slot == bottom + CORE_SELECTOR_SEARCH_OFFSET) {
            return INVALID_INT;
        }
        if (slot == searchSlot(size)) {
            return bottom + CORE_SELECTOR_SEARCH_OFFSET;
        }
        if (slot == clearSearchSlot(size) && !holder.request().filter().isBlank()) {
            return bottom + CORE_SELECTOR_CLEAR_SEARCH_OFFSET;
        }
        return slot;
    }

    private void open(Player player, Inventory inventory, EditorView view) {
        UUID uuid = player.getUniqueId();
        opening.add(uuid);
        chatPrompts.remove(uuid);
        views.put(uuid, view);
        try {
            player.openInventory(inventory);
        } finally {
            clearOpeningLater(uuid);
        }
    }

    private void clearOpeningLater(UUID uuid) {
        plugin.core().scheduler().runGlobal(() -> opening.remove(uuid));
    }

    private enum ViewType {
        MAIN,
        SETTINGS,
        WORLDS,
        BROWSER,
        EDIT,
        SOUND_SELECTOR,
        PARTICLE_SELECTOR,
        CONFIRM_DELETE
    }

    private enum PromptType {
        ADD_PRESET,
        SEARCH,
        WORLD_SEARCH,
        SOUND_SEARCH,
        PARTICLE_SEARCH,
        HORIZONTAL,
        VERTICAL,
        COOLDOWN,
        GLOBAL_COOLDOWN,
        FALL_IMMUNITY
    }

    private record EditorView(ViewType type, String presetId, int page, String search, String parentSearch) {

        private EditorView(ViewType type, String presetId, int page, String search) {
            this(type, presetId, page, search, search);
        }
    }

    private record Prompt(PromptType type, String presetId, int page, String search, String parentSearch) {

        private Prompt(PromptType type, String presetId, int page, String search) {
            this(type, presetId, page, search, search);
        }
    }

    private record PresetBrowserContext() {
    }
}
