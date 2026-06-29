package pl.anaheim.anaitemy.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KrewWampiraItem;

import java.util.HashMap;
import java.util.Map;

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

        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            player.setHealth(maxHealthAttr.getValue());
        }

        if (plugin.getBlokWidmoManager().isAffected(player)) {
            plugin.getBlokWidmoManager().forceRemoveEffect(player);

            maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth(maxHealthAttr.getValue());
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        int amount = item.getAmount();
        if (amount > 1) {
            item.setAmount(amount - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.updateInventory();
    }

    /**
     * ✅ Limit stacka do 8 przy normalnych kliknięciach.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        boolean cursorBlood = KrewWampiraItem.isKrewWampira(cursor);
        boolean currentBlood = KrewWampiraItem.isKrewWampira(current);

        if (!cursorBlood && !currentBlood) return;

        // Łączenie stacków
        if (cursorBlood && currentBlood) {
            int total = cursor.getAmount() + current.getAmount();

            if (event.getClick() == ClickType.LEFT) {
                event.setCancelled(true);

                if (total <= KrewWampiraItem.MAX_STACK) {
                    current.setAmount(total);
                    event.setCurrentItem(current);
                    event.getView().setCursor(null);
                } else {
                    current.setAmount(KrewWampiraItem.MAX_STACK);
                    cursor.setAmount(total - KrewWampiraItem.MAX_STACK);
                    event.setCurrentItem(current);
                    event.getView().setCursor(cursor);
                }
                return;
            }

            if (event.getClick() == ClickType.RIGHT) {
                event.setCancelled(true);

                if (current.getAmount() < KrewWampiraItem.MAX_STACK) {
                    current.setAmount(current.getAmount() + 1);
                    cursor.setAmount(cursor.getAmount() - 1);

                    if (cursor.getAmount() <= 0) {
                        event.getView().setCursor(null);
                    } else {
                        event.getView().setCursor(cursor);
                    }
                    event.setCurrentItem(current);
                }
                return;
            }
        }

        // Shift click - pilnuj limitu 8
        if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) && currentBlood) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> normalizeBloodStacks(event.getWhoClicked().getInventory()), 1L);
        }

        // Double click - też pilnuj limitu
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> normalizeBloodStacks(event.getWhoClicked().getInventory()), 1L);
        }
    }

    /**
     * ✅ Drag po pustych slotach — rozdziela jak vanilla, ale dla naszej krwi.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        ItemStack oldCursor = event.getOldCursor();
        if (!KrewWampiraItem.isKrewWampira(oldCursor)) return;

        // Działamy tylko dla dragów po inventory gracza / GUI
        int size = event.getView().getTopInventory().getSize() + event.getView().getBottomInventory().getSize();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 0 || rawSlot >= size) return;
        }

        // Jeśli vanilla rozdzieli na amount > 1 w wielu slotach, to jest OK.
        // Po ticku tylko normalizujemy, by nic nie przekroczyło 8.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            normalizeBloodStacks(event.getWhoClicked().getInventory());
            if (event.getWhoClicked() instanceof Player player) {
                player.updateInventory();
            }
        }, 1L);
    }

    /**
     * ✅ Łączy i przycina stacki krwi do max 8.
     */
    private void normalizeBloodStacks(Inventory inventory) {
        int totalBlood = 0;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (KrewWampiraItem.isKrewWampira(item)) {
                totalBlood += item.getAmount();
                inventory.setItem(i, null);
            }
        }

        while (totalBlood > 0) {
            int give = Math.min(KrewWampiraItem.MAX_STACK, totalBlood);
            inventory.addItem(KrewWampiraItem.create(give));
            totalBlood -= give;
        }
    }
}
