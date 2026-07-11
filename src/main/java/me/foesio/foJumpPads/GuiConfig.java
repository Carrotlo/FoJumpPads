package me.foesio.foJumpPads;

import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.message.FoMessageService;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class GuiConfig {

    private static final List<Integer> ENTRY_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    );

    private static final Map<String, String> TITLES = Map.of(
            "main", "FoJumpPads Editor",
            "settings", "Settings",
            "preset-browser", "Jump Pad Presets",
            "preset-edit", "Preset {preset}",
            "sound-selector", "Sound {preset}",
            "particle-selector", "Particle {preset}",
            "confirm-delete", "Delete {preset}"
    );

    private static final Map<String, Integer> SIZES = Map.of(
            "main", 27,
            "settings", 36,
            "preset-browser", 54,
            "preset-edit", 54,
            "sound-selector", 54,
            "particle-selector", 54,
            "confirm-delete", 27
    );

    private static final Map<String, Integer> SLOTS = Map.ofEntries(
            Map.entry("main.items.settings", 11),
            Map.entry("main.items.presets", 15),
            Map.entry("settings.items.global-cooldown", 10),
            Map.entry("settings.items.fall-immunity", 12),
            Map.entry("settings.items.drop-on-break", 14),
            Map.entry("settings.items.disabled-worlds", 16),
            Map.entry("settings.items.back", 31),
            Map.entry("preset-browser.items.add", 47),
            Map.entry("preset-browser.items.search", 51),
            Map.entry("preset-browser.items.clear-search", 52),
            Map.entry("preset-browser.items.previous", 45),
            Map.entry("preset-browser.items.next", 53),
            Map.entry("preset-browser.items.back", 49),
            Map.entry("preset-edit.items.enabled", 10),
            Map.entry("preset-edit.items.direction", 12),
            Map.entry("preset-edit.items.horizontal", 14),
            Map.entry("preset-edit.items.vertical", 16),
            Map.entry("preset-edit.items.cooldown", 20),
            Map.entry("preset-edit.items.sound", 22),
            Map.entry("preset-edit.items.particle", 24),
            Map.entry("preset-edit.items.path-lock", 28),
            Map.entry("preset-edit.items.item", 31),
            Map.entry("preset-edit.items.delete", 40),
            Map.entry("preset-edit.items.back", 49),
            Map.entry("sound-selector.items.previous", 45),
            Map.entry("sound-selector.items.next", 53),
            Map.entry("sound-selector.items.search", 51),
            Map.entry("sound-selector.items.clear-search", 52),
            Map.entry("sound-selector.items.back", 49),
            Map.entry("particle-selector.items.previous", 45),
            Map.entry("particle-selector.items.next", 53),
            Map.entry("particle-selector.items.search", 51),
            Map.entry("particle-selector.items.clear-search", 52),
            Map.entry("particle-selector.items.back", 49),
            Map.entry("confirm-delete.items.confirm", 11),
            Map.entry("confirm-delete.items.cancel", 15)
    );

    private static final Map<String, List<Integer>> INT_LISTS = Map.of(
            "preset-browser.entry-slots", ENTRY_SLOTS,
            "sound-selector.entry-slots", ENTRY_SLOTS,
            "particle-selector.entry-slots", ENTRY_SLOTS
    );

    private static final Map<String, String> STRINGS = Map.ofEntries(
            Map.entry("filler.material", "GRAY_STAINED_GLASS_PANE"),
            Map.entry("filler.name", " "),
            Map.entry("preset-browser.entry.name", "{theme}{preset}"),
            Map.entry("sound-selector.entry.material", "NOTE_BLOCK"),
            Map.entry("sound-selector.entry.selected-material", "LIME_DYE"),
            Map.entry("sound-selector.entry.name", "{theme}{value}"),
            Map.entry("particle-selector.entry.material", "FIREWORK_STAR"),
            Map.entry("particle-selector.entry.selected-material", "LIME_DYE"),
            Map.entry("particle-selector.entry.name", "{theme}{value}")
    );

    private static final Map<String, List<String>> STRING_LISTS = Map.of(
            "preset-browser.entry.lore", List.of(
                    "{white}Enabled: {enabled}",
                    "{white}Direction: {theme}{direction}",
                    "{white}Horizontal: {theme}{horizontal}",
                    "{white}Vertical: {theme}{vertical}",
                    "{muted}Click to edit."
            ),
            "sound-selector.entry.lore", List.of(
                    "{white}Current: {selected}",
                    "{muted}Click to select."
            ),
            "particle-selector.entry.lore", List.of(
                    "{white}Current: {selected}",
                    "{muted}Click to select."
            )
    );

    private static final Map<String, ItemTemplate> ITEMS = Map.ofEntries(
            Map.entry("main.items.settings", new ItemTemplate(Material.COMPARATOR, "{theme}Settings", List.of(
                    "{white}Edit general jump pad behavior."
            ))),
            Map.entry("main.items.presets", new ItemTemplate(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "{theme}Presets", List.of(
                    "{white}Browse and edit jump pad presets."
            ))),
            Map.entry("settings.items.global-cooldown", new ItemTemplate(Material.CLOCK, "{theme}Global Cooldown", List.of(
                    "{white}Current: {theme}{global_cooldown} ticks",
                    "{muted}Click to enter value."
            ))),
            Map.entry("settings.items.fall-immunity", new ItemTemplate(Material.FEATHER, "{theme}Fall Immunity", List.of(
                    "{white}Current: {theme}{fall_immunity} ticks",
                    "{muted}Click to enter value."
            ))),
            Map.entry("settings.items.drop-on-break", new ItemTemplate(Material.CHEST, "{theme}Drop Item On Break", List.of(
                    "{white}Current: {drop_on_break}",
                    "{muted}Click to toggle."
            ))),
            Map.entry("settings.items.disabled-worlds", new ItemTemplate(Material.GRASS_BLOCK, "{theme}Worlds", List.of(
                    "{white}Disabled: {theme}{disabled_worlds}",
                    "{muted}Click to select worlds."
            ))),
            Map.entry("preset-browser.items.add", new ItemTemplate(Material.LIME_DYE, "{good}Add Preset", List.of(
                    "{white}Create a new preset."
            ))),
            Map.entry("preset-edit.items.enabled", new ItemTemplate(Material.LEVER, "{theme}Enabled", List.of(
                    "{white}Current: {enabled}",
                    "{muted}Click to toggle."
            ))),
            Map.entry("preset-edit.items.direction", new ItemTemplate(Material.COMPASS, "{theme}Direction Mode", List.of(
                    "{white}Current: {theme}{direction}",
                    "{muted}Click to cycle."
            ))),
            Map.entry("preset-edit.items.horizontal", new ItemTemplate(Material.FEATHER, "{theme}Horizontal Launch", List.of(
                    "{white}Current: {theme}{horizontal}",
                    "{muted}Click to enter value."
            ))),
            Map.entry("preset-edit.items.vertical", new ItemTemplate(Material.WIND_CHARGE, "{theme}Vertical Launch", List.of(
                    "{white}Current: {theme}{vertical}",
                    "{muted}Click to enter value."
            ))),
            Map.entry("preset-edit.items.cooldown", new ItemTemplate(Material.CLOCK, "{theme}Cooldown", List.of(
                    "{white}Current: {theme}{cooldown} ticks",
                    "{muted}Click to enter value."
            ))),
            Map.entry("preset-edit.items.sound", new ItemTemplate(Material.NOTE_BLOCK, "{theme}Sound", List.of(
                    "{white}Current: {theme}{sound}",
                    "{muted}Click to choose."
            ))),
            Map.entry("preset-edit.items.particle", new ItemTemplate(Material.FIREWORK_STAR, "{theme}Particle", List.of(
                    "{white}Current: {theme}{particle}",
                    "{muted}Click to choose."
            ))),
            Map.entry("preset-edit.items.path-lock", new ItemTemplate(Material.IRON_BARS, "{theme}Path Lock", List.of(
                    "{white}Current: {path_lock}",
                    "{muted}Keep launched players on path until grounded."
            ))),
            Map.entry("preset-edit.items.item", new ItemTemplate(Material.CHEST, "{theme}Set Jump Pad Block", List.of(
                    "{white}Hold a placeable item on cursor.",
                    "{white}Sets type, name, lore, model data."
            ))),
            Map.entry("preset-edit.items.delete", new ItemTemplate(Material.LAVA_BUCKET, "{bad}Delete Preset", List.of(
                    "{white}Open confirmation."
            ))),
            Map.entry("confirm-delete.items.confirm", new ItemTemplate(Material.LIME_CONCRETE, "{good}Confirm", List.of(
                    "{white}Delete {theme}{preset}{white}."
            ))),
            Map.entry("confirm-delete.items.cancel", new ItemTemplate(Material.RED_CONCRETE, "{bad}Cancel", List.of(
                    "{white}Keep preset."
            )))
    );

    private final FoJumpPads plugin;
    private final FoMessageService messages;

    public GuiConfig(FoJumpPads plugin, FoMessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public String title(String path, Map<String, String> placeholders) {
        String template = TITLES.getOrDefault(path, path.replace('-', ' '));
        return GuiTitles.format(messages.renderTemplate(template, placeholders));
    }

    public int size(String path, int fallback) {
        int size = SIZES.getOrDefault(path, fallback);
        int rows = (size + GuiSlots.ROW_SIZE - 1) / GuiSlots.ROW_SIZE;
        return GuiSlots.sizeForRows(rows);
    }

    public int slot(String sectionPath, int fallback) {
        return SLOTS.getOrDefault(sectionPath, fallback);
    }

    public List<Integer> intList(String path) {
        return INT_LISTS.getOrDefault(path, List.of());
    }

    public String string(String path, String fallback) {
        return stringValue(path, fallback);
    }

    public List<String> stringList(String path) {
        List<String> value = STRING_LISTS.get(path);
        if (value != null) {
            return value;
        }

        ItemTemplate item = itemForField(path, "lore");
        return item == null ? List.of() : item.lore();
    }

    public Material material(String path, String fallback) {
        return material(stringValue(path, fallback));
    }

    public ItemStack item(String sectionPath, Map<String, String> placeholders) {
        ItemTemplate template = ITEMS.getOrDefault(sectionPath, new ItemTemplate(Material.BARRIER, Material.BARRIER.name(), List.of()));
        return buildItem(template.material(), template.name(), template.lore(), placeholders);
    }

    public ItemStack namedItem(Material material, String namePath, String lorePath, Map<String, String> placeholders) {
        return buildItem(
                material == null ? Material.BARRIER : material,
                stringValue(namePath, material == null ? Material.BARRIER.name() : material.name()),
                stringList(lorePath),
                placeholders
        );
    }

    public void fill(Inventory inventory) {
        ItemStack filler = buildItem(
                material(stringValue("filler.material", "GRAY_STAINED_GLASS_PANE")),
                stringValue("filler.name", " "),
                List.of(),
                Map.of()
        );
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack buildItem(Material material, String name, List<String> lore, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.renderTemplate(name, placeholders));
            meta.setLore(colorList(lore, placeholders));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String stringValue(String path, String fallback) {
        String value = STRINGS.get(path);
        if (value != null) {
            return value;
        }

        ItemTemplate nameItem = itemForField(path, "name");
        if (nameItem != null) {
            return nameItem.name();
        }

        ItemTemplate materialItem = itemForField(path, "material");
        if (materialItem != null) {
            return materialItem.material().name();
        }

        return fallback;
    }

    private ItemTemplate itemForField(String path, String field) {
        String suffix = "." + field;
        if (!path.endsWith(suffix)) {
            return null;
        }
        return ITEMS.get(path.substring(0, path.length() - suffix.length()));
    }

    private Material material(String value) {
        Material material = MaterialTypes.match(value);
        if (material == null || material == Material.AIR) {
            plugin.logWarn("Invalid hardcoded editor GUI material: " + value);
            return Material.BARRIER;
        }
        return material;
    }

    private List<String> colorList(List<String> values, Map<String, String> placeholders) {
        return values.stream()
                .map(value -> messages.renderTemplate(value, placeholders))
                .toList();
    }

    private record ItemTemplate(Material material, String name, List<String> lore) {
    }
}
