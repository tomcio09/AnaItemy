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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    // ✅ Bariera na zewnętrznej 1/4 bloku shella
    // Blok shell: [0 ... 1.0]
    // Wewnętrzna krawędź (od centrum): pozycja 0
    // Bariera: 0.75 od wewnętrznej = 0.25 od zewnętrznej
    // Teleport na środek: jeśli gracz jest w [0.75 ... 1.0] (zewnętrzna 1/4)
    private static final double BARRIER_PLANE = 0.75;   // bariera = 3/4 bloku od wewnątrz
    private static final double TELEPORT_PLANE = 0.90;  // teleport na środek = gracz głęboko w bloku

    // ✅ O ile cofamy gracza za barierę (minimalnie)
    private static final double PUSHBACK = 0.15;

    // Hitbox gracza
    private static final double HALF_W = 0.30;
    private static final double HEIGHT = 1.80;

    // Anti-spam feedback
    private static final long FEEDBACK_MS = 500L;
    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();

    private BukkitTask task;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startTask();
    }

    // ==================== TASK CO TICK ====================

    private void startTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager mgr = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : mgr.getActiveKlatki()) {
                    // ✅ Działamy TYLKO na graczach którzy są trapped
                    for (UUID id : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(id);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        Location center = klatka.getCenter();

                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        handlePlayer(player, loc, klatka, mgr, center);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== GŁÓWNA LOGIKA ====================

    private void handlePlayer(Player player, Location loc, ActiveHydroKlatka klatka,
                               HydroKlatkaManager mgr, Location center) {

        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();
        double dist = loc.distance(center);
        double radius = klatka.getRadius();

        // ==================== EXPLOIT: daleko poza klatką ====================
        if (dist > radius + 2.0) {
            teleportToCenter(player, center, loc);
            return;
        }

        // ==================== Sprawdź każdy blok planned shell ====================
        // Planned shell = pozycje gdzie shell się JESZCZE nie zbudował
        // Gdy blok się zbuduje → MC sam go blokuje, my nic nie robimy

        if (klatka.isAnimationComplete()) {
            // ✅ Po animacji: MC blokuje ruch przez bloki shella
            // Sprawdzamy tylko czy gracz nie utknął w bloku
            checkStuckInShell(player, loc, klatka, mgr, center);
            return;
        }

        // ==================== Podczas animacji: sprawdź planned shell ====================
        checkPlannedShellBarrier(player, loc, klatka, mgr, center,
                px, py, pz);
    }

    // ==================== BARIERA PLANNED SHELL ====================

    /**
     * ✅ Sprawdza czy gracz dotknął bariery na planned shell (jeszcze nie zbudowany blok).
     *
     * Dla każdego planned shell bloku sprawdzamy czy hitbox gracza
     * przekroczył BARRIER_PLANE (0.75 od wewnętrznej krawędzi).
     *
     * Jeśli tak → cofnij gracza minimalnie w kierunku centrum.
     * Jeśli gracz jest w zewnętrznej 1/4 (>TELEPORT_PLANE) → teleport na środek.
     */
    private void checkPlannedShellBarrier(Player player, Location loc,
                                           ActiveHydroKlatka klatka, HydroKlatkaManager mgr,
                                           Location center,
                                           double px, double py, double pz) {

        // Sprawdź punkty hitboxa gracza (środek ciężkości + rogi)
        // Używamy środka gracza na X/Z i stóp/głowy na Y
        double[] checkX = {px, px + HALF_W, px - HALF_W};
        double[] checkZ = {pz, pz + HALF_W, pz - HALF_W};
        double[] checkY = {py, py + HEIGHT * 0.5, py + HEIGHT};

        // Zebranie informacji o kolizji
        double pushX = 0, pushY = 0, pushZ = 0;
        boolean needsPush = false;
        boolean needsTeleport = false;

        for (double cx : checkX) {
            for (double cy : checkY) {
                for (double cz : checkZ) {
                    int bx = (int) Math.floor(cx);
                    int by = (int) Math.floor(cy);
                    int bz = (int) Math.floor(cz);

                    // ✅ Sprawdź czy to planned shell (jeszcze nie zbudowany)
                    Location blockLoc = new Location(center.getWorld(), bx, by, bz);
                    if (!isPlannedShellOnly(blockLoc, klatka, mgr)) continue;

                    // ✅ Oblicz jak głęboko gracz jest w bloku od WEWNĘTRZNEJ strony
                    // Kierunek od centrum do bloku = kierunek "na zewnątrz"
                    double dirX = (bx + 0.5) - center.getX();
                    double dirY = (by + 0.5) - center.getY();
                    double dirZ = (bz + 0.5) - center.getZ();

                    // Dominująca oś (blok shella jest na granicy sfery,
                    // więc jedna oś dominuje)
                    double absX = Math.abs(dirX);
                    double absY = Math.abs(dirY);
                    double absZ = Math.abs(dirZ);

                    // ✅ Penetracja w bloku na dominującej osi
                    double penetration = 0;
                    double localPx = 0, localPy = 0, localPz = 0;

                    if (absX >= absY && absX >= absZ) {
                        // Oś X dominuje
                        if (dirX > 0) {
                            // Blok jest na prawo od centrum → gracz wchodzi od lewej strony bloku
                            penetration = cx - bx; // ile od lewej krawędzi bloku
                        } else {
                            // Blok jest na lewo od centrum → gracz wchodzi od prawej strony bloku
                            penetration = (bx + 1.0) - cx;
                        }
                        localPx = (dirX > 0 ? 1 : -1);
                    } else if (absY >= absX && absY >= absZ) {
                        // Oś Y dominuje
                        if (dirY > 0) {
                            penetration = cy - by;
                        } else {
                            penetration = (by + 1.0) - cy;
                        }
                        localPy = (dirY > 0 ? 1 : -1);
                    } else {
                        // Oś Z dominuje
                        if (dirZ > 0) {
                            penetration = cz - bz;
                        } else {
                            penetration = (bz + 1.0) - cz;
                        }
                        localPz = (dirZ > 0 ? 1 : -1);
                    }

                    // ✅ penetration < BARRIER_PLANE → gracz jest wewnątrz, przed barierą → OK
                    // penetration >= BARRIER_PLANE → gracz przekroczył barierę → cofnij
                    // penetration >= TELEPORT_PLANE → gracz głęboko w bloku → teleport

                    if (penetration >= TELEPORT_PLANE) {
                        needsTeleport = true;
                        break;
                    }

                    if (penetration >= BARRIER_PLANE) {
                        needsPush = true;
                        // Kierunek cofnięcia = do wewnątrz klatki (odwrotny do localP)
                        pushX -= localPx;
                        pushY -= localPy;
                        pushZ -= localPz;
                    }
                }
                if (needsTeleport) break;
            }
            if (needsTeleport) break;
        }

        if (needsTeleport) {
            teleportToCenter(player, center, loc);
            return;
        }

        if (needsPush) {
            applyPushback(player, loc, pushX, pushY, pushZ);
        }
    }

    // ==================== SPRAWDŹ UTKNIĘCIE W SHELLU ====================

    /**
     * ✅ Po zakończeniu animacji: sprawdź czy gracz utknął w bloku shella
     * lub jest w zewnętrznej 1/4 bloku shella → teleport na środek.
     */
    private void checkStuckInShell(Player player, Location loc, ActiveHydroKlatka klatka,
                                    HydroKlatkaManager mgr, Location center) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        // Sprawdź środek gracza i stopy
        int bx = (int) Math.floor(px);
        int by = (int) Math.floor(py);
        int bz = (int) Math.floor(pz);

        Location feetBlock = new Location(center.getWorld(), bx, by, bz);
        Location headBlock = new Location(center.getWorld(), bx, (int) Math.floor(py + HEIGHT), bz);

        boolean feetInShell = mgr.isShellBlock(feetBlock);
        boolean headInShell = mgr.isShellBlock(headBlock);

        if (!feetInShell && !headInShell) return;

        // Gracz utknął w shellu → teleport na środek
        teleportToCenter(player, center, loc);
    }

    // ==================== SPRAWDZANIE PLANNED SHELL ====================

    /**
     * ✅ Zwraca true TYLKO jeśli blok jest planned shell (jeszcze nie zbudowany).
     * Jeśli blok jest już zbudowany (BLUE_GLAZED_TERRACOTTA) → false (MC sam blokuje).
     * Jeśli blok jest zwykłym blokiem (nie shell, nie planned) → false.
     */
    private boolean isPlannedShellOnly(Location blockLoc, ActiveHydroKlatka klatka,
                                        HydroKlatkaManager mgr) {
        // Jeśli blok jest już zbudowanym shellem → MC blokuje, my nic
        if (mgr.isShellBlock(blockLoc)) return false;

        // Jeśli animacja skończona → brak planned shell
        if (klatka.isAnimationComplete()) return false;

        // Sprawdź czy to planned shell
        return klatka.isPlannedShellLocation(blockLoc);
    }

    // ==================== PUSHBACK ====================

    /**
     * ✅ Cofa gracza minimalnie w kierunku centrum (odwrotny do kierunku wyjścia).
     * Zeruje velocity w kierunku na zewnątrz.
     */
    private void applyPushback(Player player, Location loc,
                                double pushDirX, double pushDirY, double pushDirZ) {
        // Normalizuj kierunek cofnięcia
        double len = Math.sqrt(pushDirX * pushDirX + pushDirY * pushDirY + pushDirZ * pushDirZ);
        if (len < 0.001) return;

        double nx = pushDirX / len;
        double ny = pushDirY / len;
        double nz = pushDirZ / len;

        // ✅ Nowa pozycja = cofnięcie o PUSHBACK w kierunku do środka
        Location newLoc = new Location(
                loc.getWorld(),
                loc.getX() + nx * PUSHBACK,
                loc.getY() + ny * PUSHBACK,
                loc.getZ() + nz * PUSHBACK,
                loc.getYaw(),
                loc.getPitch()
        );

        // ✅ Zeruj velocity w kierunku na zewnątrz
        Vector vel = player.getVelocity();

        // Komponent velocity w kierunku na zewnątrz (odwrotny do push)
        double outX = -nx, outY = -ny, outZ = -nz;
        double dot = vel.getX() * outX + vel.getY() * outY + vel.getZ() * outZ;

        if (dot > 0) {
            // Usuń komponent velocity w kierunku na zewnątrz
            vel.setX(vel.getX() - dot * outX);
            vel.setY(vel.getY() - dot * outY);
            vel.setZ(vel.getZ() - dot * outZ);
            player.setVelocity(vel);
        }

        player.teleport(newLoc);

        // ✅ Zeruj velocity po teleporcie (grawitacja)
        vel = player.getVelocity();
        dot = vel.getX() * outX + vel.getY() * outY + vel.getZ() * outZ;
        if (dot > 0) {
            vel.setX(vel.getX() - dot * outX);
            vel.setY(vel.getY() - dot * outY);
            vel.setZ(vel.getZ() - dot * outZ);
            player.setVelocity(vel);
        }

        feedback(player);
    }

    // ==================== TELEPORT NA ŚRODEK ====================

    private void teleportToCenter(Player player, Location center, Location currentLoc) {
        Location tp = center.clone();
        tp.setYaw(currentLoc.getYaw());
        tp.setPitch(currentLoc.getPitch());
        player.setVelocity(new Vector(0, 0, 0));
        player.teleport(tp);
        player.setVelocity(new Vector(0, 0, 0));
        feedback(player);
    }

    // ==================== FEEDBACK ====================

    private void feedback(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFeedback.get(player.getUniqueId());
        if (last != null && now - last < FEEDBACK_MS) return;
        lastFeedback.put(player.getUniqueId(), now);

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.5f, 1.2f);

        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&cNie możesz opuszczać podwodnej klatki!"),
                Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(800), Duration.ofMillis(200))
        ));
    }

    // ==================== MOVE EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager mgr = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = mgr.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Ignoruj obroty głowy
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blocked = config.getHydroKlatkaBlockedRegions();

        // ✅ Wypuść jeśli gracz jest na zablokowanym regionie
        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blocked)) {
            mgr.removePlayerFromKlatka(player);
            return;
        }

        // ✅ MoveEvent: dodatkowa warstwa — jeśli gracz próbuje wejść w planned shell
        // i FROM jest bezpieczne → cofnij do FROM
        // (clampTask co tick i tak to obsłuży, ale MoveEvent daje szybszą reakcję)
        if (!klatka.isAnimationComplete()) {
            if (wouldHitPlannedShell(to, klatka, mgr) && !wouldHitPlannedShell(from, klatka, mgr)) {
                Location stuck = from.clone();
                stuck.setYaw(to.getYaw());
                stuck.setPitch(to.getPitch());
                event.setTo(stuck);
                feedback(player);
            }
        }
    }

    /**
     * ✅ Sprawdza czy pozycja gracza przekracza barierę na planned shell.
     * Używamy centrum hitboxa gracza (nie rogi — to dla MoveEvent, szybki check).
     */
    private boolean wouldHitPlannedShell(Location loc, ActiveHydroKlatka klatka,
                                          HydroKlatkaManager mgr) {
        if (klatka.isAnimationComplete()) return false;

        Location center = klatka.getCenter();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        // Sprawdź środek gracza i kilka punktów
        double[] xs = {px, px + HALF_W, px - HALF_W};
        double[] ys = {py, py + HEIGHT * 0.5, py + HEIGHT};
        double[] zs = {pz, pz + HALF_W, pz - HALF_W};

        for (double cx : xs) {
            for (double cy : ys) {
                for (double cz : zs) {
                    int bx = (int) Math.floor(cx);
                    int by = (int) Math.floor(cy);
                    int bz = (int) Math.floor(cz);

                    Location blockLoc = new Location(center.getWorld(), bx, by, bz);
                    if (!isPlannedShellOnly(blockLoc, klatka, mgr)) continue;

                    double dirX = (bx + 0.5) - center.getX();
                    double dirY = (by + 0.5) - center.getY();
                    double dirZ = (bz + 0.5) - center.getZ();
                    double absX = Math.abs(dirX), absY = Math.abs(dirY), absZ = Math.abs(dirZ);

                    double penetration;
                    if (absX >= absY && absX >= absZ) {
                        penetration = dirX > 0 ? cx - bx : (bx + 1.0) - cx;
                    } else if (absY >= absX && absY >= absZ) {
                        penetration = dirY > 0 ? cy - by : (by + 1.0) - cy;
                    } else {
                        penetration = dirZ > 0 ? cz - bz : (bz + 1.0) - cz;
                    }

                    if (penetration >= BARRIER_PLANE) return true;
                }
            }
        }
        return false;
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        HydroKlatkaManager mgr = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = mgr.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blocked = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blocked)) {
            mgr.removePlayerFromKlatka(player);
            return;
        }

        // Blokuj teleport poza klatkę
        if (to.distance(klatka.getCenter()) > klatka.getRadius()) {
            event.setCancelled(true);
            mgr.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
    }

    public void stopTasks() {
        if (task != null) { task.cancel(); task = null; }
        lastFeedback.clear();
    }
}
