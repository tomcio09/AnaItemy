package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.TurbotrapItem;

public class TurbotrapListener implements Listener {

    private final AnaItemy plugin;

    public TurbotrapListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEggLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!(egg.getShooter() instanceof Player shooter)) return;

        // ✅ Sprawdź czy gracz trzyma turbotrap
        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        if (!TurbotrapItem.isTurbotrap(mainHand)) return;

        if (!plugin.getTurbotrapManager().isReady()) {
            event.setCancelled(true);
            plugin.getLogger().warning("[Turbotrap] Schemat nie jest załadowany!");
            return;
        }

        if (plugin.getTurbotrapManager().isInBlockedRegion(shooter.getLocation())) {
            event.setCancelled(true);
            return;
        }

        // ✅ Oznacz jajko
        plugin.getTurbotrapManager().markEgg(egg, shooter);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEggHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!plugin.getTurbotrapManager().isTurbotrapEgg(egg)) return;

        // ✅ Sprawdź region w miejscu uderzenia
        if (plugin.getTurbotrapManager().isInBlockedRegion(egg.getLocation())) {
            egg.remove();
            return;
        }

        // ✅ Wklej schemat
        plugin.getTurbotrapManager().pasteSchematic(egg.getLocation());
        egg.remove();
    }
}
