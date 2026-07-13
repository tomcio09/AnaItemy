package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;

import java.util.List;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Ignoruj obroty glowy
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Gracz wychodzi na zablokowany region — pozwol i wypusc
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        double distanceTo = to.distance(center);

        if (!klatka.isAnimationComplete()) {
            // ✅ PODCZAS ANIMACJI:
            // Niewidzialna bariera na granicy klatki (radius - 1.0)
            // Blokuje gracza NAWET gdzie bloki jeszcze nie pojawiły się
            // Ale TYLKO tam gdzie bloki SIĘ POJAWIĄ (nie na zablokowanych regionach)

            double innerRadius = radius - 1.0;

            if (distanceTo > innerRadius) {
                // Sprawdz czy w tym miejscu klatka POWINNA miec bloki
                // (czyli czy region nie blokuje budowy)
                Location checkLoc = to.clone();
                boolean regionBlocked = plugin.getWorldGuardManager().isInBlockedRegion(checkLoc, blockedRegions);

                if (regionBlocked) {
                    // Region blokuje budowe — pozwol graczowi wyjsc
                    manager.removePlayerFromKlatka(player);
                    return;
                }

                // Niewidzialna bariera — zablokuj ruch
                Location cancelLoc = from.clone();
                cancelLoc.setYaw(to.getYaw());
                cancelLoc.setPitch(to.getPitch());
                event.setTo(cancelLoc);
            }
        } else {
            // ✅ PO ANIMACJI:
            // Bariera tylko tam gdzie sa fizyczne bloki shell

            if (distanceTo > radius - 1.5 && distanceTo <= radius + 0.5) {
                boolean hasShellHere = manager.isShellBlock(to.getBlock().getLocation());
                boolean hasShellHead = manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

                if (hasShellHere || hasShellHead) {
                    // Jest blok pancerza — zablokuj ruch
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // Nie ma bloku pancerza (region zablokowal budowe) — wypusc gracza
                if (distanceTo > radius - 0.5) {
                    manager.removePlayerFromKlatka(player);
                    return;
                }
            }

            // Gracz calkowicie poza klatka
            if (distanceTo > radius) {
                manager.removePlayerFromKlatka(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // Pozwol na teleport pluginowy blisko srodka
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Location to = event.getTo();
            if (to == null) return;
            Location center = klatka.getCenter();
            if (to.distance(center) < 2.0) return;
        }

        Location to = event.getTo();
        if (to == null) return;
        Location center = klatka.getCenter();

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Na zablokowany region — wypusc
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        if (!klatka.isAnimationComplete()) {
            // Podczas animacji — niewidzialna bariera
            if (to.distance(center) > klatka.getRadius() - 1.0) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            }
        } else {
            // Po animacji — bariera tylko na shell
            boolean hasShell = manager.isShellBlock(to.getBlock().getLocation())
                    || manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

            if (hasShell) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                return;
            }

            if (to.distance(center) > klatka.getRadius()) {
                manager.removePlayerFromKlatka(player);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);

        if (klatka != null) {
            klatka.addOfflinePlayer(player.getUniqueId());
        }
    }
}
