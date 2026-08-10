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

    // ✅ Bariera na zewnętrznej 1/4 bloku shella
    // Dla dodatniej strony bloku: 0.75 od wewnętrznej krawędzi
    // Dla ujemnej strony bloku: 0.25 od lewej / dolnej / tylnej krawędzi
    private static final double BARRIER_POSITIVE = 0.75;
    private static final double BARRIER_NEGATIVE = 0.25;

    // ✅ Minicofnięcie
    private static final double PUSHBACK = 0.10;

    // ✅ Jeśli gracz wszedł jeszcze głębiej niż bariera -> teleport na środek
    private static final double TELEPORT_POSITIVE = 0.90;
    private static final double TELEPORT_NEGATIVE = 0.10;

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

                        // Twardy exploit
                        if (loc.distance(center) > klatka.getRadius() + 2.5) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        // Jeśli shell już istnieje fizycznie, Minecraft go sam blokuje.
                        // My tylko ratujemy gracza, jeśli utknął w shellu.
                        if (klatka.isAnimationComplete()) {
                            if (isInsideBuiltShell(loc, klatka, manager)) {
                                teleportToCenter(player, center, true);
                            }
                            continue;
                        }

                        CollisionResult result = checkPlannedShellCollision(player, loc, klatka, manager, center);

                        if (result.teleportCenter) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        if (!result.clamped) continue;

                        Vector vel = player.getVelocity().clone();

                        if (result.blockX && ((result.newX < loc.getX() && vel.getX() > 0) || (result.newX > loc.getX() && vel.getX() < 0))) {
                            vel.setX(0);
                        }
                        if (result.blockY && ((result.newY < loc.getY() && vel.getY() > 0) || (result.newY > loc.getY() && vel.getY() < 0))) {
                            vel.setY(0);
                        }
                        if (result.blockZ && ((result.newZ < loc.getZ() && vel.getZ() > 0) || (result.newZ > loc.getZ() && vel.getZ() < 0))) {
                            vel.setZ(0);
                        }

                        player.setVelocity(vel);

                        Location safe = new Location(
                                loc.getWorld(),
                                result.newX,
                                result.newY,
                                result.newZ,
                                loc.getYaw(),
                                loc.getPitch()
                        );
                        player.teleport(safe);

                        vel = player.getVelocity().clone();
                        if (result.blockX && ((result.newX < loc.getX() && vel.getX() > 0) || (result.newX > loc.getX() && vel.getX() < 0))) {
                            vel.setX(0);
                        }
                        if (result.blockY && ((result.newY < loc.getY() && vel.getY() > 0) || (result.newY > loc.getY() && vel.getY() < 0))) {
                            vel.setY(0);
                        }
                        if (result.blockZ && ((result.newZ < loc.getZ() && vel.getZ() > 0) || (result.newZ > loc.getZ() && vel.getZ() < 0))) {
                            vel.setZ(0);
                        }
                        player.setVelocity(vel);

                        feedback(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== KOLIZJA Z PLANNED SHELL ====================

    private CollisionResult checkPlannedShellCollision(Player player,
                                                       Location loc,
                                                       ActiveHydroKlatka klatka,
                                                       HydroKlatkaManager manager,
                                                       Location center) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        CollisionResult result = new CollisionResult(px, py, pz);

        // Punkty hitboxa
        double[] xs = {px, px + HALF_W, px - HALF_W};
        double[] ys = {py, py + HEIGHT * 0.5, py + HEIGHT};
        double[] zs = {pz, pz + HALF_W, pz - HALF_W};

        for (double cx : xs) {
            for (double cy : ys) {
                for (double cz : zs) {
                    int bx = (int) Math.floor(cx);
                    int by = (int) Math.floor(cy);
                    int bz = (int) Math.floor(cz);

                    Location blockLoc = new Location(center.getWorld(), bx, by, bz);
                    if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                    double dx = (bx + 0.5) - center.getX();
                    double dy = (by + 0.5) - center.getY();
                    double dz = (bz + 0.5) - center.getZ();

                    double adx = Math.abs(dx);
                    double ady = Math.abs(dy);
                    double adz = Math.abs(dz);

                    // Dominująca oś danego bloku shella
                    if (adx >= ady && adx >= adz) {
                        // ==================== OŚ X ====================
                        if (dx > 0) {
                            // shell po prawej od środka
                            double penetration = cx - bx;

                            if (penetration >= TELEPORT_POSITIVE) {
                                result.teleportCenter = true;
                                return result;
                            }
                            if (penetration >= BARRIER_POSITIVE) {
                                result.clamped = true;
                                result.blockX = true;
                                result.newX = Math.min(result.newX, bx + BARRIER_POSITIVE - HALF_W - PUSHBACK);
                            }
                        } else {
                            // shell po lewej od środka
                            double penetration = (bx + 1.0) - cx;

                            if (penetration >= (1.0 - TELEPORT_NEGATIVE)) {
                                result.teleportCenter = true;
                                return result;
                            }
                            if (penetration >= (1.0 - BARRIER_NEGATIVE)) {
                                result.clamped = true;
                                result.blockX = true;
                                result.newX = Math.max(result.newX, bx + BARRIER_NEGATIVE + HALF_W + PUSHBACK);
                            }
                        }
                    } else if (ady >= adx && ady >= adz) {
                        // ==================== OŚ Y ====================
                        if (dy > 0) {
                            // shell nad środkiem
                            double penetration = cy - by;

                            if (penetration >= TELEPORT_POSITIVE) {
                                result.teleportCenter = true;
                                return result;
                            }
                            if (penetration >= BARRIER_POSITIVE) {
                                result.clamped = true;
                                result.blockY = true;
                                result.newY = Math.min(result.newY, by + BARRIER_POSITIVE - HEIGHT - PUSHBACK);
                            }
                        } else {
                            // shell pod środkiem
                            double penetration = (by + 1.0) - cy;

                            if (penetration >= (1.0 - TELEPORT_NEGATIVE)) {
                                result.teleportCenter = true;
                                return result;
                            }
                            if (penetration >= (1.0 - BARRIER_NEGATIVE)) {
                                result.clamped = true;
                                result.blockY = true;
                                // ✅ wyrzuć lekko do góry
                                result.newY = Math.max(result.newY, by + BARRIER_NEGATIVE + PUSHBACK);
                            }
                        }
                    } else {
                        // ==================== OŚ Z ====================
                        if (dz > 0) {
                            // shell z przodu
                            double penetration = cz - bz;

                            if (penetration >= TELEPORT_POSITIVE) {
                                result.teleportCenter = true;
                                return result;
                            }
                            if (penetration >= BARRIER_POSITIVE) {
                                result.clamped = true;
                                result.blockZ = true;
                                result.newZ = Math.min(result.newZ, bz + BARRIER_POSITIVE - HALF_W - PUSHBACK);
                            }
                        } else {
                            // shell z tyłu
                            double penetration = (bz + 1.0) - cz;

                            if (penetration >= (1.0 - TELEPORT_NEGATIVE)) {
                                result.teleportCenter = true;
                                return result;
                            }
                            if (penetration >= (1.0 - BARRIER_NEGATIVE)) {
                                result.clamped = true;
                                result.blockZ = true;
                                result.newZ = Math.max(result.newZ, bz + BARRIER_NEGATIVE + HALF_W + PUSHBACK);
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

    private boolean isInsideBuiltShell(Location loc,
                                       ActiveHydroKlatka klatka,
                                       HydroKlatkaManager manager) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        double[] xs = {px, px + HALF_W, px - HALF_W};
        double[] ys = {py, py + HEIGHT * 0.5, py + HEIGHT};
        double[] zs = {pz, pz + HALF_W, pz - HALF_W};

        for (double cx : xs) {
            for (double cy : ys) {
                for (double cz : zs) {
                    int bx = (int) Math.floor(cx);
                    int by = (int) Math.floor(cy);
                    int bz = (int) Math.floor(cz);
                    Location blockLoc = new Location(loc.getWorld(), bx, by, bz);
                    if (manager.isShellBlock(blockLoc)) {
                        return true;
                    }
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
        Location center = klatka.getCenter();

        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        double[] xs = {px, px + HALF_W, px - HALF_W};
        double[] ys = {py, py + HEIGHT * 0.5, py + HEIGHT};
        double[] zs = {pz, pz + HALF_W, pz - HALF_W};

        for (double cx : xs) {
            for (double cy : ys) {
                for (double cz : zs) {
                    int bx = (int) Math.floor(cx);
                    int by = (int) Math.floor(cy);
                    int bz = (int) Math.floor(cz);

                    Location blockLoc = new Location(center.getWorld(), bx, by, bz);
                    if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                    double dx = (bx + 0.5) - center.getX();
                    double dy = (by + 0.5) - center.getY();
                    double dz = (bz + 0.5) - center.getZ();

                    double adx = Math.abs(dx);
                    double ady = Math.abs(dy);
                    double adz = Math.abs(dz);

                    double penetration;
                    if (adx >= ady && adx >= adz) {
                        penetration = dx > 0 ? cx - bx : (bx + 1.0) - cx;
                        if (dx > 0 && penetration >= BARRIER_POSITIVE) return true;
                        if (dx < 0 && penetration >= (1.0 - BARRIER_NEGATIVE)) return true;
                    } else if (ady >= adx && ady >= adz) {
                        penetration = dy > 0 ? cy - by : (by + 1.0) - cy;
                        if (dy > 0 && penetration >= BARRIER_POSITIVE) return true;
                        if (dy < 0 && penetration >= (1.0 - BARRIER_NEGATIVE)) return true;
                    } else {
                        penetration = dz > 0 ? cz - bz : (bz + 1.0) - cz;
                        if (dz > 0 && penetration >= BARRIER_POSITIVE) return true;
                        if (dz < 0 && penetration >= (1.0 - BARRIER_NEGATIVE)) return true;
                    }
                }
            }
        }

        return false;
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
