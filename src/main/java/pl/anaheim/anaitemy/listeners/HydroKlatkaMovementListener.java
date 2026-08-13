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
     * Dotyczy TYLKO graczy którzy NIE lecą elytrą.
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
     * Teleport awaryjny działa TYLKO:
     * 1. Po zakończeniu animacji budowania
     * 2. Gdy gracz NIE jest w locie elytrą
     *
     * Gracz lecący elytrą:
     *   → Polegamy WYŁĄCZNIE na PlayerMoveEvent (bariera ruchu)
     *   → Task RESETUJE licznik (nie teleportuje)
     *   → Gdy gracz uderzy w shell i wejdzie w blok - nadal leci (isGliding=true)
     *   → Dopiero gdy przestanie lecieć (wyląduje, straci elytrę) 
     *     i nadal jest w solid bloku klatki → task zacznie liczyć → teleport
     *
     * Gracz NIE lecący elytrą:
     *   A) Za MOVE_BARRIER (dist >= radius - 0.8)
     *   B) W SOLID bloku klatki (zbugowany w shell)
     *   → po TELEPORT_DEBOUNCE_TICKS tickach → teleport na środek
     */
    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    // Nie teleportuj podczas animacji budowania
                    if (!klatka.isAnimationComplete()) continue;

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

                        // ✅ Gracz leci elytrą → TYLKO bariera ruchu (PlayerMoveEvent)
                        // Nie teleportuj - gracz odbijający się od shell jest w locie
                        // i naturalnie się wyciągnie z bloku
                        if (player.isGliding()) {
                            outsideBarrierTicks.put(uuid, 0);
                            continue;
                        }

                        double dist = loc.distance(center);
                        boolean beyondMoveBarrier = dist >= moveBarrier;
                        boolean inSolidBlock = isPlayerInSolidBlock(player);
                        boolean isKlatkaBlock = manager.isKlatkaBlock(loc.getBlock().getLocation());

                        // Gracz NIE leci → sprawdź czy potrzebuje teleportu
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
                            outsideBarrierTicks.put(uuid, 0);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== SPRAWDZENIE SOLID BLOKU ====================

    private boolean isPlayerInSolidBlock(Player player) {
        if (player == null) return false;
        Location loc = player.getLocation();
        if (loc == null || loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();

        return feet.getType().isSolid() || head.getType().isSolid();
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    private void performTeleportToCenter(Player player, Location center, ActiveHydroKlatka klatka) {
        if (player == null || center == null || klatka == null) return;

        Location playerLoc = player.getLocation();
        if (playerLoc == null) return;

        Location destination = findSafeCenterLocation(center, klatka, playerLoc.getYaw(), playerLoc.getPitch());
        if (destination == null) return;

        internalTeleports.add(player.getUniqueId());

        player.teleport(destination);

        new BukkitRunnable() {
            @Override
            public void run() {
                internalTeleports.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 5L);
    }

    private Location findSafeCenterLocation(Location center, ActiveHydroKlatka klatka, float yaw, float pitch) {
        if (center == null || center.getWorld() == null) return center;

        Location exact = center.clone();
        exact.setYaw(yaw);
        exact.setPitch(pitch);

        if (isSafeForPlayer(exact)) {
            return exact;
        }

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
