package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    private static final double HALF_W = 0.30;
    private static final double HEIGHT = 1.80;

    private static final double TELEPORT_BOUNCE = 0.15;
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    // ✅ Flaga - nasz teleport, nie blokuj
    private final Map<UUID, Boolean> teleportingByBarrier = new ConcurrentHashMap<>();
    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== TASK CO TICK ====================

    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        // ✅ EXPLOIT: Gracz POZA shellem - teleport NA ŚRODEK
                        if (isOutsideShell(loc, klatka)) {
                            doTeleport(player, center.clone());
                            feedback(player);
                            continue;
                        }

                        // ✅ Elytra - nie teleportuj
                        if (player.isGliding()) continue;

                        // ✅ Po animacji - MC blokuje
                        if (klatka.isAnimationComplete()) {
                            if (isInsideBuiltShell(loc, manager)) {
                                doTeleport(player, center.clone());
                                feedback(player);
                            }
                            continue;
                        }

                        // ✅ Sprawdź czy gracz DOTKNĄŁ bariery
                        Vector teleportDirection = getTeleportDirection(loc, klatka, manager, center);

                        if (teleportDirection != null) {
                            Location newLoc = loc.clone().add(teleportDirection);
                            newLoc.setYaw(loc.getYaw());
                            newLoc.setPitch(loc.getPitch());

                            doTeleport(player, newLoc);
                            feedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== TELEPORT Z FLAGĄ ====================

    private void doTeleport(Player player, Location destination) {
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        // ✅ Ustaw flagę przed teleportem
        teleportingByBarrier.put(player.getUniqueId(), true);
        player.teleport(destination);
        // ✅ Usuń flagę po teleporcie
        teleportingByBarrier.remove(player.getUniqueId());
    }

    // ==================== SPRAWDŹ CZY POZA SHELLEM ====================

    private boolean isOutsideShell(Location loc, ActiveHydroKlatka klatka) {
        return loc.distance(klatka.getCenter()) > klatka.getRadius() + 1.0;
    }

    // ==================== KIERUNEK TELEPORTU ====================

    private Vector getTeleportDirection(Location loc,
                                        ActiveHydroKlatka klatka,
                                        HydroKlatkaManager manager,
                                        Location center) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        // ==================== DÓŁ ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    for (int checkY = (int) Math.floor(py) + 1; checkY >= (int) Math.floor(py) - 5; checkY--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double barrierY = checkY + 0.5;

                        if (py <= barrierY + 0.05) {
                            return new Vector(0, TELEPORT_BOUNCE, 0);
                        }
                    }
                }
            }
        }

        // ==================== GÓRA ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    int headBlockY = (int) Math.floor(py + HEIGHT);
                    for (int checkY = headBlockY - 1; checkY <= headBlockY + 4; checkY++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double barrierY = checkY + 0.5;
                        double playerTop = py + HEIGHT;

                        if (playerTop >= barrierY - 0.05) {
                            return new Vector(0, -TELEPORT_BOUNCE, 0);
                        }
                    }
                }
            }
        }

        // ==================== PRAWO (+X) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] zBlocks = getFootBlocksZ(pz);
                for (int checkZ : zBlocks) {
                    for (int scanX = (int) Math.floor(px); scanX <= (int) Math.floor(px) + 4; scanX++) {
                        Location blockLoc = new Location(center.getWorld(), scanX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double barrierX = scanX + 0.5;
                        double playerRight = px + HALF_W;

                        if (playerRight >= barrierX - 0.05) {
                            return new Vector(-TELEPORT_BOUNCE, 0, 0);
                        }
                    }
                }
            }
        }

        // ==================== LEWO (-X) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] zBlocks = getFootBlocksZ(pz);
                for (int checkZ : zBlocks) {
                    for (int scanX = (int) Math.floor(px); scanX >= (int) Math.floor(px) - 4; scanX--) {
                        Location blockLoc = new Location(center.getWorld(), scanX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double barrierX = scanX + 0.5;
                        double playerLeft = px - HALF_W;

                        if (playerLeft <= barrierX + 0.05) {
                            return new Vector(TELEPORT_BOUNCE, 0, 0);
                        }
                    }
                }
            }
        }

        // ==================== PRZÓD (+Z) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] xBlocks = getFootBlocksX(px);
                for (int checkX : xBlocks) {
                    for (int scanZ = (int) Math.floor(pz); scanZ <= (int) Math.floor(pz) + 4; scanZ++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, scanZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double barrierZ = scanZ + 0.5;
                        double playerFront = pz + HALF_W;

                        if (playerFront >= barrierZ - 0.05) {
                            return new Vector(0, 0, -TELEPORT_BOUNCE);
                        }
                    }
                }
            }
        }

        // ==================== TYŁ (-Z) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] xBlocks = getFootBlocksX(px);
                for (int checkX : xBlocks) {
                    for (int scanZ = (int) Math.floor(pz); scanZ >= (int) Math.floor(pz) - 4; scanZ--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, scanZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double barrierZ = scanZ + 0.5;
                        double playerBack = pz - HALF_W;

                        if (playerBack <= barrierZ + 0.05) {
                            return new Vector(0, 0, TELEPORT_BOUNCE);
                        }
                    }
                }
            }
        }

        return null;
    }

    // ==================== HELPERY ====================

    private boolean rowOverlapsPlayer(double py, int by) {
        return (py + HEIGHT > by) && (py < by + 1.0);
    }

    private int[] getFootBlocksX(double px) {
        int left = (int) Math.floor(px - HALF_W);
        int right = (int) Math.floor(px + HALF_W);
        if (left == right) return new int[]{left};
        return new int[]{left, right};
    }

    private int[] getFootBlocksZ(double pz) {
        int back = (int) Math.floor(pz - HALF_W);
        int front = (int) Math.floor(pz + HALF_W);
        if (back == front) return new int[]{back};
        return new int[]{back, front};
    }

    private boolean isPlannedShellOnly(Location blockLoc,
                                       ActiveHydroKlatka klatka,
                                       HydroKlatkaManager manager) {
        if (manager.isShellBlock(blockLoc)) return false;
        if (klatka.isAnimationComplete()) return false;
        return klatka.isPlannedShellLocation(blockLoc);
    }

    private boolean isInsideBuiltShell(Location loc, HydroKlatkaManager manager) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        int minBX = (int) Math.floor(px - HALF_W);
        int maxBX = (int) Math.floor(px + HALF_W);
        int minBY = (int) Math.floor(py);
        int maxBY = (int) Math.floor(py + HEIGHT);
        int minBZ = (int) Math.floor(pz - HALF_W);
        int maxBZ = (int) Math.floor(pz + HALF_W);

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    Location blockLoc = new Location(loc.getWorld(), bx, by, bz);
                    if (manager.isShellBlock(blockLoc)) return true;
                }
            }
        }
        return false;
    }

    // ==================== FEEDBACK ====================

    private void feedback(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFeedback.get(player.getUniqueId());
        if (last != null && now - last < FEEDBACK_COOLDOWN_MS) return;

        lastFeedback.put(player.getUniqueId(), now);

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.5f, 1.2f);

        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&cNie możesz opuszczać podwodnej klatki!"),
                Title.Times.times(
                        Duration.ofMillis(0),
                        Duration.ofMillis(800),
                        Duration.ofMillis(200)
                )
        ));
    }

    // ==================== MOVE EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location to = event.getTo();
        if (to == null) return;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
        }
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // ✅ NASZ teleport - NIE BLOKUJ
        if (teleportingByBarrier.getOrDefault(player.getUniqueId(), false)) return;

        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (klatka.isAnimationComplete()) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        if (to.distance(klatka.getCenter()) > klatka.getRadius()) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
        teleportingByBarrier.remove(event.getPlayer().getUniqueId());
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastFeedback.clear();
        teleportingByBarrier.clear();
    }
}
