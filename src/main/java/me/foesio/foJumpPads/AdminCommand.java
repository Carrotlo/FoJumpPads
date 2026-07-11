package me.foesio.foJumpPads;

import me.foesio.core.command.FoAdminArgument;
import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.reload.FoReloadResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand {

    private static final int LIST_PAGE_SIZE = 8;
    private static final String GIVE_USAGE = "give <player> <preset> [amount]";
    private static final String TELEPORT_USAGE = "teleport <list-index>";
    private static final String REMOVE_USAGE = "remove <radius|world|all> [value]";
    private static final String REMOVE_WORLD_USAGE = "remove world <world>";
    private static final String REMOVE_RADIUS_USAGE = "remove radius <blocks>";
    private static final List<String> REMOVE_MODES = List.of("radius", "world", "all");
    private static final List<String> AMOUNT_SUGGESTIONS = List.of("1", "8", "16", "32", "64");
    private static final List<String> RADIUS_SUGGESTIONS = List.of("5", "10", "25", "50", "100");
    private static final FoAdminArgument<Player> ONLINE_PLAYER = FoAdminArguments.onlinePlayer();

    private final FoJumpPads plugin;

    public AdminCommand(FoJumpPads plugin) {
        this.plugin = plugin;
    }

    public void register() {
        FoAdminCommand.builder(plugin, plugin.messages())
                .commandName("fojumppadsadmin")
                .permission(FoJumpPads.ADMIN_PERMISSION)
                .adminMessages(adminMessages())
                .versionCommand(false)
                .addSubcommand(versionSubcommand())
                .addSubcommand(reloadSubcommand())
                .addSubcommand(giveSubcommand())
                .addSubcommand(listSubcommand())
                .addSubcommand(teleportSubcommand())
                .addSubcommand(removeSubcommand())
                .addSubcommand(editorSubcommand())
                .register();
    }

    private FoAdminMessages adminMessages() {
        return FoAdminMessages.builder()
                .generalNoPermission("no-permission", "{prefix}{bad}No permission.")
                .generalPlayerOnly("only-player", "{prefix}{bad}Player only.")
                .usage("admin-usage", "{prefix}{bad}Usage: {muted}/{label} {usage}")
                .commandMissing("admin-command-missing", "{prefix}{bad}Admin command missing from plugin.yml: {theme}{command}")
                .commandFailed("admin-command-failed", "{prefix}{bad}Command failed. {muted}{error}")
                .build();
    }

    private FoAdminSubcommand versionSubcommand() {
        return FoAdminSubcommand.builder("version", context -> {
            sendVersion(context.sender());
            return true;
        }).usage("version").build();
    }

    private FoAdminSubcommand reloadSubcommand() {
        return FoAdminSubcommand.builder("reload", context -> {
            reload(context.sender());
            return true;
        }).usage("reload").build();
    }

    private FoAdminSubcommand giveSubcommand() {
        return FoAdminSubcommand.builder("give", context -> {
            give(context.sender(), context.label(), context.args());
            return true;
        }).usage(GIVE_USAGE).tabCompleter(this::giveTabs).build();
    }

    private FoAdminSubcommand listSubcommand() {
        return FoAdminSubcommand.builder("list", context -> {
            listPads(context.sender(), context.args());
            return true;
        }).usage("list [page]").build();
    }

    private FoAdminSubcommand teleportSubcommand() {
        return FoAdminSubcommand.builder("teleport", context -> {
            teleport(context.sender(), context.label(), context.args());
            return true;
        }).aliases("tp").usage(TELEPORT_USAGE).playerOnly().tabCompleter(this::teleportTabs).build();
    }

    private FoAdminSubcommand removeSubcommand() {
        return FoAdminSubcommand.builder("remove", context -> {
            remove(context.sender(), context.label(), context.args());
            return true;
        }).usage(REMOVE_USAGE).tabCompleter(this::removeTabs).build();
    }

    private FoAdminSubcommand editorSubcommand() {
        return FoAdminSubcommand.builder("editor", context -> {
            openEditor(context.sender());
            return true;
        }).usage("editor").playerOnly().build();
    }

    private void sendVersion(CommandSender sender) {
        plugin.messages().send(sender, "version-author", Map.of("author", "Carrotio"));
        plugin.messages().send(sender, "version-plugin", Map.of("version", plugin.getDescription().getVersion()));
        plugin.updates().checkAndSendVersion(sender);
    }

    private void reload(CommandSender sender) {
        FoReloadResult result = plugin.reloadAll();
        if (result.successful()) {
            plugin.messages().send(sender, "reload-success");
            return;
        }
        plugin.messages().send(sender, "reload-failed", Map.of(
                "step", result.failedStep(),
                "error", result.errorMessage()
        ));
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            syntax(sender, label, GIVE_USAGE);
            return;
        }
        Player target = ONLINE_PLAYER.parse(args[1]).orElse(null);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
            return;
        }
        JumpPadPreset preset = plugin.presets().get(args[2]);
        if (preset == null) {
            plugin.messages().send(sender, "preset-not-found", Map.of("preset", args[2]));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            amount = parseInt(args[3], -1);
        }
        if (amount < 1 || amount > 64) {
            plugin.messages().send(sender, "amount-invalid");
            return;
        }
        ItemStack item = preset.createItem(plugin, amount);
        plugin.core().inventoryDeposits().deposit(target, item, target.getLocation(), OverflowPolicy.DROP_OVERFLOW);
        Map<String, String> placeholders = Map.of(
                "player", target.getName(),
                "preset", preset.id(),
                "amount", String.valueOf(amount)
        );
        plugin.messages().send(sender, "give-success", placeholders);
        if (!sender.equals(target)) {
            plugin.messages().send(target, "give-received", placeholders);
        }
    }

    private void listPads(CommandSender sender, String[] args) {
        List<PadEntry> pads = sortedPads();
        if (pads.isEmpty()) {
            plugin.messages().send(sender, "pad-list-empty");
            return;
        }
        int pages = Math.max(1, (int) Math.ceil(pads.size() / (double) LIST_PAGE_SIZE));
        int page = Math.max(1, Math.min(parseInt(argument(args, 1), 1), pages));
        plugin.messages().send(sender, "pad-list-header", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages),
                "total", String.valueOf(pads.size())
        ));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(pads.size(), start + LIST_PAGE_SIZE);
        for (int index = start; index < end; index++) {
            PadEntry entry = pads.get(index);
            plugin.messages().send(sender, "pad-list-entry", Map.of(
                    "id", String.valueOf(index + 1),
                    "preset", entry.data().presetId(),
                    "world", entry.location().world(),
                    "x", String.valueOf(entry.location().x()),
                    "y", String.valueOf(entry.location().y()),
                    "z", String.valueOf(entry.location().z())
            ));
        }
    }

    private void teleport(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        if (args.length < 2) {
            syntax(sender, label, TELEPORT_USAGE);
            return;
        }
        PadEntry entry = padByIndex(args[1]);
        if (entry == null) {
            plugin.messages().send(sender, "pad-id-invalid", Map.of("id", args[1]));
            return;
        }
        World world = Bukkit.getWorld(entry.location().world());
        if (world == null || !isBlockStillPad(world, entry)) {
            prunePad(entry.location());
            plugin.messages().send(sender, "pad-id-invalid", Map.of("id", args[1]));
            return;
        }
        Location target = new Location(world,
                entry.location().x() + 0.5D,
                entry.location().y() + 1.0D,
                entry.location().z() + 0.5D,
                player.getLocation().getYaw(),
                player.getLocation().getPitch());
        player.teleport(target);
        plugin.messages().send(player, "pad-teleport-success", Map.of(
                "id", args[1],
                "preset", entry.data().presetId(),
                "world", entry.location().world(),
                "x", String.valueOf(entry.location().x()),
                "y", String.valueOf(entry.location().y()),
                "z", String.valueOf(entry.location().z())
        ));
    }

    private void remove(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            syntax(sender, label, REMOVE_USAGE);
            return;
        }
        List<PadEntry> pads = sortedPads();
        String mode = args[1].toLowerCase(Locale.ROOT);
        RemoveSelection selection;
        switch (mode) {
            case "all" -> selection = new RemoveSelection(pads, "all");
            case "world" -> selection = removeWorldMatches(sender, label, args, pads);
            case "radius" -> selection = removeRadiusMatches(sender, label, args, pads);
            default -> {
                syntax(sender, label, REMOVE_USAGE);
                return;
            }
        }
        if (selection == null) {
            return;
        }
        int removed = 0;
        for (PadEntry entry : selection.matches()) {
            if (prunePad(entry.location())) {
                removed++;
            }
        }
        plugin.messages().send(sender, "pad-remove-success", Map.of(
                "count", String.valueOf(removed),
                "scope", selection.scope()
        ));
    }

    private RemoveSelection removeWorldMatches(CommandSender sender, String label, String[] args, List<PadEntry> pads) {
        String worldName;
        if (args.length >= 3) {
            worldName = args[2];
        } else if (sender instanceof Player player) {
            worldName = player.getWorld().getName();
        } else {
            syntax(sender, label, REMOVE_WORLD_USAGE);
            return null;
        }
        List<PadEntry> matches = pads.stream()
                .filter(entry -> entry.location().world().equalsIgnoreCase(worldName))
                .toList();
        return new RemoveSelection(matches, "world \"" + worldName + "\"");
    }

    private RemoveSelection removeRadiusMatches(CommandSender sender, String label, String[] args, List<PadEntry> pads) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return null;
        }
        if (args.length < 3) {
            syntax(sender, label, REMOVE_RADIUS_USAGE);
            return null;
        }
        double radius = parseDouble(args[2], -1D);
        if (radius < 1D || radius > 500D) {
            plugin.messages().send(sender, "invalid-input", Map.of("hint", "Use a radius from 1 to 500."));
            return null;
        }
        double radiusSquared = radius * radius;
        Location center = player.getLocation();
        List<PadEntry> matches = pads.stream()
                .filter(entry -> entry.location().world().equals(player.getWorld().getName()))
                .filter(entry -> distanceSquared(center, entry.location()) <= radiusSquared)
                .toList();
        return new RemoveSelection(matches, "radius " + JumpPadPreset.format(radius));
    }

    private void openEditor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        plugin.editor().openMain(player);
        plugin.messages().send(player, "editor-opened");
    }

    private List<String> giveTabs(FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return ONLINE_PLAYER.complete(context.arg(1));
        }
        if (context.args().length == 3) {
            return matches(context.arg(2), plugin.presets().ids());
        }
        if (context.args().length == 4) {
            return matches(context.arg(3), AMOUNT_SUGGESTIONS);
        }
        return Collections.emptyList();
    }

    private List<String> teleportTabs(FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return matches(context.arg(1), sortedPads().stream()
                    .map(entry -> String.valueOf(entry.index()))
                    .toList());
        }
        return Collections.emptyList();
    }

    private List<String> removeTabs(FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return matches(context.arg(1), REMOVE_MODES);
        }
        if (context.args().length == 3 && context.arg(1).equalsIgnoreCase("world")) {
            return matches(context.arg(2), Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        if (context.args().length == 3 && context.arg(1).equalsIgnoreCase("radius")) {
            return matches(context.arg(2), RADIUS_SUGGESTIONS);
        }
        return Collections.emptyList();
    }

    private void syntax(CommandSender sender, String label, String usage) {
        plugin.messages().send(sender, "syntax", Map.of("usage", "/" + label + " " + usage));
    }

    private List<PadEntry> sortedPads() {
        List<PadEntry> pads = new ArrayList<>();
        int[] index = {1};
        plugin.padSnapshot().entrySet().stream()
                .sorted(Map.Entry.<PadLocation, PadData>comparingByKey(Comparator
                        .comparing(PadLocation::world)
                        .thenComparingInt(PadLocation::x)
                        .thenComparingInt(PadLocation::y)
                        .thenComparingInt(PadLocation::z)))
                .forEach(entry -> pads.add(new PadEntry(index[0]++, entry.getKey(), entry.getValue())));
        return pads;
    }

    private PadEntry padByIndex(String input) {
        int index = parseInt(input, -1);
        if (index < 1) {
            return null;
        }
        List<PadEntry> pads = sortedPads();
        return index > pads.size() ? null : pads.get(index - 1);
    }

    private boolean prunePad(PadLocation location) {
        try {
            plugin.storage().deletePad(location);
            plugin.removePad(location);
            return true;
        } catch (SQLException exception) {
            plugin.logWarn("Could not remove jump pad: " + exception.getMessage());
            return false;
        }
    }

    private boolean isBlockStillPad(World world, PadEntry entry) {
        return PadMaterialRules.isCompatible(entry.data().material(),
                world.getBlockAt(entry.location().x(), entry.location().y(), entry.location().z()).getType());
    }

    private double distanceSquared(Location center, PadLocation pad) {
        double dx = center.getX() - (pad.x() + 0.5D);
        double dy = center.getY() - (pad.y() + 0.5D);
        double dz = center.getZ() - (pad.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private List<String> matches(String token, List<String> options) {
        List<String> matches = new ArrayList<>(FoAdminArguments.completeOptions(options, token));
        Collections.sort(matches);
        return matches;
    }

    private static String argument(String[] args, int index) {
        return index >= 0 && index < args.length ? args[index] : "";
    }

    private static int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double parseDouble(String input, double fallback) {
        try {
            double value = Double.parseDouble(input);
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record PadEntry(int index, PadLocation location, PadData data) {
    }

    private record RemoveSelection(List<PadEntry> matches, String scope) {
    }
}
