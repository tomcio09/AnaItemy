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

    // ✅ BARIERA 2 BLOKI PRZED SHELLEM
    private static final int BARRIER_DISTANCE = 2; // Ile bloków przed shellem zatrzymać gracza
    private static final double PUSHBACK_STRONG = 1.5; // Mocne cofnięcie
    private static final double BOUNCE_VELOCITY = 0.25; // Mocniejszy impuls odrzutu

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

                        // ✅ AGRESYWNE ZATRZYMANIE
                        Location old = player.getLocation();
                        Vector vel = player.getVelocity().clone();

                        // Najpierw wyzeruj velocity
                        player.setVelocity(new Vector(0, 0, 0));

                        // Teleportuj do bezpiecznej pozycji
                        Location safe = new Location(
                                old.getWorld(),
                                result.newX,
                                result.newY,
                                result.newZ,
                                old.getYaw(),
                                old.getPitch()
                        );
                        player.teleport(safe);

                        // ✅ Dodaj impuls ODRZUTU w kierunku centrum
                        Vector bounce = new Vector(0, 0, 0);
                        
                        if (result.blockX) {
                            double dirToCenter = center.getX() - result.newX;
                            bounce.setX(Math.signum(dirToCenter) * BOUNCE_VELOCITY);
                        }
                        
                        if (result.blockY) {
                            // Jeśli gracz został zatrzymany podczas spadania - daj mocny impuls w górę
                            if (vel.getY() < -0.1) {
                                bounce.setY(BOUNCE_VELOCITY * 2.0); // Bardzo mocny impuls w górę
                            } else if (result.newY < old.getY()) {
                                bounce.setY(-BOUNCE_VELOCITY * 0.3);
                            } else {
                                bounce.setY(BOUNCE_VELOCITY * 0.5); // Delikatny impuls w górę
                            }
                        }
                        
                        if (result.blockZ) {
                            double dirToCenter = center.getZ() - result.newZ;
                            bounce.setZ(Math.signum(dirToCenter) * BOUNCE_VELOCITY);
                        }

                        player.setVelocity(bounce);

                        feedback(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== KOLIZJA Z PLANNED SHELL (BARIERA 2 BLOKI PRZED) ====================
    private CollisionResult checkPlannedShellCollision(Location loc,
                                                       ActiveHydroKlatka klatka,
                                                       HydroKlatkaManager manager,
                                                       Location center) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        CollisionResult result = new CollisionResult(px, py, pz);

        // ==================== OŚ Y: DÓŁ (spadanie) - BARIERA 2 BLOKI NAD SHELLEM ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    // ✅ Skanuj więcej bloków w dół
                    for (int checkY = (int) Math.floor(py) + 2; checkY >= (int) Math.floor(py) - 8; checkY--) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        // ✅ ZNALEZIONO SHELL - ZATRZYMAJ GRACZA 2 BLOKI WYŻEJ
                        double shellTop = checkY + 1.0;
                        double barrierY = shellTop + BARRIER_DISTANCE; // 2 bloki nad shellem

                        // Zatrzymaj gracza jeśli jest poniżej bariery
                        if (py < barrierY + 0.5) {
                            result.clamped = true;
                            result.blockY = true;
                            result.pushedUp = true;
                            // ✅ Postaw gracza NA barierze (2 bloki nad shellem)
                            double safeY = barrierY + PUSHBACK_STRONG;
                            result.newY = Math.max(result.newY, safeY);
                        }

                        break;
                    }
                }
            }
        }

        // ==================== OŚ Y: GÓRA (lot w górę) - BARIERA 2 BLOKI POD SHELLEM ====================
        {
            int[] footXBlocks = getFootBlocksX(px);
            int[] footZBlocks = getFootBlocksZ(pz);

            for (int checkX : footXBlocks) {
                for (int checkZ : footZBlocks) {
                    int headBlockY = (int) Math.floor(py + HEIGHT);
                    for (int checkY = headBlockY - 2; checkY <= headBlockY + 5; checkY++) {
                        Location blockLoc = new Location(center.getWorld(), checkX, checkY, checkZ);
                        if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                        double shellBottom = checkY;
                        double barrierY = shellBottom - BARRIER_DISTANCE; // 2 bloki pod shellem
                        double playerTop = py + HEIGHT;

                        if (playerTop > barrierY - 0.5) {
                            result.clamped = true;
                            result.blockY = true;
                            double safeY = barrierY - HEIGHT - PUSHBACK_STRONG;
                            result.newY = Math.min(result.newY, safeY);
                        }

                        break;
                    }
                }
            }
        }

        // ==================== OŚ X: BOKI - BARIERA 2 BLOKI PRZED SHELLEM ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT);

            for (int by = minBY; by <= maxBY; by++) {
                boolean overlapY = (py + HEIGHT > by) && (py < by + 1.0);
                if (!overlapY) continue;

                // ✅ +X: sprawdź shell po prawej i zatrzymaj gracza 2 bloki PRZED nim
                {
                    int[] zBlocks = getFootBlocksZ(pz);
                    for (int checkZ : zBlocks) {
                        // Skanuj 5 bloków w prawo
                        for (int scanX = (int) Math.floor(px); scanX <= (int) Math.floor(px) + 5; scanX++) {
                            Location blockLoc = new Location(center.getWorld(), scanX, by, checkZ);
                            if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                            // ✅ ZNALEZIONO SHELL - ustaw barierę 2 bloki przed nim
                            double barrierX = scanX - BARRIER_DISTANCE;
                            double playerRight = px + HALF_W;

                            if (playerRight > barrierX - 0.3) {
                                result.clamped = true;
                                result.blockX = true;
                                double safeX = barrierX - HALF_W - PUSHBACK_STRONG;
                                result.newX = Math.min(result.newX, safeX);
                            }
                            break; // Znaleziono pierwszy shell w tym kierunku
                        }
                    }
                }

                // ✅ -X: sprawdź shell po lewej i zatrzymaj gracza 2 bloki PRZED nim
                {
                    int[] zBlocks = getFootBlocksZ(pz);
                    for (int checkZ : zBlocks) {
                        // Skanuj 5 bloków w lewo
                        for (int scanX = (int) Math.floor(px); scanX >= (int) Math.floor(px) - 5; scanX--) {
                            Location blockLoc = new Location(center.getWorld(), scanX, by, checkZ);
                            if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                            // ✅ ZNALEZIONO SHELL - ustaw barierę 2 bloki przed nim
                            double barrierX = scanX + 1.0 + BARRIER_DISTANCE;
                            double playerLeft = px - HALF_W;

                            if (playerLeft < barrierX + 0.3) {
                                result.clamped = true;
                                result.blockX = true;
                                double safeX = barrierX + HALF_W + PUSHBACK_STRONG;
                                result.newX = Math.max(result.newX, safeX);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // ==================== OŚ Z: BOKI - BARIERA 2 BLOKI PRZED SHELLEM ====================
        {
            int minBY = (int) Math.floor(py);
            int maxBY = (int) Math.floor(py + HEIGHT);

            for (int by = minBY; by <= maxBY; by++) {
                boolean overlapY = (py + HEIGHT > by) && (py < by + 1.0);
                if (!overlapY) continue;

                // ✅ +Z: sprawdź shell przed i zatrzymaj gracza 2 bloki PRZED nim
                {
                    int[] xBlocks = getFootBlocksX(px);
                    for (int checkX : xBlocks) {
                        // Skanuj 5 bloków do przodu
                        for (int scanZ = (int) Math.floor(pz); scanZ <= (int) Math.floor(pz) + 5; scanZ++) {
                            Location blockLoc = new Location(center.getWorld(), checkX, by, scanZ);
                            if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                            double barrierZ = scanZ - BARRIER_DISTANCE;
                            double playerFront = pz + HALF_W;

                            if (playerFront > barrierZ - 0.3) {
                                result.clamped = true;
                                result.blockZ = true;
                                double safeZ = barrierZ - HALF_W - PUSHBACK_STRONG;
                                result.newZ = Math.min(result.newZ, safeZ);
                            }
                            break;
                        }
                    }
                }

                // ✅ -Z: sprawdź shell za i zatrzymaj gracza 2 bloki PRZED nim
                {
                    int[] xBlocks = getFootBlocksX(px);
                    for (int checkX : xBlocks) {
                        // Skanuj 5 bloków do tyłu
                        for (int scanZ = (int) Math.floor(pz); scanZ >= (int) Math.floor(pz) - 5; scanZ--) {
                            Location blockLoc = new Location(center.getWorld(), checkX, by, scanZ);
                            if (!isPlannedShellOnly(blockLoc, klatka, manager)) continue;

                            double barrierZ = scanZ + 1.0 + BARRIER_DISTANCE;
                            double playerBack = pz - HALF_W;

                            if (playerBack < barrierZ + 0.3) {
                                result.clamped = true;
                                result.blockZ = true;
                                double safeZ = barrierZ + HALF_W + PUSHBACK_STRONG;
                                result.newZ = Math.max(result.newZ, safeZ);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // ==================== EXPLOIT: Gracz poza klatką ====================
        if (loc.distance(center) > klatka.getRadius() + 0.5) {
            result.teleportCenter = true;
        }

        return result;
    }

    // ✅ Zwraca bloki X pod hitboxem gracza
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
        boolean pushedUp = false;

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
