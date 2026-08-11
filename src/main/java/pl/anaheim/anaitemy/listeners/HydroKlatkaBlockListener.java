// src/main/java/pl/anaheim/anaitemy/listeners/HydroKlatkaBlockListener.java
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
import org.bukkit.metadata.FixedMetadataValue;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HydroKlatkaBlockListener implements Listener {

    private final AnaItemy plugin;

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
        if (event == null || event.getBlock() == null) return;
        
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        if (location == null || player == null) return;

        // Bloki shell nie mogą być niszczone
        if (manager.isShellBlock(location)) {
            event.setCancelled(true);
            playShellBreakFeedback(player);
            return;
        }

        // Zaplanowane pozycje shell (jeszcze nie zbudowane) też nie mogą być niszczone
        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (!klatka.isAnimationComplete() && klatka.isPlannedShellLocation(location)) {
                event.setCancelled(true);
                playShellBreakFeedback(player);
                return;
            }
        }

        // Inne bloki klatki - sprawdź uprawnienia
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
        if (player == null) return;
        
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long lastSound = lastBreakSoundTime.get(uuid);
        if (lastSound != null && now - lastSound < BREAK_SOUND_COOLDOWN_MS) {
            return;
        }

        lastBreakSoundTime.put(uuid, now);

        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.8f, 0.8f);

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
        if (event == null || event.getBlock() == null) return;
        
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        if (location == null || player == null) return;

        // Trapped gracze nie mogą stawiać bloków wewnątrz klatki
        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isPlayerTrapped(player.getUniqueId()) && klatka.isInsideCage(location)) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                return;
            }
        }

        // Nie można stawiać bloków na blokach klatki
        if (manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== UŻYWANIE PRZEDMIOTÓW ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (player == null || item == null) return;

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
        if (event == null) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (player == null || item == null) return;

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
        if (event == null || event.getEntity() == null) return;
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                pearl.setMetadata("from_cage",
                        new FixedMetadataValue(plugin, klatka.getId().toString()));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlHit(ProjectileHitEvent event) {
        if (event == null || event.getEntity() == null) return;
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location hitLocation = pearl.getLocation();
        if (hitLocation == null) return;

        // Usuń perłę jeśli uderzy w blok shell lub blok klatki
        if (manager.isShellBlock(hitLocation) || manager.isKlatkaBlock(hitLocation)) {
            event.setCancelled(true);
            pearl.remove();
            return;
        }

        // Usuń perłę jeśli uderzy w barierę lub poza klatką (wystrzelona z wnętrza)
        if (pearl.hasMetadata("from_cage")) {
            for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
                Location center = klatka.getCenter();
                if (center == null || center.getWorld() == null) continue;
                if (hitLocation.getWorld() == null) continue;
                if (!hitLocation.getWorld().equals(center.getWorld())) continue;
                
                if (hitLocation.distance(center) >= klatka.getBarrierRadius()) {
                    event.setCancelled(true);
                    pearl.remove();
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event == null) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();
        Location to = event.getTo();
        if (player == null || to == null) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            Location center = klatka.getCenter();
            if (center == null || center.getWorld() == null) continue;
            if (to.getWorld() == null) continue;
            if (!to.getWorld().equals(center.getWorld())) continue;

            // Trapped gracz próbuje teleportować się poza barierę
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                if (to.distance(center) >= klatka.getBarrierRadius()) {
                    event.setCancelled(true);
                    manager.sendMessage(player,
                            plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                    return;
                }
            }

            // Gracz z zewnątrz próbuje teleportować się DO klatki
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
        if (event == null) return;
        
        Player player = event.getPlayer();
        Material bucket = event.getBucket();

        if (player == null || bucket == null) return;

        if (bucket == Material.LAVA_BUCKET) {
            HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

            for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
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
        if (event == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        event.blockList().removeIf(block -> block != null && manager.isKlatkaBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        event.blockList().removeIf(block -> block != null && manager.isKlatkaBlock(block.getLocation()));
    }

    // ==================== PISTONY ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        for (var block : event.getBlocks()) {
            if (block != null && manager.isKlatkaBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        for (var block : event.getBlocks()) {
            if (block != null && manager.isKlatkaBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== WODA/LAWA FLOW ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event == null || event.getBlock() == null || event.getToBlock() == null) return;
        
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location toLoc = event.getToBlock().getLocation();
        Location fromLoc = event.getBlock().getLocation();

        if (toLoc == null || fromLoc == null) return;

        if (manager.isKlatkaBlock(toLoc) || manager.isKlatkaBlock(fromLoc)) {
            event.setCancelled(true);
        }
    }

    // ==================== FIRE ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBurn(BlockBurnEvent event) {
        if (event == null || event.getBlock() == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        if (manager.isKlatkaBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event == null || event.getBlock() == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        if (manager.isKlatkaBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== FADE ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFade(BlockFadeEvent event) {
        if (event == null || event.getBlock() == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        if (manager.isKlatkaBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    // ==================== ŚMIERĆ W KLATCE ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event == null || event.getEntity() == null) return;
        Player player = event.getEntity();
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        manager.removePlayerFromKlatka(player);
    }

    // ==================== ENTITY CHANGES ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event == null || event.getBlock() == null) return;
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        if (location != null && manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
        }
    }
}
