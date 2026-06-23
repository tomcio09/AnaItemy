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
    private static final double BORDER_MARGIN = 0.5;

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

        // ✅ Ignoruj obróty głowy (tylko pozycja X/Y/Z się liczy)
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        double distanceTo = to.distance(center);
        double distanceFrom = from.distance(center);

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        
        boolean movingToBlockedRegion = plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions);
        boolean wasInBlockedRegion = plugin.getWorldGuardManager().isInBlockedRegion(from, blockedRegions);

        if (movingToBlockedRegion && !wasInBlockedRegion) {
            Location cancelLoc = from.clone();
            cancelLoc.setYaw(to.getYaw());
            cancelLoc.setPitch(to.getPitch());
            event.setTo(cancelLoc);
            return;
        }

        if (movingToBlockedRegion) {
            Location teleportLoc = center.clone();
            teleportLoc.setYaw(to.getYaw());
            teleportLoc.setPitch(to.getPitch());
            event.setCancelled(true);
            player.teleport(teleportLoc);
            return;
        }

        if (distanceTo > radius) {
            Location teleportLoc = center.clone();
            teleportLoc.setYaw(to.getYaw());
            teleportLoc.setPitch(to.getPitch());
            event.setCancelled(true);
            player.teleport(teleportLoc);
            return;
        }

        if (distanceTo >= radius - BORDER_MARGIN) {
            if (distanceTo > distanceFrom) {
                Location cancelLoc = from.clone();
                cancelLoc.setYaw(to.getYaw());
                cancelLoc.setPitch(to.getPitch());
                event.setTo(cancelLoc);
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

        if (to.distance(center) > klatka.getRadius()) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
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
