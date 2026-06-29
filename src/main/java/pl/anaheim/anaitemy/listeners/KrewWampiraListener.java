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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import pl.anaheim.anaitemy.items.KrewWampiraItem;

import java.util.*;

public class KrewWampiraListener implements Listener {

    private final AnaItemy plugin;

    public KrewWampiraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    private boolean isOurGUI(org.bukkit.event.inventory.InventoryEvent event) {
        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        return EventoweGUI.isGUITitle(plainTitle);
    }

    // ==================== BLOKADA JEDZENIA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (KrewWampiraItem.isKrewWampira(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ==================== UŻYCIE PPM ====================

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
        if (maxHealthAttr != null) player.setHealth(maxHealthAttr.getValue());

        if (plugin.getBlokWidmoManager().isAffected(player)) {
            plugin.getBlokWidmoManager().forceRemoveEffect(player);
            maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) player.setHealth(maxHealthAttr.getValue());
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    // ==================== INVENTORY CLICK ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isOurGUI(event)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        boolean cursorBlood = KrewWampiraItem.isKrewWampira(cursor);
        boolean currentBlood = KrewWampiraItem.isKrewWampira(current);

        // ✅ NUMBER KEY (1-9) — zamiana całych stacków
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot == -1) return;
            
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            
            boolean hotbarBlood = KrewWampiraItem.isKrewWampira(hotbarItem);
            
            // Jeśli którykolwiek item to krew wampira, obsługujemy manualnie
            if (hotbarBlood || currentBlood) {
                event.setCancelled(true);
                
                // Zamiana całych stacków
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), hotbarItem);
                }
                player.getInventory().setItem(hotbarSlot, current);
            }
            return;
        }

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
                if (cursor.getAmount() > 1) cursor.setAmount(cursor.getAmount() - 1);
                else event.getView().setCursor(null);
            }
            return;
        }

        // ✅ LPM: krew na pusty = odłóż wszystko (vanilla obsłuży)
        if (cursorBlood && (current == null || current.getType().isAir()) && event.getClick() == ClickType.LEFT) {
            return;
        }

        // ✅ PPM: krew na pusty = odłóż połowę
        if (cursorBlood && (current == null || current.getType().isAir()) && event.getClick() == ClickType.RIGHT) {
            event.setCancelled(true);
            int cursorAmount = cursor.getAmount();
            if (cursorAmount <= 1) {
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), KrewWampiraItem.create(1));
                }
                event.getView().setCursor(null);
            } else {
                int toPlace = cursorAmount / 2;
                int remaining = cursorAmount - toPlace;
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), KrewWampiraItem.create(toPlace));
                }
                cursor.setAmount(remaining);
            }
            return;
        }

        // ✅ LPM: pusty cursor na krew = podnieś (vanilla obsłuży)
        if (!cursorBlood && currentBlood && (cursor == null || cursor.getType().isAir()) && event.getClick() == ClickType.LEFT) {
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

                if (remaining > 0) current.setAmount(remaining);
                else event.setCurrentItem(null);
            }
            return;
        }

        // ✅ Shift click
        if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) && currentBlood) {
            event.setCancelled(true);
            int amountToMove = current.getAmount();

            Inventory targetInv = (event.getClickedInventory() == player.getInventory())
                    ? event.getView().getTopInventory()
                    : player.getInventory();

            for (int i = 0; i < targetInv.getSize() && amountToMove > 0; i++) {
                ItemStack slot = targetInv.getItem(i);
                if (!KrewWampiraItem.isKrewWampira(slot)) continue;
                if (slot.getAmount() >= KrewWampiraItem.MAX_STACK) continue;
                int canAdd = KrewWampiraItem.MAX_STACK - slot.getAmount();
                int toAdd = Math.min(canAdd, amountToMove);
                slot.setAmount(slot.getAmount() + toAdd);
                amountToMove -= toAdd;
            }

            while (amountToMove > 0) {
                int firstEmpty = targetInv.firstEmpty();
                if (firstEmpty == -1) break;
                int give = Math.min(KrewWampiraItem.MAX_STACK, amountToMove);
                targetInv.setItem(firstEmpty, KrewWampiraItem.create(give));
                amountToMove -= give;
            }

            if (amountToMove > 0) current.setAmount(amountToMove);
            else event.setCurrentItem(null);
            return;
        }

        // ✅ Double click
        if (event.getClick() == ClickType.DOUBLE_CLICK && cursorBlood) {
            event.setCancelled(true);
            int cursorAmount = cursor.getAmount();
            if (cursorAmount >= KrewWampiraItem.MAX_STACK) return;

            for (int i = 0; i < player.getInventory().getSize() && cursorAmount < KrewWampiraItem.MAX_STACK; i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (!KrewWampiraItem.isKrewWampira(slot)) continue;
                int canTake = KrewWampiraItem.MAX_STACK - cursorAmount;
                if (slot.getAmount() <= canTake) {
                    cursorAmount += slot.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    cursorAmount = KrewWampiraItem.MAX_STACK;
                    slot.setAmount(slot.getAmount() - canTake);
                }
            }

            Inventory topInv = player.getOpenInventory().getTopInventory();
            if (topInv != null) {
                for (int i = 0; i < topInv.getSize() && cursorAmount < KrewWampiraItem.MAX_STACK; i++) {
                    ItemStack slot = topInv.getItem(i);
                    if (!KrewWampiraItem.isKrewWampira(slot)) continue;
                    int canTake = KrewWampiraItem.MAX_STACK - cursorAmount;
                    if (slot.getAmount() <= canTake) {
                        cursorAmount += slot.getAmount();
                        topInv.setItem(i, null);
                    } else {
                        cursorAmount = KrewWampiraItem.MAX_STACK;
                        slot.setAmount(slot.getAmount() - canTake);
                    }
                }
            }

            cursor.setAmount(cursorAmount);
            return;
        }
    }

    // ==================== DRAG ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isOurGUI(event)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (!KrewWampiraItem.isKrewWampira(oldCursor)) return;

        event.setCancelled(true);

        int totalAmount = oldCursor.getAmount();
        Set<Integer> rawSlots = event.getRawSlots();
        
        if (rawSlots.isEmpty()) {
            event.getView().setCursor(KrewWampiraItem.create(totalAmount));
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        
        if (event.getType() == DragType.EVEN) {
            // ✅ LPM drag - dzieli równo NA ZAZNACZONE sloty
            List<Integer> validSlots = new ArrayList<>();
            
            // Znajdź zaznaczone sloty które mogą przyjąć krew
            for (int rawSlot : rawSlots) {
                ItemStack existing = getItemAtRawSlot(event, rawSlot, topSize, player);
                if (existing == null || existing.getType().isAir()) {
                    validSlots.add(rawSlot);
                } else if (KrewWampiraItem.isKrewWampira(existing) && existing.getAmount() < KrewWampiraItem.MAX_STACK) {
                    validSlots.add(rawSlot);
                }
            }
            
            if (validSlots.isEmpty()) {
                event.getView().setCursor(KrewWampiraItem.create(totalAmount));
                return;
            }
            
            // Dziel równo z uwzględnieniem reszty
            int perSlot = totalAmount / validSlots.size();
            int remainder = totalAmount % validSlots.size();
            int distributed = 0;
            
            for (int i = 0; i < validSlots.size(); i++) {
                int rawSlot = validSlots.get(i);
                int toGive = perSlot + (i < remainder ? 1 : 0);
                
                if (toGive <= 0) continue;
                
                ItemStack existing = getItemAtRawSlot(event, rawSlot, topSize, player);
                
                if (existing == null || existing.getType().isAir()) {
                    int finalAmount = Math.min(toGive, KrewWampiraItem.MAX_STACK);
                    setItemAtRawSlot(event, rawSlot, topSize, player, KrewWampiraItem.create(finalAmount));
                    distributed += finalAmount;
                } else if (KrewWampiraItem.isKrewWampira(existing)) {
                    int canAdd = KrewWampiraItem.MAX_STACK - existing.getAmount();
                    int toAdd = Math.min(canAdd, toGive);
                    if (toAdd > 0) {
                        existing.setAmount(existing.getAmount() + toAdd);
                        distributed += toAdd;
                    }
                }
            }
            
            int leftover = totalAmount - distributed;
            if (leftover > 0) {
                event.getView().setCursor(KrewWampiraItem.create(leftover));
            } else {
                event.getView().setCursor(null);
            }
            
        } else {
            // ✅ PPM drag - po 1 NA ZAZNACZONE sloty
            int distributed = 0;
            
            for (int rawSlot : rawSlots) {
                if (distributed >= totalAmount) break;
                
                ItemStack existing = getItemAtRawSlot(event, rawSlot, topSize, player);
                
                if (existing == null || existing.getType().isAir()) {
                    setItemAtRawSlot(event, rawSlot, topSize, player, KrewWampiraItem.create(1));
                    distributed++;
                } else if (KrewWampiraItem.isKrewWampira(existing) && existing.getAmount() < KrewWampiraItem.MAX_STACK) {
                    existing.setAmount(existing.getAmount() + 1);
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

        for (int i = 0; i < player.getInventory().getSize() && amountToAdd > 0; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!KrewWampiraItem.isKrewWampira(slot)) continue;
            if (slot.getAmount() >= KrewWampiraItem.MAX_STACK) continue;
            int canAdd = KrewWampiraItem.MAX_STACK - slot.getAmount();
            int toAdd = Math.min(canAdd, amountToAdd);
            slot.setAmount(slot.getAmount() + toAdd);
            amountToAdd -= toAdd;
        }

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
    }

    // ==================== UTILS ====================

    private ItemStack getItemAtRawSlot(InventoryDragEvent event, int rawSlot, int topSize, Player player) {
        if (rawSlot < topSize) {
            return event.getView().getTopInventory().getItem(rawSlot);
        } else {
            int localSlot = rawSlot - topSize;
            if (localSlot >= 0 && localSlot < player.getInventory().getSize()) {
                return player.getInventory().getItem(localSlot);
            }
        }
        return null;
    }

    private void setItemAtRawSlot(InventoryDragEvent event, int rawSlot, int topSize, Player player, ItemStack item) {
        if (rawSlot < topSize) {
            event.getView().getTopInventory().setItem(rawSlot, item);
        } else {
            int localSlot = rawSlot - topSize;
            if (localSlot >= 0 && localSlot < player.getInventory().getSize()) {
                player.getInventory().setItem(localSlot, item);
            }
        }
    }
}
