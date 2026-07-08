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
import pl.anaheim.anaitemy.items.WampirzeJablkoItem;

public class WampirzeJablkoListener implements Listener {

    private final AnaItemy plugin;

    public WampirzeJablkoListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!WampirzeJablkoItem.isWampirzeJablko(event.getItem())) return;

        int duration = plugin.getItemsConfig().getWampirzeJablkoStrengthDuration() * 20;
        int level = plugin.getItemsConfig().getWampirzeJablkoStrengthLevel() - 1;

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, duration, level, false, true, true));
        }, 1L);
    }
}
