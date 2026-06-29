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

import java.util.Map;

public class KrewWampiraListener implements Listener {

    private final AnaItemy plugin;

    public KrewWampiraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (KrewWampiraItem.isKrewWampira(event.getItem())) {
            event.setCancelled(true);
        }
    }

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        boolean cursorBlood = KrewWampiraItem.isKrewWampira(cursor);
        boolean currentBlood = KrewWampiraItem.isKrewWampira(current);

        if (!cursorBlood && !currentBlood) return;

        // ✅ LPM: krew na krew = połącz
        if (cursorBlood && currentBlood && event.getClick() == ClickType.LEFT) {
            event.setCancelled(true);
            int total = cursor.getAmount() + current.getAmount();

            if (total <= KrewWampiraItem.MAX_STACK) {
                current.setAmount(total);
                event.getView().setCursor(null);
            } else {
                current.setAmount(KrewWampiraItem.MAX_STACK);
                cursor.setAmount(total - KrewWampiraItem.MAX_STACK);
            }
            return;
        }

        // ✅ PPM: krew na krew = dodaj 1
        if (cursorBlood && currentBlood && event.getClick() == ClickType.RIGHT) {
            event.setCancelled(true);
            if (current.getAmount() < KrewWampiraItem.MAX_STACK) {
                current.setAmount(current.getAmount() + 1);
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                } else {
                    event.getView().setCursor(null);
                }
            }
            return;
        }

        // ✅ PPM: krew na pusty slot = postaw 1
        if (cursorBlood && (current == null || current.getType().isAir()) && event.getClick() == ClickType.RIGHT) {
            if (cursor.getAmount() > 1) {
                event.setCancelled(true);
                ItemStack single = KrewWampiraItem.create(1);
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), single);
                }
                cursor.setAmount(cursor.getAmount() - 1);
            }
            return;
        }

        // ✅ Shift click - pilnuj limitu 8
        if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) && currentBlood) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                capBloodStacks(player.getInventory());
                player.updateInventory();
            }, 1L);
        }

        // ✅ Double click - zbieraj do max 8
        if (event.getClick() == ClickType.DOUBLE_CLICK && cursorBlood) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Po vanilla double click cap do max 8
                ItemStack newCursor = event.getView().getCursor();
                if (KrewWampiraItem.isKrewWampira(newCursor) && newCursor.getAmount() > KrewWampiraItem.MAX_STACK) {
                    int overflow = newCursor.getAmount() - KrewWampiraItem.MAX_STACK;
                    newCursor.setAmount(KrewWampiraItem.MAX_STACK);

                    ItemStack overflowItem = KrewWampiraItem.create(overflow);
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(overflowItem);
                    for (ItemStack left : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                }
                player.updateInventory();
            }, 1L);
        }
    }

    /**
     * ✅ Drag — pozwól vanilla rozłożyć, potem tylko cap do 8.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (!KrewWampiraItem.isKrewWampira(oldCursor)) return;

        // ✅ Pozwól vanilla rozłożyć — potem po ticku sprawdź limity
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            capBloodStacks(player.getInventory());

            // Sprawdź też top inventory (np. skrzynka)
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv != null) {
                capBloodStacks(topInv);
            }

            player.updateInventory();
        }, 1L);
    }

    /**
     * ✅ Przycina stacki krwi do max 8. NIE łączy — tylko cap.
     */
    private void capBloodStacks(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (!KrewWampiraItem.isKrewWampira(item)) continue;

            if (item.getAmount() > KrewWampiraItem.MAX_STACK) {
                int overflow = item.getAmount() - KrewWampiraItem.MAX_STACK;
                item.setAmount(KrewWampiraItem.MAX_STACK);

                ItemStack overflowItem = KrewWampiraItem.create(overflow);
                Map<Integer, ItemStack> leftover = inventory.addItem(overflowItem);
                // Jeśli nie zmieściło się — nic, cap zrobił swoje
            }
        }
    }
}
