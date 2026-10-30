package me.foesio.foJumpPads.api;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.List;

/** Values editable through the API. Existing sound and custom item metadata are preserved on update. */
public record PresetSpec(String id, boolean enabled, Material material, String displayName,
                         List<String> lore, double horizontalVelocity, double verticalVelocity,
                         int cooldownTicks, int fallDamageImmunityTicks, String directionMode,
                         boolean pathLock, Particle particle, Integer customModelData) {
    public PresetSpec { lore = lore == null ? null : List.copyOf(lore); }

    /** Start an update from loaded values while preserving owner sound and custom item metadata. */
    public static PresetSpec from(PresetSnapshot snapshot) {
        return new PresetSpec(snapshot.id(), snapshot.enabled(), snapshot.material(), snapshot.displayName(),
                snapshot.lore(), snapshot.horizontalVelocity(), snapshot.verticalVelocity(),
                snapshot.cooldownTicks(), snapshot.fallDamageImmunityTicks(), snapshot.directionMode(),
                snapshot.pathLock(), snapshot.particle(), snapshot.customModelData());
    }
}
