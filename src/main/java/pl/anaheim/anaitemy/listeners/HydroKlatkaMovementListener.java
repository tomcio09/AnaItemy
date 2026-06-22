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
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    // Margines przy granicy klatki - jak blisko granicy gracz może podejść
    // zanim zostanie cofnięty (w blokach)
    private static final double BORDER_MARGIN = 0.5;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Znajdź klatkę w której gracz jest uwięziony
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        double distanceTo = to.distance(center);
        double distanceFrom = from.distance(center);

        // Gracz jest już poza klatką - teleportuj na środek
        if (distanceTo > radius) {
            // Teleport na środek z zachowaniem yaw/pitch
            Location teleportLoc = center.clone();
            teleportLoc.setYaw(to.getYaw());
            teleportLoc.setPitch(to.getPitch());
            event.setCancelled(true);
            player.teleport(teleportLoc);
            return;
        }

        // Gracz jest przy granicy klatki - zablokuj ruch w kierunku wyjścia
        if (distanceTo >= radius - BORDER_MARGIN) {
            // Sprawdź czy gracz porusza się W KIERUNKU granicy
            // (distanceTo > distanceFrom oznacza że oddala się od centrum)
            if (distanceTo > distanceFrom) {
                // Anuluj ruch - cofnij do poprzedniej pozycji
                // ale zachowaj yaw/pitch żeby gracz mógł się obracać
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

        // Znajdź klatkę w której gracz jest uwięziony
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // Jeśli to teleport przez nasz plugin (do centrum) - pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Location to = event.getTo();
            if (to == null) return;
            Location center = klatka.getCenter();
            // Jeśli teleport jest do centrum klatki - pozwól
            if (to.distance(center) < 2.0) return;
        }

        // Zablokuj teleport poza klatkę (ender perła, komenda itp.)
        Location to = event.getTo();
        if (to == null) return;
        Location center = klatka.getCenter();

        if (to.distance(center) > klatka.getRadius()) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Jeśli gracz wyjdzie z serwera będąc w klatce
        // po powrocie sprawdzimy czy klatka nadal istnieje
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);

        if (klatka != null) {
            // Zapamiętaj że gracz wyszedł z serwera
            klatka.addOfflinePlayer(player.getUniqueId());
        }
    }
}
