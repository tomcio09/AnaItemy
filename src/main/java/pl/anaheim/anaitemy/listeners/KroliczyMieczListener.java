package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
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

        KroliczyMieczManager manager = plugin.getKroliczyMieczManager();
        manager.attack(attacker, victim);
    }

    /**
     * ✅ Blokada skoku przez PlayerMoveEvent.
     * Manager decyduje czy ruch powinien być zablokowany.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        KroliczyMieczManager manager = plugin.getKroliczyMieczManager();

        if (!manager.isJumpBlocked(player)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (manager.shouldBlockJump(player, from, to)) {
            // ✅ Anuluj skok - trzymaj gracza na tej samej wysokości
            Location blocked = to.clone();
            blocked.setY(from.getY());
            event.setTo(blocked);

            // ✅ Zeruj velocity w górę
            Vector vel = player.getVelocity();
            if (vel.getY() > 0) {
                vel.setY(0);
                player.setVelocity(vel);
            }
        }
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
