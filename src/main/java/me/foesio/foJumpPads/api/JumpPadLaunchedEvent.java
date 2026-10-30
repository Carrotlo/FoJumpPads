package me.foesio.foJumpPads.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;

import java.util.Optional;

/** Fired after velocity, cooldown, and fall immunity are applied and effects are attempted. */
public final class JumpPadLaunchedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String presetId;
    private final PadSnapshot pad;
    private final LaunchCause cause;
    private final Vector velocity;

    public JumpPadLaunchedEvent(Player player, String presetId, PadSnapshot pad, LaunchCause cause, Vector velocity) {
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
    public Vector getVelocity() { return velocity.clone(); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
