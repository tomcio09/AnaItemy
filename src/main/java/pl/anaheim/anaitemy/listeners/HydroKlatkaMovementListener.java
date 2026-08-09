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

    private static final double PLAYER_WIDTH_HALF = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double SHELL_BARRIER_DEPTH = 0.5;

    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== SPRAWDZANIE SHELLA ====================

    private boolean isShell(int bx, int by, int bz,
                            ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        Location blockLoc = new Location(klatka.getCenter().getWorld(), bx, by, bz);
        if (manager.isShellBlock(blockLoc)) return true;
        if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc)) return true;
        return false;
    }

    // ==================== CLAMP TASK — CO TICK ====================

    private void startClampTask() {
        clampTask = new BukkitRunnable() {
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

                        // EXPLOIT
                        if (loc.distance(center) > radius + 5.0) {
                            Location tp = center.clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                            player.setVelocity(new Vector(0, 0, 0));
                            continue;
                        }

                        handleCollisions(player, klatka, manager);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * ✅ Sprawdza kolizje hitboxa gracza z blokami shella.
     * Bariera jest w POŁOWIE bloku shella (0.5 od wewnętrznej krawędzi).
     * Jeśli gracz przekracza barierę → wymusza pozycję NA barierze + zeruje velocity na tej osi.
     * 
     * WAŻNE: Nie tylko raz cofnij — TRZYMAJ gracza na pozycji bariery ciągle.
     * Zeruj velocity PRZED i PO teleporcie żeby grawitacja nie wciągnęła go z powrotem.
     */
    private void handleCollisions(Player player, ActiveHydroKlatka klatka,
                                   HydroKlatkaManager manager) {
        Location loc = player.getLocation();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        boolean didClamp = false;
        double newX = px;
        double newY = py;
        double newZ = pz;

        // ✅ Zbieraj informacje o clampowaniu velocity
        boolean clampVelXPos = false, clampVelXNeg = false;
        boolean clampVelYPos = false, clampVelYNeg = false;
        boolean clampVelZPos = false, clampVelZNeg = false;

        // ==================== DÓŁ ====================
        // Sprawdź bloki pod stopami gracza (Y - 1 oraz Y jeśli stopy wchodzą w shell)
        {
            for (int yCheck = (int) Math.floor(py) - 1; yCheck <= (int) Math.floor(py); yCheck++) {
                boolean shellFound = false;

                for (int cx = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                     cx <= (int) Math.floor(px + PLAYER_WIDTH_HALF) && !shellFound; cx++) {
                    for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                         cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF) && !shellFound; cz++) {
                        if (isShell(cx, yCheck, cz, klatka, manager)) {
                            shellFound = true;
                        }
                    }
                }

                if (shellFound) {
                    // Bariera Y = górna krawędź bloku minus SHELL_BARRIER_DEPTH
                    // Shell blok od yCheck do yCheck+1
                    // Bariera na yCheck + 1.0 - SHELL_BARRIER_DEPTH = yCheck + 0.5
                    double barrierY = yCheck + 1.0 - SHELL_BARRIER_DEPTH;

                    // Gracz stoi poniżej bariery?
                    if (newY < barrierY) {
                        newY = barrierY;
                        didClamp = true;
                        clampVelYNeg = true;
                    }
                }
            }
        }

        // ==================== GÓRA ====================
        {
            int headBlockY = (int) Math.floor(newY + PLAYER_HEIGHT);
            boolean shellAbove = false;

            for (int cx = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                 cx <= (int) Math.floor(px + PLAYER_WIDTH_HALF) && !shellAbove; cx++) {
                for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF) && !shellAbove; cz++) {
                    if (isShell(cx, headBlockY, cz, klatka, manager)) {
                        shellAbove = true;
                    }
                }
            }

            if (shellAbove) {
                // Bariera = dolna krawędź bloku + SHELL_BARRIER_DEPTH
                double barrierY = headBlockY + SHELL_BARRIER_DEPTH;
                double headY = newY + PLAYER_HEIGHT;

                if (headY > barrierY) {
                    newY = barrierY - PLAYER_HEIGHT;
                    didClamp = true;
                    clampVelYPos = true;
                }
            }
        }

        // ==================== BOKI X ====================
        {
            for (int by = (int) Math.floor(newY);
                 by <= (int) Math.floor(newY + PLAYER_HEIGHT); by++) {
                for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); cz++) {

                    // +X
                    int bxRight = (int) Math.floor(newX + PLAYER_WIDTH_HALF);
                    if (isShell(bxRight, by, cz, klatka, manager)) {
                        // Bariera = lewa krawędź bloku + SHELL_BARRIER_DEPTH
                        double barrierX = bxRight + SHELL_BARRIER_DEPTH;
                        double playerRight = newX + PLAYER_WIDTH_HALF;

                        if (playerRight > barrierX) {
                            newX = barrierX - PLAYER_WIDTH_HALF;
                            didClamp = true;
                            clampVelXPos = true;
                        }
                    }

                    // -X
                    int bxLeft = (int) Math.floor(newX - PLAYER_WIDTH_HALF);
                    if (isShell(bxLeft, by, cz, klatka, manager)) {
                        // Bariera = prawa krawędź bloku - SHELL_BARRIER_DEPTH
                        double barrierX = bxLeft + 1.0 - SHELL_BARRIER_DEPTH;
                        double playerLeft = newX - PLAYER_WIDTH_HALF;

                        if (playerLeft < barrierX) {
                            newX = barrierX + PLAYER_WIDTH_HALF;
                            didClamp = true;
                            clampVelXNeg = true;
                        }
                    }
                }
            }
        }

        // ==================== BOKI Z ====================
        {
            for (int by = (int) Math.floor(newY);
                 by <= (int) Math.floor(newY + PLAYER_HEIGHT); by++) {
                for (int cx = (int) Math.floor(newX - PLAYER_WIDTH_HALF);
                     cx <= (int) Math.floor(newX + PLAYER_WIDTH_HALF); cx++) {

                    // +Z
                    int bzFront = (int) Math.floor(newZ + PLAYER_WIDTH_HALF);
                    if (isShell(cx, by, bzFront, klatka, manager)) {
                        double barrierZ = bzFront + SHELL_BARRIER_DEPTH;
                        double playerFront = newZ + PLAYER_WIDTH_HALF;

                        if (playerFront > barrierZ) {
                            newZ = barrierZ - PLAYER_WIDTH_HALF;
                            didClamp = true;
                            clampVelZPos = true;
                        }
                    }

                    // -Z
                    int bzBack = (int) Math.floor(newZ - PLAYER_WIDTH_HALF);
                    if (isShell(cx, by, bzBack, klatka, manager)) {
                        double barrierZ = bzBack + 1.0 - SHELL_BARRIER_DEPTH;
                        double playerBack = newZ - PLAYER_WIDTH_HALF;

                        if (playerBack < barrierZ) {
                            newZ = barrierZ + PLAYER_WIDTH_HALF;
                            didClamp = true;
                            clampVelZNeg = true;
                        }
                    }
                }
            }
        }

        // ✅ Jeśli clampowaliśmy — NAJPIERW zeruj velocity, POTEM teleportuj
        if (didClamp) {
            // ✅ KROK 1: Zeruj velocity PRZED teleportem
            Vector vel = player.getVelocity();
            if (clampVelXPos && vel.getX() > 0) vel.setX(0);
            if (clampVelXNeg && vel.getX() < 0) vel.setX(0);
            if (clampVelYPos && vel.getY() > 0) vel.setY(0);
            if (clampVelYNeg && vel.getY() < 0) vel.setY(0);
            if (clampVelZPos && vel.getZ() > 0) vel.setZ(0);
            if (clampVelZNeg && vel.getZ() < 0) vel.setZ(0);
            player.setVelocity(vel);

            // ✅ KROK 2: Teleportuj na bezpieczną pozycję
            Location safeLoc = new Location(loc.getWorld(),
                    newX, newY, newZ, loc.getYaw(), loc.getPitch());
            player.teleport(safeLoc);

            // ✅ KROK 3: Zeruj velocity PO teleporcie (grawitacja może dodać velocity między krokami)
            vel = player.getVelocity();
            if (clampVelXPos && vel.getX() > 0) vel.setX(0);
            if (clampVelXNeg && vel.getX() < 0) vel.setX(0);
            if (clampVelYPos && vel.getY() > 0) vel.setY(0);
            if (clampVelYNeg && vel.getY() < 0) vel.setY(0);
            if (clampVelZPos && vel.getZ() > 0) vel.setZ(0);
            if (clampVelZNeg && vel.getZ() < 0) vel.setZ(0);
            player.setVelocity(vel);

            // ✅ KROK 4: Jeśli trzymamy gracza na dolnej barierze — wyłącz grawitację tymczasowo
            // żeby serwer nie dodawał velocity spadania co tick
            if (clampVelYNeg) {
                // Gracz stoi na barierze — ustaw velocity Y dokładnie na 0
                // i ustaw gracza jako "na ziemi" jeśli pod nim jest solidny shell
                vel = player.getVelocity();
                vel.setY(-0.0784); // ✅ Vanilla "stanie na ziemi" velocity
                player.setVelocity(vel);
            }

            playBarrierFeedback(player);
        }
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

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ Sprawdź czy TO penetruje barierę
        if (wouldPenetrateShell(to, klatka, manager)) {
            if (!wouldPenetrateShell(from, klatka, manager)) {
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
            }
            playBarrierFeedback(player);
        }
    }

    private boolean wouldPenetrateShell(Location loc, ActiveHydroKlatka klatka,
                                         HydroKlatkaManager manager) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        int minBX = (int) Math.floor(px - PLAYER_WIDTH_HALF);
        int maxBX = (int) Math.floor(px + PLAYER_WIDTH_HALF);
        int minBY = (int) Math.floor(py) - 1;
        int maxBY = (int) Math.floor(py + PLAYER_HEIGHT);
        int minBZ = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
        int maxBZ = (int) Math.floor(pz + PLAYER_WIDTH_HALF);

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    if (!isShell(bx, by, bz, klatka, manager)) continue;

                    // Sprawdź czy hitbox gracza wchodzi głębiej niż SHELL_BARRIER_DEPTH

                    // X
                    double playerRight = px + PLAYER_WIDTH_HALF;
                    double playerLeft = px - PLAYER_WIDTH_HALF;
                    if (playerRight > bx && playerRight < bx + 1.0) {
                        if (playerRight - bx > SHELL_BARRIER_DEPTH) return true;
                    }
                    if (playerLeft > bx && playerLeft < bx + 1.0) {
                        if ((bx + 1.0) - playerLeft > SHELL_BARRIER_DEPTH) return true;
                    }

                    // Y (stopy)
                    if (by == (int) Math.floor(py) - 1 || by == (int) Math.floor(py)) {
                        // Blok pod stopami — stopy wchodzą od góry
                        if (py > by && py < by + 1.0) {
                            double fromTop = (by + 1.0) - py;
                            if (fromTop > SHELL_BARRIER_DEPTH) return true;
                        }
                    }

                    // Y (głowa)
                    double headY = py + PLAYER_HEIGHT;
                    if (headY > by && headY < by + 1.0) {
                        if (headY - by > SHELL_BARRIER_DEPTH) return true;
                    }

                    // Z
                    double playerFront = pz + PLAYER_WIDTH_HALF;
                    double playerBack = pz - PLAYER_WIDTH_HALF;
                    if (playerFront > bz && playerFront < bz + 1.0) {
                        if (playerFront - bz > SHELL_BARRIER_DEPTH) return true;
                    }
                    if (playerBack > bz && playerBack < bz + 1.0) {
                        if ((bz + 1.0) - playerBack > SHELL_BARRIER_DEPTH) return true;
                    }
                }
            }
        }

        return false;
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

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < radius - 1.5) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        if (wouldPenetrateShell(to, klatka, manager) || to.distance(center) > radius) {
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
