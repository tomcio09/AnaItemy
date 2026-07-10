package pl.anaheim.anaitemy.commands;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class EnderchestCommand implements CommandExecutor, TabCompleter {

    private final AnaItemy plugin;

    public EnderchestCommand(AnaItemy plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda jest tylko dla graczy!");
            return true;
        }

        String cmdName = label.toLowerCase();

        // /ec — otwórz swój enderchest
        if (cmdName.equals("ec") && args.length == 0) {
            if (!player.hasPermission("anaitemy.ec")) {
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&cNie masz uprawnień!"));
                return true;
            }
            plugin.getEnderchestManager().openEnderchest(player);
            return true;
        }

        // /ecsee <nick> — podgląd EC innego gracza
        if (cmdName.equals("ecsee") && args.length == 1) {
            if (!player.hasPermission("anaitemy.ecsee")) {
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&cNie masz uprawnień!"));
                return true;
            }

            String targetName = args[0];

            // Szukaj online
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                plugin.getEnderchestManager().openEnderchest(player, target.getUniqueId(), target.getName());
                return true;
            }

            // Szukaj offline
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            if (offlineTarget.hasPlayedBefore()) {
                plugin.getEnderchestManager().openEnderchest(player, offlineTarget.getUniqueId(), targetName);
                return true;
            }

            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&cGracz &f" + targetName + " &cnie istnieje!"));
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("ecsee") && args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
