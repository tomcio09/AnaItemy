// src/main/java/pl/anaheim/anaitemy/listeners/HydroKlatkaMovementListener.java
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

                        if (player.isGliding()) continue;

                        if (klatka.isAnimationComplete()) continue;

                        Vector dir = getTeleportTowardCenter(loc, klatka, manager, center);

                        if (dir != null) {
                            Location newLoc = loc.clone().add(dir);
                            doSafeTeleport(player, newLoc);
                            feedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
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

        plugin.getLogger().info("[HK-COLLISION] Checking collision for player at: " + px + ", " + py + ", " + pz);

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Location checkLoc = new Location(loc.getWorld(),
                            (int) Math.floor(px) + dx,
                            (int) Math.floor(py) + dy,
                            (int) Math.floor(pz) + dz);

                    boolean isPlanned = klatka.isPlannedShellLocation(checkLoc);
                    boolean isBuilt = manager.isShellBlock(checkLoc);
                
                    if (isPlanned) {
                        plugin.getLogger().info("[HK-COLLISION] Found PLANNED shell at: " + checkLoc);
                    }
                    if (isBuilt && !klatka.isAnimationComplete()) {
                        plugin.getLogger().info("[HK-COLLISION] Found BUILT shell at: " + checkLoc);
                    }

                    if (!isShellBlock(checkLoc, klatka, manager)) continue;

                    double bx = checkLoc.getX() + 0.5;
                    double by = checkLoc.getY() + 0.5;
                    double bz = checkLoc.getZ() + 0.5;

                    if (isNearBarrier(px, py, pz, bx, by, bz)) {
                        plugin.getLogger().info("[HK-COLLISION] BARRIER DETECTED! Teleporting...");
                    
                        double dirX = 0;
                        double dirY = 0;
                        double dirZ = 0;

                        if (Math.abs(px - bx) > 0.15) dirX = Math.signum(cx - px) * TELEPORT_DISTANCE;
                        if (Math.abs(py - by) > 0.15) dirY = Math.signum(cy - py) * TELEPORT_DISTANCE;
                        if (Math.abs(pz - bz) > 0.15) dirZ = Math.signum(cz - pz) * TELEPORT_DISTANCE;

                        if (dirX != 0 || dirY != 0 || dirZ != 0) {
                            plugin.getLogger().info("[HK-COLLISION] Direction: " + dirX + ", " + dirY + ", " + dirZ);
                            return new Vector(dirX, dirY, dirZ);
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("[HK-COLLISION] No collision detected");
        return null;
    }

    private boolean isNearBarrier(double px, double py, double pz,
                                  double bx, double by, double bz) {
        double dx = Math.abs(px - bx);
        double dy = Math.abs(py - by);
        double dz = Math.abs(pz - bz);

        return (dx < HALF_W + 0.3 && dy < HEIGHT + 0.3 && dz < HALF_W + 0.3);
    }

    private boolean isShellBlock(Location loc, ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        if (klatka.isPlannedShellLocation(loc)) {
            return true;
        }
        if (manager.isShellBlock(loc)) {
            return true;
        }
        return false;
    }

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
                Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(800), Duration.ofMillis(200))
        ));
    }

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // ✅ NASZ teleport - NIGDY nie blokuj
        if (ourTeleports.contains(player.getUniqueId())) {
            event.setCancelled(false);
            return;
        }

        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // ✅ TYLKO blokuj teleporty poza klatką podczas animacji
        if (!klatka.isAnimationComplete()) {
            Location to = event.getTo();
            if (to != null && to.distance(klatka.getCenter()) > klatka.getRadius()) {
                if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                    event.setCancelled(true);
                    manager.sendMessage(player,
                            plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                }
            }
        }
    }

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
