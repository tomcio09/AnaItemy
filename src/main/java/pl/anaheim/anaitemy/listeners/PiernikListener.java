package pl.anaheim.anaitemy.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiernikItem;

public class PiernikListener implements Listener {

    private final AnaItemy plugin;

    public PiernikListener(AnaItemy plugin) {
        this.plugin = plugin;

        // ✅ Co tick sprawdzaj graczy i pozwalaj jeść piernika nawet przy pełnym głodzie
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.getFoodLevel() >= 20) {
                        ItemStack mainHand = player.getInventory().getItemInMainHand();
                        if (PiernikItem.isPiernik(mainHand)) {
                            // ✅ Tymczasowo obniż głód żeby pozwolić na jedzenie
                            if (player.isHandRaised()) {
                                player.setFoodLevel(19);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * ✅ Po zjedzeniu piernika — daj haste i przywróć głód.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!PiernikItem.isPiernik(item)) return;

        // ✅ Po zjedzeniu daj efekt
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            int duration = plugin.getItemsConfig().getPiernikHasteDuration() * 20;
            int level = plugin.getItemsConfig().getPiernikHasteLevel() - 1;

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.FAST_DIGGING, duration, level, false, true, true));

            // ✅ Przywróć pełny głód (żeby nie tracił głodu z jedzenia)
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }, 1L);
    }
}
