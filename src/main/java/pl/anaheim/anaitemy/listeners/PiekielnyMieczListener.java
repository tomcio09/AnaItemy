package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiekielnyMieczItem;

public class PiekielnyMieczListener implements Listener {

    private final AnaItemy plugin;

    public PiekielnyMieczListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!PiekielnyMieczItem.isPiekielnyMiecz(mainHand)) return;

        // ✅ Podpal na 20 sekund (400 ticków) - ignoruje odporność na ogień i wodę
        int fireTicks = plugin.getItemsConfig().getPiekielnyMieczFireDuration() * 20;
        victim.setFireTicks(fireTicks);

        // ✅ Jeśli gracz jest w wodzie lub ma fire resistance - nadal podpalony
        // setFireTicks nadpisuje stan ognia niezależnie od wody/efektów
        // Ale musimy co tick sprawdzać czy gracz nie zgasił ognia
        startFireTask(victim, fireTicks);
    }

    /**
     * ✅ Co tick sprawdza czy ofiara nadal się pali.
     * Jeśli woda/efekt zgasiły ogień - podpalamy ponownie.
     */
    private void startFireTask(Player victim, int totalTicks) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticksLeft = totalTicks;

            @Override
            public void run() {
                if (!victim.isOnline() || victim.isDead() || ticksLeft <= 0) {
                    cancel();
                    return;
                }

                // ✅ Jeśli ogień zgasł (woda, fire resistance) - podpal ponownie
                if (victim.getFireTicks() <= 0) {
                    victim.setFireTicks(ticksLeft);
                }

                ticksLeft -= 2;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }
}
