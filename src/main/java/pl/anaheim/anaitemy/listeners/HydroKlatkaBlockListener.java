package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaBlockListener implements Listener {

    private final AnaItemy plugin;

    // ✅ Anti-spam dla dźwięku niszczenia powłoki
    private static final int BREAK_SOUND_COOLDOWN_MS = 400;
    private final Map<UUID, Long> lastBreakSoundTime = new ConcurrentHashMap<>();

    private static final Material SHELL_MATERIAL = Material.BLUE_GLAZED_TERRACOTTA;

    public HydroKlatkaBlockListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== HELPER ====================

    private boolean isCustomPluginItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name.contains("Bombarda") || name.contains("bombarda") ||
                    name.contains("TurboTrap") || name.contains("turbotrap") ||
                    name.contains("Turbo Trap") || name.contains("turbo trap") ||
                    name.contains("HydroKlatka") || name.contains("hydroklatka") ||
                    name.contains("Hydro Klatka") || name.contains("hydro klatka")) {
                return true;
            }
        }
        return false;
    }

    // ==================== NISZCZENIE BLOKÓW ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        // ✅ Shell NIE może być zniszczony - dźwięk + subtitle
        if (manager.isShellBlock(location)) {
            event.setCancelled(true);
            playShellBreakFeedback(player);
            return;
        }

        // ✅ Blok planowanego shella (podczas animacji) - też nie można niszczyć
        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(location)) {
                event.setCancelled(true);
                playShellBreakFeedback(player);
                return;
            }
        }

        if (!manager.isKlatkaBlock(location)) {
            return;
        }

        if (!manager.canBreakBlock(player, location)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        manager.markBlockAsDestroyed(location);
    }

    // ==================== FEEDBACK DLA POWŁOKI ====================

    private void playShellBreakFeedback(Player player) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastSound = lastBreakSoundTime.get(uuid);
        if (lastSound != null && now - lastSound < BREAK_SOUND_COOLDOWN_MS) {
            return; // anti-spam
        }

        lastBreakSoundTime.put(uuid, now);

        // ✅ Dźwięk szkła
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.8f, 0.8f);

        // ✅ Subtitle
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&cNie możesz zniszczyć granicy podwodnej klatki!"),
                Title.Times.times(
                        Duration.ofMillis(0),
                        Duration.ofMillis(800),
                        Duration.ofMillis(200)
                )
        ));
    }

    // ==================== STAWIANIE BLOKÓW ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        for (var klatka : manager.getActiveKlatki()) {
            if (klatka.isPlayerTrapped(player.getUniqueId()) && klatka.isInsideCage(location)) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                return;
            }
        }

        if (manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== UŻYWANIE PRZEDMIOTÓW ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka != null) {
            if (isCustomPluginItem(item)) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 1.0f, 0.8f);
                return;
            }

            if (!manager.canUseItem(player, item.getType())) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 1.0f, 0.8f);
            }
        }
    }

    // ==================== JEDZENIE ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        if (!manager.canUseItem(player, item.getType())) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
    }

    // ==================== ENDER PERŁY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                pearl.setMetadata("from_cage",
                        new org.bukkit.metadata.FixedMetadataValue(plugin, klatka.getId().toString()));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location hitLocation = pearl.getLocation();
        double barrierOffset = 0.5;

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            double barrierRadius = klatka.getRadius() - barrierOffset;
            if (hitLocation.distance(klatka.getCenter()) >= barrierRadius) {
                event.setCancelled(true);
                pearl.remove();
                return;
            }
        }

        if (manager.isShellBlock(hitLocation) || manager.isKlatkaBlock(hitLocation)) {
            event.setCancelled(true);
            pearl.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        double barrierOffset = 0.5;

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            double barrierRadius = klatka.getRadius() - barrierOffset;

            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                if (to.distance(klatka.getCenter()) >= barrierRadius) {
                    event.setCancelled(true);
                    manager.sendMessage(player,
                            plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                    return;
                }
            }

            if (!klatka.isPlayerTrapped(player.getUniqueId())) {
                if (klatka.isInsideCage(to)) {
                    event.setCancelled(true);
                    manager.sendMessage(player,
                            plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                    return;
                }
            }
        }
    }

    // ==================== WYLEWANIE WODY/LAWY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Material bucket = event.getBucket();

        if (bucket == Material.LAVA_BUCKET) {
            HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

            for (var klatka : manager.getActiveKlatki()) {
                if (klatka.isPlayerTrapped(player.getUniqueId())) {
                    event.setCancelled(true);
                    manager.sendMessage(player,
                            plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                    return;
                }
            }
        }
    }

    // ==================== EKSPLOZJE ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        event.blockList().removeIf(block -> manager.isKlatkaBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        event.blockList().removeIf(block -> manager.isKlatkaBlock(block.getLocation()));
    }

    // ==================== PISTONY ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        for (var block : event.getBlocks()) {
            if (manager.isKlatkaBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        for (var block : event.getBlocks()) {
            if (manager.isKlatkaBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== WODA/LAWA FLOW ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location toLoc = event.getToBlock().getLocation();
        Location fromLoc = event.getBlock().getLocation();

        if (manager.isKlatkaBlock(toLoc) || manager.isKlatkaBlock(fromLoc)) {
            event.setCancelled(true);
        }
    }

    // ==================== FIRE ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        if (manager.isKlatkaBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        if (manager.isKlatkaBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== FADE ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(BlockFadeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        if (manager.isKlatkaBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== ŚMIERĆ W KLATCE ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        manager.removePlayerFromKlatka(player);
    }

    // ==================== ENTITY CHANGES ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        if (manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
        }
    }
}
