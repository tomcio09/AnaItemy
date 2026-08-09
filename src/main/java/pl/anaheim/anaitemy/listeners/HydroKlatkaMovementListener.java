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

    // ✅ Hitbox gracza
    private static final double PLAYER_WIDTH_HALF = 0.3;  // pół szerokości hitboxa
    private static final double PLAYER_HEIGHT = 1.8;       // wysokość gracza

    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== SPRAWDZANIE BLOKU SHELLA ====================

    /**
     * ✅ Sprawdza czy dany BLOK jest shellem (zbudowanym LUB zaplanowanym).
     * Shell to blok na granicy klatki: distance od centrum > radius-1.0 && <= radius
     */
    private boolean isShellBlock(int bx, int by, int bz,
                                  ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        Location blockLoc = new Location(klatka.getCenter().getWorld(), bx, by, bz);

        // 1. Już zbudowany shell
        if (manager.isShellBlock(blockLoc)) return true;

        // 2. Zaplanowany shell (podczas animacji)
        if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc)) return true;

        // 3. Fallback: pozycja POWINNA być shellem na podstawie dystansu
        double dist = blockLoc.clone().add(0.5, 0.5, 0.5).distance(klatka.getCenter());
        double radius = klatka.getRadius();
        return dist > radius - 1.0 && dist <= radius;
    }

    // ==================== CLAMP TASK — CO TICK ====================

    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    for (UUID playerId : klatka.getTrappedPlayers()) {
                        Player player = Bukkit.getPlayer(playerId);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(klatka.getCenter().getWorld())) continue;

                        // ✅ EXPLOIT: Gracz daleko poza klatką
                        double dist = loc.distance(klatka.getCenter());
                        if (dist > klatka.getRadius() + 5.0) {
                            Location tp = klatka.getCenter().clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                            player.setVelocity(new Vector(0, 0, 0));
                            continue;
                        }

                        // ✅ HARD CLAMP: Sprawdź kolizje z blokami shella
                        clampPlayerToShell(player, klatka, manager);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * ✅ GŁÓWNA LOGIKA: Sprawdza kolizję hitboxa gracza z blokami shella.
     * Jeśli koliduje → przesuwa gracza NA krawędź bloku (jak niewidzialny blok).
     *
     * Hitbox gracza: 0.6 x 1.8 x 0.6 (centered na X/Z, od stóp w górę na Y)
     * Sprawdzamy 3 warstwy Y (stopy, środek, głowa) i 4 roqi X/Z hitboxa.
     */
    private void clampPlayerToShell(Player player, ActiveHydroKlatka klatka,
                                     HydroKlatkaManager manager) {
        Location loc = player.getLocation();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        boolean clamped = false;
        double clampedX = px;
        double clampedY = py;
        double clampedZ = pz;

        // ✅ 1. SPRAWDŹ DÓŁ (spadanie) — czy pod stopami gracza jest shell
        {
            // Blok pod stopami (Y - 0.01 żeby złapać moment wejścia)
            int by = (int) Math.floor(py - 0.01);

            // Sprawdź wszystkie bloki pod hitboxem gracza (może stać na 4 blokach)
            for (int checkX = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                 checkX <= (int) Math.floor(px + PLAYER_WIDTH_HALF); checkX++) {
                for (int checkZ = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     checkZ <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); checkZ++) {

                    if (isShellBlock(checkX, by, checkZ, klatka, manager)) {
                        // ✅ Pod graczem jest shell → ustaw Y na górę tego bloku
                        clampedY = by + 1.0;
                        clamped = true;

                        // Zeruj velocity spadania
                        Vector vel = player.getVelocity();
                        if (vel.getY() < 0) {
                            vel.setY(0);
                            player.setVelocity(vel);
                        }
                    }
                }
            }
        }

        // ✅ 2. SPRAWDŹ GÓRĘ — czy nad głową gracza jest shell
        {
            int by = (int) Math.floor(clampedY + PLAYER_HEIGHT + 0.01);

            for (int checkX = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                 checkX <= (int) Math.floor(px + PLAYER_WIDTH_HALF); checkX++) {
                for (int checkZ = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     checkZ <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); checkZ++) {

                    if (isShellBlock(checkX, by, checkZ, klatka, manager)) {
                        // ✅ Nad graczem jest shell → obniż Y
                        clampedY = by - PLAYER_HEIGHT - 0.01;
                        clamped = true;

                        Vector vel = player.getVelocity();
                        if (vel.getY() > 0) {
                            vel.setY(0);
                            player.setVelocity(vel);
                        }
                    }
                }
            }
        }

        // ✅ 3. SPRAWDŹ BOKI X — czy obok gracza jest shell (kierunek X)
        {
            // Sprawdź na wysokości stóp i głowy
            for (int by = (int) Math.floor(clampedY);
                 by <= (int) Math.floor(clampedY + PLAYER_HEIGHT); by++) {
                for (int checkZ = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     checkZ <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); checkZ++) {

                    // Sprawdź kierunek +X
                    int bxPlus = (int) Math.floor(px + PLAYER_WIDTH_HALF + 0.01);
                    if (isShellBlock(bxPlus, by, checkZ, klatka, manager)) {
                        double maxX = bxPlus - PLAYER_WIDTH_HALF - 0.01;
                        if (clampedX > maxX) {
                            clampedX = maxX;
                            clamped = true;
                            clampVelocityAxis(player, 'x', true);
                        }
                    }

                    // Sprawdź kierunek -X
                    int bxMinus = (int) Math.floor(px - PLAYER_WIDTH_HALF - 0.01);
                    if (isShellBlock(bxMinus, by, checkZ, klatka, manager)) {
                        double minX = bxMinus + 1.0 + PLAYER_WIDTH_HALF + 0.01;
                        if (clampedX < minX) {
                            clampedX = minX;
                            clamped = true;
                            clampVelocityAxis(player, 'x', false);
                        }
                    }
                }
            }
        }

        // ✅ 4. SPRAWDŹ BOKI Z — czy obok gracza jest shell (kierunek Z)
        {
            for (int by = (int) Math.floor(clampedY);
                 by <= (int) Math.floor(clampedY + PLAYER_HEIGHT); by++) {
                for (int checkX = (int) Math.floor(clampedX - PLAYER_WIDTH_HALF);
                     checkX <= (int) Math.floor(clampedX + PLAYER_WIDTH_HALF); checkX++) {

                    // Sprawdź kierunek +Z
                    int bzPlus = (int) Math.floor(pz + PLAYER_WIDTH_HALF + 0.01);
                    if (isShellBlock(checkX, by, bzPlus, klatka, manager)) {
                        double maxZ = bzPlus - PLAYER_WIDTH_HALF - 0.01;
                        if (clampedZ > maxZ) {
                            clampedZ = maxZ;
                            clamped = true;
                            clampVelocityAxis(player, 'z', true);
                        }
                    }

                    // Sprawdź kierunek -Z
                    int bzMinus = (int) Math.floor(pz - PLAYER_WIDTH_HALF - 0.01);
                    if (isShellBlock(checkX, by, bzMinus, klatka, manager)) {
                        double minZ = bzMinus + 1.0 + PLAYER_WIDTH_HALF + 0.01;
                        if (clampedZ < minZ) {
                            clampedZ = minZ;
                            clamped = true;
                            clampVelocityAxis(player, 'z', false);
                        }
                    }
                }
            }
        }

        // ✅ Jeśli clampowaliśmy — teleportuj gracza
        if (clamped) {
            Location safeLoc = new Location(loc.getWorld(),
                    clampedX, clampedY, clampedZ,
                    loc.getYaw(), loc.getPitch());
            player.teleport(safeLoc);
            playBarrierFeedback(player);
        }
    }

    /**
     * ✅ Zeruje velocity na danej osi w danym kierunku.
     */
    private void clampVelocityAxis(Player player, char axis, boolean positive) {
        Vector vel = player.getVelocity();
        switch (axis) {
            case 'x' -> { if (positive && vel.getX() > 0) vel.setX(0);
                          else if (!positive && vel.getX() < 0) vel.setX(0); }
            case 'y' -> { if (positive && vel.getY() > 0) vel.setY(0);
                          else if (!positive && vel.getY() < 0) vel.setY(0); }
            case 'z' -> { if (positive && vel.getZ() > 0) vel.setZ(0);
                          else if (!positive && vel.getZ() < 0) vel.setZ(0); }
        }
        player.setVelocity(vel);
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

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ Sprawdź czy TO koliduje z blokami shella — jeśli tak, cofnij do FROM
        double toX = to.getX();
        double toY = to.getY();
        double toZ = to.getZ();

        boolean blocked = false;

        // Sprawdź stopy, kolana, głowę
        for (double checkY : new double[]{toY, toY + 0.9, toY + PLAYER_HEIGHT}) {
            int by = (int) Math.floor(checkY);

            for (int checkX = (int) Math.floor(toX - PLAYER_WIDTH_HALF);
                 checkX <= (int) Math.floor(toX + PLAYER_WIDTH_HALF); checkX++) {
                for (int checkZ = (int) Math.floor(toZ - PLAYER_WIDTH_HALF);
                     checkZ <= (int) Math.floor(toZ + PLAYER_WIDTH_HALF); checkZ++) {

                    if (isShellBlock(checkX, by, checkZ, klatka, manager)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) break;
            }
            if (blocked) break;
        }

        // ✅ Sprawdź też blok bezpośrednio pod stopami (spadanie)
        if (!blocked) {
            int belowY = (int) Math.floor(toY - 0.01);
            for (int checkX = (int) Math.floor(toX - PLAYER_WIDTH_HALF);
                 checkX <= (int) Math.floor(toX + PLAYER_WIDTH_HALF); checkX++) {
                for (int checkZ = (int) Math.floor(toZ - PLAYER_WIDTH_HALF);
                     checkZ <= (int) Math.floor(toZ + PLAYER_WIDTH_HALF); checkZ++) {

                    if (isShellBlock(checkX, belowY, checkZ, klatka, manager)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) break;
            }
        }

        if (blocked) {
            // ✅ Ruch zablokowany — cofnij
            // Sprawdź czy FROM jest bezpieczne
            boolean fromSafe = true;
            double fromX = from.getX();
            double fromY = from.getY();
            double fromZ = from.getZ();

            outerLoop:
            for (double checkY : new double[]{fromY, fromY + 0.9, fromY + PLAYER_HEIGHT}) {
                int by = (int) Math.floor(checkY);
                for (int checkXi = (int) Math.floor(fromX - PLAYER_WIDTH_HALF);
                     checkXi <= (int) Math.floor(fromX + PLAYER_WIDTH_HALF); checkXi++) {
                    for (int checkZi = (int) Math.floor(fromZ - PLAYER_WIDTH_HALF);
                         checkZi <= (int) Math.floor(fromZ + PLAYER_WIDTH_HALF); checkZi++) {
                        if (isShellBlock(checkXi, by, checkZi, klatka, manager)) {
                            fromSafe = false;
                            break outerLoop;
                        }
                    }
                }
            }

            if (fromSafe) {
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
            } else {
                // FROM też nie jest bezpieczne — clampTask zajmie się pozycją
                // Nie ruszaj event.setTo żeby uniknąć teleportu na środek
                event.setTo(from.clone().setDirection(to.getDirection()));
            }

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

        // Teleport pluginowy wewnątrz klatki — pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < radius - 1.5) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // Sprawdź czy cel teleportu koliduje z shellem
        boolean blocked = false;
        double toX = to.getX();
        double toY = to.getY();
        double toZ = to.getZ();

        for (double checkY : new double[]{toY, toY + PLAYER_HEIGHT}) {
            int by = (int) Math.floor(checkY);
            for (int cx = (int) Math.floor(toX - PLAYER_WIDTH_HALF);
                 cx <= (int) Math.floor(toX + PLAYER_WIDTH_HALF); cx++) {
                for (int cz = (int) Math.floor(toZ - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(toZ + PLAYER_WIDTH_HALF); cz++) {
                    if (isShellBlock(cx, by, cz, klatka, manager)) {
                        blocked = true;
                        break;
                    }
                }
                if (blocked) break;
            }
            if (blocked) break;
        }

        if (blocked || to.distance(center) > radius) {
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

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastSoundTime.clear();
    }
}
