package pl.anaheim.anaitemy.listeners;

import org.bukkit.Bukkit;
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
     * ✅ Gdy gracz klika PPM trzymając piernika i ma pełny głód —
     * obniż głód o 1 punkt żeby klient pozwolił zacząć jedzenie.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PiernikItem.isPiernik(item)) return;

        if (player.getFoodLevel() >= 20) {
            player.setFoodLevel(19);
        }
    }

    /**
     * ✅ Po zjedzeniu — daj efekt i przywróć pełny głód.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!PiernikItem.isPiernik(event.getItem())) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            int duration = plugin.getItemsConfig().getPiernikHasteDuration() * 20;
            int level = plugin.getItemsConfig().getPiernikHasteLevel() - 1;

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.FAST_DIGGING, duration, level, false, true, true));

            // ✅ Przywróć pełny głód
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }, 1L);
    }
}
