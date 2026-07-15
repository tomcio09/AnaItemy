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
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

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

        double distanceTo = to.distance(center);

        if (!klatka.isAnimationComplete()) {
            // ✅ PODCZAS ANIMACJI:
            // niewidzialna bariera dziala WSZEDZIE na granicy klatki
            // tak jak chciales pierwotnie
            double innerRadius = radius - 1.0;

            if (distanceTo > innerRadius) {
                Location cancelLoc = from.clone();
                cancelLoc.setYaw(to.getYaw());
                cancelLoc.setPitch(to.getPitch());
                event.setTo(cancelLoc);
                return;
            }
        } else {
            // ✅ PO ANIMACJI:
            // jesli gracz wchodzi na zablokowany region — wypusc go
            if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
                manager.removePlayerFromKlatka(player);
                return;
            }

            // blokuj tylko tam gdzie shell rzeczywiscie istnieje
            if (distanceTo > radius - 1.5 && distanceTo <= radius + 0.5) {
                boolean hasShellHere = manager.isShellBlock(to.getBlock().getLocation());
                boolean hasShellHead = manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

                if (hasShellHere || hasShellHead) {
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // nie ma shell = region zablokowal budowe = wypusc
                if (distanceTo > radius - 0.5) {
                    manager.removePlayerFromKlatka(player);
                    return;
                }
            }

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

        if (!klatka.isAnimationComplete()) {
            // podczas animacji — nadal blokuj teleport poza klatke
            if (to.distance(center) > klatka.getRadius() - 1.0) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            }
        } else {
            if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
                manager.removePlayerFromKlatka(player);
                return;
            }

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
