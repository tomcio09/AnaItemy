package pl.anaheim.anaitemy.listeners;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KrewWampiraItem;

public class KrewWampiraListener implements Listener {

    private static final int MAX_STACK = 8;
    private final AnaItemy plugin;

    public KrewWampiraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj vanilla jedzenie beetroot soup.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (KrewWampiraItem.isKrewWampira(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * ✅ PPM = użyj krwi wampira.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!KrewWampiraItem.isKrewWampira(item)) return;

        event.setCancelled(true);

        // ✅ Ulecz do pełnego HP
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }

        // ✅ Anuluj efekt bloku widmo (jeśli aktywny)
        if (plugin.getBlokWidmoManager().isAffected(player)) {
            plugin.getBlokWidmoManager().forceRemoveEffect(player);

            // Ulecz ponownie po przywróceniu max health
            maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth(maxHealthAttr.getValue());
            }
        }

        // ✅ Dźwięk
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // ✅ Zużyj 1 sztukę
        // Beetroot soup nie stackuje się vanilla - obsługujemy ręcznie
        int currentAmount = getKrewCount(player.getInventory(), player.getInventory().getHeldItemSlot());

        if (currentAmount > 1) {
            // Mamy "ręczny stack" - zmniejsz NBT counter
            setKrewCount(item, currentAmount - 1);
            updateLoreCount(item, currentAmount - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * ✅ Obsługa ręcznego stackowania w inventory.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // ✅ Kliknięcie z krwią na krew = stackuj
        if (KrewWampiraItem.isKrewWampira(cursor) && KrewWampiraItem.isKrewWampira(current)) {
            event.setCancelled(true);

            int cursorCount = getKrewCount(cursor);
            int currentCount = getKrewCount(current);
            int total = cursorCount + currentCount;

            if (total <= MAX_STACK) {
                // Wszystko się mieści
                setKrewCount(current, total);
                updateLoreCount(current, total);
                event.getView().setCursor(null);
            } else {
                // Częściowe stackowanie
                setKrewCount(current, MAX_STACK);
                updateLoreCount(current, MAX_STACK);
                setKrewCount(cursor, total - MAX_STACK);
                updateLoreCount(cursor, total - MAX_STACK);
            }
            return;
        }

        // ✅ Shift-click z krwią
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (KrewWampiraItem.isKrewWampira(current) && current != null) {
                // Szukaj istniejącego stacka w docelowym inventory
                Inventory targetInv = event.getClickedInventory() == player.getInventory()
                        ? event.getView().getTopInventory()
                        : player.getInventory();

                for (int i = 0; i < targetInv.getSize(); i++) {
                    ItemStack slot = targetInv.getItem(i);
                    if (KrewWampiraItem.isKrewWampira(slot)) {
                        int slotCount = getKrewCount(slot);
                        int currentCount = getKrewCount(current);

                        if (slotCount < MAX_STACK) {
                            event.setCancelled(true);
                            int total = slotCount + currentCount;

                            if (total <= MAX_STACK) {
                                setKrewCount(slot, total);
                                updateLoreCount(slot, total);
                                event.setCurrentItem(null);
                            } else {
                                setKrewCount(slot, MAX_STACK);
                                updateLoreCount(slot, MAX_STACK);
                                setKrewCount(current, total - MAX_STACK);
                                updateLoreCount(current, total - MAX_STACK);
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    // ==================== RĘCZNE STACKOWANIE ====================

    private int getKrewCount(ItemStack item) {
        if (!KrewWampiraItem.isKrewWampira(item)) return 0;

        // Używamy amount itemu jako counter (mimo że vanilla nie pozwala >1 dla soup)
        // Ale to nie zadziała bo vanilla ogranicza
        // Zamiast tego używamy lore jako wskaźnik ilości

        // Sprawdź lore na końcu
        if (item.hasItemMeta() && item.getItemMeta().lore() != null) {
            var lore = item.getItemMeta().lore();
            if (!lore.isEmpty()) {
                String lastLine = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(lore.get(lore.size() - 1));
                if (lastLine.startsWith("Ilość: ")) {
                    try {
                        return Integer.parseInt(lastLine.replace("Ilość: ", "").trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return 1;
    }

    private int getKrewCount(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        return getKrewCount(item);
    }

    private void setKrewCount(ItemStack item, int count) {
        // Count jest przechowywany w lore
        // updateLoreCount zaktualizuje
    }

    private void updateLoreCount(ItemStack item, int count) {
        if (!KrewWampiraItem.isKrewWampira(item)) return;

        var meta = item.getItemMeta();
        var lore = meta.lore();
        if (lore == null) lore = new java.util.ArrayList<>();
        else lore = new java.util.ArrayList<>(lore);

        // Usuń starą linię ilości jeśli istnieje
        if (!lore.isEmpty()) {
            String lastLine = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(lore.get(lore.size() - 1));
            if (lastLine.startsWith("Ilość: ")) {
                lore.remove(lore.size() - 1);
            }
        }

        // Dodaj nową linię ilości (tylko jeśli >1)
        if (count > 1) {
            lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand()
                    .deserialize("&7Ilość: &f" + count)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }
}
