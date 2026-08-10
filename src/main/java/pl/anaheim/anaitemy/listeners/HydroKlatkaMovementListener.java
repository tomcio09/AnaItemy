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

    // ✅ Cooldown teleportu - zapobiega "flying is not enabled" i pozwala graczowi spaść ponownie
    private static final long TELEPORT_COOLDOWN_MS = 300L;

    // ✅ Teleport w górę przy spadaniu
    private static final double BOUNCE_UP = 0.75;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTeleport = new ConcurrentHashMap<>();
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

                        // ✅ Elytra - ZERO INGERENCJI (nawet przy uderzeniach)
                        if (player.isGliding()) {
                            // Tylko exploit check - czy gracz nie wyleciał kompletnie poza
                            if (loc.distance(center) > klatka.getRadius() + 5.0) {
                                safeTeleportToCenter(player, center);
                            }
                            continue;
                        }

                        // ✅ Po zakończeniu animacji - MC blokuje fizycznie
                        if (klatka.isAnimationComplete()) {
                            if (isInsideBuiltShell(loc, manager)) {
                                safeTeleportToCenter(player, center);
                            }
                            continue;
                        }

                        // ✅ Sprawdź czy gracz DOTYKA planned shell (bariera w połowie bloku)
                        CollisionResult result = checkPlannedShellCollision(loc, klatka, manager, center);

                        // ✅ Exploit - gracz za barierą
                        if (result.teleportCenter) {
                            safeTeleportToCenter(player, center);
                            continue;
                        }

                        // ✅ Gracz dotyka bariery
                        if (!result.clamped) continue;

                        // ✅ Cooldown - nie teleportuj co tick (pozwól graczowi spaść ponownie)
                        if (isOnTeleportCooldown(uuid)) continue;

                        // ✅ Teleport na bezpieczną pozycję
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

                        lastTeleport.put(uuid, System.currentTimeMillis());
                        feedback(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isOnTeleportCooldown(UUID uuid) {
        Long last = lastTeleport.get(uuid);
        if (last == null) return false;
        return System.currentTimeMillis() - last < TELEPORT_COOLDOWN_MS;
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

        // ==================== OŚ Y: DÓŁ (SPADANIE) ====================
        // ✅ Bariera w POŁOWIE bloku planned shell
        // ✅ Teleport +0.75 w górę od bariery
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    for (int checkY = (int) Math.floor(py); checkY >= (int) Math.floor(py) - 8; checkY--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        // ✅ Bariera na POŁOWIE bloku planned shell
                        // Planned shell: checkY do checkY+1
                        // Bariera: checkY + 0.5
                        double barrierY = checkY + 0.5;

                        // ✅ Gracz dotyka/przenika barierę
                        if (py < barrierY + 0.1) {
                            result.clamped = true;
                            result.blockY = true;
                            // ✅ Teleport 0.75 bloku NAD barierę
                            result.newY = Math.max(result.newY, barrierY + BOUNCE_UP);
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
                    for (int checkY = headBlockY; checkY <= headBlockY + 4; checkY++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        // ✅ Bariera w połowie bloku
                        double barrierY = checkY + 0.5;
                        double playerTop = py + HEIGHT;

                        if (playerTop > barrierY - 0.1) {
                            result.clamped = true;
                            result.blockY = true;
                            // Cofinij pod barierę
                            result.newY = Math.min(result.newY, barrierY - HEIGHT - 0.1);
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

                        // ✅ Bariera w połowie bloku
                        // Blok: scanX do scanX+1
                        // Bariera: scanX + 0.5
                        double barrierX = scanX + 0.5;
                        double playerRight = px + HALF_W;

                        if (playerRight > barrierX - 0.1) {
                            result.clamped = true;
                            result.blockX = true;
                            // Cofinij za barierę
                            result.newX = Math.min(result.newX, barrierX - HALF_W - 0.1);
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

                        double barrierX = scanX + 0.5;
                        double playerLeft = px - HALF_W;

                        if (playerLeft < barrierX + 0.1) {
                            result.clamped = true;
                            result.blockX = true;
                            result.newX = Math.max(result.newX, barrierX + HALF_W + 0.1);
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

                        double barrierZ = scanZ + 0.5;
                        double playerFront = pz + HALF_W;

                        if (playerFront > barrierZ - 0.1) {
                            result.clamped = true;
                            result.blockZ = true;
                            result.newZ = Math.min(result.newZ, barrierZ - HALF_W - 0.1);
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

                        double barrierZ = scanZ + 0.5;
                        double playerBack = pz - HALF_W;

                        if (playerBack < barrierZ + 0.1) {
                            result.clamped = true;
                            result.blockZ = true;
                            result.newZ = Math.max(result.newZ, barrierZ + HALF_W + 0.1);
                        }
                        break;
                    }
                }
            }
        }

        // ==================== EXPLOIT: gracz za barierą ====================
        if (loc.distance(center) > klatka.getRadius() + 1.0) {
            result.teleportCenter = true;
        }

        return result;
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

    // ==================== SAFE TELEPORT ====================

    private void safeTeleportToCenter(Player player, Location center) {
        UUID uuid = player.getUniqueId();
        if (isOnTeleportCooldown(uuid)) return;

        Location current = player.getLocation();
        Location tp = center.clone();
        tp.setYaw(current.getYaw());
        tp.setPitch(current.getPitch());

        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(tp);
        player.setVelocity(new Vector(0, 0, 0));

        lastTeleport.put(uuid, System.currentTimeMillis());
        feedback(player);
    }

    // ==================== MOVE EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // ✅ Elytra - ZERO INGERENCJI
        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (klatka.isAnimationComplete()) return;

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

        // ✅ Elytra - ZERO INGERENCJI
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
        UUID uuid = event.getUniqueId();
        lastFeedback.remove(uuid);
        lastTeleport.remove(uuid);
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastFeedback.clear();
        lastTeleport.clear();
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
