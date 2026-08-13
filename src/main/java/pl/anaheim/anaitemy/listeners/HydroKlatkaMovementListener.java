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
import java.util.concurrent.atomic.AtomicInteger;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    /**
     * Próg bariery: radius - BARRIER_OFFSET
     * Im większy offset, tym bariera jest bliżej środka.
     * 0.8 = bariera niewidzialna jest 0.8 bloków od shell (bliżej środka).
     */
    private static final double BARRIER_OFFSET = 0.8;

    /**
     * Liczba KOLEJNYCH ticków przez które gracz musi być za barierą,
     * żeby dostać teleport awaryjny.
     *
     * Elytriści uderzający w shell są za barierą przez 1-2 ticki (bounce),
     * więc debounce = 3 ticki eliminuje fałszywe teleporty.
     *
     * Gracz który rzeczywiście uciekł (bug/hack) będzie za barierą
     * przez wiele ticków → po 3 tickach dostanie teleport.
     */
    private static final int TELEPORT_DEBOUNCE_TICKS = 3;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    /**
     * Licznik kolejnych ticków poza barierą dla każdego gracza.
     * Klucz: UUID gracza, Wartość: liczba kolejnych ticków poza barierą.
     */
    private final Map<UUID, Integer> outsideBarrierTicks = new ConcurrentHashMap<>();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== GŁÓWNY TASK ====================

    /**
     * Logika debounce:
     *
     * Co tick sprawdzamy każdego trapped gracza.
     * Jeśli jest za barierą → inkrementujemy licznik.
     * Jeśli jest wewnątrz → resetujemy licznik do 0.
     *
     * Teleport następuje dopiero gdy licznik >= TELEPORT_DEBOUNCE_TICKS.
     *
     * Elytrysta uderzający w shell:
     *   tick 1: za barierą (licznik=1)
     *   tick 2: znów w środku po odbiciu (licznik=0) → NO teleport ✓
     *
     * Gracz który rzeczywiście uciekł:
     *   tick 1: za barierą (licznik=1)
     *   tick 2: za barierą (licznik=2)
     *   tick 3: za barierą (licznik=3) → TELEPORT ✓
     */
    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    // Bariera: radius - BARRIER_OFFSET
                    double barrierRadius = klatka.getRadius() - BARRIER_OFFSET;

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) {
                            // Gracz offline - resetuj licznik
                            outsideBarrierTicks.remove(uuid);
                            continue;
                        }

                        Location loc = player.getLocation();
                        if (loc == null || loc.getWorld() == null) {
                            outsideBarrierTicks.remove(uuid);
                            continue;
                        }

                        // Inny świat -> usuń z klatki, resetuj licznik
                        if (!loc.getWorld().equals(center.getWorld())) {
                            outsideBarrierTicks.remove(uuid);
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        double dist = loc.distance(center);

                        if (dist >= barrierRadius) {
                            // Gracz jest za barierą lub na niej → inkrementuj licznik
                            int ticks = outsideBarrierTicks.getOrDefault(uuid, 0) + 1;
                            outsideBarrierTicks.put(uuid, ticks);

                            if (ticks >= TELEPORT_DEBOUNCE_TICKS) {
                                // Gracz jest za barierą przez wystarczająco długo → teleport
                                outsideBarrierTicks.put(uuid, 0); // reset po teleporcie
                                teleportToCenter(player, loc, center, klatka);
                                sendBarrierFeedback(player);
                            }
                            // Jeśli ticks < TELEPORT_DEBOUNCE_TICKS: czekamy (może to elytra bounce)
                        } else {
                            // Gracz jest wewnątrz bariery → resetuj licznik
                            outsideBarrierTicks.put(uuid, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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

        // Szukaj w spirali od środka
        int maxSearchRadius = Math.max(3, klatka.getRadius() - 2);

        for (int r = 1; r <= maxSearchRadius; r++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        // Tylko obwód pierścienia (najbliższe pozycje)
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;

                        Location candidate = new Location(
                                center.getWorld(),
                                center.getBlockX() + dx + 0.5,
                                center.getBlockY() + dy,
                                center.getBlockZ() + dz + 0.5,
                                yaw,
                                pitch
                        );

                        // Musi być sensownie wewnątrz klatki
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
     * Blokuje ruch przekraczający barierę.
     * Ten event anuluje ruch NATYCHMIAST (bez debounce),
     * bo tu nie ma problemu z fałszywymi teleportami - anulowanie ruchu jest bezpieczne.
     *
     * Task z debounce służy jako backup dla przypadków których event nie łapie
     * (np. teleport, lag, plugin bypass).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from == null) return;

        // Ignoruj obrót głową (bez zmiany pozycji bloku)
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

        double barrierRadius = klatka.getRadius() - BARRIER_OFFSET;

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

        double barrierRadius = klatka.getRadius() - BARRIER_OFFSET;

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
        outsideBarrierTicks.remove(uuid);
    }

    public void stopTasks() {
        if (barrierTask != null) {
            barrierTask.cancel();
            barrierTask = null;
        }
        lastFeedback.clear();
        internalTeleports.clear();
        outsideBarrierTicks.clear();
    }
}
