package me.foesio.foJumpPads.api;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.List;

/** Current loaded preset values. Changes after reload do not alter an earlier snapshot. */
public record PresetSnapshot(String id, boolean enabled, Material material, String displayName,
                             List<String> lore, double horizontalVelocity, double verticalVelocity,
                             int cooldownTicks, int fallDamageImmunityTicks, String directionMode,
                             boolean pathLock, Particle particle, Integer customModelData) {
    public PresetSnapshot { lore = List.copyOf(lore); }
}
