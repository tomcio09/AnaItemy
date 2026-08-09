package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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

    // ✅ Anti-spam dla dźwięku i subtitle - co ile ticków pokazać
    private static final int SOUND_COOLDOWN_TICKS = 8;
    private final Map<UUID, Long> lastSoundTime = new ConcurrentHashMap<>();

    // ✅ Promień niewidzialnej bariery = radius - 0.5 (środek ostatniego bloku shella)
    // Shell jest na granicy radius, więc bariera jest 0.5 bloku wewnątrz
    private static final double INVISIBLE_WALL_OFFSET = 0.5;

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

        // ✅ Ignoruj obroty głowy (bez zmiany pozycji)
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

        // ✅ NIEWIDZIALNA BARIERA: radius - INVISIBLE_WALL_OFFSET
        // Bariera jest w środku bloku shella (0.5 bloku wewnątrz granicy)
        double barrierRadius = radius - INVISIBLE_WALL_OFFSET;
        double distanceTo = to.distance(center);

        if (distanceTo >= barrierRadius) {
            // ✅ Gracz próbuje przekroczyć barierę - cofnij
            Location stuckLoc = from.clone();
            stuckLoc.setYaw(to.getYaw());
            stuckLoc.setPitch(to.getPitch());
            event.setTo(stuckLoc);

            // ✅ Dźwięk i subtitle z anti-spam
            playSoundAndSubtitle(player, manager);
            return;
        }

        // ✅ Gracz jest poza barierą (exploit/bug) - teleport na środek
        // Sprawdź czy gracz POWINIEN być w środku ale jakoś się wydostał
        if (distanceTo > radius && klatka.isAnimationComplete()) {
            // Gracz poza klatką - teleport na środek
            Location teleportLoc = center.clone();
            teleportLoc.setYaw(to.getYaw());
            teleportLoc.setPitch(to.getPitch());
            event.setTo(teleportLoc);
        }
    }

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
        double barrierRadius = radius - INVISIBLE_WALL_OFFSET;

        // ✅ Teleport pluginowy wewnątrz klatki - pozwól
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            if (to.distance(center) < barrierRadius) return;
        }

        ItemsConfig config = plugin.getItemsConfig();
        List<String> blockedRegions = config.getHydroKlatkaBlockedRegions();

        // ✅ Teleport na zablokowany region - wypuść z klatki
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

    // ==================== DŹWIĘK I SUBTITLE ====================

    private void playSoundAndSubtitle(Player player, HydroKlatkaManager manager) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastSound = lastSoundTime.get(uuid);
        // ✅ Anti-spam: dźwięk co SOUND_COOLDOWN_TICKS ticków (= 400ms)
        if (lastSound != null && now - lastSound < (SOUND_COOLDOWN_TICKS * 50L)) {
            return;
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
