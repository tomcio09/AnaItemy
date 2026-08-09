package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    private static final long SOUND_COOLDOWN_MS = 400;
    private final Map<UUID, Long> lastSoundTime = new ConcurrentHashMap<>();

    /**
     * ✅ PROMIEŃ BARIERY:
     * Shell jest budowany na: distance > radius - 1.0 && distance <= radius
     * Bariera = wewnętrzna krawędź shella minus margines na hitbox gracza (0.3)
     * Gracz ma hitbox 0.6 szeroki, więc środek gracza musi być 0.3 od bloku shella
     */
    private static final double BARRIER_INSET = 1.3;

    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== CLAMP TASK — CO TICK ====================

    /**
     * ✅ HARD CLAMP: Co tick wymusza pozycję gracza wewnątrz bariery.
     *
     * Logika:
     * 1. Oblicz dystans gracza od centrum klatki
     * 2. Jeśli >= barrierRadius → clampuj pozycję NA granicy bariery
     *    (nie na środek, tylko w tym samym kierunku ale bliżej)
     * 3. Wyzeruj komponent velocity na zewnątrz
     * 4. Teleport na środek TYLKO jeśli gracz > radius + 5 (exploit)
     */
    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    double radius = klatka.getRadius();
                    double barrierRadius = radius - BARRIER_INSET;

                    for (UUID playerId : klatka.getTrappedPlayers()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        double distance = loc.distance(center);

                        // ✅ EXPLOIT: Gracz daleko poza klatką → środek
                        if (distance > radius + 5.0) {
                            Location tp = center.clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                            player.setVelocity(new Vector(0, 0, 0));
                            continue;
                        }

                        // ✅ HARD CLAMP: Gracz poza barierą → wymuś pozycję na granicy
                        if (distance >= barrierRadius) {
                            clampPlayerPosition(player, center, barrierRadius, distance);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * ✅ Wymusza pozycję gracza dokładnie na granicy bariery.
     * Gracz zostaje w tym samym KIERUNKU od centrum, ale na bezpiecznym dystansie.
     * NIE teleportuje na środek — gracz zostaje przy ścianie.
     */
    private void clampPlayerPosition(Player player, Location center,
                                      double barrierRadius, double currentDistance) {
        Location loc = player.getLocation();

        // Kierunek od centrum do gracza
        Vector fromCenter = loc.toVector().subtract(center.toVector());

        if (fromCenter.lengthSquared() < 0.001) {
            // Gracz dokładnie na środku — nie ruszaj
            return;
        }

        Vector direction = fromCenter.clone().normalize();

        // ✅ Bezpieczna pozycja = na granicy bariery minus mały margines
        double safeDistance = barrierRadius - 0.05;

        Location safeLoc = center.clone().add(direction.multiply(safeDistance));
        safeLoc.setY(loc.getY()); // ✅ Zachowaj Y gracza — nie przesuwaj w pionie
        safeLoc.setYaw(loc.getYaw());
        safeLoc.setPitch(loc.getPitch());

        // ✅ Sprawdź czy Y też nie wychodzi poza barierę
        // (gracz spada przez dolną granicę lub wylatuje przez górną)
        double safeLocDist = safeLoc.distance(center);
        if (safeLocDist >= barrierRadius) {
            // Y powoduje wyjście poza barierę — clampuj też Y
            Vector safeFromCenter = safeLoc.toVector().subtract(center.toVector());
            if (safeFromCenter.lengthSquared() > 0.001) {
                safeFromCenter.normalize().multiply(safeDistance);
                safeLoc = center.clone().add(safeFromCenter);
                safeLoc.setYaw(loc.getYaw());
                safeLoc.setPitch(loc.getPitch());
            }
        }

        // ✅ Teleportuj gracza na bezpieczną pozycję
        player.teleport(safeLoc);

        // ✅ Wyzeruj velocity w kierunku na zewnątrz
        clampVelocity(player, center);

        // ✅ Feedback (dźwięk + subtitle) z anti-spam
        playBarrierFeedback(player);
    }

    /**
     * ✅ Zeruje komponent velocity gracza w kierunku na zewnątrz klatki.
     * Zachowuje velocity WZDŁUŻ ściany (ruch obrotowy) i w pionie (spadanie OK wewnątrz).
     */
    private void clampVelocity(Player player, Location center) {
        Vector velocity = player.getVelocity();
        if (velocity.lengthSquared() < 0.0001) return;

        Location loc = player.getLocation();
        Vector fromCenter = loc.toVector().subtract(center.toVector());

        if (fromCenter.lengthSquared() < 0.001) return;

        Vector outwardDir = fromCenter.clone().normalize();

        // Oblicz ile velocity idzie "na zewnątrz"
        double outwardSpeed = velocity.dot(outwardDir);

        if (outwardSpeed > 0) {
            // ✅ Usuń komponent na zewnątrz
            velocity.subtract(outwardDir.clone().multiply(outwardSpeed));
            player.setVelocity(velocity);
        }
    }

    // ==================== PLAYER MOVE EVENT ====================

    /**
     * ✅ Dodatkowa warstwa ochrony — MoveEvent łapie ruch ZANIM gracz się przesunie.
     * ClampTask koryguje PO przesunięciu. Razem = szczelna bariera.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Ignoruj obroty głowy
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();
        double barrierRadius = radius - BARRIER_INSET;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Gracz na zablokowanym regionie — wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        double distanceTo = to.distance(center);
        double distanceFrom = from.distance(center);

        // ✅ Gracz próbuje przekroczyć barierę — cofnij do FROM
        if (distanceTo >= barrierRadius) {

            if (distanceFrom < barrierRadius) {
                // ✅ FROM jest bezpieczne — cofnij tam
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
            } else {
                // ✅ FROM też jest poza barierą — clampuj TO na granicę
                Vector fromCenterDir = to.toVector().subtract(center.toVector());
                if (fromCenterDir.lengthSquared() > 0.001) {
                    fromCenterDir.normalize();
                }

                Location clamped = center.clone().add(
                        fromCenterDir.multiply(barrierRadius - 0.05));
                clamped.setY(to.getY());
                clamped.setYaw(to.getYaw());
                clamped.setPitch(to.getPitch());

                // Sprawdź czy clamped Y nie wychodzi poza barierę
                if (clamped.distance(center) >= barrierRadius) {
                    Vector clampedDir = clamped.toVector().subtract(center.toVector());
                    if (clampedDir.lengthSquared() > 0.001) {
                        clampedDir.normalize().multiply(barrierRadius - 0.05);
                        clamped = center.clone().add(clampedDir);
                        clamped.setYaw(to.getYaw());
                        clamped.setPitch(to.getPitch());
                    }
                }

                event.setTo(clamped);
            }

            // ✅ Zeruj velocity na zewnątrz
            clampVelocity(player, center);

            playBarrierFeedback(player);
        }
    }

    // ==================== TELEPORT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location to = event.getTo();
        if (to == null) return;

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();
        double barrierRadius = radius - BARRIER_INSET;

        // Teleport pluginowy wewnątrz klatki — pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < barrierRadius) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // Blokuj teleport poza barierę
        if (to.distance(center) >= barrierRadius) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== DŹWIĘK I SUBTITLE ====================

    private void playBarrierFeedback(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastSound = lastSoundTime.get(uuid);
        if (lastSound != null && now - lastSound < SOUND_COOLDOWN_MS) {
            return;
        }

        lastSoundTime.put(uuid, now);

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

    // ==================== CLEANUP ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);

        if (klatka != null) {
            klatka.addOfflinePlayer(player.getUniqueId());
        }

        lastSoundTime.remove(player.getUniqueId());
    }

    /**
     * ✅ Cleanup przy wyłączeniu pluginu.
     */
    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastSoundTime.clear();
    }
}
