package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiernikItem;

public class PiernikListener implements Listener {

    private final AnaItemy plugin;

    public PiernikListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!PiernikItem.isPiernik(item)) return;

        // ✅ Pozwól zjeść (vanilla cookie consume)
        // Po zjedzeniu daj haste 11 (amplifier 10) na 15s
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int duration = plugin.getItemsConfig().getPiernikHasteDuration() * 20;
            int level = plugin.getItemsConfig().getPiernikHasteLevel() - 1;
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, duration, level, false, true, true));
        }, 1L);
    }
}
