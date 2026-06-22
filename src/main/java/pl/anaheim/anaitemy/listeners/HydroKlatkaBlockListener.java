package pl.anaheim.anaitemy.listeners;

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
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

public class HydroKlatkaBlockListener implements Listener {

    private final AnaItemy plugin;

    public HydroKlatkaBlockListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== NISZCZENIE BLOKÓW ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        if (!manager.isKlatkaBlock(location)) return;

        if (manager.isShellBlock(location)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        if (!manager.canBreakBlock(player, location)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
        }

        manager.markBlockAsDestroyed(location);
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

        if (!manager.canUseItem(player, item.getType())) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());

            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
    }

    // ==================== JEDZENIE (CHORUS FRUIT) ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Sprawdź czy gracz jest w klatce i próbuje zjeść zablokowany item
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

        // Jeśli gracz jest w klatce - już blokowane przez canUseItem
        // Ale dodatkowo oznaczamy perłę jako "z klatki"
        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isPlayerTrapped(player.getUniqueId())) {
                // Perła już nie powinna być rzucona (zablokowane w interact)
                // ale dla pewności oznaczamy
                pearl.setMetadata("from_cage", new org.bukkit.metadata.FixedMetadataValue(plugin, klatka.getId().toString()));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location hitLocation = pearl.getLocation();

        // Sprawdź czy perła uderzyła w ścianę klatki
        if (manager.isShellBlock(hitLocation) || manager.isKlatkaBlock(hitLocation)) {
            // Perła znika bez teleportacji
            event.setCancelled(true);
            pearl.remove();
            return;
        }

        // Sprawdź czy perła próbuje teleportować do środka klatki
        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isInsideCage(hitLocation)) {
                // Perła próbuje teleportować do środka - anuluj
                event.setCancelled(true);
                pearl.remove();
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Zablokuj teleport do środka klatki
        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isInsideCage(to)) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                return;
            }
        }

        // Zablokuj teleport przez ścianę
        if (manager.isShellBlock(to) || manager.isKlatkaBlock(to)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
        }
    }

    // ==================== WYLEWANIE WODY/LAWY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Material bucket = event.getBucket();

        // Dozwól wylewanie wody, ale nie lawy
        if (bucket == Material.LAVA_BUCKET) {
            HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

            for (var klatka : manager.getActiveKlatki()) {
                if (klatka.isPlayerTrapped(player.getUniqueId())) {
                    event.setCancelled(true);
                    manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                    return;
                }
            }
        }
    }

    // ==================== EKSPLOZJE ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        event.blockList().removeIf(block ->
                manager.isKlatkaBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        event.blockList().removeIf(block ->
                manager.isKlatkaBlock(block.getLocation()));
    }

    // ==================== PISTONY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        for (var block : event.getBlocks()) {
            if (manager.isShellBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        for (var block : event.getBlocks()) {
            if (manager.isShellBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== ENTITY CHANGES ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();

        if (manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
        }
    }
}
