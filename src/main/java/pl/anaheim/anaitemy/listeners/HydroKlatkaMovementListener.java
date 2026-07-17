package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
    private static final Material SHELL = Material.BLUE_GLAZED_TERRACOTTA;

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
        double distanceFrom = from.distance(center);

        if (!klatka.isAnimationComplete()) {
            // ✅ PODCZAS ANIMACJI:
            // Niewidzialna sciana na granicy klatki
            // Gracz sie "buguje" — jest cofany do from, nie na srodek

            double innerRadius = radius - 1.0;

            if (distanceTo > innerRadius) {
                // Sprawdz czy region nie blokuje budowy w tym miejscu
                boolean regionBlocked = plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions);

                if (regionBlocked) {
                    // Region blokuje — wypusc gracza
                    manager.removePlayerFromKlatka(player);
                    return;
                }

                // ✅ "Bugowanie" — cofnij gracza do from (nie na srodek!)
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
                return;
            }
        } else {
            // ✅ PO ANIMACJI:

            // Sprawdz czy gracz jest WEWNATRZ bloku shell (glowa lub nogi)
            Block feetBlock = to.getBlock();
            Block headBlock = to.clone().add(0, 1, 0).getBlock();

            boolean feetInShell = feetBlock.getType() == SHELL && manager.isShellBlock(feetBlock.getLocation());
            boolean headInShell = headBlock.getType() == SHELL && manager.isShellBlock(headBlock.getLocation());

            if (feetInShell || headInShell) {
                // ✅ Gracz jest CALKOWICIE wewnatrz bloku shell
                // Sprawdz czy to nie lekkie "wejscie hitboxem" przy elytrze

                // Jesli gracz leci elytra i byl blizej srodka niz teraz
                // = lekko wpadl hitboxem w sciane = NIE teleportuj
                if (player.isGliding() && distanceFrom < distanceTo) {
                    // Cofnij do from zamiast teleportowac na srodek
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // Gracz jest w scianie i NIE leci elytra = teleportuj na srodek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // Sprawdz czy gracz probuje wyjsc przez shell
            if (distanceTo > radius - 1.5 && distanceTo <= radius + 0.5) {
                boolean shellAhead = manager.isShellBlock(to.getBlock().getLocation());
                boolean shellHeadAhead = manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

                if (shellAhead || shellHeadAhead) {
                    // Shell istnieje — zablokuj ruch (bugowanie)
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // Nie ma shell = region zablokowal budowe = wypusc
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
