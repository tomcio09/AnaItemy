package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        if (klatka != null) {
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

        // Sprawdź czy gracz ma cooldown i wznów action bar
        if (manager.isPlayerOnCooldown(player)) {
            manager.startCooldownDisplay(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Zatrzymaj action bar display przy wylogowaniu
        manager.stopCooldownDisplay(player);
    }
}
