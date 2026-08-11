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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaMovementListener implements Listener {

    private final AnaItemy plugin;

    private static final double TELEPORT_DISTANCE = 0.75;
    private static final long FEEDBACK_COOLDOWN_MS = 500L;

    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private final Set<UUID> ourTeleports = ConcurrentHashMap.newKeySet();
    private BukkitTask clampTask;

    public HydroKlatkaMovementListener(AnaItemy plugin) {
        this.plugin = plugin;
        startClampTask();
    }

    // ==================== TASK CO TICK ====================

    private void startClampTask() {
        clampTask = new BukkitRunnable() {
            @Override
            public void run() {
                HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

                for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                    Location center = klatka.getCenter();
                    double radius = klatka.getRadius();

                    for (UUID uuid : new ArrayList<>(klatka.getTrappedPlayers())) {
                        Player player = plugin.getServer().getPlayer(uuid);
                        if (player == null || !player.isOnline()) continue;

                        Location loc = player.getLocation();
                        if (!loc.getWorld().equals(center.getWorld())) continue;

                        if (player.isGliding()) continue;

                        if (klatka.isAnimationComplete()) continue;

                        // ✅ Oblicz odległość gracza od centrum klatki
                        double distance = loc.distance(center);

                        plugin.getLogger().info("[HK-DEBUG] Player: " + player.getName()
                                + " distance: " + distance + " radius: " + radius);

                        // ✅ Gracz zbliża się do granicy shella (bariera = radius - 0.5)
                        double barrierRadius = radius - 0.5;

                        if (distance >= barrierRadius) {
                            plugin.getLogger().info("[HK-DEBUG] BARRIER HIT! distance=" + distance
                                    + " barrierRadius=" + barrierRadius);

                            // ✅ Teleport 0.75 bloku W STRONĘ ŚRODKA
                            // Oblicz kierunek od gracza DO centrum
                            Vector toCenter = center.toVector().subtract(loc.toVector()).normalize();
                            Vector teleportVec = toCenter.multiply(TELEPORT_DISTANCE);

                            plugin.getLogger().info("[HK-DEBUG] Teleporting by: " + teleportVec);

                            Location newLoc = loc.clone().add(teleportVec);
                            doSafeTeleport(player, newLoc);
                            feedback(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ==================== BEZPIECZNY TELEPORT ====================

    private void doSafeTeleport(Player player, Location destination) {
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        ourTeleports.add(player.getUniqueId());
        player.teleport(destination);

        new BukkitRunnable() {
            @Override
            public void run() {
                ourTeleports.remove(player.getUniqueId());
            }
        }.runTask(plugin);
    }

    // ==================== FEEDBACK ====================

    private void feedback(Player player) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        Location to = event.getTo();
        if (to == null) return;

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        if (plugin.getWorldGuardManager().isInBlockedRegion(to, blockedRegions)) {
            manager.removePlayerFromKlatka(player);
        }
    }

    // ==================== TELEPORT EVENT ====================

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // ✅ NASZ teleport - przepuść zawsze
        if (ourTeleports.contains(player.getUniqueId())) {
            plugin.getLogger().info("[HK-DEBUG] Allowing our teleport for " + player.getName());
            event.setCancelled(false);
            return;
        }

        if (player.isGliding()) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka == null) return;

        if (klatka.isAnimationComplete()) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;

        Location to = event.getTo();
        if (to == null) return;

        if (to.distance(klatka.getCenter()) > klatka.getRadius()) {
            plugin.getLogger().info("[HK-DEBUG] Blocking external teleport for " + player.getName());
            event.setCancelled(true);
            manager.sendMessage(player,
                    plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
        ourTeleports.remove(event.getPlayer().getUniqueId());
    }

    public void stopTasks() {
        if (clampTask != null) {
            clampTask.cancel();
            clampTask = null;
        }
        lastFeedback.clear();
        ourTeleports.clear();
    }
}
