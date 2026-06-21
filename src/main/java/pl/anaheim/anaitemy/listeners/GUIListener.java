package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;

public class GUIListener implements Listener {

    private final AnaItemy plugin;

    public GUIListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Sprawdź tytuł GUI
        Component inventoryTitle = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(inventoryTitle);

        if (!plainTitle.equals(EventoweGUI.GUI_TITLE_PLAIN)) return;

        // Anuluj domyślne klikanie
        event.setCancelled(true);

        // Sprawdź czy kliknięto na item
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        // Spróbuj dać graczowi item
        giveItem(player, clickedItem.clone());
    }

    private void giveItem(Player player, ItemStack item) {
        // Sprawdź czy gracz ma miejsce
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(color("&cNie masz miejsca w ekwipunku!"));
            return;
        }
        player.getInventory().addItem(item);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
