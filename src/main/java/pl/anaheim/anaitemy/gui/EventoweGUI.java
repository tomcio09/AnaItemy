package pl.anaheim.anaitemy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.Excalibur;
import pl.anaheim.anaitemy.items.HydroKlatka;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;

public class EventoweGUI {

    public static final String GUI_TITLE_PLAIN = "Itemy Eventowe";

    public static void open(Player player, AnaItemy plugin) {
        int maxKills = plugin.getConfig().getInt("excalibur.max-kills", 100);

        Component title = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&8&lItemy Eventowe")
                .decoration(TextDecoration.ITALIC, false);

        // 6 rzędów = 54 sloty
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Slot 0 - Totem Ułaskawienia
        gui.setItem(0, TotemUlaskawienia.create());

        // Slot 1 - Excalibur
        gui.setItem(1, Excalibur.create(maxKills));

        // Slot 2 - Hydro Klatka
        gui.setItem(2, HydroKlatka.create());

        player.openInventory(gui);
    }
}
