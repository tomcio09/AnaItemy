package pl.anaheim.anaitemy.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import pl.anaheim.anaitemy.items.Excalibur;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

        // /itemyeventowe kills <liczba>
        if (args.length == 2 && args[0].equalsIgnoreCase("kills")) {
            handleKillsCommand(player, args[1]);
            return true;
        }

        // /itemyeventowe - otwórz GUI
        if (args.length == 0) {
            EventoweGUI.open(player, plugin);
            return true;
        }

        player.sendMessage(color("&cUżycie: /itemyeventowe [kills <liczba>]"));
        return true;
    }

    private void handleKillsCommand(Player player, String killsStr) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!Excalibur.isExcalibur(item)) {
            player.sendMessage(color("&cMusisz trzymać Excalibur w ręku!"));
            return;
        }

        int kills;
        try {
            kills = Integer.parseInt(killsStr);
        } catch (NumberFormatException e) {
            player.sendMessage(color("&cPodaj poprawną liczbę!"));
            return;
        }

        int maxKills = plugin.getConfig().getInt("excalibur.max-kills", 100);

        if (kills < 0) {
            player.sendMessage(color("&cLiczba zabójstw nie może być ujemna!"));
            return;
        }

        if (kills > maxKills) {
            kills = maxKills;
        }

        // Edytuj tylko konkretne linie w lore
        ItemStack updated = Excalibur.updateKills(item, kills, maxKills);
        player.getInventory().setItemInMainHand(updated);

        player.sendMessage(color("&aUstawiono zabójstwa Excalibura na: &f" + kills));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                  @NotNull Command command,
                                                  @NotNull String label,
                                                  @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("kills");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("kills")) {
            completions.add("0");
            completions.add("50");
            completions.add("100");
        }

        return completions;
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
