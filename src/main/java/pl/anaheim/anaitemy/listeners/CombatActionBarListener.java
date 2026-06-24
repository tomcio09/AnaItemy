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
 * Jeśli tak - oznacza combat jako aktywny w ActionBarManager.
 */
public class CombatActionBarListener implements Listener {

    private final AnaItemy plugin;

    public CombatActionBarListener(AnaItemy plugin) {
        this.plugin = plugin;
        startCombatCheckTask();
    }

    /**
     * ✅ Co sekundę sprawdzaj combat status każdego gracza.
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
        }.runTaskTimer(plugin, 0L, 10L); // Co 10 ticków (0.5s)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getActionBarManager().clearAll(event.getPlayer());
    }
}
