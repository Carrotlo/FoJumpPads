package me.foesio.foJumpPads.api;

/** Result of a persisted pad or preset mutation. */
public enum ChangeResult {
    CREATED, UPDATED, REMOVED, UNCHANGED, NOT_FOUND, ALREADY_EXISTS,
    INVALID_INPUT, MATERIAL_MISMATCH, STORAGE_ERROR, PLUGIN_DISABLED
}
