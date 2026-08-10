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

    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    private static final double HALF_W = 0.30;
    private static final double HEIGHT = 1.80;

    private static final double BARRIER_OFFSET_HORIZONTAL = 0.5;
    private static final double BARRIER_OFFSET_DOWN = 1.35;   // zwiększone - bliżej centrum
    private static final double BARRIER_OFFSET_UP = 0.1;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
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

                        if (!klatka.getTrappedPlayers().contains(player.getUniqueId())) continue;

                        // Twardy exploit check
                        if (loc.distance(center) > klatka.getRadius() + 2.5) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        // Po zakończeniu animacji - MC blokuje fizycznie
                        if (klatka.isAnimationComplete()) {
                            if (isInsideBuiltShell(loc, manager)) {
                                teleportToCenter(player, center, true);
                            }
                            continue;
                        }

                        // ✅ Elytra - pomiń KAŻDY lot elytrą (nie tylko szybki)
                        if (player.isGliding()) continue;

                        CollisionResult result = checkPlannedShellCollision(loc, klatka, manager, center);

                        if (result.teleportCenter) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        if (!result.clamped) continue;

                        Vector vel = player.getVelocity().clone();

                        // ✅ Spadanie - TELEPORT + podwójne zerowanie velocity
                        if (result.blockY && vel.getY() <= 0) {
                            player.setVelocity(new Vector(0, 0, 0));

                            Location safe = new Location(
                                    loc.getWorld(),
                                    result.newX,
                                    result.newY,
                                    result.newZ,
                                    loc.getYaw(),
                                    loc.getPitch()
                            );
                            player.teleport(safe);
                            player.setVelocity(new Vector(0, 0, 0));

                            feedback(player);
                            continue;
                        }

                        // Reszta kolizji (boki, góra)
                        player.setVelocity(new Vector(0, 0, 0));

                        Location old = player.getLocation();
                        Location safe = new Location(
                                old.getWorld(),
                                result.newX,
                                result.newY,
                                result.newZ,
                                old.getYaw(),
                                old.getPitch()
                        );
                        player.teleport(safe);

                        Vector bounce = new Vector(0, 0, 0);
                        if (result.blockX) {
                            double dir = center.getX() - result.newX;
                            bounce.setX(Math.signum(dir) * 0.02);
                        }
                        if (result.blockZ) {
                            double dir = center.getZ() - result.newZ;
                            bounce.setZ(Math.signum(dir) * 0.02);
                        }
                        if (result.blockY && vel.getY() > 0) {
                            bounce.setY(-0.02);
                        }
                        player.setVelocity(bounce);

                        feedback(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== KOLIZJA Z PLANNED SHELL ====================

    private CollisionResult checkPlannedShellCollision(Location loc,
                                                       ActiveHydroKlatka klatka,
                                                       HydroKlatkaManager manager,
                                                       Location center) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        CollisionResult result = new CollisionResult(px, py, pz);

        // ==================== OŚ Y: DÓŁ ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    for (int checkY = (int) Math.floor(py); checkY >= (int) Math.floor(py) - 8; checkY--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellTop = checkY + 1.0;
                        double barrierY = shellTop + BARRIER_OFFSET_DOWN;

                        if (py <= barrierY) {
                            result.clamped = true;
                            result.blockY  = true;
                            result.newY    = Math.max(result.newY, barrierY);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== OŚ Y: GÓRA ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    int headBlockY = (int) Math.floor(py + HEIGHT);
                    for (int checkY = headBlockY; checkY <= headBlockY + 6; checkY++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellBottom = checkY;
                        double barrierMaxY = shellBottom - HEIGHT - BARRIER_OFFSET_UP;
                        double playerTop   = py + HEIGHT;

                        if (playerTop >= shellBottom - BARRIER_OFFSET_UP) {
                            result.clamped = true;
                            result.blockY  = true;
                            result.newY    = Math.min(result.newY, barrierMaxY);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== OŚ X: PRAWO (+X) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] zBlocks = getFootBlocksZ(pz);
                for (int checkZ : zBlocks) {
                    int startX = (int) Math.floor(px + HALF_W);

                    for (int scanX = startX; scanX <= startX + 8; scanX++) {
                        Location blockLoc = new Location(center.getWorld(), scanX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellLeft = scanX;
                        double barrierX  = shellLeft - BARRIER_OFFSET_HORIZONTAL;

                        double playerRight = px + HALF_W;
                        if (playerRight >= barrierX) {
                            result.clamped = true;
                            result.blockX  = true;
                            double safeX = barrierX - HALF_W;
                            result.newX  = Math.min(result.newX, safeX);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== OŚ X: LEWO (-X) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] zBlocks = getFootBlocksZ(pz);
                for (int checkZ : zBlocks) {
                    int startX = (int) Math.floor(px - HALF_W);

                    for (int scanX = startX; scanX >= startX - 8; scanX--) {
                        Location blockLoc = new Location(center.getWorld(), scanX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellRight = scanX + 1.0;
                        double barrierX  = shellRight + BARRIER_OFFSET_HORIZONTAL;

                        double playerLeft = px - HALF_W;
                        if (playerLeft <= barrierX) {
                            result.clamped = true;
                            result.blockX  = true;
                            double safeX = barrierX + HALF_W;
                            result.newX  = Math.max(result.newX, safeX);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== OŚ Z: PRZÓD (+Z) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] xBlocks = getFootBlocksX(px);
                for (int checkX : xBlocks) {
                    int startZ = (int) Math.floor(pz + HALF_W);

                    for (int scanZ = startZ; scanZ <= startZ + 8; scanZ++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, scanZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellFront = scanZ;
                        double barrierZ   = shellFront - BARRIER_OFFSET_HORIZONTAL;

                        double playerFront = pz + HALF_W;
                        if (playerFront >= barrierZ) {
                            result.clamped = true;
                            result.blockZ  = true;
                            double safeZ = barrierZ - HALF_W;
                            result.newZ  = Math.min(result.newZ, safeZ);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== OŚ Z: TYŁ (-Z) ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT - 0.01);

            for (int by = minBY; by <= maxBY; by++) {
                if (!rowOverlapsPlayer(py, by)) continue;

                int[] xBlocks = getFootBlocksX(px);
                for (int checkX : xBlocks) {
                    int startZ = (int) Math.floor(pz - HALF_W);

                    for (int scanZ = startZ; scanZ >= startZ - 8; scanZ--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, scanZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellBack = scanZ + 1.0;
                        double barrierZ  = shellBack + BARRIER_OFFSET_HORIZONTAL;

                        double playerBack = pz - HALF_W;
                        if (playerBack <= barrierZ) {
                            result.clamped = true;
                            result.blockZ  = true;
                            double safeZ = barrierZ + HALF_W;
                            result.newZ  = Math.max(result.newZ, safeZ);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== EXPLOIT ====================
        if (loc.distance(center) > klatka.getRadius() + 0.5) {
            result.teleportCenter = true;
        }

        return result;
    }

    // ==================== HELPERY ====================

    private boolean rowOverlapsPlayer(double py, int by) {
        return (py + HEIGHT > by) && (py < by + 1.0);
    }

    private int[] getFootBlocksX(double px) {
        int left  = (int) Math.floor(px - HALF_W);
        int right = (int) Math.floor(px + HALF_W);
        if (left == right) return new int[]{left};
        return new int[]{left, right};
    }

    private int[] getFootBlocksZ(double pz) {
        int back  = (int) Math.floor(pz - HALF_W);
        int front = (int) Math.floor(pz + HALF_W);
        if (back == front) return new int[]{back};
        return new int[]{back, front};
    }

    // ==================== SHELL CHECKI ====================

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

    // ==================== TELEPORT NA ŚRODEK ====================

    private void teleportToCenter(Player player, Location center, boolean zeroVelocity) {
        Location current = player.getLocation();
        Location tp = center.clone();
        tp.setYaw(current.getYaw());
        tp.setPitch(current.getPitch());

        if (zeroVelocity) player.setVelocity(new Vector(0, 0, 0));
        player.teleport(tp);
        if (zeroVelocity) player.setVelocity(new Vector(0, 0, 0));
        feedback(player);
    }

    // ==================== MOVE EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // ✅ Elytra - nie blokuj w MoveEvent
        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (klatka.isAnimationComplete()) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        if (wouldHitPlannedShell(to, klatka, manager)
                && !wouldHitPlannedShell(from, klatka, manager)) {
            Location stuck = from.clone();
            stuck.setYaw(to.getYaw());
            stuck.setPitch(to.getPitch());
            event.setTo(stuck);
            feedback(player);
        }
    }

    private boolean wouldHitPlannedShell(Location loc,
                                         ActiveHydroKlatka klatka,
                                         HydroKlatkaManager manager) {
        CollisionResult result = checkPlannedShellCollision(loc, klatka, manager, klatka.getCenter());
        return result.clamped || result.teleportCenter;
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        if (to.distance(klatka.getCenter()) > klatka.getRadius()) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== FEEDBACK ====================

    private void feedback(Player player) {
        long now  = System.currentTimeMillis();
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

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastFeedback.clear();
    }

    // ==================== RESULT ====================

    private static class CollisionResult {
        boolean clamped = false;
        boolean blockX  = false;
        boolean blockY  = false;
        boolean blockZ  = false;
        boolean teleportCenter = false;

        double newX;
        double newY;
        double newZ;

        CollisionResult(double x, double y, double z) {
            this.newX = x;
            this.newY = y;
            this.newZ = z;
        }
    }
}
