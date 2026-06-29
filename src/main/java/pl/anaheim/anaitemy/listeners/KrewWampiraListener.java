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

    private final AnaItemy plugin;

    public KrewWampiraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj vanilla jedzenie.
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

        // ✅ Anuluj efekt bloku widmo
        if (plugin.getBlokWidmoManager().isAffected(player)) {
            plugin.getBlokWidmoManager().forceRemoveEffect(player);

            maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth(maxHealthAttr.getValue());
            }
        }

        // ✅ Dźwięk
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // ✅ Zużyj 1 sztukę
        int count = KrewWampiraItem.getCount(item);
        if (count > 1) {
            KrewWampiraItem.setCount(item, count - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * ✅ Ręczne stackowanie w inventory.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // ✅ Normalne kliknięcie: krew na krew = stackuj
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            if (KrewWampiraItem.isKrewWampira(cursor) && KrewWampiraItem.isKrewWampira(current)) {
                event.setCancelled(true);

                int cursorCount = KrewWampiraItem.getCount(cursor);
                int currentCount = KrewWampiraItem.getCount(current);

                if (event.getClick() == ClickType.RIGHT) {
                    // PPM = dodaj 1
                    if (currentCount < KrewWampiraItem.MAX_STACK) {
                        KrewWampiraItem.setCount(current, currentCount + 1);
                        if (cursorCount > 1) {
                            KrewWampiraItem.setCount(cursor, cursorCount - 1);
                        } else {
                            event.getView().setCursor(null);
                        }
                    }
                } else {
                    // LPM = dodaj wszystko
                    int total = cursorCount + currentCount;
                    if (total <= KrewWampiraItem.MAX_STACK) {
                        KrewWampiraItem.setCount(current, total);
                        event.getView().setCursor(null);
                    } else {
                        KrewWampiraItem.setCount(current, KrewWampiraItem.MAX_STACK);
                        KrewWampiraItem.setCount(cursor, total - KrewWampiraItem.MAX_STACK);
                    }
                }
                return;
            }

            // ✅ PPM na pustym slocie z krwią = rozdziel
            if (event.getClick() == ClickType.RIGHT
                    && KrewWampiraItem.isKrewWampira(cursor)
                    && (current == null || current.getType().isAir())) {

                int cursorCount = KrewWampiraItem.getCount(cursor);
                if (cursorCount > 1) {
                    event.setCancelled(true);

                    ItemStack single = KrewWampiraItem.create(1);
                    if (event.getClickedInventory() != null) {
                        event.getClickedInventory().setItem(event.getSlot(), single);
                    }
                    KrewWampiraItem.setCount(cursor, cursorCount - 1);
                }
                return;
            }
        }

        // ✅ Shift-click: szukaj stacka do połączenia
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (!KrewWampiraItem.isKrewWampira(current)) return;

            int currentCount = KrewWampiraItem.getCount(current);

            Inventory targetInv = (event.getClickedInventory() == player.getInventory())
                    ? event.getView().getTopInventory()
                    : player.getInventory();

            // Szukaj istniejącego stacka
            for (int i = 0; i < targetInv.getSize(); i++) {
                ItemStack slot = targetInv.getItem(i);
                if (!KrewWampiraItem.isKrewWampira(slot)) continue;

                int slotCount = KrewWampiraItem.getCount(slot);
                if (slotCount >= KrewWampiraItem.MAX_STACK) continue;

                event.setCancelled(true);

                int total = slotCount + currentCount;
                if (total <= KrewWampiraItem.MAX_STACK) {
                    KrewWampiraItem.setCount(slot, total);
                    event.setCurrentItem(null);
                } else {
                    KrewWampiraItem.setCount(slot, KrewWampiraItem.MAX_STACK);
                    KrewWampiraItem.setCount(current, total - KrewWampiraItem.MAX_STACK);
                }
                return;
            }

            // Nie znaleziono stacka - pozwól vanilla przenieść do pustego slotu
        }

        // ✅ Double click - zbieranie itemów
        if (event.getClick() == ClickType.DOUBLE_CLICK && KrewWampiraItem.isKrewWampira(cursor)) {
            event.setCancelled(true);

            int cursorCount = KrewWampiraItem.getCount(cursor);
            if (cursorCount >= KrewWampiraItem.MAX_STACK) return;

            // Szukaj w inventory gracza
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                if (cursorCount >= KrewWampiraItem.MAX_STACK) break;

                ItemStack slot = player.getInventory().getItem(i);
                if (!KrewWampiraItem.isKrewWampira(slot)) continue;

                int slotCount = KrewWampiraItem.getCount(slot);
                int canTake = KrewWampiraItem.MAX_STACK - cursorCount;

                if (slotCount <= canTake) {
                    cursorCount += slotCount;
                    player.getInventory().setItem(i, null);
                } else {
                    cursorCount = KrewWampiraItem.MAX_STACK;
                    KrewWampiraItem.setCount(slot, slotCount - canTake);
                }
            }

            KrewWampiraItem.setCount(cursor, cursorCount);
        }
    }
}
