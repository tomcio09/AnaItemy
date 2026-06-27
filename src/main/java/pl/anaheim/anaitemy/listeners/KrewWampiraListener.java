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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KrewWampiraItem;

public class KrewWampiraListener implements Listener {

    private final AnaItemy plugin;

    public KrewWampiraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj vanilla picie honey bottle.
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
            // Przywróć max health ale zostaw bossbar
            plugin.getBlokWidmoManager().forceRemoveEffect(player);

            // Ulecz ponownie po przywróceniu max health
            maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealthAttr != null) {
                player.setHealth(maxHealthAttr.getValue());
            }
        }

        // ✅ Dźwięk jedzenia zupki
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // ✅ Zużyj 1 sztukę
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * ✅ Ograniczenie stackowania do 8 sztuk.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Sprawdź po kliknięciu czy stack nie przekracza 8
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (!player.isOnline()) return;

            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack slot = player.getInventory().getItem(i);
                if (slot == null) continue;

                if (KrewWampiraItem.isKrewWampira(slot) && slot.getAmount() > KrewWampiraItem.MAX_STACK) {
                    int overflow = slot.getAmount() - KrewWampiraItem.MAX_STACK;
                    slot.setAmount(KrewWampiraItem.MAX_STACK);

                    ItemStack overflowItem = KrewWampiraItem.create();
                    overflowItem.setAmount(overflow);

                    // Spróbuj dać do eq
                    java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(overflowItem);

                    // Jeśli nie zmieściło się - wyrzuć na ziemię
                    for (ItemStack left : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), left);
                    }
                }
            }
        }, 1L);
    }
}
