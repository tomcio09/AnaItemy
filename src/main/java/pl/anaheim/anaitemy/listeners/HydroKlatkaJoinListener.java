// src/main/java/pl/anaheim/anaitemy/listeners/HydroKlatkaJoinListener.java
package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
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
        if (event == null || event.getPlayer() == null) return;
        
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Sprawdź czy gracz był w klatce gdy wyszedł z serwera
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka != null) {
            Location playerLoc = player.getLocation();
            Location center = klatka.getCenter();

            if (playerLoc == null || center == null || center.getWorld() == null) return;

            // Jeśli gracz jest poza barierą - teleportuj do centrum
            if (playerLoc.getWorld() == null
                    || !playerLoc.getWorld().equals(center.getWorld())
                    || playerLoc.distance(center) > klatka.getBarrierRadius()) {
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(playerLoc.getYaw());
                teleportLoc.setPitch(playerLoc.getPitch());
                player.teleport(teleportLoc);
            }
        }

        // Sprawdź czy gracz ma cooldown i wznów action bar + item cooldown
        if (manager.isPlayerOnCooldown(player)) {
            long remaining = manager.getPlayerCooldownRemaining(player);
            player.setCooldown(Material.BLAZE_ROD, (int) (remaining * 20));
            manager.startCooldownDisplay(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) return;
        
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Zatrzymaj action bar display przy wylogowaniu
        manager.stopCooldownDisplay(player);

        // Oznacz gracza jako offline w klatce (żeby nie stracić informacji o trappingu)
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka != null) {
            klatka.addOfflinePlayer(player.getUniqueId());
        }
    }
}
