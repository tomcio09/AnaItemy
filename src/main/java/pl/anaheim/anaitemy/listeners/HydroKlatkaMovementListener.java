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

    // ✅ Bariera na zewnętrznej 1/4 bloku
    private static final double BARRIER_POSITIVE = 0.60;
    private static final double BARRIER_NEGATIVE = 0.40;

    // ✅ Głębiej niż to -> teleport na środek
    private static final double TELEPORT_POSITIVE = 0.85;
    private static final double TELEPORT_NEGATIVE = 0.15;

    // ✅ Minicofnięcie
    private static final double PUSHBACK = 0.35;

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

                        // ✅ Twardy exploit
                        if (loc.distance(center) > klatka.getRadius() + 2.5) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        // ✅ Po animacji MC blokuje shell, my tylko ratujemy gdy gracz utknie
                        if (klatka.isAnimationComplete()) {
                            if (isInsideBuiltShell(loc, manager)) {
                                teleportToCenter(player, center, true);
                            }
                            continue;
                        }

                        CollisionResult result = checkPlannedShellCollision(loc, klatka, manager, center);

                        if (result.teleportCenter) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        if (!result.clamped) continue;

                        Location old = player.getLocation();
                        Vector vel = player.getVelocity().clone();

                        if (result.blockX && ((result.newX < old.getX() && vel.getX() > 0) || (result.newX > old.getX() && vel.getX() < 0))) {
                            vel.setX(0);
                        }
                        if (result.blockY && ((result.newY < old.getY() && vel.getY() > 0) || (result.newY > old.getY() && vel.getY() < 0))) {
                            vel.setY(0);
                        }
                        if (result.blockZ && ((result.newZ < old.getZ() && vel.getZ() > 0) || (result.newZ > old.getZ() && vel.getZ() < 0))) {
                            vel.setZ(0);
                        }
                        player.setVelocity(vel);

                        Location safe = new Location(
                                old.getWorld(),
                                result.newX,
                                result.newY,
                                result.newZ,
                                old.getYaw(),
                                old.getPitch()
                        );
                        player.teleport(safe);

                        vel = player.getVelocity().clone();
                        if (result.blockX) vel.setX(0);
                        if (result.blockY) vel.setY(0);
                        if (result.blockZ) vel.setZ(0);
                        player.setVelocity(vel);

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

        int minBX = (int) Math.floor(px - HALF_W) - 1;
        int maxBX = (int) Math.floor(px + HALF_W) + 1;
        int minBY = (int) Math.floor(py) - 2;
        int maxBY = (int) Math.floor(py + HEIGHT) + 1;
        int minBZ = (int) Math.floor(pz - HALF_W) - 1;
        int maxBZ = (int) Math.floor(pz + HALF_W) + 1;

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    Location blockLoc = new Location(center.getWorld(), bx, by, bz);
                    if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                    // ✅ Hitbox gracza: [px-0.3, px+0.3] x [py, py+1.8] x [pz-0.3, pz+0.3]
                    // Blok: [bx, bx+1] x [by, by+1] x [bz, bz+1]

                    boolean overlapX = (px + HALF_W > bx) && (px - HALF_W < bx + 1.0);
                    boolean overlapZ = (pz + HALF_W > bz) && (pz - HALF_W < bz + 1.0);

                    // ==================== DÓŁ ====================
                    // Stopy gracza (py) spadają w blok shella
                    // Bariera na górnej krawędzi bloku (by + 1.0) minus offset
                    if (overlapX && overlapZ) {
                        double blockTop = by + 1.0;
                        // Gracz jest NAD blokiem lub wchodzi w niego od góry
                        if (py < blockTop && py > by - 0.5) {
                            // Stopy gracza weszły poniżej górnej krawędzi bloku
                            double penetrationFromTop = blockTop - py;

                            if (penetrationFromTop > 0 && penetrationFromTop < 1.5) {
                                if (penetrationFromTop > 0.6) {
                                    // Głęboko — teleport na środek
                                    result.teleportCenter = true;
                                    return result;
                                }
                                // ✅ Cofnij na górę bloku + pushback
                                result.clamped = true;
                                result.blockY = true;
                                double safeY = blockTop + PUSHBACK;
                                result.newY = Math.max(result.newY, safeY);
                            }
                        }
                    }

                    // ==================== GÓRA ====================
                    // Głowa gracza (py + HEIGHT) uderza w blok shella od dołu
                    if (overlapX && overlapZ) {
                        double blockBottom = by;
                        double playerTop = py + HEIGHT;

                        if (playerTop > blockBottom && playerTop < by + 1.5) {
                            double penetrationFromBottom = playerTop - blockBottom;

                            if (penetrationFromBottom > 0 && penetrationFromBottom < 1.5) {
                                if (penetrationFromBottom > 0.6) {
                                    result.teleportCenter = true;
                                    return result;
                                }
                                result.clamped = true;
                                result.blockY = true;
                                double safeY = blockBottom - HEIGHT - PUSHBACK;
                                result.newY = Math.min(result.newY, safeY);
                            }
                        }
                    }

                    boolean overlapY = (py + HEIGHT > by) && (py < by + 1.0);

                    // ==================== PRAWO (+X) ====================
                    // Prawa krawędź hitboxa wchodzi w blok shella od lewej strony
                    if (overlapY && overlapZ) {
                        double playerRight = px + HALF_W;
                        double blockLeft = bx;

                        if (playerRight > blockLeft && playerRight < bx + 1.0) {
                            double penetration = playerRight - blockLeft;

                            if (penetration > 0 && penetration < 1.0) {
                                // Sprawdź czy gracz idzie OD ŚRODKA (na zewnątrz)
                                double dx = (bx + 0.5) - center.getX();
                                if (dx > 0) {
                                    if (penetration > 0.6) {
                                        result.teleportCenter = true;
                                        return result;
                                    }
                                    result.clamped = true;
                                    result.blockX = true;
                                    double safeX = blockLeft - HALF_W - PUSHBACK;
                                    result.newX = Math.min(result.newX, safeX);
                                }
                            }
                        }
                    }

                    // ==================== LEWO (-X) ====================
                    if (overlapY && overlapZ) {
                        double playerLeft = px - HALF_W;
                        double blockRight = bx + 1.0;

                        if (playerLeft < blockRight && playerLeft > bx) {
                            double penetration = blockRight - playerLeft;

                            if (penetration > 0 && penetration < 1.0) {
                                double dx = (bx + 0.5) - center.getX();
                                if (dx < 0) {
                                    if (penetration > 0.6) {
                                        result.teleportCenter = true;
                                        return result;
                                    }
                                    result.clamped = true;
                                    result.blockX = true;
                                    double safeX = blockRight + HALF_W + PUSHBACK;
                                    result.newX = Math.max(result.newX, safeX);
                                }
                            }
                        }
                    }

                    // ==================== PRZÓD (+Z) ====================
                    if (overlapY && overlapX) {
                        double playerFront = pz + HALF_W;
                        double blockFront = bz;

                        if (playerFront > blockFront && playerFront < bz + 1.0) {
                            double penetration = playerFront - blockFront;

                            if (penetration > 0 && penetration < 1.0) {
                                double dz = (bz + 0.5) - center.getZ();
                                if (dz > 0) {
                                    if (penetration > 0.6) {
                                        result.teleportCenter = true;
                                        return result;
                                    }
                                    result.clamped = true;
                                    result.blockZ = true;
                                    double safeZ = blockFront - HALF_W - PUSHBACK;
                                    result.newZ = Math.min(result.newZ, safeZ);
                                }
                            }
                        }
                    }

                    // ==================== TYŁ (-Z) ====================
                    if (overlapY && overlapX) {
                        double playerBack = pz - HALF_W;
                        double blockBack = bz + 1.0;

                        if (playerBack < blockBack && playerBack > bz) {
                            double penetration = blockBack - playerBack;

                            if (penetration > 0 && penetration < 1.0) {
                                double dz = (bz + 0.5) - center.getZ();
                                if (dz < 0) {
                                    if (penetration > 0.6) {
                                        result.teleportCenter = true;
                                        return result;
                                    }
                                    result.clamped = true;
                                    result.blockZ = true;
                                    double safeZ = blockBack + HALF_W + PUSHBACK;
                                    result.newZ = Math.max(result.newZ, safeZ);
                                }
                            }
                        }
                    }
                }
            }
        }

        return result;
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

        if (!klatka.isAnimationComplete()
                && wouldHitPlannedShell(to, klatka, manager)
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
        boolean blockX = false;
        boolean blockY = false;
        boolean blockZ = false;
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
