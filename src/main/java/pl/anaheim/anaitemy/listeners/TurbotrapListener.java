package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

public class TurbotrapListener implements Listener {

    private final AnaItemy plugin;

    public TurbotrapListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEggLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!(egg.getShooter() instanceof Player shooter)) return;

        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        if (!TurbotrapItem.isTurbotrap(mainHand)) return;

        // ✅ Zablokuj użycie turbotrapa w klatce
        HydroKlatkaManager klatkaManager = plugin.getHydroKlatkaManager();
        if (klatkaManager.getKlatkaForPlayer(shooter) != null) {
            event.setCancelled(true);
            klatkaManager.sendMessage(shooter,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
            return;
        }

        if (!plugin.getTurbotrapManager().isReady()) {
            event.setCancelled(true);
            plugin.getLogger().warning("[Turbotrap] Schemat nie jest załadowany!");
            return;
        }

        if (plugin.getTurbotrapManager().isInBlockedRegion(shooter.getLocation())) {
            event.setCancelled(true);
            return;
        }

        plugin.getTurbotrapManager().markEgg(egg, shooter);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEggHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        if (!plugin.getTurbotrapManager().isTurbotrapEgg(egg)) return;

        Location hitLocation = egg.getLocation();
        HydroKlatkaManager klatkaManager = plugin.getHydroKlatkaManager();

        // ✅ Sprawdź czy jajko uderza w blok klatki
        if (klatkaManager.isKlatkaBlock(hitLocation.getBlock().getLocation())) {
            egg.remove();
            return;
        }

        // ✅ Sprawdź czy jajko uderza wewnątrz aktywnej klatki
        for (ActiveHydroKlatka klatka : klatkaManager.getActiveKlatki()) {
            if (klatka.isInsideCage(hitLocation)) {
                egg.remove();
                return;
            }
        }

        // ✅ Sprawdź czy schemat zachodzi na klatkę
        // Jeśli tak — nie wklejaj w ogóle
        if (plugin.getTurbotrapManager().wouldOverlapCage(hitLocation)) {
            egg.remove();
            return;
        }

        if (plugin.getTurbotrapManager().isInBlockedRegion(egg.getLocation())) {
            egg.remove();
            return;
        }

        plugin.getTurbotrapManager().pasteSchematic(egg.getLocation());
        egg.remove();
    }
}
