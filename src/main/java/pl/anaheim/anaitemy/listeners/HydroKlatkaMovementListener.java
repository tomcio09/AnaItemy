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

    /**
     * Teleporty wykonane przez nas - ignorujemy je w onPlayerTeleport.
     * Używamy osobnego setu zamiast polegać na kolejności eventów.
     */
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    private BukkitTask barrierTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startBarrierTask();
    }

    // ==================== TASK ====================

    private void startBarrierTask() {
        barrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    if (center == null || center.getWorld() == null) continue;

                    boolean animDone = klatka.isAnimationComplete();
                    double barrierRadius = klatka.getBarrierRadius();
                    double safetyRadius = klatka.getSafetyRadius();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (loc.getWorld() == null) continue;

                        // === INNY ŚWIAT → USUŃ Z KLATKI ===
                        if (!loc.getWorld().equals(center.getWorld())) {
                            manager.removePlayerFromKlatka(player);
                            continue;
                        }

                        double dist = loc.distance(center);

                        // === SYSTEM BEZPIECZEŃSTWA ===
                        // Gracz jest co najmniej 1 kratkę za radius → teleport na środek
                        if (dist >= safetyRadius) {
                            teleportToCenter(player, loc, center);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === PODCZAS ANIMACJI - PUSHBACK ===
                        if (!animDone && dist >= barrierRadius) {
                            pushTowards(player, loc, center, barrierRadius);
                            sendBarrierFeedback(player);
                            continue;
                        }

                        // === PO ANIMACJI - GRACZ W BLOKU SHELL ===
                        if (animDone) {
                            handleShellCollision(player, loc, center, klatka);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    /**
     * Teleportuje gracza na środek klatki.
     * Zachowuje yaw/pitch.
     * Używa internalTeleports żeby blokować PlayerTeleportEvent.
     */
    private void teleportToCenter(Player player, Location playerLoc, Location center) {
        Location dest = center.clone();
        dest.setYaw(playerLoc.getYaw());
        dest.setPitch(playerLoc.getPitch());
        doInternalTeleport(player, dest);
    }

    // ==================== PUSHBACK PODCZAS ANIMACJI ====================

    /**
     * Pushback gracza w stronę centrum gdy dotknie bariery.
     * 
     * Matematyka:
     * - gracz dotknął bariery (dist >= barrierRadius)
     * - teleportujemy go na pozycję barrierRadius - 1.25 od centrum
     *   (czyli 1.25 bloku wewnątrz bariery)
     * - kierunek = (centrum - gracz).normalize()
     */
    private void pushTowards(Player player, Location playerLoc, Location center, double barrierRadius) {
        Vector dir = center.toVector().subtract(playerLoc.toVector());
        if (dir.lengthSquared() < 0.001) return;

        dir.normalize();

        // Pozycja = centrum - dir * (barrierRadius - 1.25)
        // Czyli: centrum przesunięte w kierunku GRACZA o (barrierRadius - 1.25)
        // = gracz przesuniany W STRONĘ centrum o tyle aby być 1.25 bloku od bariery
        double targetDist = barrierRadius - 1.25;
        if (targetDist < 0) targetDist = 0;

        Location dest = center.clone().subtract(dir.clone().multiply(targetDist));
        dest.setYaw(playerLoc.getYaw());
        dest.setPitch(playerLoc.getPitch());

        doInternalTeleport(player, dest);
    }

    // ==================== KOLIZJA Z SHELL PO ANIMACJI ====================

    /**
     * Po zakończeniu animacji: jeśli gracz jest w bloku shell,
     * teleportuj go do bezpiecznej pozycji wewnątrz klatki.
     *
     * Matematyka pozycji bezpiecznej:
     * - weź kierunek od centrum DO gracza
     * - bezpieczna pozycja = centrum + kierunek * (radius - 1.5)
     * - to jest zawsze WEWNĄTRZ klatki, tuż przed blokiem shell
     *
     * Dla spadającego gracza: najpierw znajdź solidny blok pod nim.
     */
    private void handleShellCollision(Player player, Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        Block feetBlock = playerLoc.getBlock();
        Block headBlock = playerLoc.clone().add(0, 1, 0).getBlock();

        boolean inShell = feetBlock.getType() == SHELL_MATERIAL
                || headBlock.getType() == SHELL_MATERIAL;

        if (!inShell) return;

        Vector velocity = player.getVelocity();
        boolean falling = velocity.getY() < -0.1;

        if (falling) {
            // Gracz spada w dół przez shell (np. przy dnie klatki)
            // Znajdź solidny blok pod nim i postaw go na górze
            Location aboveSolid = findSolidAbove(playerLoc, center, klatka);
            if (aboveSolid != null) {
                aboveSolid.setYaw(playerLoc.getYaw());
                aboveSolid.setPitch(playerLoc.getPitch());
                doInternalTeleport(player, aboveSolid);
            } else {
                // Fallback: bezpieczna pozycja w klatce
                doInternalTeleport(player, getSafeInsideLocation(playerLoc, center, klatka));
            }
        } else {
            // Gracz jest w bloku shell nie przez spadanie (boki, góra)
            // Teleport do bezpiecznej pozycji wewnątrz klatki
            Location safe = getSafeInsideLocation(playerLoc, center, klatka);
            safe.setYaw(playerLoc.getYaw());
            safe.setPitch(playerLoc.getPitch());
            doInternalTeleport(player, safe);
        }
    }

    /**
     * Oblicza BEZPIECZNĄ pozycję wewnątrz klatki dla gracza który jest w bloku shell.
     *
     * Logika:
     * - kierunek od centrum DO gracza
     * - bezpieczna pozycja = centrum + kierunek * (radius - 1.5)
     * - zawsze WEWNĄTRZ klatki, 1.5 bloku od shell
     *
     * Jeśli gracz jest dokładnie w centrum (edge case), zwraca centrum + małe przesunięcie w górę.
     */
    private Location getSafeInsideLocation(Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        Vector fromCenter = playerLoc.toVector().subtract(center.toVector());

        if (fromCenter.lengthSquared() < 0.001) {
            // Gracz dokładnie w centrum - przesuń lekko w górę
            Location safe = center.clone();
            safe.setYaw(playerLoc.getYaw());
            safe.setPitch(playerLoc.getPitch());
            return safe;
        }

        fromCenter.normalize();

        // Bezpieczna odległość od centrum: radius - 1.5 (1.5 bloku przed shell)
        double safeDist = Math.max(0, klatka.getRadius() - 1.5);
        Location safe = center.clone().add(fromCenter.multiply(safeDist));
        safe.setYaw(playerLoc.getYaw());
        safe.setPitch(playerLoc.getPitch());
        return safe;
    }

    /**
     * Dla gracza który spada przez dolny shell:
     * Szuka solidnego bloku powyżej gracza (wewnątrz klatki) i zwraca pozycję na jego górze.
     * Szuka też poniżej jeśli gracz jest zawieszony.
     *
     * Fallback: getSafeInsideLocation.
     */
    private Location findSolidAbove(Location playerLoc, Location center, ActiveHydroKlatka klatka) {
        if (playerLoc.getWorld() == null) return null;

        // Szukaj solidnego bloku PONIŻEJ gracza (który mógłby go "złapać")
        // max 3 bloki w dół
        for (int dy = 1; dy <= 3; dy++) {
            Location checkLoc = playerLoc.clone().subtract(0, dy, 0);
            Block block = checkLoc.getBlock();

            if (block.getType().isSolid() && block.getType() != SHELL_MATERIAL) {
                // Znaleziono solidny blok nie-shell pod graczem
                Location top = block.getLocation().clone().add(0.5, 1.0, 0.5);

                // Sprawdź czy jest wewnątrz klatki
                if (top.distance(center) < klatka.getBarrierRadius()) {
                    return top;
                }
            }
        }

        return null; // Fallback do getSafeInsideLocation
    }

    // ==================== TELEPORT WEWNĘTRZNY ====================

    /**
     * Oznacza teleport jako "nasz" i wykonuje go.
     * Flaga zostaje usunięta po 2 tickach (zabezpieczenie na wypadek opóźnień).
     */
    private void doInternalTeleport(Player player, Location destination) {
        if (player == null || destination == null) return;

        // Dodaj flagę PRZED teleportem
        internalTeleports.add(player.getUniqueId());
        player.teleport(destination);

        // Usuń po 2 tickach (bezpieczny margines)
        new BukkitRunnable() {
            @Override
            public void run() {
                internalTeleports.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, 2L);
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

        // Tylko jeśli gracz faktycznie zmienił pozycję bloku
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

        // === BARIERA SOFTWAROWA - TYLKO PODCZAS ANIMACJI ===
        if (!klatka.isAnimationComplete()) {
            Location center = klatka.getCenter();
            if (center == null || center.getWorld() == null) return;
            if (to.getWorld() == null || !to.getWorld().equals(center.getWorld())) return;

            if (to.distance(center) >= klatka.getBarrierRadius()) {
                event.setCancelled(true);
                sendBarrierFeedback(player);
            }
        }
    }

    // ==================== TELEPORT EVENT ====================

    /**
     * NAJWYŻSZY PRIORYTET = MONITOR nie, używamy HIGHEST żeby być późno
     * ale móc jeszcze anulować.
     *
     * Kluczowe: sprawdzamy internalTeleports NA POCZĄTKU.
     * Jeśli to nasz teleport - setCancelled(false) i return.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // === NASZ TELEPORT - ZAWSZE PRZEPUŚĆ ===
        // HIGHEST priorytet = inne pluginy już zdążyły anulować
        // Nadpisujemy ich decyzję jeśli to nasz teleport
        if (internalTeleports.contains(player.getUniqueId())) {
            event.setCancelled(false);
            return;
        }

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        // Pluginowe teleporty (np. /tp admina) - przepuść
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        if (center == null || center.getWorld() == null) return;

        // Blokuj jeśli cel jest poza barierą
        if (to.getWorld() == null
                || !to.getWorld().equals(center.getWorld())
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

    public void stopTasks() {
        if (barrierTask != null) {
            barrierTask.cancel();
            barrierTask = null;
        }
        lastFeedback.clear();
        internalTeleports.clear();
    }
}
