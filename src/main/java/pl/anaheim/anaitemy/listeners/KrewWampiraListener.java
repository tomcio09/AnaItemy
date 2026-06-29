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

public class KrewWampiraListener implements Listener {

    private final AnaItemy plugin;

    public KrewWampiraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    private boolean isOurGUI(InventoryEvent event) {
        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        return EventoweGUI.isGUITitle(plainTitle);
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
        player.updateInventory();
    }

    // ==================== INVENTORY CLICK ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ✅ Nie ingeruj w nasze GUI
        if (isOurGUI(event)) return;

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
                // Odłóż jedyną sztukę
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), KrewWampiraItem.create(1));
                }
                event.getView().setCursor(null);
            } else {
                // Odłóż połowę (zaokrąglenie w dół)
                int toPlace = cursorAmount / 2;
                int remaining = cursorAmount - toPlace;
                if (event.getClickedInventory() != null) {
                    event.getClickedInventory().setItem(event.getSlot(), KrewWampiraItem.create(toPlace));
                }
                cursor.setAmount(remaining);
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
            player.updateInventory();
            return;
        }
    }

    // ==================== DRAG ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isOurGUI(event)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (!KrewWampiraItem.isKrewWampira(oldCursor)) return;

        event.setCancelled(true);

        int totalAmount = oldCursor.getAmount();
        java.util.List<Integer> slots = new java.util.ArrayList<>(event.getRawSlots());
        int slotCount = slots.size();

        if (slotCount == 0) {
            // Nic nie dragujemy - przywróć cursor
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.getOpenInventory().setCursor(KrewWampiraItem.create(totalAmount));
                player.updateInventory();
            }, 1L);
            return;
        }

        final int distributed;

        if (event.getType() == DragType.EVEN) {
            // ✅ LPM drag = dziel równo
            int perSlot = Math.max(1, totalAmount / slotCount);
            int dist = 0;

            for (int rawSlot : slots) {
                if (dist >= totalAmount) break;

                int topSize = event.getView().getTopInventory().getSize();
                Inventory inv;
                int localSlot;

                if (rawSlot < topSize) {
                    inv = event.getView().getTopInventory();
                    localSlot = rawSlot;
                } else {
                    inv = player.getInventory();
                    localSlot = rawSlot - topSize;
                    if (localSlot < 0 || localSlot >= player.getInventory().getSize()) continue;
                }

                ItemStack existing = inv.getItem(localSlot);
                int give = Math.min(perSlot, totalAmount - dist);
                if (give <= 0) continue;

                if (existing != null && !existing.getType().isAir()) {
                    if (!KrewWampiraItem.isKrewWampira(existing)) continue;
                    int canAdd = KrewWampiraItem.MAX_STACK - existing.getAmount();
                    int toAdd = Math.min(canAdd, give);
                    if (toAdd <= 0) continue;
                    existing.setAmount(existing.getAmount() + toAdd);
                    dist += toAdd;
                } else {
                    inv.setItem(localSlot, KrewWampiraItem.create(give));
                    dist += give;
                }
            }

            distributed = dist;
        } else {
            // ✅ PPM drag = po 1 na slot
            int dist = 0;

            for (int rawSlot : slots) {
                if (dist >= totalAmount) break;

                int topSize = event.getView().getTopInventory().getSize();
                Inventory inv;
                int localSlot;

                if (rawSlot < topSize) {
                    inv = event.getView().getTopInventory();
                    localSlot = rawSlot;
                } else {
                    inv = player.getInventory();
                    localSlot = rawSlot - topSize;
                    if (localSlot < 0 || localSlot >= player.getInventory().getSize()) continue;
                }

                ItemStack existing = inv.getItem(localSlot);

                if (existing != null && !existing.getType().isAir()) {
                    if (!KrewWampiraItem.isKrewWampira(existing)) continue;
                    if (existing.getAmount() >= KrewWampiraItem.MAX_STACK) continue;
                    existing.setAmount(existing.getAmount() + 1);
                    dist++;
                } else {
                    inv.setItem(localSlot, KrewWampiraItem.create(1));
                    dist++;
                }
            }

            distributed = dist;
        }

        // ✅ Ustaw cursor po ticku (po anulowaniu vanilla draga)
        final int leftover = totalAmount - distributed;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            if (leftover > 0) {
                player.getOpenInventory().setCursor(KrewWampiraItem.create(leftover));
            } else {
                player.getOpenInventory().setCursor(null);
            }
            player.updateInventory();
        }, 1L);
    }

    // ==================== PICKUP ====================

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
