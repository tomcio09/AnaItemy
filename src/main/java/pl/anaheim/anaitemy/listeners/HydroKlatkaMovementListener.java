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
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    /**
     * Bariera blokująca ruch jest przesunięta o 0.1 bliżej środka
     * względem starej pozycji.
     *
     * Stara pozycja = radius - 0.5
     * Nowa pozycja  = radius - 0.6
     */
    private static final double MOVEMENT_BARRIER_OFFSET = 0.6;

    /**
     * Awaryjny teleport na środek jest na starej pozycji bariery.
     */
    private static final double EMERGENCY_TELEPORT_OFFSET = 0.5;

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
     * 1. Bariera blokowania ruchu = radius - 0.6
     * 2. Awaryjny teleport na środek = radius - 0.5
     *
     * Czyli:
     * - jeśli działa blokowanie ruchu, to działa tylko ono
     * - jeśli gracz jakimś bugiem przejdzie dalej, łapie go teleport
     *
     * To rozdziela oba systemy i nie pozwala im na siebie nachodzić.
     */
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

                        // Inny świat -> usuń z klatki
                        if (!loc.getWorld().equals(center.getWorld())) {
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        double dist = loc.distance(center);

                        // ====================
                        // AWARYJNY TELEPORT NA ŚRODEK
                        // ====================
                        // To jest stara pozycja bariery.
                        // Jeśli gracz tutaj dotrze, to znaczy że zbugował się przez blokadę ruchu.
                        if (dist >= emergencyTeleportRadius) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // ====================
                        // PODCZAS ANIMACJI - SOFT PUSHBACK
                        // ====================
                        // W trakcie budowy bloki jeszcze nie istnieją,
                        // więc gdy gracz dobije do nowej bariery ruchu,
                        // cofamy go lekko do środka.
                        if (!animationComplete && dist >= movementBarrierRadius) {
                            pushInsideBarrier(player, loc, center, movementBarrierRadius);
                            sendBarrierFeedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    private void teleportToCenter(Player player, Location playerLoc, Location center) {
        Location destination = findSafeCenterLocation(center, playerLoc.getYaw(), playerLoc.getPitch());
        doInternalTeleport(player, destination);
    }

    /**
     * Szuka bezpiecznej pozycji możliwie blisko środka klatki.
     */
    private Location findSafeCenterLocation(Location center, float yaw, float pitch) {
        if (center == null || center.getWorld() == null) return center;

        Location exact = center.clone();
        exact.setYaw(yaw);
        exact.setPitch(pitch);

        if (isSafeForPlayer(exact)) {
            return exact;
        }

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

        return exact;
    }

    private boolean isSafeForPlayer(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return !loc.getBlock().getType().isSolid()
                && !loc.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }

    // ==================== PUSHBACK PODCZAS ANIMACJI ====================

    /**
     * Ustawia gracza trochę wewnątrz nowej bariery ruchu.
     */
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

    /**
     * Główna blokada ruchu:
     * nowa bariera = radius - 0.6
     *
     * Teleport awaryjny łapie dopiero task na radius - 0.5.
     */
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
