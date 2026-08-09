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

    /**
     * ✅ Bariera jest w POŁOWIE bloku shella.
     * Blok shella to 1x1x1. Bariera jest 0.5 bloku od wewnętrznej krawędzi.
     * Gracz może wejść w zewnętrzną połowę bloku (bliżej centrum klatki),
     * ale nie może przejść przez środek bloku (połowa = bariera).
     */
    private static final double SHELL_BARRIER_DEPTH = 0.5;

    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== SPRAWDZANIE SHELLA ====================

    /**
     * ✅ Sprawdza czy blok na (bx, by, bz) jest SHELLEM.
     * TYLKO zbudowany shell lub zaplanowany shell — BEZ fallbacku dystansowego.
     */
    private boolean isShell(int bx, int by, int bz,
                            ActiveHydroKlatka klatka, HydroKlatkaManager manager) {
        Location blockLoc = new Location(klatka.getCenter().getWorld(), bx, by, bz);

        // Zbudowany shell
        if (manager.isShellBlock(blockLoc)) return true;

        // Zaplanowany shell (podczas animacji)
        if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(blockLoc)) return true;

        return false;
    }

    /**
     * ✅ Oblicza jak głęboko gracz wchodzi w blok shella od WEWNĘTRZNEJ strony.
     * Zwraca wartość 0.0-1.0 gdzie 0.0 = krawędź bloku, 1.0 = druga strona.
     * Bariera jest na SHELL_BARRIER_DEPTH (0.5) — gracz może wejść do 0.5, ale nie dalej.
     *
     * @param playerEdge pozycja krawędzi hitboxa gracza na danej osi
     * @param blockCoord koordynat bloku (int)
     * @param fromInside true jeśli gracz wchodzi od strony centrum klatki
     * @return penetracja (ile gracz wchodzi w blok), lub -1 jeśli nie koliduje
     */
    private double getPenetration(double playerEdge, int blockCoord, boolean fromInside) {
        if (fromInside) {
            // Gracz wchodzi od wewnętrznej strony (od centrum)
            // Blok jest od blockCoord do blockCoord+1
            // Gracz wchodzi od strony bliższej centrum
            double penetration = playerEdge - blockCoord;
            if (penetration > 0 && penetration < 1.0) {
                return penetration;
            }
        } else {
            // Gracz wchodzi od zewnętrznej strony
            double penetration = (blockCoord + 1.0) - playerEdge;
            if (penetration > 0 && penetration < 1.0) {
                return penetration;
            }
        }
        return -1;
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

                        // EXPLOIT check
                        if (loc.distance(center) > radius + 5.0) {
                            Location tp = center.clone();
                            tp.setYaw(loc.getYaw());
                            tp.setPitch(loc.getPitch());
                            player.teleport(tp);
                            player.setVelocity(new Vector(0, 0, 0));
                            continue;
                        }

                        // ✅ HARD CLAMP per-blok
                        handleCollisions(player, klatka, manager);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * ✅ Sprawdza kolizje hitboxa gracza z blokami shella.
     * Bariera jest w POŁOWIE bloku — gracz może wejść do 0.5 bloku od wewnętrznej strony,
     * ale jeśli przekroczy połowę → zostaje cofnięty NA pozycję bariery.
     */
    private void handleCollisions(Player player, ActiveHydroKlatka klatka,
                                   HydroKlatkaManager manager) {
        Location loc = player.getLocation();
        Location center = klatka.getCenter();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        boolean didClamp = false;
        double newX = px;
        double newY = py;
        double newZ = pz;

        // ✅ 1. DÓŁ — spadanie na shell
        {
            int belowY = (int) Math.floor(py) - 1;
            // Sprawdź pod każdym rogiem hitboxa
            boolean shellBelow = false;
            for (int cx = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                 cx <= (int) Math.floor(px + PLAYER_WIDTH_HALF); cx++) {
                for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); cz++) {
                    if (isShell(cx, belowY, cz, klatka, manager)) {
                        shellBelow = true;
                        break;
                    }
                }
                if (shellBelow) break;
            }

            if (shellBelow) {
                // Bariera jest w połowie bloku shella
                double barrierY = belowY + 1.0 - SHELL_BARRIER_DEPTH;
                // barrierY = górna krawędź dolnej połowy bloku

                // Gracz stoi PONIŻEJ bariery?
                if (py < belowY + 1.0 - SHELL_BARRIER_DEPTH + 0.5) {
                    // Gracz jest w dolnej połowie — ale bariera jest na 0.5
                    // Jeśli stopy gracza < belowY + 0.5 (połowa bloku) → cofnij na górę połowy
                    if (py < belowY + SHELL_BARRIER_DEPTH) {
                        // Gracz spadł poniżej bariery — nie powinien tu być
                        // Ale może to normalne chodzenie po bloku wewnętrznym
                    }
                }

                // Prostsze: jeśli stopy gracza wchodzą w blok shella poniżej
                // i gracz spada (velY < 0), zatrzymaj na górze bloku
                double feetInBlock = (belowY + 1.0) - py;
                if (feetInBlock > SHELL_BARRIER_DEPTH && player.getVelocity().getY() <= 0) {
                    newY = belowY + 1.0 - SHELL_BARRIER_DEPTH + 0.001;
                    didClamp = true;
                    clampVel(player, 'y', false);
                }
            }

            // ✅ Sprawdź też blok na którym gracz stoi (Y pozycja gracza)
            int feetBlockY = (int) Math.floor(py);
            boolean shellAtFeet = false;
            for (int cx = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                 cx <= (int) Math.floor(px + PLAYER_WIDTH_HALF); cx++) {
                for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); cz++) {
                    if (isShell(cx, feetBlockY, cz, klatka, manager)) {
                        shellAtFeet = true;
                        break;
                    }
                }
                if (shellAtFeet) break;
            }

            if (shellAtFeet) {
                // Stopy gracza SĄ w bloku shella
                // Oblicz ile gracz wchodzi od wewnętrznej strony (od centrum)
                double distFromCenter = loc.distance(center);
                double centerDist = new Location(center.getWorld(),
                        px, center.getY(), pz).distance(center);

                // Jeśli gracz jest bliżej centrum niż blok — wchodzi od wewnątrz
                // Bariera w połowie bloku: gracz nie może przekroczyć środka bloku
                double penetrationInBlock = py - feetBlockY;

                // Gracz powinien stać NA bloku, nie W nim
                if (penetrationInBlock < SHELL_BARRIER_DEPTH) {
                    newY = feetBlockY + SHELL_BARRIER_DEPTH + 0.001;
                    didClamp = true;
                    clampVel(player, 'y', false);
                }
            }
        }

        // ✅ 2. GÓRA — lot w górę do shella
        {
            int headBlockY = (int) Math.floor(newY + PLAYER_HEIGHT);
            boolean shellAbove = false;
            for (int cx = (int) Math.floor(px - PLAYER_WIDTH_HALF);
                 cx <= (int) Math.floor(px + PLAYER_WIDTH_HALF); cx++) {
                for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); cz++) {
                    if (isShell(cx, headBlockY, cz, klatka, manager)) {
                        shellAbove = true;
                        break;
                    }
                }
                if (shellAbove) break;
            }

            if (shellAbove) {
                // Głowa gracza wchodzi w blok shella od dołu
                double headInBlock = (newY + PLAYER_HEIGHT) - headBlockY;
                if (headInBlock > SHELL_BARRIER_DEPTH) {
                    newY = headBlockY + SHELL_BARRIER_DEPTH - PLAYER_HEIGHT - 0.001;
                    didClamp = true;
                    clampVel(player, 'y', true);
                }
            }
        }

        // ✅ 3. BOKI X
        {
            for (int by = (int) Math.floor(newY);
                 by <= (int) Math.floor(newY + PLAYER_HEIGHT); by++) {
                for (int cz = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
                     cz <= (int) Math.floor(pz + PLAYER_WIDTH_HALF); cz++) {

                    // +X: gracz idzie w prawo, shell jest na prawo
                    int bxRight = (int) Math.floor(newX + PLAYER_WIDTH_HALF);
                    if (isShell(bxRight, by, cz, klatka, manager)) {
                        double edgeInBlock = (newX + PLAYER_WIDTH_HALF) - bxRight;
                        if (edgeInBlock > SHELL_BARRIER_DEPTH) {
                            newX = bxRight + SHELL_BARRIER_DEPTH - PLAYER_WIDTH_HALF - 0.001;
                            didClamp = true;
                            clampVel(player, 'x', true);
                        }
                    }

                    // -X: gracz idzie w lewo, shell jest na lewo
                    int bxLeft = (int) Math.floor(newX - PLAYER_WIDTH_HALF);
                    if (isShell(bxLeft, by, cz, klatka, manager)) {
                        double edgeInBlock = (bxLeft + 1.0) - (newX - PLAYER_WIDTH_HALF);
                        if (edgeInBlock > SHELL_BARRIER_DEPTH) {
                            newX = bxLeft + 1.0 - SHELL_BARRIER_DEPTH + PLAYER_WIDTH_HALF + 0.001;
                            didClamp = true;
                            clampVel(player, 'x', false);
                        }
                    }
                }
            }
        }

        // ✅ 4. BOKI Z
        {
            for (int by = (int) Math.floor(newY);
                 by <= (int) Math.floor(newY + PLAYER_HEIGHT); by++) {
                for (int cx = (int) Math.floor(newX - PLAYER_WIDTH_HALF);
                     cx <= (int) Math.floor(newX + PLAYER_WIDTH_HALF); cx++) {

                    // +Z
                    int bzFront = (int) Math.floor(newZ + PLAYER_WIDTH_HALF);
                    if (isShell(cx, by, bzFront, klatka, manager)) {
                        double edgeInBlock = (newZ + PLAYER_WIDTH_HALF) - bzFront;
                        if (edgeInBlock > SHELL_BARRIER_DEPTH) {
                            newZ = bzFront + SHELL_BARRIER_DEPTH - PLAYER_WIDTH_HALF - 0.001;
                            didClamp = true;
                            clampVel(player, 'z', true);
                        }
                    }

                    // -Z
                    int bzBack = (int) Math.floor(newZ - PLAYER_WIDTH_HALF);
                    if (isShell(cx, by, bzBack, klatka, manager)) {
                        double edgeInBlock = (bzBack + 1.0) - (newZ - PLAYER_WIDTH_HALF);
                        if (edgeInBlock > SHELL_BARRIER_DEPTH) {
                            newZ = bzBack + 1.0 - SHELL_BARRIER_DEPTH + PLAYER_WIDTH_HALF + 0.001;
                            didClamp = true;
                            clampVel(player, 'z', false);
                        }
                    }
                }
            }
        }

        // ✅ Teleportuj tylko jeśli faktycznie clampowaliśmy
        if (didClamp) {
            Location safeLoc = new Location(loc.getWorld(),
                    newX, newY, newZ, loc.getYaw(), loc.getPitch());
            player.teleport(safeLoc);
            playBarrierFeedback(player);
        }
    }

    private void clampVel(Player player, char axis, boolean positive) {
        Vector vel = player.getVelocity();
        switch (axis) {
            case 'x' -> {
                if (positive && vel.getX() > 0) vel.setX(0);
                else if (!positive && vel.getX() < 0) vel.setX(0);
            }
            case 'y' -> {
                if (positive && vel.getY() > 0) vel.setY(0);
                else if (!positive && vel.getY() < 0) vel.setY(0);
            }
            case 'z' -> {
                if (positive && vel.getZ() > 0) vel.setZ(0);
                else if (!positive && vel.getZ() < 0) vel.setZ(0);
            }
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

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
            return;
        }

        // ✅ Sprawdź czy TO wchodzi za głęboko w jakikolwiek blok shella
        if (wouldPenetrateShell(to, klatka, manager)) {
            // Cofnij do FROM jeśli FROM jest bezpieczne
            if (!wouldPenetrateShell(from, klatka, manager)) {
                Location stuckLoc = from.clone();
                stuckLoc.setYaw(to.getYaw());
                stuckLoc.setPitch(to.getPitch());
                event.setTo(stuckLoc);
            }
            // Jeśli FROM też penetruje — clampTask zajmie się tym
            playBarrierFeedback(player);
        }
    }

    /**
     * ✅ Sprawdza czy pozycja gracza penetruje barierę w jakimkolwiek bloku shella.
     * Bariera = połowa bloku (SHELL_BARRIER_DEPTH = 0.5).
     * Gracz MOŻE wchodzić w pierwszą połowę bloku shella (od strony wnętrza).
     * NIE MOŻE przekroczyć połowy.
     */
    private boolean wouldPenetrateShell(Location loc, ActiveHydroKlatka klatka,
                                         HydroKlatkaManager manager) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        // Sprawdź wszystkie bloki z którymi koliduje hitbox gracza
        int minBX = (int) Math.floor(px - PLAYER_WIDTH_HALF);
        int maxBX = (int) Math.floor(px + PLAYER_WIDTH_HALF);
        int minBY = (int) Math.floor(py) - 1; // -1 dla bloku pod stopami
        int maxBY = (int) Math.floor(py + PLAYER_HEIGHT);
        int minBZ = (int) Math.floor(pz - PLAYER_WIDTH_HALF);
        int maxBZ = (int) Math.floor(pz + PLAYER_WIDTH_HALF);

        for (int bx = minBX; bx <= maxBX; bx++) {
            for (int by = minBY; by <= maxBY; by++) {
                for (int bz = minBZ; bz <= maxBZ; bz++) {
                    if (!isShell(bx, by, bz, klatka, manager)) continue;

                    // Ten blok jest shellem — sprawdź penetrację na każdej osi

                    // X: sprawdź od której strony gracz wchodzi
                    double rightEdge = px + PLAYER_WIDTH_HALF;
                    double leftEdge = px - PLAYER_WIDTH_HALF;

                    if (rightEdge > bx && rightEdge < bx + 1.0) {
                        double pen = rightEdge - bx;
                        if (pen > SHELL_BARRIER_DEPTH) return true;
                    }
                    if (leftEdge > bx && leftEdge < bx + 1.0) {
                        double pen = (bx + 1.0) - leftEdge;
                        if (pen > SHELL_BARRIER_DEPTH) return true;
                    }

                    // Y: stopy i głowa
                    if (py > by && py < by + 1.0) {
                        double pen = py - by;
                        if (pen < 1.0 - SHELL_BARRIER_DEPTH) return true; // wchodzi od góry za głęboko
                    }
                    double headY = py + PLAYER_HEIGHT;
                    if (headY > by && headY < by + 1.0) {
                        double pen = headY - by;
                        if (pen > SHELL_BARRIER_DEPTH) return true;
                    }

                    // Z
                    double frontEdge = pz + PLAYER_WIDTH_HALF;
                    double backEdge = pz - PLAYER_WIDTH_HALF;

                    if (frontEdge > bz && frontEdge < bz + 1.0) {
                        double pen = frontEdge - bz;
                        if (pen > SHELL_BARRIER_DEPTH) return true;
                    }
                    if (backEdge > bz && backEdge < bz + 1.0) {
                        double pen = (bz + 1.0) - backEdge;
                        if (pen > SHELL_BARRIER_DEPTH) return true;
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
