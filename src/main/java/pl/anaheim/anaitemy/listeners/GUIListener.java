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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import pl.anaheim.anaitemy.items.SakiewkaDropu;

public class GUIListener implements Listener {

    private final AnaItemy plugin;

    public GUIListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        if (!EventoweGUI.isGUITitle(plainTitle)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        // ✅ Filtr (hopper) — slot 49
        if (slot == 49 && clickedItem.getType() == Material.HOPPER) {
            EventoweGUI.GUIState state = EventoweGUI.getState(player);
            EventoweGUI.Category currentCat = (state != null) ? state.getCategory() : EventoweGUI.Category.ALL;
            EventoweGUI.Category nextCat = EventoweGUI.getNextCategory(currentCat);

            // ✅ Zamknij i otwórz ponownie z opóźnieniem
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    EventoweGUI.open(player, plugin, nextCat, 1);
                }
            }, 1L);
            return;
        }

        // ✅ Następna strona — slot 53
        if (slot == 53 && clickedItem.getType() == Material.LIME_DYE) {
            EventoweGUI.GUIState state = EventoweGUI.getState(player);
            if (state == null) return;
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    EventoweGUI.open(player, plugin, state.getCategory(), state.getPage() + 1);
                }
            }, 1L);
            return;
        }

        // ✅ Poprzednia strona — slot 45
        if (slot == 45 && clickedItem.getType() == Material.RED_DYE) {
            EventoweGUI.GUIState state = EventoweGUI.getState(player);
            if (state == null) return;
            player.closeInventory();
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    EventoweGUI.open(player, plugin, state.getCategory(), state.getPage() - 1);
                }
            }, 1L);
            return;
        }

        // ✅ Sloty 36+ — nie dawaj itemów
        if (slot >= 36) return;

        // ✅ Daj item graczowi
        if (SakiewkaDropu.isSakiewka(clickedItem)) {
            giveItem(player, SakiewkaDropu.create());
            return;
        }

        giveItem(player, clickedItem.clone());
    }

    /**
     * ✅ Blokuj drag w naszym GUI.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        if (EventoweGUI.isGUITitle(plainTitle)) {
            event.setCancelled(true);
        }
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
