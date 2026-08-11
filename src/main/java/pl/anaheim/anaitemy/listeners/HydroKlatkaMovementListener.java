// src/main/java/pl/anaheim/anaitemy/listeners/HydroKlatkaMovementListener.java
package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private static final Material SHELL_MATERIAL = Material.BLUE_GLAZED_TERRACOTTA;

    /**
     * Połowa szerokości hitboxu gracza.
     * Hitbox gracza = 0.6 bloku szerokości → 0.3 na każdą stronę.
     */
    private static final double HITBOX_HALF_WIDTH = 0.3;

    /**
     * Wysokość hitboxu gracza (stojącego).
     */
    private static final double HITBOX_HEIGHT = 1.8;

    /**
     * Offsety do sprawdzania punktów hitboxu gracza.
     * Sprawdzamy 8 rogów + 2 punkty środkowe (stopy i głowa).
     */
    private static final double[][] HITBOX_CHECK_OFFSETS = {
            // Stopy - 4 rogi + środek
            {0, 0, 0},
            {HITBOX_HALF_WIDTH, 0, HITBOX_HALF_WIDTH},
            {HITBOX_HALF_WIDTH, 0, -HITBOX_HALF_WIDTH},
            {-HITBOX_HALF_WIDTH, 0, HITBOX_HALF_WIDTH},
            {-HITBOX_HALF_WIDTH, 0, -HITBOX_HALF_WIDTH},
            // Głowa - 4 rogi + środek
            {0, HITBOX_HEIGHT, 0},
            {HITBOX_HALF_WIDTH, HITBOX_HEIGHT, HITBOX_HALF_WIDTH},
            {HITBOX_HALF_WIDTH, HITBOX_HEIGHT, -HITBOX_HALF_WIDTH},
            {-HITBOX_HALF_WIDTH, HITBOX_HEIGHT, HITBOX_HALF_WIDTH},
            {-HITBOX_HALF_WIDTH, HITBOX_HEIGHT, -HITBOX_HALF_WIDTH},
            // Środek ciała - 4 rogi + środek
            {0, 0.9, 0},
            {HITBOX_HALF_WIDTH, 0.9, HITBOX_HALF_WIDTH},
            {HITBOX_HALF_WIDTH, 0.9, -HITBOX_HALF_WIDTH},
            {-HITBOX_HALF_WIDTH, 0.9, HITBOX_HALF_WIDTH},
            {-HITBOX_HALF_WIDTH, 0.9, -HITBOX_HALF_WIDTH},
    };

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== GŁÓWNY TASK ====================

    /**
     * Task co tick.
     *
     * Progi (od centrum):
     * 
     * [0 ........... radius-0.5 ... radius ... radius+∞]
     *  |   WEWNĄTRZ   | BARIERA |  TELEPORT NA ŚRODEK  |
     *  |   (OK)       | (push)  |  (agresywny)         |
     *
     * Dodatkowo po animacji:
     * - sprawdza czy JAKIKOLWIEK punkt hitboxu gracza jest w bloku shell
     * - jeśli tak → teleport na środek
     *
     * Elytra: gracz obija się o bloki od WEWNĄTRZ (distance < radius - 0.5)
     * więc NIE jest łapany. Ale jeśli wleci W blok shell → teleport.
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
                    boolean animDone = klatka.isAnimationComplete();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (loc.getWorld() == null) continue;

                        // === INNY ŚWIAT → USUŃ ===
                        if (!loc.getWorld().equals(center.getWorld())) {
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        double dist = loc.distance(center);

                        // === AGRESYWNY TELEPORT - za zewnętrzną krawędzią shell ===
                        if (dist >= shellOuterEdge) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === SPRAWDŹ CZY HITBOX GRACZA JEST W BLOKU SHELL ===
                        // Działa ZAWSZE (podczas i po animacji)
                        // Sprawdza 15 punktów hitboxu gracza
                        if (isHitboxInShellBlock(loc, center, klatka)) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === BARIERA SOFTWAROWA (połowa shell bloku) ===
                        if (dist >= barrierRadius) {
                            if (!animDone) {
                                // Podczas animacji: pushback
                                pushbackToBarrier(player, loc, center, barrierRadius);
                                sendBarrierFeedback(player);
                            }
                            // Po animacji: fizyczne bloki shell zatrzymują gracza
                            // Hitbox check powyżej złapie gracza jeśli jest W bloku
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== SPRAWDZENIE HITBOXU W SHELL ====================

    /**
     * Sprawdza czy JAKIKOLWIEK punkt hitboxu gracza jest w bloku shell.
     * 
     * Sprawdza 15 punktów:
     * - 5 na poziomie stóp (środek + 4 rogi)
     * - 5 na poziomie głowy (środek + 4 rogi)
     * - 5 na poziomie środka ciała (środek + 4 rogi)
     * 
     * Dodatkowo: jeśli punkt jest po WEWNĘTRZNEJ stronie shell bloku
     * (bliżej centrum niż bariera), to NIE liczymy go jako kolizję.
     * Dzięki temu elytra obijająca się o bloki od wewnątrz nie teleportuje.
     */
    private boolean isHitboxInShellBlock(Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        if (playerLoc == null || playerLoc.getWorld() == null) return false;

        double barrierRadius = klatka.getBarrierRadius();

        for (double[] offset : HITBOX_CHECK_OFFSETS) {
            Location checkPoint = playerLoc.clone().add(offset[0], offset[1], offset[2]);
            
            // Sprawdź czy ten punkt jest w bloku shell
            Block block = checkPoint.getBlock();
            if (block.getType() != SHELL_MATERIAL) continue;

            // Punkt jest w bloku shell - ale czy jest po zewnętrznej stronie?
            // Jeśli punkt jest bliżej centrum niż bariera → to normalne obijanie się
            // Jeśli punkt jest dalej od centrum niż bariera → gracz wchodzi W shell
            double pointDist = checkPoint.distance(center);
            
            if (pointDist >= barrierRadius) {
                // Punkt hitboxu jest za barierą w bloku shell → KOLIZJA
                return true;
            }
        }

        return false;
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    private void teleportToCenter(Player player, Location playerLoc, Location center) {
        Location dest = center.clone();
        dest.setYaw(playerLoc.getYaw());
        dest.setPitch(playerLoc.getPitch());
        doInternalTeleport(player, dest);
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

        // === WORLDGUARD ===
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // === BARIERA - ZAWSZE ===
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
