package pl.anaheim.anaitemy.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import org.jetbrains.annotations.NotNull;

public class ItemyEventoweCommand implements CommandExecutor {

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

        EventoweGUI.open(player, plugin);
        return true;
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
