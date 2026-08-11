// src/main/java/pl/anaheim/anaitemy/listeners/HydroKlatkaMovementListener.java
package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
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
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== GŁÓWNY TASK ====================

    /**
     * Logika:
     *
     * 1. Jeśli środek gracza jest za radius -> teleport na środek
     * 2. Jeśli CAŁY hitbox gracza jest za barierą (radius - 0.5) -> teleport na środek
     *    To łapie sytuację "jestem cały w bloku shell i nie wystaję do środka".
     * 3. Podczas animacji, gdy gracz tylko dotyka bariery -> pushback
     *
     * Dzięki temu:
     * - elytra obijająca się o bloki od środka NIE teleportuje,
     *   bo część hitboxu nadal jest wewnątrz bariery
     * - gracz zbugowany w shell i cały za połową bloku -> teleportuje
     */
    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    int radius = klatka.getRadius();
                    double barrierRadius = radius - 0.5;
                    double shellOuterEdge = radius;
                    boolean animationComplete = klatka.isAnimationComplete();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (loc == null || loc.getWorld() == null) continue;

                        // Inny świat -> usuń z klatki
                        if (!loc.getWorld().equals(center.getWorld())) {
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        double playerDistance = loc.distance(center);

                        // ====================
                        // 1. ŚRODEK GRACZA ZA SHELL
                        // ====================
                        if (playerDistance >= shellOuterEdge) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // ====================
                        // 2. CAŁY HITBOX ZA BARIERĄ
                        // ====================
                        // To jest dokładnie przypadek:
                        // "jestem zbugowany w shell i nie wystaję hitboxem do środka"
                        if (isEntireHitboxBeyondBarrier(player, center, barrierRadius)) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // ====================
                        // 3. PODCZAS ANIMACJI - PUSHBACK
                        // ====================
                        if (!animationComplete && playerDistance >= barrierRadius) {
                            pushbackToBarrier(player, loc, center, barrierRadius);
                            sendBarrierFeedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== HITBOX VS BARIERA ====================

    /**
     * Sprawdza czy CAŁY hitbox gracza jest już za barierą.
     *
     * Bierzemy bounding box gracza i znajdujemy punkt hitboxu,
     * który jest NAJBLIŻEJ środka klatki.
     *
     * Jeśli nawet ten najbliższy punkt ma dystans >= barrierRadius,
     * to znaczy że cały hitbox jest po zewnętrznej stronie połowy shell bloku.
     *
     * To idealnie łapie:
     * - gracza zbugowanego w shell
     * - NIE łapie zwykłego obijania się od wewnątrz
     */
    private boolean isEntireHitboxBeyondBarrier(Player player, Location center, double barrierRadius) {
        if (player == null || center == null) return false;

        BoundingBox box = player.getBoundingBox();
        double nearestDistance = getNearestDistanceFromCenterToBoundingBox(center, box);

        return nearestDistance >= barrierRadius;
    }

    /**
     * Liczy odległość od środka klatki do NAJBLIŻSZEGO punktu bounding boxa gracza.
     */
    private double getNearestDistanceFromCenterToBoundingBox(Location center, BoundingBox box) {
        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();

        double nearestX = clamp(cx, box.getMinX(), box.getMaxX());
        double nearestY = clamp(cy, box.getMinY(), box.getMaxY());
        double nearestZ = clamp(cz, box.getMinZ(), box.getMaxZ());

        double dx = nearestX - cx;
        double dy = nearestY - cy;
        double dz = nearestZ - cz;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    /**
     * Teleportuje gracza jak najbliżej środka, ale do bezpiecznej pozycji.
     * Jeśli exact center jest wolny -> użyje center.
     * Jeśli nie -> szuka najbliższego bezpiecznego miejsca w okolicy środka.
     */
    private void teleportToCenter(Player player, Location playerLoc, Location center) {
        Location destination = findSafeCenterLocation(center, playerLoc.getYaw(), playerLoc.getPitch());
        doInternalTeleport(player, destination);
    }

    /**
     * Szuka bezpiecznej pozycji możliwie najbliżej środka klatki.
     */
    private Location findSafeCenterLocation(Location center, float yaw, float pitch) {
        if (center == null || center.getWorld() == null) return center;

        // 1. Najpierw exact center
        Location exact = center.clone();
        exact.setYaw(yaw);
        exact.setPitch(pitch);
        if (isSafeForPlayer(exact)) {
            return exact;
        }

        // 2. Szukaj najbliżej środka
        int[] yOffsets = {0, 1, -1, 2, -2, 3, -3};

        for (int radius = 0; radius <= 2; radius++) {
            for (int yOff : yOffsets) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        Location candidate = new Location(
                                center.getWorld(),
                                center.getBlockX() + x + 0.5,
                                center.getBlockY() + yOff,
                                center.getBlockZ() + z + 0.5,
                                yaw,
                                pitch
                        );

                        if (isSafeForPlayer(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        // 3. Fallback
        exact.setYaw(yaw);
        exact.setPitch(pitch);
        return exact;
    }

    /**
     * Czy gracz może stać w tej pozycji.
     */
    private boolean isSafeForPlayer(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();

        return !feet.getType().isSolid() && !head.getType().isSolid();
    }

    // ==================== PUSHBACK PODCZAS ANIMACJI ====================

    private void pushbackToBarrier(Player player, Location playerLoc, Location center, double barrierRadius) {
        Vector fromCenter = playerLoc.toVector().subtract(center.toVector());
        if (fromCenter.lengthSquared() < 0.001) return;

        fromCenter.normalize();

        double targetDist = Math.max(0, barrierRadius - 1.0);
        Location dest = center.clone().add(fromCenter.multiply(targetDist));
        dest.setYaw(playerLoc.getYaw());
        dest.setPitch(playerLoc.getPitch());

        doInternalTeleport(player, dest);
    }

    // ==================== TELEPORT WEWNĘTRZNY ====================

    private void doInternalTeleport(Player player, Location destination) {
        if (player == null || destination == null) return;

        internalTeleports.add(player.getUniqueId());

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.teleport(destination);
                }
            }
        }.runTask(plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                internalTeleports.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 3L);
    }

    // ==================== FEEDBACK ====================

    void sendBarrierFeedback(Player player) {
        if (player == null) return;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;
        if (to.getWorld() == null || !to.getWorld().equals(center.getWorld())) return;

        double barrierRadius = klatka.getRadius() - 0.5;

        if (to.distance(center) >= barrierRadius) {
            event.setCancelled(true);
            sendBarrierFeedback(player);
        }
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        if (internalTeleports.contains(player.getUniqueId())) {
            event.setCancelled(false);
            return;
        }

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;

        double barrierRadius = klatka.getRadius() - 0.5;

        if (to.getWorld() == null
                || !to.getWorld().equals(center.getWorld())
                || to.distance(center) >= barrierRadius) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) return;
        UUID uuid = event.getPlayer().getUniqueId();
        lastFeedback.remove(uuid);
        internalTeleports.remove(uuid);
    }

    public void stopTasks() {
        if (barrierTask != null) {
            barrierTask.cancel();
            barrierTask = null;
        }
        lastFeedback.clear();
        internalTeleports.clear();
    }
}
