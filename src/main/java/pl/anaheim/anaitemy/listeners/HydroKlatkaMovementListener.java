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

        // Podczas animacji nie blokuj ruchu
        if (!klatka.isAnimationComplete()) {
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

        // ✅ Bariera tylko tam gdzie sa bloki pancerza klatki
        // Sprawdz czy docelowa pozycja jest w bloku ktory jest czescia klatki (shell)
        if (distanceTo > radius - 1.5 && distanceTo <= radius + 0.5) {
            // Gracz jest blisko granicy — sprawdz czy w tym miejscu jest shell
            Location checkLoc = to.clone();
            boolean hasShellHere = manager.isShellBlock(checkLoc.getBlock().getLocation());
            boolean hasShellHead = manager.isShellBlock(checkLoc.clone().add(0, 1, 0).getBlock().getLocation());

            if (hasShellHere || hasShellHead) {
                // Jest blok pancerza — zablokuj ruch
                Location cancelLoc = from.clone();
                cancelLoc.setYaw(to.getYaw());
                cancelLoc.setPitch(to.getPitch());
                event.setTo(cancelLoc);
                return;
            }

            // ✅ Nie ma bloku pancerza w tym miejscu (region zablokowal budowe)
            // Pozwol graczowi wyjsc — wypusc go z klatki
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

        // Sprawdz czy docelowa lokalizacja ma shell
        boolean hasShell = manager.isShellBlock(to.getBlock().getLocation())
                || manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

        if (hasShell) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        // Jesli teleportuje poza klatke i nie ma shell — wypusc
        if (to.distance(center) > klatka.getRadius()) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
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
