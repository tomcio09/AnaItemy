package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.CombatIntegrationManager;

/**
 * ✅ Listener który monitoruje stan walki - ActionBarManager się tym zajmuje teraz.
 */
public class CombatActionBarListener implements Listener {

    private final AnaItemy plugin;

    public CombatActionBarListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getActionBarManager().clearAll(event.getPlayer());
    }
}
