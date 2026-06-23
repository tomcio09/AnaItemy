package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;

public class SakiewkaPortalListener implements Listener {

    private final AnaItemy plugin;

    public SakiewkaPortalListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Zabezpieczenie przed teleportacją przez portale.
     * Zamyka GUI sakiewki 1 sekundę przed teleportacją.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalEnter(PlayerPortalEvent event) {
        Player player = event.getPlayer();

        // ✅ Zamknij GUI jeśli otwarte
        if (player.getOpenInventory().getTopInventory().getHolder() == null) {
            String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(player.getOpenInventory().title());
            
            if (title.equals("Sakiewka dropu")) {
                player.closeInventory();
            }
        }

        // ✅ Oznacz gracza jako teleportującego się (1.5s)
        player.setMetadata("sakiewka_teleporting", new FixedMetadataValue(plugin, true));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.removeMetadata("sakiewka_teleporting", plugin);
                }
            }
        }.runTaskLater(plugin, 30L); // 1.5 sekundy
    }

    /**
     * ✅ Zabezpieczenie przed wszystkimi teleportacjami.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Portal End - dodatkowe zabezpieczenie
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            // Zamknij wszystkie GUI
            if (player.getOpenInventory().getTopInventory().getHolder() == null) {
                String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(player.getOpenInventory().title());
                
                if (title.equals("Sakiewka dropu")) {
                    player.closeInventory();
                }
            }

            // Oznacz gracza
            player.setMetadata("sakiewka_teleporting", new FixedMetadataValue(plugin, true));

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.removeMetadata("sakiewka_teleporting", plugin);
                    }
                }
            }.runTaskLater(plugin, 30L);
        }
    }
}
