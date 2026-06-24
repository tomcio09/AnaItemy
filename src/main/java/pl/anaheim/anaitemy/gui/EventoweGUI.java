package pl.anaheim.anaitemy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.*;

public class EventoweGUI {

    public static final String GUI_TITLE_PLAIN = "Itemy Eventowe";

    public static void open(Player player, AnaItemy plugin) {
        int maxKills = plugin.getItemsConfig().getExcaliburMaxKills();

        Component title = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&8&lItemy Eventowe")
                .decoration(TextDecoration.ITALIC, false);

        Inventory gui = Bukkit.createInventory(null, 54, title);

        gui.setItem(0, TotemUlaskawienia.create());
        gui.setItem(1, Excalibur.create(maxKills));
        gui.setItem(2, HydroKlatka.create());
        gui.setItem(3, RozdzkailuzjonistyItem.create());
        gui.setItem(4, WedkaNielotaItem.create());
        gui.setItem(5, SakiewkaDropu.create());
        gui.setItem(6, WzmocnianaElytra.create());
        gui.setItem(7, BlokWidmoItem.create());
        gui.setItem(8, SiekieraGrinchaItem.create());
        gui.setItem(9, HydroTrojzabItem.create());
        gui.setItem(10, CudownaLatarniaItem.create());

        player.openInventory(gui);
    }
}
