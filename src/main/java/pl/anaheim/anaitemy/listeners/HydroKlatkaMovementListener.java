package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    private static final long SOUND_COOLDOWN_MS = 500;
    private final Map<UUID, Long> lastSoundTime = new ConcurrentHashMap<>();

    private static final double PLAYER_WIDTH_HALF = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double SHELL_BARRIER_DEPTH = 0.5;
    private static final double PUSHBACK_DISTANCE = 0.1;

    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== SHELL CHECK ====================

    private boolean isShell(int bx, int by, int bz,
                            ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        Location blockLoc = new Location(klatka.getCenter().getWorld(), bx, by, bz);
        if (manager.isShellBlock(blockLoc)) return true;
        if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc)) return true;
        return false;
    }

    // ==================== COLLISION CHECK ====================

    /**
     * ✅ Sprawdza kolizję hitboxa gracza z blokami shella.
     * Kolizja działa JEDNOKIERUNKOWO — blokuje TYLKO ruch NA ZEWNĄTRZ klatki.
     * Gracz MOŻE wejść do klatki z zewnątrz (bariera przepuszcza do wewnątrz).
     *
     * @param center centrum klatki — potrzebne do określenia kierunku "na zewnątrz"
     */
    private CollisionResult checkCollision(double px, double py, double pz,
                                            ActiveHydroKlatka klatka,
                                            HydroKlatkaManager manager,
                                            Location center) {
        CollisionResult result = new CollisionResult();

        int minBX = (int) Math.floor(px - PLAYER_WIDTH_HALF - 0.1);
        int maxBX = (int) Math.floor(px + PLAYER_WIDTH_HALF + 0.1);
        int minBY = (int) Math.floor(py - 0.1);
        int maxBY = (int) Math.floor(py + PLAYER_HEIGHT + 0.1);
        int minBZ = (int) Math.floor(pz - PLAYER_WIDTH_HALF - 0.1);
        int maxBZ = (int) Math.floor(pz + PLAYER_WIDTH_HALF + 0.1);

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    if (!isShell(bx, by, bz, klatka, manager)) continue;

                    // ✅ Środek tego bloku shella
                    double blockCenterX = bx + 0.5;
                    double blockCenterY = by + 0.5;
                    double blockCenterZ = bz + 0.5;

                    // ✅ Kierunek od centrum klatki do tego bloku
                    // Jeśli blok jest NA PRAWO od centrum → bariera blokuje ruch W PRAWO
                    // Jeśli blok jest POD centrum → bariera blokuje ruch W DÓŁ
                    double dirX = blockCenterX - center.getX();
                    double dirY = blockCenterY - center.getY();
                    double dirZ = blockCenterZ - center.getZ();

                    // ==================== Y DÓŁ ====================
                    // Blok jest PONIŻEJ centrum → bariera blokuje spadanie (ruch w dół)
                    if (dirY < 0) {
                        if (py < by + 1.0 && py > by + 1.0 - SHELL_BARRIER_DEPTH - 0.05) {
                            if (px + PLAYER_WIDTH_HALF > bx && px - PLAYER_WIDTH_HALF < bx + 1.0
                                    && pz + PLAYER_WIDTH_HALF > bz && pz - PLAYER_WIDTH_HALF < bz + 1.0) {
                                double penetration = (by + 1.0) - py;
                                if (penetration > 0) {
                                    result.blockedDown = true;
                                    result.safeY = Math.max(result.safeY, by + 1.0);
                                }
                            }
                        }
                    }

                    // ==================== Y GÓRA ====================
                    // Blok jest POWYŻEJ centrum → bariera blokuje lot w górę
                    if (dirY > 0) {
                        double headY = py + PLAYER_HEIGHT;
                        if (headY > by && headY < by + SHELL_BARRIER_DEPTH + 0.05) {
                            if (px + PLAYER_WIDTH_HALF > bx && px - PLAYER_WIDTH_HALF < bx + 1.0
                                    && pz + PLAYER_WIDTH_HALF > bz && pz - PLAYER_WIDTH_HALF < bz + 1.0) {
                                result.blockedUp = true;
                                result.safeYUp = Math.min(result.safeYUp, by - PLAYER_HEIGHT);
                            }
                        }
                    }

                    // ==================== X+ ====================
                    // Blok jest NA PRAWO od centrum → bariera blokuje ruch w prawo
                    if (dirX > 0) {
                        double rightEdge = px + PLAYER_WIDTH_HALF;
                        if (rightEdge > bx && rightEdge < bx + SHELL_BARRIER_DEPTH + 0.05) {
                            if (py < by + 1.0 && py + PLAYER_HEIGHT > by
                                    && pz + PLAYER_WIDTH_HALF > bz && pz - PLAYER_WIDTH_HALF < bz + 1.0) {
                                result.blockedXPos = true;
                                result.safeXPos = Math.min(result.safeXPos, bx - PLAYER_WIDTH_HALF);
                            }
                        }
                    }

                    // ==================== X- ====================
                    // Blok jest NA LEWO od centrum → bariera blokuje ruch w lewo
                    if (dirX < 0) {
                        double leftEdge = px - PLAYER_WIDTH_HALF;
                        if (leftEdge < bx + 1.0 && leftEdge > bx + 1.0 - SHELL_BARRIER_DEPTH - 0.05) {
                            if (py < by + 1.0 && py + PLAYER_HEIGHT > by
                                    && pz + PLAYER_WIDTH_HALF > bz && pz - PLAYER_WIDTH_HALF < bz + 1.0) {
                                result.blockedXNeg = true;
                                result.safeXNeg = Math.max(result.safeXNeg, bx + 1.0 + PLAYER_WIDTH_HALF);
                            }
                        }
                    }

                    // ==================== Z+ ====================
                    if (dirZ > 0) {
                        double frontEdge = pz + PLAYER_WIDTH_HALF;
                        if (frontEdge > bz && frontEdge < bz + SHELL_BARRIER_DEPTH + 0.05) {
                            if (py < by + 1.0 && py + PLAYER_HEIGHT > by
                                    && px + PLAYER_WIDTH_HALF > bx && px - PLAYER_WIDTH_HALF < bx + 1.0) {
                                result.blockedZPos = true;
                                result.safeZPos = Math.min(result.safeZPos, bz - PLAYER_WIDTH_HALF);
                            }
                        }
                    }

                    // ==================== Z- ====================
                    if (dirZ < 0) {
                        double backEdge = pz - PLAYER_WIDTH_HALF;
                        if (backEdge < bz + 1.0 && backEdge > bz + 1.0 - SHELL_BARRIER_DEPTH - 0.05) {
                            if (py < by + 1.0 && py + PLAYER_HEIGHT > by
                                    && px + PLAYER_WIDTH_HALF > bx && px - PLAYER_WIDTH_HALF < bx + 1.0) {
                                result.blockedZNeg = true;
                                result.safeZNeg = Math.max(result.safeZNeg, bz + 1.0 + PLAYER_WIDTH_HALF);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    // ==================== CLAMP TASK — CO TICK ====================

    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    double radius = klatka.getRadius();

                    for (UUID playerId : klatka.getTrappedPlayers()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        // EXPLOIT
                        if (loc.distance(center) > radius + 5.0) {
                            Location tp = center.clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                            player.setVelocity(new Vector(0, 0, 0));
                            continue;
                        }

                        // ✅ Sprawdź kolizje TYLKO dla trapped graczy
                        CollisionResult collision = checkCollision(
                                loc.getX(), loc.getY(), loc.getZ(),
                                klatka, manager, center);

                        if (!collision.hasCollision()) continue;

                        double newX = loc.getX();
                        double newY = loc.getY();
                        double newZ = loc.getZ();
                        Vector vel = player.getVelocity();
                        boolean showFeedback = false;

                        if (collision.blockedDown) {
                            newY = collision.safeY + PUSHBACK_DISTANCE;
                            if (vel.getY() < 0) vel.setY(0);
                            showFeedback = true;
                        }

                        if (collision.blockedUp) {
                            newY = collision.safeYUp - PUSHBACK_DISTANCE;
                            if (vel.getY() > 0) vel.setY(0);
                            showFeedback = true;
                        }

                        if (collision.blockedXPos) {
                            newX = collision.safeXPos - PUSHBACK_DISTANCE;
                            if (vel.getX() > 0) vel.setX(0);
                            showFeedback = true;
                        }

                        if (collision.blockedXNeg) {
                            newX = collision.safeXNeg + PUSHBACK_DISTANCE;
                            if (vel.getX() < 0) vel.setX(0);
                            showFeedback = true;
                        }

                        if (collision.blockedZPos) {
                            newZ = collision.safeZPos - PUSHBACK_DISTANCE;
                            if (vel.getZ() > 0) vel.setZ(0);
                            showFeedback = true;
                        }

                        if (collision.blockedZNeg) {
                            newZ = collision.safeZNeg + PUSHBACK_DISTANCE;
                            if (vel.getZ() < 0) vel.setZ(0);
                            showFeedback = true;
                        }

                        // ✅ Zeruj velocity PRZED teleportem
                        player.setVelocity(vel);

                        // ✅ Teleportuj
                        Location safeLoc = new Location(loc.getWorld(),
                                newX, newY, newZ, loc.getYaw(), loc.getPitch());
                        player.teleport(safeLoc);

                        // ✅ Zeruj velocity PO teleporcie
                        vel = player.getVelocity();
                        if (collision.blockedDown && vel.getY() < 0) vel.setY(0);
                        if (collision.blockedUp && vel.getY() > 0) vel.setY(0);
                        if (collision.blockedXPos && vel.getX() > 0) vel.setX(0);
                        if (collision.blockedXNeg && vel.getX() < 0) vel.setX(0);
                        if (collision.blockedZPos && vel.getZ() > 0) vel.setZ(0);
                        if (collision.blockedZNeg && vel.getZ() < 0) vel.setZ(0);
                        player.setVelocity(vel);

                        if (showFeedback) {
                            playBarrierFeedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== PLAYER MOVE EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        // ✅ Tylko trapped gracze mają barierę
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location center = klatka.getCenter();

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ Sprawdź kolizję na pozycji TO (tylko wyjście blokowane)
        CollisionResult collision = checkCollision(
                to.getX(), to.getY(), to.getZ(),
                klatka, manager, center);

        if (collision.hasCollision()) {
            CollisionResult fromCollision = checkCollision(
                    from.getX(), from.getY(), from.getZ(),
                    klatka, manager, center);

            if (!fromCollision.hasCollision()) {
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
            }

            playBarrierFeedback(player);
        }
    }

    // ==================== TELEPORT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        // ✅ Tylko trapped gracze mają barierę
        if (klatka == null) return;

        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < radius - 1.5) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        CollisionResult collision = checkCollision(
                to.getX(), to.getY(), to.getZ(),
                klatka, manager, center);

        if (collision.hasCollision() || to.distance(center) > radius) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== DŹWIĘK I SUBTITLE ====================

    private void playBarrierFeedback(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastSound = lastSoundTime.get(uuid);
        if (lastSound != null && now - lastSound < SOUND_COOLDOWN_MS) {
            return;
        }

        lastSoundTime.put(uuid, now);

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
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);

        if (klatka != null) {
            klatka.addOfflinePlayer(player.getUniqueId());
        }

        lastSoundTime.remove(player.getUniqueId());
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastSoundTime.clear();
    }

    // ==================== COLLISION RESULT ====================

    private static class CollisionResult {
        boolean blockedDown = false;
        boolean blockedUp = false;
        boolean blockedXPos = false;
        boolean blockedXNeg = false;
        boolean blockedZPos = false;
        boolean blockedZNeg = false;

        double safeY = Double.MIN_VALUE;
        double safeYUp = Double.MAX_VALUE;
        double safeXPos = Double.MAX_VALUE;
        double safeXNeg = Double.MIN_VALUE;
        double safeZPos = Double.MAX_VALUE;
        double safeZNeg = Double.MIN_VALUE;

        boolean hasCollision() {
            return blockedDown || blockedUp
                    || blockedXPos || blockedXNeg
                    || blockedZPos || blockedZNeg;
        }
    }
}
