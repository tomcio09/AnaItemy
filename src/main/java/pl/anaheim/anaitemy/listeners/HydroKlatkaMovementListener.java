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

    /**
     * Dystans teleportu w stronę środka klatki gdy gracz dotknie bariery (backup).
     */
    private static final double PUSHBACK_DISTANCE = 0.75;

    /**
     * Minimalny czas między feedbackami (dźwięk + subtitle) dla jednego gracza.
     */
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();

    /**
     * Zbiór UUID graczy, których teleportujemy MY (bariera/bezpieczeństwo).
     * Używany żeby nie blokować naszych własnych teleportów.
     */
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== GŁÓWNY TASK - BACKUP ====================

    /**
     * Task uruchamiany co tick (50ms) - BACKUP dla szybkiego ruchu.
     *
     * Dla każdego trapped gracza sprawdza:
     * 1. Czy gracz jest poza strefą bezpieczeństwa (radius + 0.8) → teleport na środek
     * 2. Czy gracz jest poza barierą (radius - 0.5) → teleport 0.75 w stronę środka
     *
     * Główna ochrona jest w PlayerMoveEvent - ten task to backup dla elytra/teleportów.
     */
    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location playerLoc = player.getLocation();
                        if (playerLoc == null || playerLoc.getWorld() == null) continue;

                        // Sprawdź czy ten sam świat
                        if (!playerLoc.getWorld().equals(center.getWorld())) continue;

                        double distance = playerLoc.distance(center);

                        // === SYSTEM BEZPIECZEŃSTWA ===
                        // Gracz jest daleko poza klatką (glitch) → teleport na środek
                        if (distance >= klatka.getSafetyRadius()) {
                            Location safeLoc = center.clone();
                            safeLoc.setYaw(playerLoc.getYaw());
                            safeLoc.setPitch(playerLoc.getPitch());
                            doInternalTeleport(player, safeLoc);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === BARIERA BACKUP ===
                        // Jeśli gracz jakoś jest poza barierą (np. szybki ruch elytrą)
                        // → pushback 0.75 w stronę centrum
                        if (distance >= klatka.getBarrierRadius()) {
                            // Oblicz kierunek od gracza DO centrum
                            Vector toCenter = center.toVector().subtract(playerLoc.toVector());

                            // Zabezpieczenie: jeśli gracz jest dokładnie w centrum
                            if (toCenter.lengthSquared() < 0.001) {
                                continue;
                            }

                            toCenter.normalize();
                            Vector pushback = toCenter.multiply(PUSHBACK_DISTANCE);

                            Location newLoc = playerLoc.clone().add(pushback);
                            newLoc.setYaw(playerLoc.getYaw());
                            newLoc.setPitch(playerLoc.getPitch());

                            doInternalTeleport(player, newLoc);
                            sendBarrierFeedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== TELEPORT WEWNĘTRZNY ====================

    /**
     * Teleportuje gracza i oznacza teleport jako "nasz" żeby listener teleportów
     * go nie blokował.
     */
    private void doInternalTeleport(Player player, Location destination) {
        if (player == null || destination == null) return;
        internalTeleports.add(player.getUniqueId());
        player.teleport(destination);

        // Usuń flagę w następnym TICK-u
        new BukkitRunnable() {
            @Override
            public void run() {
                internalTeleports.remove(player.getUniqueId());
            }
        }.runTask(plugin);
    }

    // ==================== FEEDBACK ====================

    /**
     * Wysyła dźwięk i subtitle graczowi, z cooldownem żeby nie spamować.
     * Package-private żeby onPlayerMove mógł używać.
     */
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

    // ==================== MOVE EVENT - GŁÓWNA OCHRONA ====================

    /**
     * GŁÓWNA OCHRONA PRZED WYJŚCIEM Z KLATKI.
     * 
     * Sprawdza:
     * 1. Czy gracz nie wchodzi w zablokowany region WorldGuard → usuń z klatki
     * 2. Czy gracz nie próbuje wyjść poza barierę → zatrzymaj ruch i wyświetl feedback
     * 
     * To działa dla normalnego ruchu (chodzenie, bieganie, pływanie).
     * Dla szybkiego ruchu (elytra) działa barrierTask jako backup.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from == null) return;

        // Optymalizacja - sprawdź czy gracz faktycznie się poruszył (nie tylko obrócił głową)
        if (from.getBlockX() == to.getBlockX() 
                && from.getBlockY() == to.getBlockY() 
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // === SPRAWDŹ WORLDGUARD ===
        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // === SPRAWDŹ BARIERĘ ===
        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;
        if (to.getWorld() == null || !to.getWorld().equals(center.getWorld())) return;

        double distanceTo = to.distance(center);
        
        // Jeśli gracz próbuje wyjść poza barierę - ZATRZYMAJ RUCH
        if (distanceTo >= klatka.getBarrierRadius()) {
            // Anuluj ruch - gracz zostaje w miejscu (jak uderzenie w blok)
            event.setCancelled(true);
            
            // Wyświetl feedback (z cooldownem)
            sendBarrierFeedback(player);
        }
    }

    // ==================== TELEPORT EVENT ====================

    /**
     * Blokuje zewnętrzne teleporty trapped graczy poza klatkę.
     * Przepuszcza NASZE teleporty (bariera/bezpieczeństwo).
     * Przepuszcza teleporty typu PLUGIN.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
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

        // Teleporty pluginowe (np. /tp) - przepuść
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;

        // Sprawdź czy cel teleportu jest poza barierę
        if (to.getWorld() == null || !to.getWorld().equals(center.getWorld())
                || to.distance(center) >= klatka.getBarrierRadius()) {
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

    /**
     * Zatrzymuje task bariery i czyści dane.
     * Wywoływane przy wyłączaniu pluginu.
     */
    public void stopTasks() {
        if (barrierTask != null) {
            barrierTask.cancel();
            barrierTask = null;
        }
        lastFeedback.clear();
        internalTeleports.clear();
    }
}
