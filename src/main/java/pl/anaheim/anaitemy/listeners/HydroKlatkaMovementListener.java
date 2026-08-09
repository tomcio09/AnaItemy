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
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    // ✅ Anti-spam: minimalny czas między dźwiękami (ms)
    private static final long SOUND_COOLDOWN_MS = 400;
    private final Map<UUID, Long> lastSoundTime = new ConcurrentHashMap<>();

    // ✅ Bariera jest w środku bloku shella
    // Shell jest na pozycjach gdzie distance > radius - 1.0 && distance <= radius
    // Więc bariera zaczyna się od radius - 1.0 (wewnętrzna krawędź shella)
    // Dodajemy 0.3 marginesu żeby gracz nie "wchodził" w blok
    private static final double BARRIER_MARGIN = 0.3;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== GŁÓWNA LOGIKA RUCHU ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // ✅ Ignoruj obroty głowy
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // ✅ Jeśli gracz jest na zablokowanym regionie - wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ OBLICZ PROMIEŃ BARIERY
        // Shell budowany jest na: distance > radius - 1.0 && distance <= radius
        // Bariera = wewnętrzna krawędź shella - margines
        double barrierRadius = radius - 1.0 - BARRIER_MARGIN;

        double distanceFrom = from.distance(center);
        double distanceTo = to.distance(center);

        // ==========================================
        // PRZYPADEK 1: Gracz jest WEWNĄTRZ bariery i próbuje wyjść
        // ==========================================
        if (distanceFrom < barrierRadius && distanceTo >= barrierRadius) {
            // ✅ Gracz przekracza barierę - ZATRZYMAJ na granicy

            // Oblicz punkt na granicy bariery (kierunek od centrum do gracza)
            Vector direction = to.toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.001) {
                direction = from.toVector().subtract(center.toVector());
            }
            if (direction.lengthSquared() < 0.001) {
                direction = new Vector(1, 0, 0);
            }
            direction.normalize();

            // Ustaw gracza na granicy bariery (cofnij do bezpiecznej pozycji)
            Location safeLocation = center.clone().add(
                    direction.multiply(barrierRadius - 0.1));
            safeLocation.setY(from.getY()); // zachowaj Y z from (nie przesuwaj w pionie)
            safeLocation.setYaw(to.getYaw());
            safeLocation.setPitch(to.getPitch());

            event.setTo(safeLocation);

            // ✅ Zeruj velocity w kierunku na zewnątrz
            cancelOutwardVelocity(player, center);

            // ✅ Dźwięk + subtitle z anti-spam
            playBarrierFeedback(player);
            return;
        }

        // ==========================================
        // PRZYPADEK 2: Gracz JUŻ jest za barierą (exploit/bug/animacja)
        // ==========================================
        if (distanceTo >= barrierRadius) {
            // Gracz jest poza barierą

            if (distanceTo > radius + 1.0) {
                // ✅ Daleko poza klatką - teleport na środek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // ✅ Gracz próbuje się ruszać za barierą - cofnij do wewnątrz
            // Oblicz kierunek od gracza do centrum
            Vector toCenter = center.toVector().subtract(to.toVector());
            if (toCenter.lengthSquared() < 0.001) {
                toCenter = new Vector(1, 0, 0);
            }
            toCenter.normalize();

            // Przesuń gracza do wnętrza bariery
            Location pushBack = center.clone().add(
                    to.toVector().subtract(center.toVector()).normalize()
                            .multiply(barrierRadius - 0.3));
            pushBack.setY(to.getY());
            pushBack.setYaw(to.getYaw());
            pushBack.setPitch(to.getPitch());

            event.setTo(pushBack);

            cancelOutwardVelocity(player, center);
            playBarrierFeedback(player);
            return;
        }

        // ==========================================
        // PRZYPADEK 3: Gracz jest blisko bariery i zbliża się do niej
        // ==========================================
        if (distanceTo > barrierRadius - 0.5 && distanceTo > distanceFrom) {
            // Gracz zbliża się do bariery - sprawdź czy następny tick by go przeniosło za barierę
            // Predykcja: velocity * 2 ticki
            Vector velocity = player.getVelocity();
            double predictedDistance = to.clone().add(velocity).distance(center);

            if (predictedDistance >= barrierRadius) {
                // ✅ Elytra/sprint - zatrzymaj na granicy
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);

                cancelOutwardVelocity(player, center);
                return;
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
        double barrierRadius = radius - 1.0 - BARRIER_MARGIN;

        // ✅ Teleport pluginowy wewnątrz bariery - pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < barrierRadius) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // ✅ Teleport na zablokowany region - wypuść
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ Blokuj teleport poza barierę
        if (to.distance(center) >= barrierRadius) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== VELOCITY CONTROL ====================

    /**
     * ✅ Anuluj velocity gracza w kierunku na zewnątrz klatki.
     * Zachowaj velocity wzdłuż ściany (ruch obrotowy wokół centrum).
     */
    private void cancelOutwardVelocity(Player player, Location center) {
        Vector velocity = player.getVelocity();
        if (velocity.lengthSquared() < 0.001) return;

        // Kierunek od centrum do gracza (na zewnątrz)
        Vector outward = player.getLocation().toVector().subtract(center.toVector());
        outward.setY(0);

        if (outward.lengthSquared() < 0.001) return;
        outward.normalize();

        // Oblicz komponent velocity w kierunku na zewnątrz
        double outwardComponent = velocity.dot(outward);

        if (outwardComponent > 0) {
            // ✅ Usuń komponent na zewnątrz, zachowaj ruch wzdłuż ściany + Y
            Vector cancelVector = outward.clone().multiply(outwardComponent);
            velocity.subtract(cancelVector);

            // ✅ Zachowaj spadanie (Y velocity)
            if (velocity.getY() > 0.5) velocity.setY(0); // blokuj wyskakiwanie
            player.setVelocity(velocity);
        }
    }

    // ==================== DŹWIĘK I SUBTITLE ====================

    private void playBarrierFeedback(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastSound = lastSoundTime.get(uuid);
        if (lastSound != null && now - lastSound < SOUND_COOLDOWN_MS) {
            return; // anti-spam
        }

        lastSoundTime.put(uuid, now);

        // ✅ Dźwięk szkła
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.5f, 1.2f);

        // ✅ Subtitle
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
}
