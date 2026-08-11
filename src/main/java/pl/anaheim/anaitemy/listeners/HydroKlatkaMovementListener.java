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

                    boolean animDone = klatka.isAnimationComplete();
                    int radius = klatka.getRadius();

                    // POPRAWNE wartości:
                    double barrierRadius = radius - 0.5;        // bariera w połowie shell bloku
                    double safetyRadius = radius + 1.0;         // 1 PEŁNA kratka za radius

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

                        // === SYSTEM BEZPIECZEŃSTWA - 1 kratka za radius ===
                        if (dist > safetyRadius) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === GRACZ W BLOKU SHELL - ZAWSZE teleport na środek ===
                        if (animDone && isPlayerInShellBlock(loc)) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === BARIERA BACKUP (gdy PlayerMoveEvent nie złapie) ===
                        if (dist >= barrierRadius) {
                            if (animDone) {
                                // Po animacji: teleport na środek (fizyczne bloki mają chronić ale mogą być buggy)
                                teleportToCenter(player, loc, center);
                            } else {
                                // Podczas animacji: pushback
                                pushbackToBarrier(player, loc, center, barrierRadius);
                            }
                            sendBarrierFeedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== SPRAWDZENIE BLOKU SHELL ====================

    /**
     * Sprawdza czy gracz jest w bloku shell (na wysokości stóp lub głowy).
     */
    private boolean isPlayerInShellBlock(Location loc) {
        if (loc.getWorld() == null) return false;
        
        Block feetBlock = loc.getBlock();
        Block headBlock = loc.clone().add(0, 1, 0).getBlock();
        
        return feetBlock.getType() == SHELL_MATERIAL || headBlock.getType() == SHELL_MATERIAL;
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    /**
     * Teleportuje gracza na środek klatki.
     * PROSTE I PEWNE - żadnych obliczeń.
     */
    private void teleportToCenter(Player player, Location playerLoc, Location center) {
        Location dest = center.clone();
        dest.setYaw(playerLoc.getYaw());
        dest.setPitch(playerLoc.getPitch());
        doInternalTeleport(player, dest);
    }

    // ==================== PUSHBACK PODCZAS ANIMACJI ====================

    /**
     * Pushback gracza gdy dotknie bariery podczas animacji.
     * 
     * Oblicza pozycję 1 blok WEWNĄTRZ bariery:
     * pozycja = centrum + (gracz - centrum).normalize() * (barrierRadius - 1.0)
     */
    private void pushbackToBarrier(Player player, Location playerLoc, Location center, double barrierRadius) {
        Vector fromCenter = playerLoc.toVector().subtract(center.toVector());
        if (fromCenter.lengthSquared() < 0.001) return; // gracz w centrum

        fromCenter.normalize();
        
        // Pozycja 1 blok WEWNĄTRZ bariery
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
        
        // Teleport przez scheduler dla pewności
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.teleport(destination);
                }
            }
        }.runTask(plugin);
        
        // Usuń flagę po 3 tickach
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

    // ==================== MOVE EVENT - BARIERA ZAWSZE ====================

    /**
     * GŁÓWNA OCHRONA BARIERY - DZIAŁA ZAWSZE (podczas i po animacji).
     * 
     * To jest główna ochrona. barrierTask to tylko backup.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from == null) return;

        // Tylko gdy gracz zmienia blok
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

        // === BARIERA - ZAWSZE (podczas i po animacji) ===
        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;
        if (to.getWorld() == null || !to.getWorld().equals(center.getWorld())) return;

        double barrierRadius = klatka.getRadius() - 0.5;
        
        if (to.distance(center) >= barrierRadius) {
            // ZATRZYMAJ RUCH
            event.setCancelled(true);
            sendBarrierFeedback(player);
        }
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // === NASZ TELEPORT - ZAWSZE PRZEPUŚĆ ===
        if (internalTeleports.contains(player.getUniqueId())) {
            event.setCancelled(false);
            return;
        }

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // Pluginowe /tp - przepuść
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;

        double barrierRadius = klatka.getRadius() - 0.5;

        // Blokuj teleport poza barierę
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
