package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KroliczyMieczItem;
import pl.anaheim.anaitemy.managers.KroliczyMieczManager;

public class KroliczyMieczListener implements Listener {

    private final AnaItemy plugin;

    public KroliczyMieczListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!KroliczyMieczItem.isKroliczyMiecz(mainHand)) return;

        // ✅ NIE anulujemy eventu - normalny damage przechodzi
        // Umiejętność (blokada skoku) jest DODATKOWA
        KroliczyMieczManager manager = plugin.getKroliczyMieczManager();
        manager.attack(attacker, victim);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getKroliczyMieczManager().cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getKroliczyMieczManager().cleanupPlayer(event.getEntity());
    }
}
