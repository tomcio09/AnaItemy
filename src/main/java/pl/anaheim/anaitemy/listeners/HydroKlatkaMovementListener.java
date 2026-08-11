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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    private static final double HALF_W = 0.30;
    private static final double HEIGHT = 1.80;
    private static final double TELEPORT_DISTANCE = 0.75;
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> ourTeleports = ConcurrentHashMap.newKeySet();
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

                plugin.getLogger().info("[HK-DEBUG] Active klatki: " + manager.getActiveKlatki().size());

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();

                    plugin.getLogger().info("[HK-DEBUG] Klatka trapped players: " + klatka.getTrappedPlayers().size());
                    plugin.getLogger().info("[HK-DEBUG] Animation complete: " + klatka.isAnimationComplete());

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);

                        plugin.getLogger().info("[HK-DEBUG] Player UUID: " + uuid + " online: " + (player != null && player.isOnline()));

                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        plugin.getLogger().info("[HK-DEBUG] Player " + player.getName() + " gliding: " + player.isGliding());

                        if (player.isGliding()) continue;

                        // ✅ Po animacji - MC blokuje fizycznie
                        if (klatka.isAnimationComplete()) {
                            if (isInsideBuiltShell(loc, manager)) {
                                plugin.getLogger().info("[HK-DEBUG] Player inside built shell - teleporting to center");
                                teleportToCenter(player, center);
                            }
                            continue;
                        }

                        // ✅ PODCZAS ANIMACJI - sprawdź kolizję z barierą
                        plugin.getLogger().info("[HK-DEBUG] Checking collision for " + player.getName() + " at " + loc);

                        Vector dir = getTeleportTowardCenter(loc, klatka, manager, center);

                        if (dir != null) {
                            plugin.getLogger().info("[HK-DEBUG] TELEPORTING " + player.getName() + " by: " + dir);
                            Location newLoc = loc.clone().add(dir);
                            doSafeTeleport(player, newLoc);
                            feedback(player);
                        } else {
                            plugin.getLogger().info("[HK-DEBUG] No collision detected for " + player.getName());
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== TELEPORT W STRONĘ ŚRODKA ====================

    private Vector getTeleportTowardCenter(Location loc,
                                           ActiveHydroKlatka klatka,
                                           HydroKlatkaManager manager,
                                           Location center) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Location checkLoc = new Location(
                            loc.getWorld(),
                            (int) Math.floor(px) + dx,
                            (int) Math.floor(py) + dy,
                            (int) Math.floor(pz) + dz
                    );

                    boolean isPlanned = klatka.isPlannedShellLocation(checkLoc);
                    boolean isBuilt = manager.isShellBlock(checkLoc);

                    if (isPlanned) {
                        plugin.getLogger().info("[HK-DEBUG] Found PLANNED shell at: " + checkLoc);
                    }
                    if (isBuilt) {
                        plugin.getLogger().info("[HK-DEBUG] Found BUILT shell at: " + checkLoc);
                    }

                    // ✅ Sprawdź ZARÓWNO planned JAK I zbudowane bloki shella
                    if (!isPlanned && !isBuilt) continue;

                    double bx = checkLoc.getX() + 0.5;
                    double by = checkLoc.getY() + 0.5;
                    double bz = checkLoc.getZ() + 0.5;

                    plugin.getLogger().info("[HK-DEBUG] Checking barrier distance: dx=" +
                            Math.abs(px - bx) + " dy=" + Math.abs(py - by) + " dz=" + Math.abs(pz - bz));

                    if (isNearBarrier(px, py, pz, bx, by, bz)) {
                        plugin.getLogger().info("[HK-DEBUG] BARRIER HIT at: " + checkLoc);

                        double dirX = 0;
                        double dirY = 0;
                        double dirZ = 0;

                        if (Math.abs(px - bx) > 0.15) {
                            dirX = Math.signum(cx - px) * TELEPORT_DISTANCE;
                        }
                        if (Math.abs(py - by) > 0.15) {
                            dirY = Math.signum(cy - py) * TELEPORT_DISTANCE;
                        }
                        if (Math.abs(pz - bz) > 0.15) {
                            dirZ = Math.signum(cz - pz) * TELEPORT_DISTANCE;
                        }

                        if (dirX != 0 || dirY != 0 || dirZ != 0) {
                            plugin.getLogger().info("[HK-DEBUG] Direction: " + dirX + ", " + dirY + ", " + dirZ);
                            return new Vector(dirX, dirY, dirZ);
                        }
                    }
                }
            }
        }

        return null;
    }

    // ==================== SPRAWDZENIE BARIERY ====================

    private boolean isNearBarrier(double px, double py, double pz,
                                  double bx, double by, double bz) {
        double dx = Math.abs(px - bx);
        double dy = Math.abs(py - by);
        double dz = Math.abs(pz - bz);

        return (dx < HALF_W + 0.3 && dy < HEIGHT + 0.3 && dz < HALF_W + 0.3);
    }

    // ==================== SPRAWDZENIE BUILT SHELL ====================

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

    private void teleportToCenter(Player player, Location center) {
        Location current = player.getLocation();
        Location tp = center.clone();
        tp.setYaw(current.getYaw());
        tp.setPitch(current.getPitch());

        ourTeleports.add(player.getUniqueId());
        player.teleport(tp);

        new BukkitRunnable() {
            @Override
            public void run() {
                ourTeleports.remove(player.getUniqueId());
            }
        }.runTask(plugin);

        feedback(player);
    }

    // ==================== BEZPIECZNY TELEPORT ====================

    private void doSafeTeleport(Player player, Location destination) {
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        ourTeleports.add(player.getUniqueId());
        player.teleport(destination);

        new BukkitRunnable() {
            @Override
            public void run() {
                ourTeleports.remove(player.getUniqueId());
            }
        }.runTask(plugin);
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // ✅ NASZ teleport - przepuść zawsze
        if (ourTeleports.contains(player.getUniqueId())) {
            plugin.getLogger().info("[HK-DEBUG] Allowing our teleport for " + player.getName());
            event.setCancelled(false);
            return;
        }

        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (klatka.isAnimationComplete()) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        if (to.distance(klatka.getCenter()) > klatka.getRadius()) {
            plugin.getLogger().info("[HK-DEBUG] Blocking external teleport for " + player.getName());
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
        ourTeleports.remove(event.getPlayer().getUniqueId());
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastFeedback.clear();
        ourTeleports.clear();
    }
}
