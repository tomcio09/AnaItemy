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

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ✅ Pomocnicza metoda - sprawdza czy gracz jest w bloku shella
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

        if (!klatka.isAnimationComplete()) {
            // ====================================================
            // PODCZAS ANIMACJI: niewidzialna ściana sferyczna
            // ====================================================

            double wallRadius = radius - WALL_MARGIN;

            if (distanceTo >= wallRadius) {
                // Sprawdź czy region blokuje tu budowę shella
                boolean regionBlocked = plugin.getWorldGuardManager()
                        .isInBlockedRegion(to, blockedRegions);

                if (regionBlocked) {
                    // Brak shella w tym kierunku = wypuść
                    manager.removePlayerFromKlatka(player);
                    return;
                }

                // Niewidzialna ściana — zatrzymaj gracza
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

            // --------------------------------------------------
            // PRIORYTET 1: Gracz jest POZA klatką (exploit/bug)
            // --------------------------------------------------
            if (distanceTo > radius) {

                // ❌ Elytra = NIE teleportuj, tylko cofnij
                if (player.isGliding()) {
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // ✅ Nie-elytra poza klatką = teleport na środek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // --------------------------------------------------
            // PRIORYTET 2: Gracz jest W bloku shella
            // --------------------------------------------------
            boolean playerInShellTo = isPlayerInShell(to, manager);

            if (playerInShellTo) {

                // ❌ Elytra = NIGDY nie teleportuj na środek
                if (player.isGliding()) {
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }

                // Sprawdź czy FROM też jest w shellu
                boolean playerInShellFrom = isPlayerInShell(from, manager);

                if (playerInShellFrom) {
                    // ✅ Gracz jest ZAKLESZCZONY (i from i to w shellu)
                    // Jedyny ratunek = teleport na środek
                    Location teleportLoc = center.clone();
                    teleportLoc.setYaw(to.getYaw());
                    teleportLoc.setPitch(to.getPitch());
                    event.setTo(teleportLoc);
                    return;
                }

                // FROM nie jest w shellu = gracz dopiero wchodzi w shell
                // Sprawdź kierunek ruchu
                if (distanceFrom < distanceTo) {
                    // Gracz idzie W STRONĘ shella = cofnij
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                } else {
                    // Gracz idzie OD shella = pozwól wrócić do środka
                }
                return;
            }

            // --------------------------------------------------
            // PRIORYTET 3: Gracz zbliża się do shella od wewnątrz
            // --------------------------------------------------
            if (distanceTo > radius - 1.5) {
                Block feetBlock = to.getBlock();
                Block headBlock = to.clone().add(0, 1, 0).getBlock();

                boolean shellAtFeet = manager.isShellBlock(feetBlock.getLocation());
                boolean shellAtHead = manager.isShellBlock(headBlock.getLocation());

                if (shellAtFeet || shellAtHead) {
                    // Shell istnieje — zablokuj ruch
                    Location cancelLoc = from.clone();
                    cancelLoc.setYaw(to.getYaw());
                    cancelLoc.setPitch(to.getPitch());
                    event.setTo(cancelLoc);
                    return;
                }

                // Brak shella (region zablokował) = gracz może wyjść
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

        // Pozwól na teleport pluginowy wewnątrz klatki
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < klatka.getRadius()) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Teleport na zablokowany region = wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        if (!klatka.isAnimationComplete()) {
            double wallRadius = klatka.getRadius() - WALL_MARGIN;
            if (to.distance(center) > wallRadius) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            }
        } else {
            // Zablokuj teleport w shell
            boolean hasShell = manager.isShellBlock(to.getBlock().getLocation())
                    || manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

            if (hasShell) {
                event.setCancelled(true);
                manager.sendMessage(player,
                        plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                return;
            }

            // Teleport poza klatkę = anuluj
            if (to.distance(center) > klatka.getRadius()) {
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
