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
     * BARIERA RUCHU (PlayerMoveEvent):
     * radius - MOVE_BARRIER_OFFSET
     * Niewidzialna ściana wewnątrz klatki.
     */
    private static final double MOVE_BARRIER_OFFSET = 0.8;

    /**
     * Liczba KOLEJNYCH ticków przez które gracz musi być za MOVE_BARRIER
     * LUB w solid bloku klatki, żeby dostać teleport awaryjny.
     *
     * Zabezpieczenie przed fałszywym teleportem elytrzystów.
     */
    private static final int TELEPORT_DEBOUNCE_TICKS = 3;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> outsideBarrierTicks = new ConcurrentHashMap<>();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== GŁÓWNY TASK ====================

    /**
     * Logika:
     *
     * Gracz potrzebuje teleportu awaryjnego gdy:
     * A) Jest za MOVE_BARRIER (dist >= radius - 0.8) - przeszedł przez barierę
     * B) Jest w SOLID bloku (zbugowany w shell podczas tworzenia)
     *
     * Oba przypadki inkrementują licznik. Po TELEPORT_DEBOUNCE_TICKS → teleport.
     *
     * Gracz wewnątrz bariery i NIE w solid bloku → reset licznika.
     */
    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    double moveBarrier = klatka.getRadius() - MOVE_BARRIER_OFFSET;

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) {
                            outsideBarrierTicks.remove(uuid);
                            continue;
                        }

                        Location loc = player.getLocation();
                        if (loc == null || loc.getWorld() == null) {
                            outsideBarrierTicks.remove(uuid);
                            continue;
                        }

                        // Inny świat → usuń z klatki
                        if (!loc.getWorld().equals(center.getWorld())) {
                            outsideBarrierTicks.remove(uuid);
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        double dist = loc.distance(center);
                        boolean beyondMoveBarrier = dist >= moveBarrier;
                        boolean inSolidBlock = isPlayerInSolidBlock(player);
                        boolean isKlatkaBlock = manager.isKlatkaBlock(loc.getBlock().getLocation());

                        // Gracz potrzebuje teleportu gdy:
                        // 1. Jest za move barrier (przeszedł niewidzialną ścianę)
                        // 2. Jest w solid bloku KLATKI (zbugowany w shell/mapped block)
                        boolean needsTeleport = beyondMoveBarrier || (inSolidBlock && isKlatkaBlock);

                        if (needsTeleport) {
                            int ticks = outsideBarrierTicks.getOrDefault(uuid, 0) + 1;
                            outsideBarrierTicks.put(uuid, ticks);

                            if (ticks >= TELEPORT_DEBOUNCE_TICKS) {
                                outsideBarrierTicks.put(uuid, 0);
                                performTeleportToCenter(player, center, klatka);
                                sendBarrierFeedback(player);
                            }
                        } else {
                            // Gracz jest bezpiecznie wewnątrz → reset
                            outsideBarrierTicks.put(uuid, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== SPRAWDZENIE SOLID BLOKU ====================

    /**
     * Sprawdza czy gracz stoi w solid bloku (nogi LUB głowa).
     * Łapie gracza zbugowanego w shell podczas tworzenia klatki.
     */
    private boolean isPlayerInSolidBlock(Player player) {
        if (player == null) return false;
        Location loc = player.getLocation();
        if (loc == null || loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();

        return feet.getType().isSolid() || head.getType().isSolid();
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    /**
     * Wykonuje teleport gracza na bezpieczne miejsce blisko środka klatki.
     * Teleport jest SYNCHRONICZNY (w tym samym ticku) żeby uniknąć
     * blokowania przez PlayerMoveEvent/PlayerTeleportEvent.
     */
    private void performTeleportToCenter(Player player, Location center, ActiveHydroKlatka klatka) {
        if (player == null || center == null || klatka == null) return;

        Location playerLoc = player.getLocation();
        if (playerLoc == null) return;

        Location destination = findSafeCenterLocation(center, klatka, playerLoc.getYaw(), playerLoc.getPitch());
        if (destination == null) return;

        // Oznacz jako nasz teleport PRZED teleportacją
        internalTeleports.add(player.getUniqueId());

        // Teleport synchroniczny - w tym samym ticku co task
        player.teleport(destination);

        // Usuń flagę po kilku tickach
        new BukkitRunnable() {
            @Override
            public void run() {
                internalTeleports.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 5L);
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
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;

                        Location candidate = new Location(
                                center.getWorld(),
                                center.getBlockX() + dx + 0.5,
                                center.getBlockY() + dy,
                                center.getBlockZ() + dz + 0.5,
                                yaw,
                                pitch
                        );

                        if (candidate.distance(center) >= klatka.getRadius() - 1.5) continue;

                        if (isSafeForPlayer(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        // Fallback - exact center
        return exact;
    }

    private boolean isSafeForPlayer(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return !feet.getType().isSolid() && !head.getType().isSolid();
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
                        .deserialize("&cNie możesz opuszczać &4podwodnej klatki&c!"),
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

        // Przepuść nasze teleporty
        if (internalTeleports.contains(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from == null) return;

        // Ignoruj obrót głową
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

        double moveBarrier = klatka.getRadius() - MOVE_BARRIER_OFFSET;

        if (to.distance(center) >= moveBarrier) {
            event.setCancelled(true);
            sendBarrierFeedback(player);
        }
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // Nasz teleport - ZAWSZE przepuść
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

        double moveBarrier = klatka.getRadius() - MOVE_BARRIER_OFFSET;

        if (to.getWorld() == null
                || !to.getWorld().equals(center.getWorld())
                || to.distance(center) >= moveBarrier) {
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
