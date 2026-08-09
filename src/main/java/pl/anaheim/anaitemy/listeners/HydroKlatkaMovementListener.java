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

    // ✅ Task który co tick sprawdza velocity graczy i koryguje pozycje
    private BukkitTask velocityTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startVelocityTask();
    }

    // ==================== VELOCITY TASK ====================
    // ✅ Co tick sprawdza WSZYSTKICH uwięzionych graczy i koryguje ich pozycję/velocity
    // To jest kluczowe — PlayerMoveEvent nie wystarczy bo velocity działa między eventami

    private void startVelocityTask() {
        velocityTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    double radius = klatka.getRadius();

                    for (UUID playerId : klatka.getTrappedPlayers()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        // ✅ Sprawdź czy gracz jest w/przy strefie shella
                        double distance = loc.distance(center);

                        // Strefa shella: distance > radius - 1.0 && distance <= radius
                        // Bariera zaczyna się od radius - 1.3 (z marginesem dla hitboxa gracza)
                        double barrierStart = radius - 1.3;

                        if (distance >= barrierStart) {
                            // ✅ Gracz jest przy/w barierze — koryguj velocity

                            Vector velocity = player.getVelocity();
                            Vector fromCenter = loc.toVector().subtract(center.toVector());

                            if (fromCenter.lengthSquared() < 0.001) continue;

                            Vector outwardDir = fromCenter.clone().normalize();

                            // Oblicz komponent velocity w kierunku na zewnątrz
                            double outwardSpeed = velocity.dot(outwardDir);

                            if (outwardSpeed > 0) {
                                // ✅ Gracz leci na zewnątrz — anuluj tę część velocity
                                Vector corrected = velocity.clone().subtract(
                                        outwardDir.clone().multiply(outwardSpeed));
                                player.setVelocity(corrected);
                            }

                            // ✅ Sprawdź czy stopy/głowa są w bloku shella
                            boolean feetInShell = isInShellZone(
                                    loc.getBlock().getLocation(), center, radius);
                            boolean headInShell = isInShellZone(
                                    loc.clone().add(0, 1, 0).getBlock().getLocation(),
                                    center, radius);

                            if (feetInShell || headInShell) {
                                // ✅ Gracz JEST w bloku shella — wypchnij do wewnątrz
                                // NIE teleportuj na środek — tylko minimalnie cofnij

                                Vector pushInward = center.toVector().subtract(loc.toVector());
                                pushInward.normalize();

                                // Znajdź bezpieczną pozycję tuż wewnątrz bariery
                                Location safe = center.clone().add(
                                        fromCenter.normalize().multiply(barrierStart - 0.3));
                                safe.setY(loc.getY());
                                safe.setYaw(loc.getYaw());
                                safe.setPitch(loc.getPitch());

                                // ✅ Sprawdź czy safe location nie jest też w shellu
                                double safeDist = safe.distance(center);
                                if (safeDist >= barrierStart) {
                                    // Safe jest nadal za blisko — cofnij bardziej
                                    safe = center.clone().add(
                                            fromCenter.normalize().multiply(barrierStart - 1.0));
                                    safe.setY(loc.getY());
                                    safe.setYaw(loc.getYaw());
                                    safe.setPitch(loc.getPitch());
                                }

                                player.teleport(safe);

                                // Zeruj velocity
                                Vector newVel = player.getVelocity();
                                double out = newVel.dot(outwardDir);
                                if (out > 0) {
                                    newVel.subtract(outwardDir.clone().multiply(out));
                                    player.setVelocity(newVel);
                                }
                            }
                        }

                        // ✅ Gracz daleko poza klatką (exploit) — teleport na środek
                        // Ale TYLKO jeśli naprawdę daleko (>radius + 3 bloki)
                        if (distance > radius + 3.0) {
                            Location tp = center.clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // ✅ CO TICK
    }

    // ==================== SHELL ZONE CHECK ====================

    /**
     * ✅ Sprawdza czy dany blok jest w strefie shella (zbudowany, zaplanowany, lub powinien być).
     * Używa dystansu od centrum — shell jest na distance > radius - 1.0 && distance <= radius
     */
    private boolean isInShellZone(Location blockLoc, Location center, double radius) {
        // Dystans od centrum do środka bloku
        double distance = blockLoc.clone().add(0.5, 0.5, 0.5).distance(center);
        return distance > radius - 1.0 && distance <= radius;
    }

    // ==================== PLAYER MOVE EVENT ====================

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

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // Gracz na zablokowanym regionie — wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        double barrierStart = radius - 1.3;
        double distanceTo = to.distance(center);
        double distanceFrom = from.distance(center);

        // ✅ PRZYPADEK 1: Gracz próbuje wejść w strefę shella
        if (distanceTo >= barrierStart && distanceFrom < barrierStart) {
            // Gracz przekracza barierę — cofnij do FROM
            Location stuckLoc = from.clone();
            stuckLoc.setYaw(to.getYaw());
            stuckLoc.setPitch(to.getPitch());
            event.setTo(stuckLoc);

            playBarrierFeedback(player);
            return;
        }

        // ✅ PRZYPADEK 2: Gracz JUŻ jest w strefie shella (velocity go tam wrzuciło)
        if (distanceTo >= barrierStart) {
            // Cofnij w kierunku centrum — ale BEZ teleportu na sam środek
            Vector toCenter = center.toVector().subtract(to.toVector());
            if (toCenter.lengthSquared() > 0.01) {
                toCenter.normalize();
            }

            // Znajdź punkt tuż wewnątrz bariery
            Vector fromCenterDir = to.toVector().subtract(center.toVector());
            if (fromCenterDir.lengthSquared() > 0.01) {
                fromCenterDir.normalize();
            }

            Location safeLoc = center.clone().add(
                    fromCenterDir.multiply(barrierStart - 0.5));
            safeLoc.setY(to.getY());
            safeLoc.setYaw(to.getYaw());
            safeLoc.setPitch(to.getPitch());

            // ✅ Sprawdź czy safeLoc nie jest za barierą
            if (safeLoc.distance(center) >= barrierStart) {
                safeLoc = from.clone();
                safeLoc.setYaw(to.getYaw());
                safeLoc.setPitch(to.getPitch());
            }

            event.setTo(safeLoc);
            playBarrierFeedback(player);
            return;
        }

        // ✅ PRZYPADEK 3: Gracz zbliża się szybko (elytra) — predykcja
        if (distanceTo > barrierStart - 1.5 && distanceTo > distanceFrom) {
            Vector velocity = player.getVelocity();
            if (velocity.lengthSquared() > 0.3) {
                // Przewiduj pozycję za 3 ticki
                Location predicted = to.clone().add(velocity.clone().multiply(3));
                double predictedDist = predicted.distance(center);

                if (predictedDist >= barrierStart) {
                    // Za chwilę uderzy w barierę — zatrzymaj
                    Location stuckLoc = from.clone();
                    stuckLoc.setYaw(to.getYaw());
                    stuckLoc.setPitch(to.getPitch());
                    event.setTo(stuckLoc);
                    return;
                }
            }
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
        double barrierStart = radius - 1.3;

        // Teleport pluginowy wewnątrz klatki — pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < barrierStart) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // Blokuj teleport poza barierę
        if (to.distance(center) >= barrierStart) {
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
        if (velocityTask != null) {
            velocityTask.cancel();
            velocityTask = null;
        }
        lastSoundTime.clear();
    }
}
