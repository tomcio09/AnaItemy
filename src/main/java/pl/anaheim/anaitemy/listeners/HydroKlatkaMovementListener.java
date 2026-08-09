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

    private static final long SOUND_COOLDOWN_MS = 400;
    private final Map<UUID, Long> lastSoundTime = new ConcurrentHashMap<>();

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

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

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ GŁÓWNA LOGIKA: Sprawdź czy NOWA pozycja gracza (stopy + głowa)
        // koliduje z blokiem shella (istniejącym LUB zaplanowanym)

        // Pozycje do sprawdzenia: stopy gracza i głowa (2 bloki wysokości)
        Location feetTo = to.getBlock().getLocation();
        Location headTo = to.clone().add(0, 1, 0).getBlock().getLocation();

        boolean feetInShell = isShellOrPlannedShell(feetTo, klatka, manager);
        boolean headInShell = isShellOrPlannedShell(headTo, klatka, manager);

        if (feetInShell || headInShell) {
            // ✅ Gracz próbuje wejść w blok shella — COFNIJ

            // Sprawdź czy FROM też jest w shellu (zakleszczony)
            Location feetFrom = from.getBlock().getLocation();
            Location headFrom = from.clone().add(0, 1, 0).getBlock().getLocation();
            boolean feetFromInShell = isShellOrPlannedShell(feetFrom, klatka, manager);
            boolean headFromInShell = isShellOrPlannedShell(headFrom, klatka, manager);

            if (feetFromInShell || headFromInShell) {
                // Zakleszczony w shellu — teleport na środek
                Location teleportLoc = center.clone();
                teleportLoc.setYaw(to.getYaw());
                teleportLoc.setPitch(to.getPitch());
                event.setTo(teleportLoc);
                return;
            }

            // Normalnie — cofnij do FROM
            Location stuckLoc = from.clone();
            stuckLoc.setYaw(to.getYaw());
            stuckLoc.setPitch(to.getPitch());
            event.setTo(stuckLoc);

            cancelOutwardVelocity(player, center);
            playBarrierFeedback(player);
            return;
        }

        // ✅ DODATKOWE SPRAWDZENIE: Gracz daleko poza klatką (exploit/bug)
        double distanceTo = to.distance(center);

        if (distanceTo > radius + 1.0) {
            // Daleko poza klatką — teleport na środek
            Location teleportLoc = center.clone();
            teleportLoc.setYaw(to.getYaw());
            teleportLoc.setPitch(to.getPitch());
            event.setTo(teleportLoc);
            return;
        }

        // ✅ SPRAWDZENIE CIĄGŁE: Gracz jest poza shellem ale wewnątrz radius
        // (może być w strefie powłoki gdzie blok jeszcze nie został zbudowany)
        if (distanceTo > radius - 1.0) {
            // Gracz jest w strefie gdzie powinien być shell
            // Sprawdź czy bloki wokół gracza to shell/planned shell

            // Sprawdź 8 kierunków + góra/dół czy gracz nie jest otoczony shellem
            boolean surroundedByShell = checkSurroundingShell(to, klatka, manager);

            if (surroundedByShell) {
                // Gracz jest w strefie shella ale nie bezpośrednio w bloku
                // Cofnij do bezpiecznej pozycji bliżej centrum
                Vector toCenter = center.toVector().subtract(to.toVector());
                if (toCenter.lengthSquared() > 0.01) {
                    toCenter.normalize();
                }
                Location safeLoc = to.clone().add(toCenter.multiply(0.5));
                safeLoc.setYaw(to.getYaw());
                safeLoc.setPitch(to.getPitch());
                event.setTo(safeLoc);

                cancelOutwardVelocity(player, center);
                playBarrierFeedback(player);
                return;
            }
        }

        // ✅ PREDYKCJA: Gracz z dużą prędkością (elytra) — sprawdź następny tick
        Vector velocity = player.getVelocity();
        if (velocity.lengthSquared() > 0.5) {
            // Przewiduj pozycję za 2 ticki
            Location predicted = to.clone().add(velocity.clone().multiply(2));
            Location predFeet = predicted.getBlock().getLocation();
            Location predHead = predicted.clone().add(0, 1, 0).getBlock().getLocation();

            if (isShellOrPlannedShell(predFeet, klatka, manager)
                    || isShellOrPlannedShell(predHead, klatka, manager)) {
                // Zatrzymaj gracza zanim wleci w shell
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);

                cancelOutwardVelocity(player, center);
                return;
            }
        }
    }

    // ==================== SHELL CHECK ====================

    /**
     * ✅ Sprawdza czy dana lokalizacja bloku jest shellem (zbudowanym LUB zaplanowanym).
     * Działa zarówno podczas animacji jak i po niej.
     */
    private boolean isShellOrPlannedShell(Location blockLoc, ActiveHydroKlatka klatka,
                                          HydroKlatkaManager manager) {
        // 1. Sprawdź czy to już zbudowany shell
        if (manager.isShellBlock(blockLoc)) return true;

        // 2. Sprawdź czy to zaplanowana pozycja shella (podczas animacji)
        if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc)) return true;

        // 3. Nawet po animacji — sprawdź po dystansie czy pozycja powinna być shellem
        double distance = blockLoc.clone().add(0.5, 0.5, 0.5).distance(klatka.getCenter());
        double radius = klatka.getRadius();

        // Shell jest na: distance > radius - 1.0 && distance <= radius
        if (distance > radius - 1.0 && distance <= radius) {
            // Ta pozycja POWINNA być shellem
            // Mogła nie zostać zbudowana (blocked region, protected block)
            // Ale bariera powinna tam i tak być
            return true;
        }

        return false;
    }

    /**
     * ✅ Sprawdza czy gracz jest otoczony shellem/planned shellem.
     * Używane gdy gracz jest w strefie shella ale nie bezpośrednio w bloku.
     */
    private boolean checkSurroundingShell(Location playerLoc, ActiveHydroKlatka klatka,
                                           HydroKlatkaManager manager) {
        Location center = klatka.getCenter();
        double radius = klatka.getRadius();

        // Sprawdź dystans od centrum — jeśli w strefie shella
        double distance = playerLoc.distance(center);
        return distance > radius - 0.8;
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

        // Teleport pluginowy wewnątrz klatki — pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            double dist = to.distance(center);
            if (dist < radius - 1.0) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // Sprawdź czy cel teleportu jest w shellu
        Location feetTo = to.getBlock().getLocation();
        Location headTo = to.clone().add(0, 1, 0).getBlock().getLocation();

        if (isShellOrPlannedShell(feetTo, klatka, manager)
                || isShellOrPlannedShell(headTo, klatka, manager)) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        // Blokuj teleport poza klatką
        if (to.distance(center) > radius) {
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== VELOCITY CONTROL ====================

    private void cancelOutwardVelocity(Player player, Location center) {
        Vector velocity = player.getVelocity();
        if (velocity.lengthSquared() < 0.001) return;

        Vector outward = player.getLocation().toVector().subtract(center.toVector());
        outward.setY(0);

        if (outward.lengthSquared() < 0.001) return;
        outward.normalize();

        double outwardComponent = velocity.dot(outward);

        if (outwardComponent > 0) {
            Vector cancelVector = outward.clone().multiply(outwardComponent);
            velocity.subtract(cancelVector);
            player.setVelocity(velocity);
        }

        // ✅ Blokuj również spadanie przez dolną granicę shella
        // Sprawdź czy pod graczem jest shell
        Location belowFeet = player.getLocation().clone().subtract(0, 1, 0);
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);

        if (klatka != null && velocity.getY() < -0.1) {
            Location belowBlock = belowFeet.getBlock().getLocation();
            if (isShellOrPlannedShell(belowBlock, klatka, manager)) {
                // Pod graczem jest shell — zatrzymaj spadanie
                velocity.setY(0);
                player.setVelocity(velocity);
            }
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
}
