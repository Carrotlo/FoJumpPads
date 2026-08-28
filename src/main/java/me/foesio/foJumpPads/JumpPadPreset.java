package me.foesio.foJumpPads;

import me.foesio.core.message.FoMessageService;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.number.NumberFormatters;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record JumpPadPreset(
        String id,
        boolean enabled,
        Material material,
        String displayName,
        List<String> lore,
        double horizontalVelocity,
        double verticalVelocity,
        int cooldownTicks,
        int fallDamageImmunityTicks,
        DirectionMode directionMode,
        boolean pathLock,
        String sound,
        Particle particle,
        Integer customModelData
) {

    public static JumpPadPreset from(FoJumpPads plugin, String id, ConfigurationSection section) {
        Material material = parseMaterial(plugin, section.getString("material", "LIGHT_WEIGHTED_PRESSURE_PLATE"));
        String sound = plugin.presetSoundName(id, section.getString("sound", "ENTITY_FIREWORK_ROCKET_LAUNCH"));
        Particle particle = parseEnum(plugin, Particle.class, section.getString("particle", "CLOUD"), "particle");
        Integer customModelData = section.isInt("custom-model-data") ? section.getInt("custom-model-data") : null;
        return new JumpPadPreset(
                id.toLowerCase(Locale.ROOT),
                section.getBoolean("enabled", true),
                material,
                section.getString("display-name", "{theme}" + id),
                section.getStringList("lore"),
                section.getDouble("velocity-horizontal", 1.0D),
                section.getDouble("velocity-vertical", 1.0D),
                Math.max(0, section.getInt("cooldown-ticks", plugin.getConfig().getInt("settings.global-cooldown-ticks", 10))),
                Math.max(0, section.getInt("fall-damage-immunity-ticks", plugin.getConfig().getInt("settings.fall-damage-immunity-ticks", 80))),
                DirectionMode.parse(section.getString("direction-mode", DirectionMode.PLAYER_FACING.name())),
                section.getBoolean("path-lock", false),
                sound,
                particle,
                customModelData
        );
    }

    public ItemStack createItem(FoJumpPads plugin, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = placeholders(plugin.messages());
            meta.setDisplayName(plugin.messages().renderTemplate(displayName, placeholders));
            meta.setLore(lore.stream()
                    .map(line -> plugin.messages().renderTemplate(line, placeholders))
                    .toList());
            meta.getPersistentDataContainer().set(plugin.presetKey(), PersistentDataType.STRING, id);
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public Map<String, String> placeholders(FoMessageService messages) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("preset", id);
        placeholders.put("horizontal", format(horizontalVelocity));
        placeholders.put("vertical", format(verticalVelocity));
        placeholders.put("cooldown", String.valueOf(cooldownTicks));
        placeholders.put("direction", directionMode.displayName());
        placeholders.put("path_lock", status(messages, pathLock));
        placeholders.put("sound", sound == null || sound.isBlank() ? "none" : sound);
        placeholders.put("particle", particle == null ? "none" : particle.name());
        placeholders.put("enabled", status(messages, enabled));
        return placeholders;
    }

    public static String format(double value) {
        return NumberFormatters.decimal(BigDecimal.valueOf(value));
    }

    private static Material parseMaterial(FoJumpPads plugin, String value) {
        Material material = MaterialTypes.match(value);
        if (material == null || material == Material.AIR || !material.isItem() || !material.isBlock()) {
            plugin.logWarn("Invalid preset material '" + value + "'. Using LIGHT_WEIGHTED_PRESSURE_PLATE.");
            return Material.LIGHT_WEIGHTED_PRESSURE_PLATE;
        }
        return material;
    }

    private static <T extends Enum<T>> T parseEnum(FoJumpPads plugin, Class<T> type, String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.logWarn("Invalid preset " + label + " '" + value + "'.");
            return null;
        }
    }

    private static String status(FoMessageService messages, boolean enabled) {
        return messages.renderTemplate(enabled ? "{good}Enabled" : "{bad}Disabled", Map.of());
    }
}
