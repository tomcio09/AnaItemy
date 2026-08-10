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

        // ==================== OŚ Y: DÓŁ (spadanie) ====================
        // ✅ Szukaj bloków shella PONIŻEJ gracza — skanuj kolumnę w dół
        // Sprawdź wszystkie kolumny pod hitboxem gracza (gracz może stać na 4 blokach)
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    // Skanuj od pozycji stóp gracza w dół (max 5 bloków)
                    for (int checkY = (int) Math.floor(py); checkY >= (int) Math.floor(py) - 5; checkY--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        // ✅ Znaleziono shell pod graczem!
                        double shellTop = checkY + 1.0;

                        // Gracz jest poniżej górnej krawędzi shella?
                        if (py < shellTop + 0.1) {
                            // ✅ Cofnij na górę bloku shella
                            result.clamped = true;
                            result.blockY = true;
                            double safeY = shellTop + PUSHBACK;
                            result.newY = Math.max(result.newY, safeY);
                        }

                        break; // Znaleziono pierwszy shell w tej kolumnie
                    }
                }
            }
        }

        // ==================== OŚ Y: GÓRA (lot w górę) ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    // Skanuj od głowy gracza w górę (max 3 bloki)
                    int headBlockY = (int) Math.floor(py + HEIGHT);
                    for (int checkY = headBlockY; checkY <= headBlockY + 3; checkY++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellBottom = checkY;
                        double playerTop = py + HEIGHT;

                        if (playerTop > shellBottom - 0.1) {
                            result.clamped = true;
                            result.blockY = true;
                            double safeY = shellBottom - HEIGHT - PUSHBACK;
                            result.newY = Math.min(result.newY, safeY);
                        }

                        break;
                    }
                }
            }
        }

        // ==================== OŚ X i Z: BOKI ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT);

            for (int by = minBY; by <= maxBY; by++) {
                boolean overlapY = (py + HEIGHT > by) && (py < by + 1.0);
                if (!overlapY) continue;

                // +X: sprawdź bloki po prawej stronie gracza
                {
                    int checkX = (int) Math.floor(px + HALF_W + 0.1);
                    int[] zBlocks = getFootBlocksZ(pz);
                    for (int checkZ : zBlocks) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double dx = (checkX + 0.5) - center.getX();
                        if (dx <= 0) continue; // bariera blokuje tylko wyjście

                        double playerRight = px + HALF_W;
                        if (playerRight > checkX) {
                            double penetration = playerRight - checkX;
                            if (penetration > 0.6) {
                                result.teleportCenter = true;
                                return result;
                            }
                            result.clamped = true;
                            result.blockX = true;
                            double safeX = checkX - HALF_W - PUSHBACK;
                            result.newX = Math.min(result.newX, safeX);
                        }
                    }
                }

                // -X: sprawdź bloki po lewej stronie gracza
                {
                    int checkX = (int) Math.floor(px - HALF_W - 0.1);
                    int[] zBlocks = getFootBlocksZ(pz);
                    for (int checkZ : zBlocks) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double dx = (checkX + 0.5) - center.getX();
                        if (dx >= 0) continue;

                        double playerLeft = px - HALF_W;
                        if (playerLeft < checkX + 1.0) {
                            double penetration = (checkX + 1.0) - playerLeft;
                            if (penetration > 0.6) {
                                result.teleportCenter = true;
                                return result;
                            }
                            result.clamped = true;
                            result.blockX = true;
                            double safeX = checkX + 1.0 + HALF_W + PUSHBACK;
                            result.newX = Math.max(result.newX, safeX);
                        }
                    }
                }

                // +Z: sprawdź bloki przed graczem
                {
                    int checkZ = (int) Math.floor(pz + HALF_W + 0.1);
                    int[] xBlocks = getFootBlocksX(px);
                    for (int checkX : xBlocks) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double dz = (checkZ + 0.5) - center.getZ();
                        if (dz <= 0) continue;

                        double playerFront = pz + HALF_W;
                        if (playerFront > checkZ) {
                            double penetration = playerFront - checkZ;
                            if (penetration > 0.6) {
                                result.teleportCenter = true;
                                return result;
                            }
                            result.clamped = true;
                            result.blockZ = true;
                            double safeZ = checkZ - HALF_W - PUSHBACK;
                            result.newZ = Math.min(result.newZ, safeZ);
                        }
                    }
                }

                // -Z: sprawdź bloki za graczem
                {
                    int checkZ = (int) Math.floor(pz - HALF_W - 0.1);
                    int[] xBlocks = getFootBlocksX(px);
                    for (int checkX : xBlocks) {
                        Location blockLoc = new Location(center.getWorld(), checkX, by, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double dz = (checkZ + 0.5) - center.getZ();
                        if (dz >= 0) continue;

                        double playerBack = pz - HALF_W;
                        if (playerBack < checkZ + 1.0) {
                            double penetration = (checkZ + 1.0) - playerBack;
                            if (penetration > 0.6) {
                                result.teleportCenter = true;
                                return result;
                            }
                            result.clamped = true;
                            result.blockZ = true;
                            double safeZ = checkZ + 1.0 + HALF_W + PUSHBACK;
                            result.newZ = Math.max(result.newZ, safeZ);
                        }
                    }
                }
            }
        }

        // ==================== EXPLOIT: Gracz poza klatką ====================
        if (loc.distance(center) > klatka.getRadius()) {
            result.teleportCenter = true;
        }

        return result;
    }

    // ✅ Zwraca bloki X pod hitboxem gracza (gracz może stać na 2 blokach)
    private int[] getFootBlocksX(double px) {
        int left = (int) Math.floor(px - HALF_W);
        int right = (int) Math.floor(px + HALF_W);
        if (left == right) return new int[]{left};
        return new int[]{left, right};
    }

    // ✅ Zwraca bloki Z pod hitboxem gracza
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
