package pl.anaheim.anaitemy.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
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

    // ==================== INVENTORY CLICK ====================

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

        // ✅ LPM: krew na pusty slot = postaw wszystko
        if (cursorBlood && (current == null || current.getType().isAir()) && event.getClick() == ClickType.LEFT) {
            // Vanilla obsłuży to normalnie
            return;
        }

        // ✅ PPM: krew na pusty slot = postaw 1
        if (cursorBlood && (current == null || current.getType().isAir()) && event.getClick() == ClickType.RIGHT) {
            if (cursor.getAmount() >= 1) {
                event.setCancelled(true);
                ItemStack single = KrewWampiraItem.create(1);
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), single);
                }
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                } else {
                    event.getView().setCursor(null);
                }
            }
            return;
        }

        // ✅ LPM: pusty cursor na krew = podnieś
        if (!cursorBlood && currentBlood && (cursor == null || cursor.getType().isAir()) && event.getClick() == ClickType.LEFT) {
            // Vanilla obsłuży
            return;
        }

        // ✅ PPM: pusty cursor na krew = podnieś połowę
        if (!cursorBlood && currentBlood && (cursor == null || cursor.getType().isAir()) && event.getClick() == ClickType.RIGHT) {
            if (current.getAmount() > 1) {
                event.setCancelled(true);
                int half = (int) Math.ceil(current.getAmount() / 2.0);
                int remaining = current.getAmount() - half;

                ItemStack picked = KrewWampiraItem.create(half);
                event.getView().setCursor(picked);

                if (remaining > 0) {
                    current.setAmount(remaining);
                } else {
                    event.setCurrentItem(null);
                }
            }
            return;
        }

        // ✅ Shift click — szukaj stacka do połączenia
        if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) && currentBlood) {
            event.setCancelled(true);

            int amountToMove = current.getAmount();

            // Szukaj stacka krwi w drugim inventory
            Inventory targetInv = (event.getClickedInventory() == player.getInventory())
                    ? event.getView().getTopInventory()
                    : player.getInventory();

            // Najpierw szukaj istniejących stacków
            for (int i = 0; i < targetInv.getSize() && amountToMove > 0; i++) {
                ItemStack slot = targetInv.getItem(i);
                if (!KrewWampiraItem.isKrewWampira(slot)) continue;
                if (slot.getAmount() >= KrewWampiraItem.MAX_STACK) continue;

                int canAdd = KrewWampiraItem.MAX_STACK - slot.getAmount();
                int toAdd = Math.min(canAdd, amountToMove);
                slot.setAmount(slot.getAmount() + toAdd);
                amountToMove -= toAdd;
            }

            // Potem szukaj pustych slotów
            while (amountToMove > 0) {
                int firstEmpty = targetInv.firstEmpty();
                if (firstEmpty == -1) break;

                int give = Math.min(KrewWampiraItem.MAX_STACK, amountToMove);
                targetInv.setItem(firstEmpty, KrewWampiraItem.create(give));
                amountToMove -= give;
            }

            if (amountToMove > 0) {
                current.setAmount(amountToMove);
            } else {
                event.setCurrentItem(null);
            }
            return;
        }

        // ✅ Double click — zbierz wszystkie do jednego stacka (max 8)
        if (event.getClick() == ClickType.DOUBLE_CLICK && cursorBlood) {
            event.setCancelled(true);

            int cursorAmount = cursor.getAmount();
            if (cursorAmount >= KrewWampiraItem.MAX_STACK) return;

            // Zbieraj z inventory gracza
            for (int i = 0; i < player.getInventory().getSize() && cursorAmount < KrewWampiraItem.MAX_STACK; i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (!KrewWampiraItem.isKrewWampira(slot)) continue;

                int canTake = KrewWampiraItem.MAX_STACK - cursorAmount;
                int slotAmount = slot.getAmount();

                if (slotAmount <= canTake) {
                    cursorAmount += slotAmount;
                    player.getInventory().setItem(i, null);
                } else {
                    cursorAmount = KrewWampiraItem.MAX_STACK;
                    slot.setAmount(slotAmount - canTake);
                }
            }

            // Zbieraj z top inventory (jeśli otwarte)
            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv != null) {
                for (int i = 0; i < topInv.getSize() && cursorAmount < KrewWampiraItem.MAX_STACK; i++) {
                    ItemStack slot = topInv.getItem(i);
                    if (!KrewWampiraItem.isKrewWampira(slot)) continue;

                    int canTake = KrewWampiraItem.MAX_STACK - cursorAmount;
                    int slotAmount = slot.getAmount();

                    if (slotAmount <= canTake) {
                        cursorAmount += slotAmount;
                        topInv.setItem(i, null);
                    } else {
                        cursorAmount = KrewWampiraItem.MAX_STACK;
                        slot.setAmount(slotAmount - canTake);
                    }
                }
            }

            cursor.setAmount(cursorAmount);
            player.updateInventory();
            return;
        }
    }

    // ==================== DRAG ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (!KrewWampiraItem.isKrewWampira(oldCursor)) return;

        // ✅ Anuluj vanilla drag — robimy własny
        event.setCancelled(true);

        int totalAmount = oldCursor.getAmount();
        java.util.Set<Integer> slots = event.getRawSlots();
        int slotCount = slots.size();

        if (slotCount == 0) return;

        if (event.getType() == DragType.EVEN) {
            // ✅ LPM drag = dziel równo
            int perSlot = totalAmount / slotCount;
            int remainder = totalAmount % slotCount;

            if (perSlot < 1) {
                // Za mało itemów na tyle slotów
                perSlot = 1;
            }

            int distributed = 0;

            for (int rawSlot : slots) {
                if (distributed >= totalAmount) break;

                Inventory inv = getInventoryForSlot(event, rawSlot);
                int localSlot = getLocalSlot(event, rawSlot);
                if (inv == null) continue;

                ItemStack existing = inv.getItem(localSlot);

                if (existing != null && !existing.getType().isAir()) {
                    if (!KrewWampiraItem.isKrewWampira(existing)) continue;
                    int canAdd = KrewWampiraItem.MAX_STACK - existing.getAmount();
                    int toAdd = Math.min(canAdd, perSlot);
                    if (toAdd <= 0) continue;
                    existing.setAmount(existing.getAmount() + toAdd);
                    distributed += toAdd;
                } else {
                    int give = Math.min(perSlot, totalAmount - distributed);
                    if (give <= 0) continue;
                    inv.setItem(localSlot, KrewWampiraItem.create(give));
                    distributed += give;
                }
            }

            int leftover = totalAmount - distributed;
            if (leftover > 0) {
                event.getView().setCursor(KrewWampiraItem.create(leftover));
            } else {
                event.getView().setCursor(null);
            }
        } else {
            // ✅ PPM drag = po 1 na slot
            int distributed = 0;

            for (int rawSlot : slots) {
                if (distributed >= totalAmount) break;

                Inventory inv = getInventoryForSlot(event, rawSlot);
                int localSlot = getLocalSlot(event, rawSlot);
                if (inv == null) continue;

                ItemStack existing = inv.getItem(localSlot);

                if (existing != null && !existing.getType().isAir()) {
                    if (!KrewWampiraItem.isKrewWampira(existing)) continue;
                    if (existing.getAmount() >= KrewWampiraItem.MAX_STACK) continue;
                    existing.setAmount(existing.getAmount() + 1);
                    distributed++;
                } else {
                    inv.setItem(localSlot, KrewWampiraItem.create(1));
                    distributed++;
                }
            }

            int leftover = totalAmount - distributed;
            if (leftover > 0) {
                event.getView().setCursor(KrewWampiraItem.create(leftover));
            } else {
                event.getView().setCursor(null);
            }
        }

        player.updateInventory();
    }

    // ==================== PICKUP Z ZIEMI ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item itemEntity = event.getItem();
        ItemStack item = itemEntity.getItemStack();
        if (!KrewWampiraItem.isKrewWampira(item)) return;

        event.setCancelled(true);

        int amountToAdd = item.getAmount();

        // Szukaj istniejących stacków krwi
        for (int i = 0; i < player.getInventory().getSize() && amountToAdd > 0; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!KrewWampiraItem.isKrewWampira(slot)) continue;
            if (slot.getAmount() >= KrewWampiraItem.MAX_STACK) continue;

            int canAdd = KrewWampiraItem.MAX_STACK - slot.getAmount();
            int toAdd = Math.min(canAdd, amountToAdd);
            slot.setAmount(slot.getAmount() + toAdd);
            amountToAdd -= toAdd;
        }

        // Potem szukaj pustych slotów
        while (amountToAdd > 0) {
            int firstEmpty = player.getInventory().firstEmpty();
            if (firstEmpty == -1) break;

            int give = Math.min(KrewWampiraItem.MAX_STACK, amountToAdd);
            player.getInventory().setItem(firstEmpty, KrewWampiraItem.create(give));
            amountToAdd -= give;
        }

        if (amountToAdd <= 0) {
            itemEntity.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP,
                    SoundCategory.PLAYERS, 0.3f, 1.0f);
        } else {
            item.setAmount(amountToAdd);
            itemEntity.setItemStack(item);
        }

        player.updateInventory();
    }

    // ==================== UTILS ====================

    private Inventory getInventoryForSlot(InventoryDragEvent event, int rawSlot) {
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize) return event.getView().getTopInventory();
        if (rawSlot < topSize + 36) return event.getView().getBottomInventory();
        return null;
    }

    private int getLocalSlot(InventoryDragEvent event, int rawSlot) {
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize) return rawSlot;
        return rawSlot - topSize;
    }
}
