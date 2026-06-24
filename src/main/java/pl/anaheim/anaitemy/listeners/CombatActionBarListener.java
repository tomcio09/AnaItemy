package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.ActionBarManager;
import pl.anaheim.anaitemy.managers.CombatIntegrationManager;

/**
 * ✅ Listener który co sekundę sprawdza czy gracz jest w walce.
 */
public class CombatActionBarListener implements Listener {

    private final AnaItemy plugin;

    public CombatActionBarListener(AnaItemy plugin) {
        this.plugin = plugin;
        startCombatCheckTask();
    }

    /**
     * ✅ Co 1 sekundę sprawdzaj combat status każdego gracza (nie co 0.5s - mniej obciążenia).
     */
    private void startCombatCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                CombatIntegrationManager combat = plugin.getCombatIntegrationManager();
                ActionBarManager actionBar = plugin.getActionBarManager();

                if (!combat.isEnabled()) return;
                if (!plugin.getItemsConfig().isActionBarIntegrationEnabled()) return;

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (combat.isInCombat(player)) {
                        actionBar.markCombatActive(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // ✅ ZMIENIONO: Co 20 ticków (1s) zamiast 10
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getActionBarManager().clearAll(event.getPlayer());
    }
}
