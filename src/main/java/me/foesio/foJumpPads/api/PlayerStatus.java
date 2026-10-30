package me.foesio.foJumpPads.api;

/** Remaining time in milliseconds, captured when queried. */
public record PlayerStatus(long cooldownRemainingMillis, long fallImmunityRemainingMillis) { }
