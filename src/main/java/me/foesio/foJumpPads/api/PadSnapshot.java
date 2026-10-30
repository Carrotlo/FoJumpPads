package me.foesio.foJumpPads.api;

import org.bukkit.Material;

/** Immutable saved registration; active is effective config state, not a live block check. */
public record PadSnapshot(String world, int x, int y, int z, String presetId, Material material, boolean active) { }
