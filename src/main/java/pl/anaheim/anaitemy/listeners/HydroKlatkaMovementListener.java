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

    private static final double PLAYER_HALF_WIDTH = 0.30;
    private static final double PLAYER_HEIGHT = 1.80;
    private static final double SHELL_BARRIER_PLANE = 0.50;
    private static final double PUSHBACK = 0.10;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== CLAMP TASK ====================

    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location center = klatka.getCenter();
                        Location loc = player.getLocation();

                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        if (loc.distance(center) > klatka.getRadius() + 5.0) {
                            Location tp = center.clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                            player.setVelocity(new Vector(0, 0, 0));
                            continue;
                        }

                        applySoftBarrier(player, klatka, manager);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== SOFT BARRIER ====================

    private void applySoftBarrier(Player player, ActiveHydroKlatka klatka,
                                   HydroKlatkaManager manager) {
        Location loc = player.getLocation();
        Location center = klatka.getCenter();

        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        double newX = px;
        double newY = py;
        double newZ = pz;

        boolean clamped = false;

        Vector velocity = player.getVelocity();
        boolean movingXPos = velocity.getX() > 0.001;
        boolean movingXNeg = velocity.getX() < -0.001;
        boolean movingYPos = velocity.getY() > 0.001;
        boolean movingYNeg = velocity.getY() < -0.001;
        boolean movingZPos = velocity.getZ() > 0.001;
        boolean movingZNeg = velocity.getZ() < -0.001;

        int minBX = (int) Math.floor(px - PLAYER_HALF_WIDTH - 0.02);
        int maxBX = (int) Math.floor(px + PLAYER_HALF_WIDTH + 0.02);
        int minBY = (int) Math.floor(py - 0.02);
        int maxBY = (int) Math.floor(py + PLAYER_HEIGHT + 0.02);
        int minBZ = (int) Math.floor(pz - PLAYER_HALF_WIDTH - 0.02);
        int maxBZ = (int) Math.floor(pz + PLAYER_HALF_WIDTH + 0.02);

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    if (!isShell(bx, by, bz, klatka, manager)) continue;

                    double dirX = (bx + 0.5) - center.getX();
                    double dirY = (by + 0.5) - center.getY();
                    double dirZ = (bz + 0.5) - center.getZ();

                    // DÓŁ
                    if (dirY < 0 && movingYNeg) {
                        boolean ok = (px + PLAYER_HALF_WIDTH > bx && px - PLAYER_HALF_WIDTH < bx + 1.0)
                                && (pz + PLAYER_HALF_WIDTH > bz && pz - PLAYER_HALF_WIDTH < bz + 1.0);
                        if (ok && py < by + SHELL_BARRIER_PLANE) {
                            newY = Math.max(newY, by + SHELL_BARRIER_PLANE + PUSHBACK);
                            clamped = true;
                        }
                    }

                    // GÓRA
                    if (dirY > 0 && movingYPos) {
                        boolean ok = (px + PLAYER_HALF_WIDTH > bx && px - PLAYER_HALF_WIDTH < bx + 1.0)
                                && (pz + PLAYER_HALF_WIDTH > bz && pz - PLAYER_HALF_WIDTH < bz + 1.0);
                        if (ok && py + PLAYER_HEIGHT > by + SHELL_BARRIER_PLANE) {
                            newY = Math.min(newY, by + SHELL_BARRIER_PLANE - PLAYER_HEIGHT - PUSHBACK);
                            clamped = true;
                        }
                    }

                    // PRAWO +X
                    if (dirX > 0 && movingXPos) {
                        boolean ok = (py < by + 1.0 && py + PLAYER_HEIGHT > by)
                                && (pz + PLAYER_HALF_WIDTH > bz && pz - PLAYER_HALF_WIDTH < bz + 1.0);
                        if (ok && px + PLAYER_HALF_WIDTH > bx + SHELL_BARRIER_PLANE) {
                            newX = Math.min(newX, bx + SHELL_BARRIER_PLANE - PLAYER_HALF_WIDTH - PUSHBACK);
                            clamped = true;
                        }
                    }

                    // LEWO -X
                    if (dirX < 0 && movingXNeg) {
                        boolean ok = (py < by + 1.0 && py + PLAYER_HEIGHT > by)
                                && (pz + PLAYER_HALF_WIDTH > bz && pz - PLAYER_HALF_WIDTH < bz + 1.0);
                        if (ok && px - PLAYER_HALF_WIDTH < bx + SHELL_BARRIER_PLANE) {
                            newX = Math.max(newX, bx + SHELL_BARRIER_PLANE + PLAYER_HALF_WIDTH + PUSHBACK);
                            clamped = true;
                        }
                    }

                    // PRZÓD +Z
                    if (dirZ > 0 && movingZPos) {
                        boolean ok = (py < by + 1.0 && py + PLAYER_HEIGHT > by)
                                && (px + PLAYER_HALF_WIDTH > bx && px - PLAYER_HALF_WIDTH < bx + 1.0);
                        if (ok && pz + PLAYER_HALF_WIDTH > bz + SHELL_BARRIER_PLANE) {
                            newZ = Math.min(newZ, bz + SHELL_BARRIER_PLANE - PLAYER_HALF_WIDTH - PUSHBACK);
                            clamped = true;
                        }
                    }

                    // TYŁ -Z
                    if (dirZ < 0 && movingZNeg) {
                        boolean ok = (py < by + 1.0 && py + PLAYER_HEIGHT > by)
                                && (px + PLAYER_HALF_WIDTH > bx && px - PLAYER_HALF_WIDTH < bx + 1.0);
                        if (ok && pz - PLAYER_HALF_WIDTH < bz + SHELL_BARRIER_PLANE) {
                            newZ = Math.max(newZ, bz + SHELL_BARRIER_PLANE + PLAYER_HALF_WIDTH + PUSHBACK);
                            clamped = true;
                        }
                    }
                }
            }
        }

        if (!clamped) return;

        // ✅ Zeruj velocity przed teleportem
        Vector corrected = velocity.clone();
        if (newX != px) corrected.setX(0);
        if (newY != py) corrected.setY(0);
        if (newZ != pz) corrected.setZ(0);
        player.setVelocity(corrected);

        // ✅ Teleportuj
        Location safe = new Location(loc.getWorld(), newX, newY, newZ,
                loc.getYaw(), loc.getPitch());
        player.teleport(safe);

        // ✅ Zeruj velocity po teleporcie
        corrected = player.getVelocity().clone();
        if (newY < py && corrected.getY() < 0) corrected.setY(0);
        if (newY > py && corrected.getY() > 0) corrected.setY(0);
        if (newX < px && corrected.getX() > 0) corrected.setX(0);
        if (newX > px && corrected.getX() < 0) corrected.setX(0);
        if (newZ < pz && corrected.getZ() > 0) corrected.setZ(0);
        if (newZ > pz && corrected.getZ() < 0) corrected.setZ(0);
        player.setVelocity(corrected);

        playBarrierFeedback(player);
    }

    // ==================== SHELL CHECK ====================

    private boolean isShell(int bx, int by, int bz,
                            ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        Location blockLoc = new Location(klatka.getCenter().getWorld(), bx, by, bz);
        if (manager.isShellBlock(blockLoc)) return true;
        return !klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc);
    }

    // ==================== MOVE EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
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

        if (wouldExit(from, to, klatka, manager)) {
            Location stuck = from.clone();
            stuck.setYaw(to.getYaw());
            stuck.setPitch(to.getPitch());
            event.setTo(stuck);
            playBarrierFeedback(player);
        }
    }

    private boolean wouldExit(Location from, Location to,
                              ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        Location center = klatka.getCenter();

        Vector move = to.toVector().subtract(from.toVector());
        if (move.lengthSquared() < 0.0001) return false;

        double px = to.getX();
        double py = to.getY();
        double pz = to.getZ();

        int minBX = (int) Math.floor(px - PLAYER_HALF_WIDTH - 0.02);
        int maxBX = (int) Math.floor(px + PLAYER_HALF_WIDTH + 0.02);
        int minBY = (int) Math.floor(py - 0.02);
        int maxBY = (int) Math.floor(py + PLAYER_HEIGHT + 0.02);
        int minBZ = (int) Math.floor(pz - PLAYER_HALF_WIDTH - 0.02);
        int maxBZ = (int) Math.floor(pz + PLAYER_HALF_WIDTH + 0.02);

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    if (!isShell(bx, by, bz, klatka, manager)) continue;

                    double dirX = (bx + 0.5) - center.getX();
                    double dirY = (by + 0.5) - center.getY();
                    double dirZ = (bz + 0.5) - center.getZ();

                    if (dirX > 0 && move.getX() > 0
                            && px + PLAYER_HALF_WIDTH > bx + SHELL_BARRIER_PLANE) return true;
                    if (dirX < 0 && move.getX() < 0
                            && px - PLAYER_HALF_WIDTH < bx + SHELL_BARRIER_PLANE) return true;
                    if (dirY > 0 && move.getY() > 0
                            && py + PLAYER_HEIGHT > by + SHELL_BARRIER_PLANE) return true;
                    if (dirY < 0 && move.getY() < 0
                            && py < by + SHELL_BARRIER_PLANE) return true;
                    if (dirZ > 0 && move.getZ() > 0
                            && pz + PLAYER_HALF_WIDTH > bz + SHELL_BARRIER_PLANE) return true;
                    if (dirZ < 0 && move.getZ() < 0
                            && pz - PLAYER_HALF_WIDTH < bz + SHELL_BARRIER_PLANE) return true;
                }
            }
        }

        return false;
    }

    // ==================== TELEPORT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location to = event.getTo();
        if (to == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }

        if (wouldExit(player.getLocation(), to, klatka, manager)) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== FEEDBACK ====================

    private void playBarrierFeedback(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long last = lastFeedback.get(uuid);
        if (last != null && now - last < FEEDBACK_COOLDOWN_MS) return;

        lastFeedback.put(uuid, now);

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
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastFeedback.clear();
    }
}
