package me.foesio.foJumpPads.api;

/** Result of an attempted launch. */
public enum LaunchResult {
    LAUNCHED, COOLDOWN, IN_PROGRESS, PRESET_NOT_FOUND, PRESET_DISABLED, WORLD_DISABLED,
    CANCELLED, PLAYER_OFFLINE, PLUGIN_DISABLED
}
