package pl.anaheim.anaitemy.listeners;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    // ✅ Anti-spam dźwięku / subtitle
    private static final long FEEDBACK_COOLDOWN_MS = 500L;
    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();

    // ✅ Hitbox gracza
    private static final double HALF_W = 0.30;
    private static final double HEIGHT = 1.80;

    // ✅ Bariera na ZEWNĘTRZNEJ 1/4 bloku shella
    // Dla strony "wewnętrznej" shell block:
    // - plane = 0.75 dla stron dodatnich (+X,+Y,+Z)
    // - plane = 0.25 dla stron ujemnych (-X,-Y,-Z)
    private static final double OUTER_QUARTER_POSITIVE = 0.75;
    private static final double OUTER_QUARTER_NEGATIVE = 0.25;

    // ✅ Minimalne cofnięcie do środka po kolizji
    private static final double PUSHBACK = 0.10;

    // ✅ Gdy gracz jest już "za bardzo" poza shell plane → teleport na środek
    private static final double TELEPORT_EXTRA_PENETRATION = 0.18;

    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== TASK CO TICK ====================

    /**
     * ✅ Główna logika.
     * Co tick sprawdza TYLKO trappedPlayers.
     * Nie stawia żadnych bloków.
     * Działa na planned shell i built shell.
     */
    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    double radius = klatka.getRadius();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        // ✅ Twardy exploit / bug — daleko poza klatką
                        if (loc.distance(center) > radius + 2.5) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        CollisionResult result = checkVirtualBarrier(player, klatka, manager, center);

                        if (result.teleportCenter) {
                            teleportToCenter(player, center, true);
                            continue;
                        }

                        if (!result.clamped) continue;

                        // ✅ Ustaw velocity PRZED teleportem
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

                        // ✅ Miniteleportacja do środka / do góry / do tyłu
                        Location safe = new Location(
                                loc.getWorld(),
                                result.newX,
                                result.newY,
                                result.newZ,
                                loc.getYaw(),
                                loc.getPitch()
                        );
                        player.teleport(safe);

                        // ✅ Ustaw velocity PO teleporcie jeszcze raz
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

    // ==================== VIRTUAL BARRIER ====================

    /**
     * ✅ Serce mechaniki.
     *
     * Działa tak:
     * - bierze hitbox gracza
     * - sprawdza czy któryś punkt hitboxa wszedł w shell block
     * - ale blokuje tylko jeśli wszedł GŁĘBIEJ niż "bariera" na zewnętrznej 1/4
     * - jeśli bardzo głęboko wszedł → teleport na środek
     *
     * Bariera działa tylko dla trappedPlayers.
     * Gracze spoza trappedPlayers nie są tu sprawdzani.
     */
    private CollisionResult checkVirtualBarrier(Player player,
                                                ActiveHydroKlatka klatka,
                                                HydroKlatkaManager manager,
                                                Location center) {
        Location loc = player.getLocation();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        CollisionResult result = new CollisionResult(px, py, pz);

        // Punkty hitboxa do sprawdzenia
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
                    if (!isShellOrPlannedShell(blockLoc, klatka, manager)) continue;

                    // ✅ Określ dominującą oś tego shell bloku względem centrum
                    double dx = (bx + 0.5) - center.getX();
                    double dy = (by + 0.5) - center.getY();
                    double dz = (bz + 0.5) - center.getZ();

                    double adx = Math.abs(dx);
                    double ady = Math.abs(dy);
                    double adz = Math.abs(dz);

                    // ==================== OŚ X ====================
                    if (adx >= ady && adx >= adz) {
                        if (dx > 0) {
                            // Shell po prawej stronie klatki
                            // Wewnętrzna strona bloku = x=bx
                            // Bariera = bx+0.75
                            double rightEdge = cx;
                            double penetration = rightEdge - bx;

                            if (penetration >= OUTER_QUARTER_POSITIVE + TELEPORT_EXTRA_PENETRATION) {
                                result.teleportCenter = true;
                                return result;
                            }

                            if (penetration >= OUTER_QUARTER_POSITIVE) {
                                result.clamped = true;
                                result.blockX = true;
                                // cofnij do bx+0.75-0.10
                                result.newX = Math.min(result.newX, bx + OUTER_QUARTER_POSITIVE - PUSHBACK);
                            }
                        } else {
                            // Shell po lewej stronie klatki
                            // Wewnętrzna strona bloku = x=bx+1
                            // Bariera = bx+0.25
                            double leftEdge = cx;
                            double penetration = (bx + 1.0) - leftEdge;

                            if (penetration >= (1.0 - OUTER_QUARTER_NEGATIVE) + TELEPORT_EXTRA_PENETRATION) {
                                result.teleportCenter = true;
                                return result;
                            }

                            if (penetration >= (1.0 - OUTER_QUARTER_NEGATIVE)) {
                                result.clamped = true;
                                result.blockX = true;
                                result.newX = Math.max(result.newX, bx + OUTER_QUARTER_NEGATIVE + PUSHBACK);
                            }
                        }
                    }

                    // ==================== OŚ Y ====================
                    else if (ady >= adx && ady >= adz) {
                        if (dy > 0) {
                            // Shell nad środkiem klatki
                            // Wewnętrzna strona bloku = y=by
                            // Bariera = by+0.75
                            double head = cy;
                            double penetration = head - by;

                            if (penetration >= OUTER_QUARTER_POSITIVE + TELEPORT_EXTRA_PENETRATION) {
                                result.teleportCenter = true;
                                return result;
                            }

                            if (penetration >= OUTER_QUARTER_POSITIVE) {
                                result.clamped = true;
                                result.blockY = true;
                                result.newY = Math.min(result.newY, by + OUTER_QUARTER_POSITIVE - HEIGHT - PUSHBACK);
                            }
                        } else {
                            // Shell pod środkiem klatki
                            // Wewnętrzna strona bloku = y=by+1
                            // Bariera = by+0.25
                            double feet = cy;
                            double penetration = (by + 1.0) - feet;

                            if (penetration >= (1.0 - OUTER_QUARTER_NEGATIVE) + TELEPORT_EXTRA_PENETRATION) {
                                result.teleportCenter = true;
                                return result;
                            }

                            if (penetration >= (1.0 - OUTER_QUARTER_NEGATIVE)) {
                                result.clamped = true;
                                result.blockY = true;
                                // ✅ Wypycha do góry nad shell
                                result.newY = Math.max(result.newY, by + OUTER_QUARTER_NEGATIVE + PUSHBACK);
                            }
                        }
                    }

                    // ==================== OŚ Z ====================
                    else {
                        if (dz > 0) {
                            // Shell z przodu
                            double front = cz;
                            double penetration = front - bz;

                            if (penetration >= OUTER_QUARTER_POSITIVE + TELEPORT_EXTRA_PENETRATION) {
                                result.teleportCenter = true;
                                return result;
                            }

                            if (penetration >= OUTER_QUARTER_POSITIVE) {
                                result.clamped = true;
                                result.blockZ = true;
                                result.newZ = Math.min(result.newZ, bz + OUTER_QUARTER_POSITIVE - PUSHBACK);
                            }
                        } else {
                            // Shell z tyłu
                            double back = cz;
                            double penetration = (bz + 1.0) - back;

                            if (penetration >= (1.0 - OUTER_QUARTER_NEGATIVE) + TELEPORT_EXTRA_PENETRATION) {
                                result.teleportCenter = true;
                                return result;
                            }

                            if (penetration >= (1.0 - OUTER_QUARTER_NEGATIVE)) {
                                result.clamped = true;
                                result.blockZ = true;
                                result.newZ = Math.max(result.newZ, bz + OUTER_QUARTER_NEGATIVE + PUSHBACK);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    // ==================== SHELL CHECK ====================

    /**
     * ✅ Tylko:
     * - zbudowany shell (BLUE_GLAZED_TERRACOTTA)
     * - planned shell podczas animacji
     *
     * Żadnych fake barier, żadnych inner blocków.
     */
    private boolean isShellOrPlannedShell(Location blockLoc,
                                          ActiveHydroKlatka klatka,
                                          HydroKlatkaManager manager) {
        if (manager.isShellBlock(blockLoc)) return true;
        return !klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc);
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

        // ignoruj sam obrót
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

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
        if (last != null && now - last < FEEDBACK_MS) return;

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
        if (task != null) {
            task.cancel();
            task = null;
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
