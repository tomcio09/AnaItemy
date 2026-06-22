package pl.anaheim.anaitemy.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import pl.anaheim.anaitemy.items.Excalibur;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ItemyEventoweCommand implements CommandExecutor, TabCompleter {

    private final AnaItemy plugin;

    public ItemyEventoweCommand(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy!");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(color("&cNie masz uprawnień do tej komendy!"));
            return true;
        }

        // /itemyeventowe
        if (args.length == 0) {
            EventoweGUI.open(player, plugin);
            return true;
        }

        // /itemyeventowe kills <liczba>
        if (args.length == 2 && args[0].equalsIgnoreCase("kills")) {
            handleKillsCommand(player, args[1]);
            return true;
        }

        // /itemyeventowe cooldown reset <nick>
        if (args.length == 3
                && args[0].equalsIgnoreCase("cooldown")
                && args[1].equalsIgnoreCase("reset")) {
            handleCooldownReset(sender, args[2]);
            return true;
        }

        player.sendMessage(color("&cUżycie:"));
        player.sendMessage(color("&7/itemyeventowe &8- &fotwiera GUI"));
        player.sendMessage(color("&7/itemyeventowe kills <liczba> &8- &fustawia kille Excalibura"));
        player.sendMessage(color("&7/itemyeventowe cooldown reset <nick> &8- &fresetuje cooldowny gracza"));
        return true;
    }

    // ==================== KILLS COMMAND ====================

    private void handleKillsCommand(Player player, String killsStr) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!Excalibur.isExcalibur(item)) {
            player.sendMessage(color(
                    plugin.getItemsConfig().getExcaliburMessageNotHolding()
            ));
            return;
        }

        int kills;
        try {
            kills = Integer.parseInt(killsStr);
        } catch (NumberFormatException e) {
            player.sendMessage(color(
                    plugin.getItemsConfig().getExcaliburMessageInvalidNumber()
            ));
            return;
        }

        if (kills < 0) {
            player.sendMessage(color(
                    plugin.getItemsConfig().getExcaliburMessageNegativeNumber()
            ));
            return;
        }

        int maxKills = plugin.getItemsConfig().getExcaliburMaxKills();
        if (kills > maxKills) kills = maxKills;

        ItemStack updated = Excalibur.updateKills(item, kills, maxKills);
        player.getInventory().setItemInMainHand(updated);

        String msg = plugin.getItemsConfig().getExcaliburMessageKillsSet()
                .replace("{kills}", String.valueOf(kills));
        player.sendMessage(color(msg));
    }

    // ==================== COOLDOWN RESET COMMAND ====================

    private void handleCooldownReset(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage(color("&cGracz &f" + targetName + " &cnie jest online!"));
            return;
        }

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Reset cooldownu Hydro Klatki
        manager.resetCooldown(target);

        // Reset wizualnego cooldownu (szare tło na slocie)
        target.setCooldown(org.bukkit.Material.BLAZE_ROD, 0);

        // Zatrzymaj action bar display
        manager.stopCooldownDisplay(target);

        // Wiadomości
        sender.sendMessage(color("&aZresetowano cooldowny gracza &f" + target.getName() + "&a!"));
        target.sendMessage(color("&aTwoje cooldowny zostały zresetowane przez &f" + sender.getName() + "&a!"));

        plugin.getLogger().info("[AnaItemy] " + sender.getName() +
                " zresetowal cooldowny gracza " + target.getName());
    }

    // ==================== TAB COMPLETE ====================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("kills");
            completions.add("cooldown");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("kills")) {
                completions.add("<liczba>");
            } else if (args[0].equalsIgnoreCase("cooldown")) {
                completions.add("reset");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("cooldown") && args[1].equalsIgnoreCase("reset")) {
                // Podpowiedz nicki graczy online
                completions.addAll(
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList())
                );
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
