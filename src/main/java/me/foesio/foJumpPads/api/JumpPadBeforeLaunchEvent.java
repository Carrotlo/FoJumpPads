package me.foesio.foJumpPads.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;

import java.util.Optional;

/** Fired on the player's execution context after eligibility checks, before any launch effects. */
public final class JumpPadBeforeLaunchEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String presetId;
    private final PadSnapshot pad;
    private final LaunchCause cause;
    private final Vector velocity;
    private boolean cancelled;

    public JumpPadBeforeLaunchEvent(Player player, String presetId, PadSnapshot pad, LaunchCause cause, Vector velocity) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.presetId = presetId;
        this.pad = pad;
        this.cause = cause;
        this.velocity = velocity.clone();
    }

    public Player getPlayer() { return player; }
    public String getPresetId() { return presetId; }
    /** Empty for a programmatic launch. */
    public Optional<PadSnapshot> getPad() { return Optional.ofNullable(pad); }
    public LaunchCause getCause() { return cause; }
    /** Defensive copy of the proposed velocity. */
    public Vector getVelocity() { return velocity.clone(); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
