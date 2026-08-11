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

    /**
     * Dystans teleportu w stronę środka klatki gdy gracz dotknie bariery (podczas animacji).
     */
    private static final double PUSHBACK_DISTANCE = 0.75;

    /**
     * Minimalny czas między feedbackami (dźwięk + subtitle) dla jednego gracza.
     */
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    /**
     * Material bloku shell.
     */
    private static final Material SHELL_MATERIAL = Material.BLUE_GLAZED_TERRACOTTA;

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

    // ==================== GŁÓWNY TASK ====================

    /**
     * Task uruchamiany co tick (50ms).
     *
     * PODCZAS ANIMACJI:
     * - System bezpieczeństwa (radius + 0.9) → teleport na środek
     * - Bariera (radius - 0.5) → pushback 0.75 w stronę środka
     *
     * PO ANIMACJI:
     * - System bezpieczeństwa (radius + 0.9) → teleport na środek
     * - Wykrywanie kolizji z blokami shell → wypychanie gracza z bloku
     */
    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    boolean animationComplete = klatka.isAnimationComplete();
                    double safetyRadius = klatka.getSafetyRadius();
                    double barrierRadius = klatka.getBarrierRadius();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location playerLoc = player.getLocation();
                        if (playerLoc == null || playerLoc.getWorld() == null) continue;

                        // Sprawdź czy ten sam świat
                        if (!playerLoc.getWorld().equals(center.getWorld())) continue;

                        double distance = playerLoc.distance(center);

                        // === SYSTEM BEZPIECZEŃSTWA (ZAWSZE) ===
                        // Jeśli gracz jest daleko poza klatką → teleport na środek
                        if (distance >= safetyRadius) {
                            Location safeLoc = center.clone();
                            safeLoc.setYaw(playerLoc.getYaw());
                            safeLoc.setPitch(playerLoc.getPitch());
                            doInternalTeleport(player, safeLoc);
                            sendBarrierFeedback(player);
                            continue; // Skip rest of checks
                        }

                        // === PODCZAS ANIMACJI - BARIERA SOFTWAROWA ===
                        if (!animationComplete) {
                            if (distance >= barrierRadius) {
                                Vector toCenter = center.toVector().subtract(playerLoc.toVector());
                                if (toCenter.lengthSquared() < 0.001) continue;

                                toCenter.normalize();
                                Vector pushback = toCenter.multiply(PUSHBACK_DISTANCE);

                                Location newLoc = playerLoc.clone().add(pushback);
                                newLoc.setYaw(playerLoc.getYaw());
                                newLoc.setPitch(playerLoc.getPitch());

                                doInternalTeleport(player, newLoc);
                                sendBarrierFeedback(player);
                            }
                        }
                        // === PO ANIMACJI - WYPYCHANIE Z BLOKÓW SHELL ===
                        else {
                            handleShellBlockCollision(player, playerLoc, center, klatka);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== KOLIZJA Z BLOKAMI SHELL ====================

    /**
     * Wykrywa czy gracz jest w kolizji z blokiem shell i wypycha go.
     * 
     * Przypadki:
     * 1. Gracz spada w dół i jest w bloku shell → teleport NA górę bloku
     * 2. Gracz jest w bloku shell z boku → teleport do najbliższej bezpiecznej pozycji (w stronę centrum)
     */
    private void handleShellBlockCollision(Player player, Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        if (player == null || playerLoc == null || center == null) return;

        // Sprawdź czy gracz jest w bloku shell
        Block blockAtPlayer = playerLoc.getBlock();
        Block blockAtPlayerHead = playerLoc.clone().add(0, 1, 0).getBlock();
        
        boolean inShellBlock = isShellBlock(blockAtPlayer) || isShellBlock(blockAtPlayerHead);
        
        if (!inShellBlock) return;

        // Sprawdź czy gracz spada (velocity w dół)
        Vector velocity = player.getVelocity();
        boolean isFalling = velocity.getY() < -0.1;

        if (isFalling) {
            // === GRACZ SPADA - TELEPORT NA GÓRĘ BLOKU ===
            Location safeLocation = findSafeLocationAbove(playerLoc);
            if (safeLocation != null) {
                safeLocation.setYaw(playerLoc.getYaw());
                safeLocation.setPitch(playerLoc.getPitch());
                doInternalTeleport(player, safeLocation);
            }
        } else {
            // === GRACZ W BLOKU Z BOKU - TELEPORT DO NAJBLIŻSZEJ BEZPIECZNEJ POZYCJI ===
            Location safeLocation = findSafeLocationTowardsCenter(playerLoc, center, klatka);
            if (safeLocation != null) {
                safeLocation.setYaw(playerLoc.getYaw());
                safeLocation.setPitch(playerLoc.getPitch());
                doInternalTeleport(player, safeLocation);
            }
        }
    }

    /**
     * Sprawdza czy blok jest blokiem shell.
     */
    private boolean isShellBlock(Block block) {
        if (block == null) return false;
        return block.getType() == SHELL_MATERIAL;
    }

    /**
     * Sprawdza czy lokacja jest bezpieczna dla gracza (2 bloki wysokości wolne).
     */
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        
        Block blockAtFeet = loc.getBlock();
        Block blockAtHead = loc.clone().add(0, 1, 0).getBlock();
        
        // Musi być wolne miejsce (nie solid)
        return !blockAtFeet.getType().isSolid() && !blockAtHead.getType().isSolid();
    }

    /**
     * Znajduje bezpieczną lokację nad blokiem shell (dla spadającego gracza).
     * Zwraca pozycję NA górze najbliższego bloku pod graczem.
     */
    private Location findSafeLocationAbove(Location playerLoc) {
        if (playerLoc == null || playerLoc.getWorld() == null) return null;

        // Szukaj najbliższego solidnego bloku pod graczem (max 5 bloków w dół)
        for (int y = 0; y <= 5; y++) {
            Location checkLoc = playerLoc.clone().subtract(0, y, 0);
            Block block = checkLoc.getBlock();
            
            if (block.getType().isSolid()) {
                // Znaleziono solidny blok - teleport NA górę tego bloku
                Location safeLoc = block.getLocation().clone().add(0.5, 1.0, 0.5);
                
                // Sprawdź czy nad blokiem jest wolne miejsce (2 bloki wysokości dla gracza)
                if (isSafeLocation(safeLoc)) {
                    return safeLoc;
                }
            }
        }

        // Fallback - teleport 1 blok wyżej
        return playerLoc.clone().add(0, 1, 0);
    }

    /**
     * Znajduje najbliższą bezpieczną lokację przed blokiem shell (w stronę centrum).
     * 
     * Iteruje od 0.1 do 2.0 bloków w stronę centrum, znajduje pierwszą bezpieczną pozycję.
     * Zapobiega teleportowaniu gracza za daleko (przez centrum na drugą stronę).
     */
    private Location findSafeLocationTowardsCenter(Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        if (playerLoc == null || center == null) return null;

        // Kierunek do centrum
        Vector toCenter = center.toVector().subtract(playerLoc.toVector());
        if (toCenter.lengthSquared() < 0.001) {
            // Gracz w centrum - teleport lekko w górę
            return playerLoc.clone().add(0, 0.5, 0);
        }

        toCenter.normalize();

        // Iteruj od 0.1 do 2.0 bloków w stronę centrum
        // Znajdź pierwszą bezpieczną pozycję (nie w bloku solid)
        for (double distance = 0.1; distance <= 2.0; distance += 0.1) {
            Location testLoc = playerLoc.clone().add(toCenter.clone().multiply(distance));
            
            // Sprawdź czy pozycja jest bezpieczna
            if (isSafeLocation(testLoc)) {
                // Dodatkowe sprawdzenie: czy nie wychodzimy poza klatkę
                double distFromCenter = testLoc.distance(center);
                if (distFromCenter < klatka.getBarrierRadius()) {
                    return testLoc;
                }
            }
        }

        // Fallback - teleport do centrum klatki (bezpieczne)
        Location centerSafe = center.clone();
        centerSafe.setY(playerLoc.getY()); // zachowaj wysokość gracza
        return centerSafe;
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

    // ==================== MOVE EVENT - BARIERA PODCZAS ANIMACJI ====================

    /**
     * GŁÓWNA OCHRONA PODCZAS ANIMACJI.
     * 
     * Sprawdza:
     * 1. Czy gracz nie wchodzi w zablokowany region WorldGuard → usuń z klatki
     * 2. PODCZAS ANIMACJI: Czy gracz nie próbuje wyjść poza barierę → zatrzymaj ruch
     * 
     * Po zakończeniu animacji - fizyczne bloki shell zatrzymują gracza naturalnie.
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

        // === SPRAWDŹ BARIERĘ - TYLKO PODCZAS ANIMACJI ===
        // Po zakończeniu animacji fizyczne bloki shell zatrzymują gracza
        if (!klatka.isAnimationComplete()) {
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
