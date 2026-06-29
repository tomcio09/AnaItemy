package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import pl.anaheim.anaitemy.items.SakiewkaDropu;

public class GUIListener implements Listener {

    private final AnaItemy plugin;

    public GUIListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component inventoryTitle = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(inventoryTitle);

        if (!EventoweGUI.isGUITitle(plainTitle)) return;

        // ✅ Anuluj WSZYSTKO w tym GUI
        event.setCancelled(true);

        int slot = event.getRawSlot();

        // ✅ Kliknięcia w inventory gracza (poniżej GUI) — ignoruj
        if (slot >= 54 || slot < 0) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        // ✅ Filtr (hopper) — slot 49
        if (slot == 49 && clickedItem.getType() == Material.HOPPER) {
            EventoweGUI.GUIState state = EventoweGUI.getState(player);
            EventoweGUI.Category currentCategory = (state != null) ? state.getCategory() : EventoweGUI.Category.ALL;
            EventoweGUI.Category nextCategory = EventoweGUI.getNextCategory(currentCategory);
            EventoweGUI.open(player, plugin, nextCategory, 1);
            return;
        }

        // ✅ Następna strona (lime dye) — slot 53
        if (slot == 53 && clickedItem.getType() == Material.LIME_DYE) {
            EventoweGUI.GUIState state = EventoweGUI.getState(player);
            if (state == null) return;
            EventoweGUI.open(player, plugin, state.getCategory(), state.getPage() + 1);
            return;
        }

        // ✅ Poprzednia strona (red dye) — slot 45
        if (slot == 45 && clickedItem.getType() == Material.RED_DYE) {
            EventoweGUI.GUIState state = EventoweGUI.getState(player);
            if (state == null) return;
            EventoweGUI.open(player, plugin, state.getCategory(), state.getPage() - 1);
            return;
        }

        // ✅ Sloty 36-53 (przedostatni i ostatni rząd) — nie dawaj itemów
        if (slot >= 36) return;

        // ✅ Kliknięcie na item (sloty 0-35) — daj graczowi
        if (SakiewkaDropu.isSakiewka(clickedItem)) {
            ItemStack newSakiewka = SakiewkaDropu.create();
            giveItem(player, newSakiewka);
            return;
        }

        giveItem(player, clickedItem.clone());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        if (EventoweGUI.isGUITitle(plainTitle)) {
            EventoweGUI.removeState(player);
        }
    }

    private void giveItem(Player player, ItemStack item) {
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
