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

        // Ignoruj tylko obroty głowy (bez zmiany pozycji)
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

        if (!klatka.isAnimationComplete()) {
            // ====================================================
            // PODCZAS ANIMACJI: niewidzialna ściana na granicy
            // ====================================================

            // Granica niewidzialnej ściany = radius - 1.0
            // (tyle samo ile wynosi granica shella w buildLayer)
            double wallRadius = radius - 1.0;

            if (distanceTo >= wallRadius) {
                // Sprawdź czy to miejsce jest na zablokowanym regionie
                // (jeśli tak, to tam nie powstanie shell = nie ma ściany)
                boolean regionBlockedHere = plugin.getWorldGuardManager()
                        .isInBlockedRegion(to, blockedRegions);

                if (regionBlockedHere) {
                    // Brak shella w tym miejscu = gracz może wyjść
                    manager.removePlayerFromKlatka(player);
                    return;
                }

                // ✅ Niewidzialna ściana - zatrzymaj gracza dokładnie przy granicy
                // Przesuń gracza z powrotem do from
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
                return;
            }

        } else {
            // ====================================================
            // PO ANIMACJI: shell fizycznie istnieje
            // ====================================================

            Block feetBlock = to.getBlock();
            Block headBlock = to.clone().add(0, 1, 0).getBlock();

            boolean feetInShell = feetBlock.getType() == SHELL
                    && manager.isShellBlock(feetBlock.getLocation());
            boolean headInShell = headBlock.getType() == SHELL
                    && manager.isShellBlock(headBlock.getLocation());

            if (feetInShell || headInShell) {
                // Gracz jest w bloku shella

                if (player.isGliding()) {
                    // Elytra - tylko zablokuj, nie teleportuj
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // Sprawdź głębokość wejścia w shell
                // Jeśli środek ciała gracza (Y+0.9) jest w bloku shella = faktyczne zakleszczenie
                Location playerBodyCenter = to.clone().add(0, 0.9, 0);
                Block bodyCenterBlock = playerBodyCenter.getBlock();
                boolean deeplyInShell = bodyCenterBlock.getType() == SHELL
                        && manager.isShellBlock(bodyCenterBlock.getLocation());

                if (deeplyInShell) {
                    // Faktycznie zakleszczony - teleportuj na środek
                    Location teleportLoc = center.clone();
                    teleportLoc.setYaw(to.getYaw());
                    teleportLoc.setPitch(to.getPitch());
                    event.setTo(teleportLoc);
                } else {
                    // Lekkie zahaczenie - tylko zablokuj ruch
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                }
                return;
            }

            // Gracz poza shellem ale za daleko od centrum (exploit/bug)
            if (distanceTo > radius) {
                // Teleportuj z powrotem na środek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // Gracz zbliża się do shella od wewnątrz - zablokuj przy granicy
            if (distanceTo > radius - 1.0) {
                boolean shellAtFeet = manager.isShellBlock(feetBlock.getLocation());
                boolean shellAtHead = manager.isShellBlock(headBlock.getLocation());

                if (shellAtFeet || shellAtHead) {
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // Brak shella w tym miejscu = region zablokował = wypuść
                if (distanceTo > radius - 0.5) {
                    manager.removePlayerFromKlatka(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location to = event.getTo();
        if (to == null) return;
        Location center = klatka.getCenter();

        // Pozwól na teleport pluginowy blisko środka (nasze własne teleporty)
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < klatka.getRadius()) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Teleport na zablokowany region = wypuść gracza
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        if (!klatka.isAnimationComplete()) {
            // Podczas animacji - zablokuj teleport poza granicę
            if (to.distance(center) > klatka.getRadius() - 1.0) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            }
        } else {
            // Po animacji

            // Zablokuj teleport w shell
            boolean hasShell = manager.isShellBlock(to.getBlock().getLocation())
                    || manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

            if (hasShell) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                return;
            }

            // Teleport poza klatkę = wypuść gracza
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
