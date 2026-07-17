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
import org.bukkit.inventory.meta.ItemMeta;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.models.ActiveHydroKlatka;

public class HydroKlatkaBlockListener implements Listener {

    private final AnaItemy plugin;

    public HydroKlatkaBlockListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== HELPER: Sprawdza czy item to custom item pluginu ====================

    private boolean isCustomPluginItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Sprawdź PDC (PersistentDataContainer) lub display name
        // Dostosuj do swoich itemów
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            // Sprawdź czy to jakikolwiek custom item z pluginu
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

        // ✅ Shell NIGDY nie może być zniszczony ręcznie
        if (manager.isShellBlock(location)) {
            event.setCancelled(true);
            manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
            return;
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

        // ✅ Sprawdź czy gracz jest w klatce
        ActiveHydroKlatka klatka = manager.getKlatkaForPlayer(player);
        if (klatka != null) {

            // ✅ Zablokuj WSZYSTKIE custom itemy pluginu w klatce
            if (isCustomPluginItem(item)) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 1.0f, 0.8f);
                return;
            }

            // ✅ Sprawdź blocked items z configu
            if (!manager.canUseItem(player, item.getType())) {
                event.setCancelled(true);
                manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 1.0f, 0.8f);
                return;
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

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (!klatka.isAnimationComplete()) {
                if (klatka.isInsideCage(hitLocation) || hitLocation.distance(klatka.getCenter()) <= klatka.getRadius()) {
                    event.setCancelled(true);
                    pearl.remove();
                    return;
                }
            }
        }

        if (manager.isShellBlock(hitLocation) || manager.isKlatkaBlock(hitLocation)) {
            event.setCancelled(true);
            pearl.remove();
            return;
        }

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (klatka.isInsideCage(hitLocation)) {
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

        for (ActiveHydroKlatka klatka : manager.getActiveKlatki()) {
            if (!klatka.isAnimationComplete()) {
                double distance = to.distance(klatka.getCenter());

                if (klatka.isPlayerTrapped(player.getUniqueId())) {
                    if (distance > klatka.getRadius() - 1.0) {
                        event.setCancelled(true);
                        manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                        return;
                    }
                }

                if (!klatka.isPlayerTrapped(player.getUniqueId())) {
                    if (klatka.isInsideCage(to)) {
                        event.setCancelled(true);
                        manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                        return;
                    }
                }
            } else {
                if (klatka.isPlayerTrapped(player.getUniqueId())) {
                    boolean hasShell = manager.isShellBlock(to.getBlock().getLocation())
                            || manager.isShellBlock(to.clone().add(0, 1, 0).getBlock().getLocation());

                    if (hasShell) {
                        event.setCancelled(true);
                        manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                        return;
                    }
                }

                if (!klatka.isPlayerTrapped(player.getUniqueId())) {
                    if (klatka.isInsideCage(to)) {
                        event.setCancelled(true);
                        manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                        return;
                    }
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
                    manager.sendMessage(player, plugin.getItemsConfig().getHydroKlatkaMessageCannotUseInCage());
                    return;
                }
            }
        }
    }

    // ==================== EKSPLOZJE ====================

    // ✅ HIGHEST priority - shell NIGDY nie może być zniszczony przez eksplozje
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        HydroKlatkaManager manager = plugin.getHydroKlatkaManager();
        // Usuń WSZYSTKIE bloki klatki z listy eksplozji (nie mogą być zniszczone)
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

    // ==================== FADE (np. lód, śnieg) ====================

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
