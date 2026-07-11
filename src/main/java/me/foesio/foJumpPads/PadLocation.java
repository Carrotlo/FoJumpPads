package me.foesio.foJumpPads;

import org.bukkit.block.Block;

public record PadLocation(String world, int x, int y, int z) {

    public static PadLocation from(Block block) {
        return new PadLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
