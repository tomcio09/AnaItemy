package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

public class HydroKlatkaJoinListener implements Listener {

    private final AnaItemy plugin;

    public HydroKlatkaJoinListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Sprawdź czy gracz był w klatce gdy wyszedł z serwera
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // Klatka nadal istnieje - sprawdź czy gracz jest w środku
        Location playerLoc = player.getLocation();
        Location center = klatka.getCenter();

        // Jeśli gracz jest poza klatką - teleportuj do centrum
        if (playerLoc.distance(center) > klatka.getRadius()) {
            Location teleportLoc = center.clone();
            teleportLoc.setYaw(playerLoc.getYaw());
            teleportLoc.setPitch(playerLoc.getPitch());
            player.teleport(teleportLoc);
        }
    }
}
