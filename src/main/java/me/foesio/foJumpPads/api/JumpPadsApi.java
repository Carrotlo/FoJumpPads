package me.foesio.foJumpPads.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

/** FoJumpPads integration service. Obtain it from Bukkit's ServicesManager after FoJumpPads enables. */
public interface JumpPadsApi {
    /** Stored registration; it may be inactive if its world or preset is disabled. */
    Optional<PadSnapshot> findPad(Block block);
    List<PadSnapshot> listPads();
    Optional<PresetSnapshot> findPreset(String id);
    List<PresetSnapshot> listPresets();
    /** Returns a new tagged item; amount must be 1..64. Viewer may be null for plain fallback text. */
    Optional<ItemStack> createPresetItem(String id, Player viewer, int amount);
    /** Returns a known preset ID from this plugin's tag, even when the preset is disabled. */
    Optional<String> identifyPresetItem(ItemStack item);
    /** Registers an already placed matching block. Does not place or consume a block. */
    ChangeResult registerPad(Block block, String presetId);
    /** Removes registration only; does not change the block or drop an item. */
    ChangeResult removePad(Block block);
    /** Launches through the same cooldown, world, preset, and event rules as a physical pad. */
    LaunchResult launchPlayer(Player player, String presetId);
    ChangeResult createPreset(PresetSpec spec);
    ChangeResult updatePreset(PresetSpec spec);
    /** Change the preset sound key while preserving configured playback volume and pitch. */
    ChangeResult setPresetSound(String id, String soundKey);
    /** Save a custom block item as the preset's item template. */
    ChangeResult setPresetItem(String id, ItemStack item);
    ChangeResult deletePreset(String id);
    /** Remaining transient time; zero means inactive. State is cleared when the player quits. */
    PlayerStatus playerStatus(Player player);
}
