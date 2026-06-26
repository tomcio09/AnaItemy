package pl.anaheim.anaitemy.listeners;

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
import pl.anaheim.anaitemy.items.MarchewkowyMieczItem;
import pl.anaheim.anaitemy.managers.MarchewkowyMieczManager;

public class MarchewkowyMieczListener implements Listener {

    private final AnaItemy plugin;

    public MarchewkowyMieczListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!MarchewkowyMieczItem.isMarchewkowyMiecz(mainHand)) return;

        plugin.getMarchewkowyMieczManager().attack(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getMarchewkowyMieczManager().isFrozen(player)) return;

        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.getTo().setX(event.getFrom().getX());
            event.getTo().setY(event.getFrom().getY());
            event.getTo().setZ(event.getFrom().getZ());
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMarchewkowyMieczManager().cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getMarchewkowyMieczManager().cleanupPlayer(event.getEntity());
    }
}
