package me.foesio.foJumpPads;

import me.foesio.foJumpPads.api.JumpPadBeforeLaunchEvent;
import me.foesio.foJumpPads.api.JumpPadLaunchedEvent;
import me.foesio.foJumpPads.api.ChangeResult;
import me.foesio.foJumpPads.api.LaunchCause;
import me.foesio.foJumpPads.api.LaunchResult;
import me.foesio.foJumpPads.api.PadSnapshot;
import me.foesio.foJumpPads.api.PlayerStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JumpPadListener implements Listener {

    private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 0.001D;
    private static final double PATH_LOCK_EPSILON = 0.01D;
    private static final long GROUND_UNLOCK_GRACE_MILLIS = 350L;

    private final FoJumpPads plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallImmunity = new ConcurrentHashMap<>();
    private final Map<UUID, PathLock> pathLocks = new ConcurrentHashMap<>();
    private final Set<UUID> launchesInProgress = ConcurrentHashMap.newKeySet();

    public JumpPadListener(FoJumpPads plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String presetId = presetId(event.getItemInHand());
        if (presetId == null) {
            return;
        }
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) {
            plugin.messages().send(event.getPlayer(), "preset-not-found", Map.of("preset", presetId));
            event.setCancelled(true);
            return;
        }
        PadLocation location = PadLocation.from(event.getBlockPlaced());
        PadData data = new PadData(preset.id(), event.getBlockPlaced().getType());
        ChangeResult result = plugin.jumpPadsService().registerPad(location, data);
        if (result == ChangeResult.CREATED || result == ChangeResult.UPDATED || result == ChangeResult.UNCHANGED) {
            plugin.messages().send(event.getPlayer(), "pad-saved", Map.of("preset", preset.id()));
        } else {
            plugin.messages().send(event.getPlayer(), "pad-save-failed");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        PadLocation location = PadLocation.from(event.getBlock());
        PadData data = plugin.padData(location);
        if (data == null) {
            return;
        }
        ChangeResult result = plugin.jumpPadsService().removePad(location);
        if (result == ChangeResult.REMOVED) {
            dropPresetItem(event, data.presetId());
            plugin.messages().send(event.getPlayer(), "pad-removed", Map.of("preset", data.presetId()));
        } else {
            plugin.messages().send(event.getPlayer(), "pad-remove-failed");
            event.setCancelled(true);
        }
    }

    private void dropPresetItem(BlockBreakEvent event, String presetId) {
        if (!plugin.dropPresetItemOnBreak() || !event.isDropItems()) {
            return;
        }
        JumpPadPreset preset = plugin.presets().get(presetId);
        if (preset == null) {
            return;
        }
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                preset.createItem(plugin, event.getPlayer(), 1));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.isWorldDisabled(player.getWorld().getName())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!PadMaterialRules.isPressurePlate(block.getType())) {
            return;
        }
        PadData data = validPad(PadLocation.from(block), block, true);
        if (data == null) {
            return;
        }
        JumpPadPreset preset = plugin.presets().get(data.presetId());
        if (preset != null && preset.enabled()) {
            tryLaunch(player, preset, PadLocation.from(block));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        enforcePathLock(player);
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        if (plugin.isWorldDisabled(player.getWorld().getName())) {
            return;
        }
        PadMatch match = padAt(event.getTo(), false);
        if (match == null) {
            return;
        }
        JumpPadPreset preset = plugin.presets().get(match.data().presetId());
        if (preset != null && preset.enabled()) {
            tryLaunch(player, preset, match.location());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        PadData data = plugin.padData(PadLocation.from(event.getBlock()));
        if (data == null || !PadMaterialRules.isDirtLike(data.material())) {
            return;
        }
        if (PadMaterialRules.isDirtLike(event.getNewState().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        long until = fallImmunity.getOrDefault(player.getUniqueId(), 0L);
        if (until >= System.currentTimeMillis()) {
            event.setCancelled(true);
        } else {
            fallImmunity.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
        fallImmunity.remove(event.getPlayer().getUniqueId());
        pathLocks.remove(event.getPlayer().getUniqueId());
    }

    LaunchResult tryLaunch(Player player, JumpPadPreset preset, PadLocation padLocation) {
        UUID playerId = player.getUniqueId();
        if (!launchesInProgress.add(playerId)) {
            return LaunchResult.IN_PROGRESS;
        }
        try {
            long now = System.currentTimeMillis();
            if (cooldowns.getOrDefault(playerId, 0L) > now) {
                return LaunchResult.COOLDOWN;
            }
            Vector velocity = launchVelocity(player, preset);
            PadData data = padLocation == null ? null : plugin.padData(padLocation);
            PadSnapshot pad = data == null ? null : plugin.jumpPadsService().snapshot(padLocation, data);
            LaunchCause cause = padLocation == null ? LaunchCause.API : LaunchCause.PAD;
            JumpPadBeforeLaunchEvent before = new JumpPadBeforeLaunchEvent(player, preset.id(), pad, cause, velocity);
            Bukkit.getPluginManager().callEvent(before);
            if (before.isCancelled()) {
                return LaunchResult.CANCELLED;
            }
            launch(player, preset, velocity);
            now = System.currentTimeMillis();
            cooldowns.put(playerId, now + preset.cooldownTicks() * 50L);
            fallImmunity.put(playerId, now + preset.fallDamageImmunityTicks() * 50L);
            Bukkit.getPluginManager().callEvent(new JumpPadLaunchedEvent(player, preset.id(), pad, cause, velocity));
            return LaunchResult.LAUNCHED;
        } finally {
            launchesInProgress.remove(playerId);
        }
    }

    PlayerStatus status(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        return new PlayerStatus(Math.max(0, cooldowns.getOrDefault(id, 0L) - now),
                Math.max(0, fallImmunity.getOrDefault(id, 0L) - now));
    }

    private Vector launchVelocity(Player player, JumpPadPreset preset) {
        Vector direction = horizontalDirection(player, preset.directionMode());
        if (direction.lengthSquared() > MIN_HORIZONTAL_LENGTH_SQUARED) {
            direction.normalize().multiply(preset.horizontalVelocity());
        } else {
            direction = new Vector();
        }
        direction.setY(preset.verticalVelocity());
        return direction;
    }

    private void launch(Player player, JumpPadPreset preset, Vector velocity) {
        player.setVelocity(velocity.clone());
        startPathLock(player, preset, velocity);
        plugin.sounds().playWithPitchVariation(
                player,
                "jump-pad.presets." + preset.id(),
                0.04f
        );
        if (preset.particle() != null) {
            player.getWorld().spawnParticle(preset.particle(), player.getLocation().add(0, 0.2, 0), 18, 0.35, 0.2, 0.35, 0.01);
        }
    }

    private void startPathLock(Player player, JumpPadPreset preset, Vector launchVelocity) {
        if (!preset.pathLock()) {
            pathLocks.remove(player.getUniqueId());
            return;
        }
        Vector horizontal = launchVelocity.clone();
        horizontal.setY(0);
        if (horizontal.lengthSquared() < MIN_HORIZONTAL_LENGTH_SQUARED) {
            pathLocks.remove(player.getUniqueId());
            return;
        }
        long now = System.currentTimeMillis();
        pathLocks.put(player.getUniqueId(), new PathLock(horizontal, now, now + plugin.pathLockMaxMillis()));
    }

    private void enforcePathLock(Player player) {
        PathLock lock = pathLocks.get(player.getUniqueId());
        if (lock == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > lock.expiresAtMillis()
                || (now - lock.startedAtMillis() > GROUND_UNLOCK_GRACE_MILLIS && player.isOnGround() && player.getVelocity().getY() <= 0.0D)) {
            pathLocks.remove(player.getUniqueId());
            return;
        }
        Vector velocity = player.getVelocity();
        if (Math.abs(velocity.getX() - lock.horizontalVelocity().getX()) < PATH_LOCK_EPSILON
                && Math.abs(velocity.getZ() - lock.horizontalVelocity().getZ()) < PATH_LOCK_EPSILON) {
            return;
        }
        velocity.setX(lock.horizontalVelocity().getX());
        velocity.setZ(lock.horizontalVelocity().getZ());
        player.setVelocity(velocity);
    }

    private Vector horizontalDirection(Player player, DirectionMode mode) {
        if (mode == DirectionMode.PLAYER_FACING) {
            Vector direction = player.getLocation().getDirection();
            direction.setY(0);
            return direction;
        }
        Vector fixed = mode.fixedDirection();
        return fixed == null ? new Vector() : fixed;
    }

    private PadMatch padAt(Location location, boolean allowPressurePlate) {
        Block feet = location.getBlock();
        PadLocation feetLocation = PadLocation.from(feet);
        PadData feetData = validPad(feetLocation, feet, allowPressurePlate);
        if (feetData != null) {
            return new PadMatch(feetLocation, feetData);
        }
        Block below = feet.getRelative(BlockFace.DOWN);
        PadLocation belowLocation = PadLocation.from(below);
        PadData belowData = validPad(belowLocation, below, allowPressurePlate);
        return belowData == null ? null : new PadMatch(belowLocation, belowData);
    }

    private PadData validPad(PadLocation location, Block block, boolean allowPressurePlate) {
        PadData data = plugin.padData(location);
        if (data == null) {
            return null;
        }
        if (!PadMaterialRules.isCompatible(data.material(), block.getType())) {
            pruneStalePad(location);
            return null;
        }
        if (!allowPressurePlate && PadMaterialRules.isPressurePlate(data.material())) {
            return null;
        }
        return data;
    }

    private void pruneStalePad(PadLocation location) {
        plugin.jumpPadsService().removePad(location);
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private String presetId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(plugin.presetKey(), PersistentDataType.STRING);
    }

    private record PathLock(Vector horizontalVelocity, long startedAtMillis, long expiresAtMillis) {
    }

    private record PadMatch(PadLocation location, PadData data) { }
}
