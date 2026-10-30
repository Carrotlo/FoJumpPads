![FoJumpPads cover](docs/assets/cover.png)

# FoJumpPads

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fojumppads) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fojumppads/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Configurable launch pads with reusable presets and administrator tools.

*Developed by Carrotio.*

## Features

- Creates configurable jump-pad presets with launch behavior and effects.
- Lets administrators give, list, teleport to, and remove pads.
- Provides an in-game editor for pad presets and settings.
- Includes configurable safety and landing behavior.
- Keeps messages and interaction sounds configurable.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fojumppads). Check the [versions page](https://modrinth.com/plugin/fojumppads/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** None

## Screenshots

![Open the jump-pad administrator editor.](docs/assets/screenshots/fojumppads-admin-editor.png)

*Open the jump-pad administrator editor.*

![Manage reusable pad presets.](docs/assets/screenshots/fojumppads-pad-presets.png)

*Manage reusable pad presets.*

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fojumppadsadmin <version|reload|give|list|teleport|remove|editor>` — FoJumpPads admin command. Aliases: `/jumppadsadmin`.

### Permissions

- `fojumppads.admin` — Allows FoJumpPads admin commands and update notices. Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

This plugin does not register public PlaceholderAPI placeholders.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `settings`, `presets`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

FoJumpPads 1.6 exposes `me.foesio.foJumpPads.api.JumpPadsApi` through Bukkit's `ServicesManager`. The service is registered after pads and presets load and unregistered on disable. Obtain it after your plugin enables, and reacquire it if FoJumpPads is disabled and enabled again. A cached service returns disabled results after disable.

Compile against the matching FoJumpPads jar as `provided` (Maven) or `compileOnly` (Gradle). Do not shade FoJumpPads or FoPluginCore into your plugin. There is no published API-only artifact in this checkout; use the matching provider jar. For a `plugin.yml` consumer using provider classes, declare `depend: [FoJumpPads]`. An optional consumer can use `softdepend: [FoJumpPads]` and load direct API references only in a separate integration class after checking that FoJumpPads is present and enabled. Paper-plugin consumers must declare a server dependency with `join-classpath: true` if referencing provider classes.

After installing the matching FoJumpPads jar in your local or internal Maven repository, a Maven consumer can use its current project coordinates:

```xml
<dependency>
    <groupId>me.foesio</groupId>
    <artifactId>FoJumpPads</artifactId>
    <version>1.6</version>
    <scope>provided</scope>
</dependency>
```

Its `plugin.yml` should contain `depend: [FoJumpPads]` for direct use, or `softdepend: [FoJumpPads]` with a guarded integration class.

```java
RegisteredServiceProvider<JumpPadsApi> registration =
        Bukkit.getServicesManager().getRegistration(JumpPadsApi.class);
if (registration != null) {
    JumpPadsApi pads = registration.getProvider();
    pads.findPad(block).ifPresent(pad -> logger.info("Preset: " + pad.presetId()));
    pads.createPresetItem("default", player, 1).ifPresent(player.getInventory()::addItem);
}
```

Imports: `org.bukkit.Bukkit`, `org.bukkit.plugin.RegisteredServiceProvider`, and `me.foesio.foJumpPads.api.JumpPadsApi`. A `plugin.yml` consumer with a hard dependency can use these imports directly. For an optional dependency, keep this code in a class that is never loaded unless FoJumpPads is enabled.

### Service contract

| Operation | Meaning |
| --- | --- |
| `findPad`, `listPads` | Stored registrations. `active` reflects loaded preset/world settings, not whether the world block still matches. Startup database loading is not a new registration. Lists and values are immutable snapshots. |
| `findPreset`, `listPresets` | Current loaded preset values as immutable snapshots. Presets may change on reload. |
| `createPresetItem`, `identifyPresetItem` | Create a fresh tagged item (amount 1–64), or read a known current preset ID from its tag. Unknown IDs and untagged items return empty. A disabled preset can still have an item. The returned item belongs to the caller. |
| `registerPad`, `removePad` | Persist an already placed matching block or remove its registration. They do not place/break blocks, consume/drop items, or send messages. `UNCHANGED` and `NOT_FOUND` are no-ops. A successful result means SQLite completed and the in-memory map changed. |
| `launchPlayer` | Trigger normal launch rules without a block. Disabled worlds/presets, cooldown, cancellation, an in-progress launch, offline players, and missing presets return distinct `LaunchResult` values. No item or block changes. |
| `createPreset`, `updatePreset`, `deletePreset` | Write `config.yml` and reload the preset registry. `PresetSpec` covers launch and standard visual values; update preserves owner-customized sound and serialized item data. Use `PresetSpec.from(snapshot)` to start an update. A new ID gets the default launch sound; reusing a deleted ID can retain its existing sound entry. Delete does not delete placed pads or sound entries, matching the editor behavior. |
| `setPresetSound`, `setPresetItem` | Complete preset edits through the same sound and custom-item paths used by the editor. Sound changes preserve owner volume/pitch. Item changes preserve other launch settings. |
| `playerStatus` | Remaining cooldown and fall-immunity milliseconds, sampled at call time. Zero means inactive. Status is transient and cleared on quit; it is not persisted. |

Call world/block methods on the block's owning server execution context and player methods on the player's owning context. Preset mutation changes shared config and must be serialized with other config edits. Do not call these methods from an arbitrary async worker. On traditional Bukkit this normally means the primary server thread; regionized servers require the appropriate region/player scheduler. The service does not schedule or block for callers.

Invalid input and no-op mutation results do not write. A storage/reload error returns `STORAGE_ERROR`; inspect current state before retrying because a reload error can follow a successful config or sound write.

### Events

`JumpPadBeforeLaunchEvent` is cancellable. It runs after the pad/world/preset/cooldown checks and before velocity, effects, cooldown, or immunity change. `JumpPadLaunchedEvent` runs after velocity, cooldown, and immunity change and after effects are attempted. Both include player, preset ID, launch cause (`PAD` or `API`), a defensive velocity copy, and an optional pad snapshot (empty for an API launch). A cancelled launch fires no success event and changes no launch state. Cooldown, disabled, and invalid attempts fire neither event. Natural pressure-plate and movement launches both use this path.

Events run in the player's current execution context. Their asynchronous flag reflects whether dispatch is on Bukkit's primary thread; on regionized servers, a flagged event may still be running on the player's owning region. Listeners should use the player's scheduler for later work and must not assume access to unrelated regions.

```java
@EventHandler
public void onBeforeLaunch(JumpPadBeforeLaunchEvent event) {
    if (myArenaBlocksLaunch(event.getPlayer())) event.setCancelled(true);
}

@EventHandler
public void onLaunched(JumpPadLaunchedEvent event) {
    recordJump(event.getPlayer().getUniqueId(), event.getPresetId());
}
```

The events are plugin-owned Bukkit events. They have no FoPluginCore types in public signatures. Runtime loading and behavior should be tested against the server versions used by a consumer; compiling against the jar alone does not prove live behavior.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `JumpPadsApi`

```java
Optional<PadSnapshot> findPad(block.Block);
List<PadSnapshot> listPads();
Optional<PresetSnapshot> findPreset(String);
List<PresetSnapshot> listPresets();
Optional<ItemStack> createPresetItem(String, Player, int);
Optional<String> identifyPresetItem(ItemStack);
ChangeResult registerPad(block.Block, String);
ChangeResult removePad(block.Block);
LaunchResult launchPlayer(Player, String);
ChangeResult createPreset(PresetSpec);
ChangeResult updatePreset(PresetSpec);
ChangeResult setPresetSound(String, String);
ChangeResult setPresetItem(String, ItemStack);
ChangeResult deletePreset(String);
PlayerStatus playerStatus(Player);
```

**Other public contract types:** `ChangeResult`, `LaunchCause`, `LaunchResult`, `PadSnapshot`, `PlayerStatus`, `PresetSnapshot`, `PresetSpec`.

### Complete Bukkit event index

- `JumpPadBeforeLaunchEvent`.
- `JumpPadLaunchedEvent`.

</details>
