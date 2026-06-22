package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;

public class HydroKlatkaBlockListener implements Listener {

    private final AnaItemy plugin;

    public HydroKlatkaBlockListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        if (!manager.isKlatkaBlock(location)) return;

        // Sprawdź czy to shell block (niezniszczalny)
        if (manager.isShellBlock(location)) {
            event.setCancelled(true);
            manager.sendMessage(player, "&cNie możesz tego zrobić w Hydro Klatce!");
            return;
        }

        // Sprawdź permissions (WorldGuard integration)
        if (!manager.canBreakBlock(player, location)) {
            event.setCancelled(true);
            manager.sendMessage(player, "&cNie możesz tego zrobić w Hydro Klatce!");
            return;
        }

        // Blok może być zniszczony - oznacz jako zniszczony
        manager.markBlockAsDestroyed(location);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        Player player = event.getPlayer();

        // Sprawdź czy gracz jest w klatce
        for (var klatka : manager.getActiveKlatki()) {
            if (klatka.isPlayerTrapped(player.getUniqueId()) && klatka.isInsideCage(location)) {
                // W klatce nie można stawiać bloków (poza dozwolonymi)
                event.setCancelled(true);
                manager.sendMessage(player, "&cNie możesz tego zrobić w Hydro Klatce!");
                return;
            }
        }

        // Sprawdź czy próbuje postawić blok na miejscu klatki
        if (manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
            manager.sendMessage(player, "&cNie możesz tego zrobić w Hydro Klatce!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();

        // Sprawdź czy gracz jest w klatce i używa zakazanego przedmiotu
        if (!manager.canUseItem(player, item.getType())) {
            event.setCancelled(true);
            manager.sendMessage(player, "&cNie możesz tego zrobić w Hydro Klatce!");
            
            // Dźwięk błędu
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
    }

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
                    manager.sendMessage(player, "&cNie możesz tego zrobić w Hydro Klatce!");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        
        // Usuń bloki klatki z listy bloków do zniszczenia
        event.blockList().removeIf(block -> 
                manager.isKlatkaBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        
        // Usuń bloki klatki z listy bloków do zniszczenia
        event.blockList().removeIf(block -> 
                manager.isKlatkaBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        
        // Sprawdź czy piston próbuje przesunąć bloki klatki
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
        
        // Sprawdź czy piston próbuje przesunąć bloki klatki
        for (var block : event.getBlocks()) {
            if (manager.isShellBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        Location location = event.getBlock().getLocation();
        
        // Zablokuj zmiany bloków klatki przez entity (np. Enderman)
        if (manager.isKlatkaBlock(location)) {
            event.setCancelled(true);
        }
    }
}
