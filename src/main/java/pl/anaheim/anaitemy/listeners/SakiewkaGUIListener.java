package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.SakiewkaDropu;
import pl.anaheim.anaitemy.utils.SakiewkaData;

import java.time.Duration;
import java.util.*;

public class SakiewkaGUIListener implements Listener {

    private final AnaItemy plugin;
    private final Map<UUID, ItemStack> openSakiewki = new HashMap<>();

    public SakiewkaGUIListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Otwieranie sakiewki PPM.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSakiewkaOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && 
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!SakiewkaDropu.isSakiewka(item)) return;

        event.setCancelled(true);

        // ✅ Sprawdź czy gracz nie jest w trakcie teleportacji
        if (player.hasMetadata("sakiewka_teleporting")) {
            return;
        }

        // Wiadomość na chacie
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&aOtwieranie worka..."));

        // Dźwięk
        player.playSound(player.getLocation(), Sound.BLOCK_SHULKER_BOX_OPEN, 
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Otwórz GUI
        openGUI(player, item);
    }

    /**
     * Otwiera GUI sakiewki.
     */
    private void openGUI(Player player, ItemStack sakiewka) {
        // Zapisz którą sakiewkę otworzył (w razie update)
        openSakiewki.put(player.getUniqueId(), sakiewka);

        Component title = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&8Sakiewka dropu");

        Inventory gui = Bukkit.createInventory(null, 54, title);

        // ✅ Załaduj itemy z sakiewki (sloty 0-44)
        List<ItemStack> items = SakiewkaData.loadItems(sakiewka);
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            ItemStack item = items.get(i);
            if (item != null && !item.getType().isAir()) {
                gui.setItem(i, item);
            }
        }

        // ✅ Ostatni rząd (45-53) - kontrolki
        // Slot 49 - Barrier (zamknij)
        gui.setItem(49, createBarrier());

        // Slot 50 - Lime Dye (wypłać wszystko)
        gui.setItem(50, createPayoutButton());

        player.openInventory(gui);
    }

    /**
     * Tworzy barrier (zamknij).
     */
    private ItemStack createBarrier() {
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        
        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&cZamknij")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        
        barrier.setItemMeta(meta);
        return barrier;
    }

    /**
     * Tworzy lime_dye (wypłać wszystko).
     */
    private ItemStack createPayoutButton() {
        ItemStack button = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = button.getItemMeta();
        
        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&aWypłać wszystko")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(" &8» &7Kliknij, aby wypłacić wszystkie")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        lore.add(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(" &8» &7przedmioty z sakiewki")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        
        button.setItemMeta(meta);
        return button;
    }

    /**
     * ✅ Obsługa kliknięć w GUI.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Component viewTitle = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(viewTitle);

        if (!plainTitle.equals("Sakiewka dropu")) return;

        event.setCancelled(true); // Zablokuj wszystkie akcje domyślne

        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType().isAir()) return;

        // ✅ Slot 49 - Zamknij
        if (slot == 49 && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        // ✅ Slot 50 - Wypłać wszystko
        if (slot == 50 && clicked.getType() == Material.LIME_DYE) {
            handlePayoutAll(player);
            return;
        }

        // ✅ Sloty 0-44 - Wypłacanie pojedynczego itemu
        if (slot >= 0 && slot < 45) {
            handlePayoutSingle(player, slot, clicked);
        }
    }

    /**
     * Wypłaca pojedynczy item z sakiewki.
     */
    private void handlePayoutSingle(Player player, int slot, ItemStack item) {
        // Sprawdź czy gracz ma miejsce w eq
        if (player.getInventory().firstEmpty() == -1) {
            player.closeInventory();
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&cNie posiadasz miejsca w ekwipunku"));
            
            player.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&cBrak miejsca w ekwipunku!"),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
            ));
            
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        ItemStack sakiewka = openSakiewki.get(player.getUniqueId());
        if (sakiewka == null) return;

        // Usuń item z sakiewki
        ItemStack removed = SakiewkaData.removeItem(sakiewka, slot);
        if (removed == null) return;

        // Dodaj do eq gracza
        player.getInventory().addItem(removed);
        
        // Dźwięk
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Odśwież GUI (usuń item ze slotu)
        player.getOpenInventory().getTopInventory().setItem(slot, null);
    }

    /**
     * Wypłaca wszystkie itemy z sakiewki.
     */
    private void handlePayoutAll(Player player) {
        ItemStack sakiewka = openSakiewki.get(player.getUniqueId());
        if (sakiewka == null) return;

        // ✅ Sprawdź zablokowane regiony (nie można wypłacać wszystkiego na spawnie)
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getSakiewkaBlockedRegionsNoPayout();
        if (plugin.getWorldGuardManager().isInBlockedRegion(player.getLocation(), blockedRegions)) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&cNie możesz wypłacić wszystkich przedmiotów w tym regionie!"));
            return;
        }

        // Pobierz wszystkie itemy
        List<ItemStack> items = SakiewkaData.removeAllItems(sakiewka);
        
        // Zamknij GUI
        player.closeInventory();

        // Wiadomości
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&aWypłacanie przedmiotów..."));
        
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&aWypłacanie przedmiotów..."),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
        ));

        // Dźwięk
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 
                SoundCategory.PLAYERS, 1.0f, 1.5f);

        // Wypłać pod nogi (do eq lub na ziemię)
        Location dropLocation = player.getLocation();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            
            // Spróbuj dodać do eq
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            
            // Jeśli się nie zmieściło - wyrzuć pod nogi
            if (!overflow.isEmpty()) {
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(dropLocation, leftover);
                }
            }
        }
    }

    /**
     * ✅ Cleanup przy zamykaniu GUI.
     */
    @EventHandler
    public void onGUIClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Component viewTitle = event.getView().title();
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(viewTitle);

        if (plainTitle.equals("Sakiewka dropu")) {
            openSakiewki.remove(player.getUniqueId());
        }
    }
}
