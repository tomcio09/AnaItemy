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

        // Ignoruj obroty głowy
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Gracz wychodzi na zablokowany region — pozwól i wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        double distanceTo = to.distance(center);
        double distanceFrom = from.distance(center);

        if (!klatka.isAnimationComplete()) {
            // ✅ PODCZAS ANIMACJI:
            // Niewidzialna kolizja tam, gdzie BĘDZIE shell

            // Sprawdź pozycje hitboxa gracza (nogi i głowa)
            Location feetLoc = to.getBlock().getLocation();
            Location headLoc = to.clone().add(0, 1, 0).getBlock().getLocation();

            // Sprawdź czy gracz wchodzi w miejsce, gdzie BĘDZIE shell
            boolean feetWillBeShell = manager.willShellBeAt(feetLoc, klatka);
            boolean headWillBeShell = manager.willShellBeAt(headLoc, klatka);

            if (feetWillBeShell || headWillBeShell) {
                // ✅ Gracz wchodzi w przyszłą pozycję shella
                // Efekt "bugowania" - zatrzymaj go w miejscu
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
                return;
            }

            // Sprawdź czy gracz nie wychodzi poza ogólny promień
            if (distanceTo > radius - 1.0) {
                boolean regionBlocked = plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions);

                if (regionBlocked) {
                    // Region blokuje — wypuść gracza
                    manager.removePlayerFromKlatka(player);
                    return;
                }

                // Niewidzialna bariera - cofnij gracza
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
                return;
            }

        } else {
            // ✅ PO ANIMACJI - shell już istnieje

            Block feetBlock = to.getBlock();
            Block headBlock = to.clone().add(0, 1, 0).getBlock();

            boolean feetInShell = feetBlock.getType() == SHELL && manager.isShellBlock(feetBlock.getLocation());
            boolean headInShell = headBlock.getType() == SHELL && manager.isShellBlock(headBlock.getLocation());

            if (feetInShell || headInShell) {
                // Gracz jest w bloku shella

                // ❌ NIE teleportuj jeśli gracz leci elytrą
                if (player.isGliding()) {
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // ❌ NIE teleportuj jeśli to tylko lekkie zahaczenie
                // Sprawdź czy CENTRUM hitboxa gracza jest w bloku shella
                Location playerCenter = to.clone().add(0, 0.9, 0); // Środek gracza (~1.8 wysokości / 2)
                Block centerBlock = playerCenter.getBlock();
                boolean centerInShell = centerBlock.getType() == SHELL && manager.isShellBlock(centerBlock.getLocation());

                if (!centerInShell) {
                    // ✅ Tylko lekkie zahaczenie - NIE teleportuj, tylko zablokuj ruch
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // ✅ Gracz jest FAKTYCZNIE zakleszczony - teleportuj na środek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // Sprawdź czy gracz próbuje wyjść przez shell
            if (distanceTo > radius - 1.5 && distanceTo <= radius + 0.5) {
                boolean shellAhead = manager.isShellBlock(to.getBlock().getLocation());
                boolean shellHeadAhead = manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

                if (shellAhead || shellHeadAhead) {
                    // Shell istnieje — zablokuj ruch
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // Brak shella = region zablokował budowę = wypuść gracza
                if (distanceTo > radius - 0.5) {
                    manager.removePlayerFromKlatka(player);
                    return;
                }
            }

            // Gracz całkowicie poza klatką
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

        // Pozwól na teleport pluginowy blisko środka
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
