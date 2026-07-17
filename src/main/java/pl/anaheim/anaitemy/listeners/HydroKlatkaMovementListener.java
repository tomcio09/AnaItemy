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

    private static final double WALL_MARGIN = 0.8;
    private static final double INVISIBLE_WALL_EXTRA = 0.5;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    private boolean isPlayerInShell(Location loc, HydroKlatkaManager manager) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();

        boolean feetShell = feet.getType() == SHELL && manager.isShellBlock(feet.getLocation());
        boolean headShell = head.getType() == SHELL && manager.isShellBlock(head.getLocation());

        return feetShell || headShell;
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

        // Gracz na zablokowanym regionie — wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        double distanceTo = to.distance(center);
        double distanceFrom = from.distance(center);

        // ==============================================================
        // NIEWIDZIALNA ŚCIANA - DZIAŁA ZAWSZE
        // Dla WSZYSTKICH graczy (elytra, bieganie, spadanie)
        // Jest 0.5 bloku za prawdziwym shellem
        // radius=8 -> invisibleWallRadius = 8 - 0.8 + 0.5 = 7.7
        // ==============================================================
        double invisibleWallRadius = radius - WALL_MARGIN + INVISIBLE_WALL_EXTRA;

        if (distanceTo >= invisibleWallRadius) {
            // Sprawdź czy region blokuje budowę shella w tym kierunku
            boolean regionBlocked = plugin.getWorldGuardManager()
                    .isInBlockedRegion(to, blockedRegions);

            if (regionBlocked) {
                manager.removePlayerFromKlatka(player);
                return;
            }

            // ✅ Niewidzialna ściana — zatrzymaj gracza (również elytrę!)
            Location stuckLoc = from.clone();
            stuckLoc.setYaw(to.getYaw());
            stuckLoc.setPitch(to.getPitch());
            event.setTo(stuckLoc);
            return;
        }

        // ==============================================================
        // DODATKOWE SPRAWDZENIA PO ANIMACJI
        // ==============================================================
        if (klatka.isAnimationComplete()) {

            // -----------------------------------------------
            // Gracz jest POZA klatką (exploit/bug)
            // -----------------------------------------------
            if (distanceTo > radius) {
                // Elytra = cofnij, NIE teleportuj na środek
                if (player.isGliding()) {
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // Nie-elytra poza klatką = teleport na środek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // -----------------------------------------------
            // Gracz jest W bloku shella
            // -----------------------------------------------
            boolean playerInShellTo = isPlayerInShell(to, manager);

            if (playerInShellTo) {
                // Elytra = cofnij, NIGDY nie teleportuj
                if (player.isGliding()) {
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // Sprawdź czy FROM też jest w shellu = zakleszczony
                boolean playerInShellFrom = isPlayerInShell(from, manager);

                if (playerInShellFrom) {
                    // Zakleszczony = teleport na środek
                    Location teleportLoc = center.clone();
                    teleportLoc.setYaw(to.getYaw());
                    teleportLoc.setPitch(to.getPitch());
                    event.setTo(teleportLoc);
                    return;
                }

                // Wchodzi w shell = cofnij jeśli idzie od środka
                if (distanceFrom < distanceTo) {
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                }
                return;
            }

            // -----------------------------------------------
            // Gracz zbliża się do shella od wewnątrz
            // -----------------------------------------------
            if (distanceTo > radius - 1.5) {
                Block feetBlock = to.getBlock();
                Block headBlock = to.clone().add(0, 1, 0).getBlock();

                boolean shellAtFeet = manager.isShellBlock(feetBlock.getLocation());
                boolean shellAtHead = manager.isShellBlock(headBlock.getLocation());

                if (shellAtFeet || shellAtHead) {
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // Brak shella = region zablokował = wypuść
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
        double radius = klatka.getRadius();

        // Pozwól na teleport pluginowy wewnątrz klatki
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < radius) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // Zablokuj KAŻDY teleport poza niewidzialną ścianę
        double invisibleWallRadius = radius - WALL_MARGIN + INVISIBLE_WALL_EXTRA;

        if (to.distance(center) > invisibleWallRadius) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        // Zablokuj teleport w shell
        if (klatka.isAnimationComplete()) {
            boolean hasShell = manager.isShellBlock(to.getBlock().getLocation())
                    || manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

            if (hasShell) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
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
