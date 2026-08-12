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

    // Bariera blokowania ruchu
    private static final double MOVEMENT_BARRIER_OFFSET = 0.6;
    // Awaryjny teleport
    private static final double EMERGENCY_TELEPORT_OFFSET = 0.5;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== GŁÓWNY TASK ====================

    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    double movementBarrierRadius = klatka.getRadius() - MOVEMENT_BARRIER_OFFSET;
                    double emergencyTeleportRadius = klatka.getRadius() - EMERGENCY_TELEPORT_OFFSET;
                    boolean animationComplete = klatka.isAnimationComplete();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (loc == null || loc.getWorld() == null) continue;

                        if (!loc.getWorld().equals(center.getWorld())) {
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        // ====================
                        // 1. GRACZ W SOLID BLOKU KLATKI
                        // ====================
                        // Najprostsze i najpewniejsze rozwiązanie:
                        // sprawdź czy gracz stoi w jakimkolwiek solid bloku który jest blokiem klatki.
                        // To łapie przypadek "gracz podczas budowy klatki stoi w miejscu gdzie shell się buduje"
                        if (isPlayerStuckInCageBlock(player, manager)) {
                            teleportToCenter(player, loc, center, klatka);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        double dist = loc.distance(center);

                        // ====================
                        // 2. AWARYJNY TELEPORT NA ŚRODEK
                        // ====================
                        if (dist >= emergencyTeleportRadius) {
                            teleportToCenter(player, loc, center, klatka);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // ====================
                        // 3. PODCZAS ANIMACJI - PUSHBACK
                        // ====================
                        if (!animationComplete && dist >= movementBarrierRadius) {
                            pushInsideBarrier(player, loc, center, movementBarrierRadius);
                            sendBarrierFeedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== DETEKCJA GRACZA ZABLOKOWANEGO W BLOKU KLATKI ====================

    /**
     * Sprawdza czy gracz jest w solid bloku który należy do klatki.
     * 
     * To jest najprostrze i najpewniejsze rozwiązanie:
     * - sprawdź bloki na pozycji stóp i głowy gracza
     * - sprawdź 4 rogi hitboxu gracza (0.3 bloku na każdą stronę)
     * - jeśli którykolwiek z tych bloków jest solid I jest blokiem klatki → TRUE
     * 
     * To łapie dokładnie przypadek:
     * "gracz stoi w miejscu gdzie shell się buduje i jest w nim uwięziony"
     */
    private boolean isPlayerStuckInCageBlock(Player player, HydroKlatkaManager manager) {
        if (player == null) return false;

        Location loc = player.getLocation();
        if (loc == null || loc.getWorld() == null) return false;

        // Sprawdź bloki na pozycji gracza:
        // - stopy
        // - głowa
        // - środek
        // - 4 rogi hitboxu na poziomie stóp
        // - 4 rogi hitboxu na poziomie głowy

        double[][] offsets = {
                {0, 0, 0},           // stopy
                {0, 1, 0},           // głowa
                {0, 0.9, 0},         // środek
                // rogi stóp (hitbox gracza = 0.6 szerokości → 0.3 na każdą stronę)
                {0.3, 0, 0.3},
                {0.3, 0, -0.3},
                {-0.3, 0, 0.3},
                {-0.3, 0, -0.3},
                // rogi głowy
                {0.3, 1.8, 0.3},
                {0.3, 1.8, -0.3},
                {-0.3, 1.8, 0.3},
                {-0.3, 1.8, -0.3},
        };

        for (double[] offset : offsets) {
            Location checkLoc = loc.clone().add(offset[0], offset[1], offset[2]);
            Block block = checkLoc.getBlock();

            // Sprawdź czy to solid block
            if (!block.getType().isSolid()) continue;

            // Sprawdź czy to blok klatki
            if (manager.isKlatkaBlock(block.getLocation())) {
                return true;
            }
        }

        return false;
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    private void teleportToCenter(Player player, Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        Location destination = findSafeCenterLocation(center, klatka, playerLoc.getYaw(), playerLoc.getPitch());
        doInternalTeleport(player, destination);
    }

    /**
     * Szuka bezpiecznej pozycji blisko środka klatki.
     */
    private Location findSafeCenterLocation(Location center, ActiveHydroKlatka klatka, float yaw, float pitch) {
        if (center == null || center.getWorld() == null) return center;

        // Sprawdź exact center
        Location exact = center.clone();
        exact.setYaw(yaw);
        exact.setPitch(pitch);

        if (isSafeForPlayer(exact)) {
            return exact;
        }

        // Szukaj najbliżej środka
        int maxSearchRadius = Math.max(3, klatka.getRadius() - 2);

        for (int r = 1; r <= maxSearchRadius; r++) {
            for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        // Tylko obwód pierścienia
                        if (Math.abs(dx) != r && Math.abs(dz) != r) continue;

                        Location candidate = new Location(
                                center.getWorld(),
                                center.getBlockX() + dx + 0.5,
                                center.getBlockY() + dy,
                                center.getBlockZ() + dz + 0.5,
                                yaw,
                                pitch
                        );

                        // Musi być wewnątrz klatki
                        if (candidate.distance(center) >= klatka.getRadius() - 1.5) continue;

                        if (isSafeForPlayer(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        // Fallback - exact center (lepsze niż nic)
        return exact;
    }

    private boolean isSafeForPlayer(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return !feet.getType().isSolid() && !head.getType().isSolid();
    }

    // ==================== PUSHBACK PODCZAS ANIMACJI ====================

    private void pushInsideBarrier(Player player, Location playerLoc, Location center, double movementBarrierRadius) {
        Vector fromCenter = playerLoc.toVector().subtract(center.toVector());
        if (fromCenter.lengthSquared() < 0.001) return;

        fromCenter.normalize();

        double targetDist = Math.max(0, movementBarrierRadius - 0.75);
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

        double movementBarrierRadius = klatka.getRadius() - MOVEMENT_BARRIER_OFFSET;

        if (to.distance(center) >= movementBarrierRadius) {
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

        double movementBarrierRadius = klatka.getRadius() - MOVEMENT_BARRIER_OFFSET;

        if (to.getWorld() == null
                || !to.getWorld().equals(center.getWorld())
                || to.distance(center) >= movementBarrierRadius) {
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
