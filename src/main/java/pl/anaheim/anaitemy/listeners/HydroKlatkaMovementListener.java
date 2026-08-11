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

    /**
     * Task co tick.
     *
     * Progi (od centrum):
     * 
     * [0 ........... radius-0.5 ... radius ... radius+∞]
     *  |   WEWNĄTRZ   | BARIERA |  TELEPORT NA ŚRODEK  |
     *  |   (OK)       | (push)  |  (agresywny)         |
     *  |              |         |                       |
     *  | elytra OK    | cancel  | zawsze tp na środek   |
     *  |              | move    |                       |
     *
     * radius - 0.5 = wewnętrzna krawędź shell (połowa bloku)
     * radius       = zewnętrzna krawędź shell (koniec bloku)
     *
     * Gracz na elytrze obija się o bloki od WEWNĄTRZ (distance < radius - 0.5)
     * więc NIE jest łapany przez żaden próg.
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
                    double barrierRadius = radius - 0.5;   // połowa shell bloku (wewnętrzna krawędź)
                    double shellOuterEdge = radius;         // zewnętrzna krawędź shell bloku

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

                        // === AGRESYWNY TELEPORT NA ŚRODEK ===
                        // Gracz jest za zewnętrzną krawędzią shell bloku
                        // = zbugowany w shell tak że hitbox nie wystaje
                        // = wypadł z klatki
                        // Teleport NATYCHMIASTOWY na środek
                        if (dist >= shellOuterEdge) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === BARIERA (połowa shell bloku) ===
                        // Gracz jest między wewnętrzną a zewnętrzną krawędzią shell
                        // = w bloku shell od wewnętrznej strony
                        // Pushback w stronę centrum
                        if (dist >= barrierRadius) {
                            // Podczas animacji: pushback
                            if (!klatka.isAnimationComplete()) {
                                pushbackToBarrier(player, loc, center, barrierRadius);
                                sendBarrierFeedback(player);
                            }
                            // Po animacji: sprawdź czy gracz jest W bloku shell
                            else if (isPlayerInShellBlock(loc)) {
                                // Gracz jest w bloku shell → teleport na środek
                                teleportToCenter(player, loc, center);
                                sendBarrierFeedback(player);
                            }
                            // Po animacji: gracz jest blisko bariery ale NIE w bloku shell
                            // = normalne obijanie się o bloki (elytra, chodzenie)
                            // → nie robimy nic, fizyczne bloki go zatrzymują
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== SPRAWDZENIE BLOKU SHELL ====================

    /**
     * Sprawdza czy gracz jest w bloku shell (stopy lub głowa).
     */
    private boolean isPlayerInShellBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block feetBlock = loc.getBlock();
        Block headBlock = loc.clone().add(0, 1, 0).getBlock();

        return feetBlock.getType() == SHELL_MATERIAL || headBlock.getType() == SHELL_MATERIAL;
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    /**
     * Teleportuje gracza na środek klatki. Zachowuje yaw/pitch.
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
     * Pozycja = na linii gracz-centrum, 1 blok wewnątrz bariery.
     */
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

    /**
     * Główna ochrona bariery.
     *
     * Anuluje ruch gdy gracz próbuje przekroczyć barierę (radius - 0.5).
     * Działa ZAWSZE (podczas i po animacji).
     *
     * Gracz na elytrze obija się o fizyczne bloki shell od wewnątrz
     * (distance < radius - 0.5) więc ten event go NIE łapie.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from == null) return;

        // Tylko jeśli gracz faktycznie zmienił pozycję
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

        // Nasz teleport - zawsze przepuść
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
