package me.foesio.foJumpPads;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public final class PadMaterialRules {

    private static final Set<Material> DIRT_LIKE = EnumSet.of(
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.PODZOL,
            Material.MYCELIUM,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT
    );

    private PadMaterialRules() {
    }

    public static boolean isCompatible(Material expected, Material actual) {
        return expected == actual || (DIRT_LIKE.contains(expected) && DIRT_LIKE.contains(actual));
    }

    public static boolean isDirtLike(Material material) {
        return DIRT_LIKE.contains(material);
    }

    public static boolean isPressurePlate(Material material) {
        return material != null && material.name().endsWith("_PRESSURE_PLATE");
    }
}
