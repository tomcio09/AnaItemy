package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiernikItem;

public class PiernikListener implements Listener {

    private final AnaItemy plugin;

    public PiernikListener(AnaItemy plugin) { this.plugin = plugin; }

    /**
     * ✅ Blokuj vanilla jedzenie (bo nie pozwala jeść przy pełnym głodzie).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (PiernikItem.isPiernik(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * ✅ PPM = zjedz piernika (działa zawsze, nawet przy pełnym głodzie).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PiernikItem.isPiernik(item)) return;

        event.setCancelled(true);

        int duration = plugin.getItemsConfig().getPiernikHasteDuration() * 20;
        int level = plugin.getItemsConfig().getPiernikHasteLevel() - 1;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FAST_DIGGING, duration, level, false, true, true));

        // Zużyj
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }
}
