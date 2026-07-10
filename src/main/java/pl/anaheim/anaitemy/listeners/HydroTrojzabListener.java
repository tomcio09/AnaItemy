package pl.anaheim.anaitemy.listeners;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.HydroTrojzabItem;
import pl.anaheim.anaitemy.managers.HydroTrojzabManager;

public class HydroTrojzabListener implements Listener {

    private final AnaItemy plugin;

    public HydroTrojzabListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== GHOST ARROW - PPM ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!HydroTrojzabItem.isHydroTrojzab(item)) return;

        HydroTrojzabManager manager = plugin.getHydroTrojzabManager();

        if (manager.isInBlockedRegion(player.getLocation())) return;

        manager.prepareGhostArrow(player);
    }

    // ==================== STRZAŁ ====================

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getBow() == null) return;
        if (!HydroTrojzabItem.isHydroTrojzab(event.getBow())) return;

        HydroTrojzabManager manager = plugin.getHydroTrojzabManager();

        // ✅ Przywróć ghost arrow
        manager.restoreGhostArrow(player);

        // ✅ Anuluj vanilla strzałę
        event.setCancelled(true);
        if (event.getProjectile() != null) event.getProjectile().remove();

        if (manager.isInBlockedRegion(player.getLocation())) return;

        // SHIFT = launch
        if (player.isSneaking()) {
            if (manager.isLaunchOnCooldown(player)) {
                manager.sendLaunchCooldownSubtitle(player);
                return;
            }
            manager.useLaunch(player);
            return;
        }

        // Normalny strzał
        if (manager.isShotOnCooldown(player)) {
            manager.sendShotCooldownSubtitle(player);
            return;
        }

        manager.fireHydroTrident(player, event.getForce());
    }

    // ==================== TRAFIENIE TRIDENTU ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;

        plugin.getHydroTrojzabManager().handleImpact(trident);
    }

    // ==================== BLOKADA VANILLA DAMAGE OD TRIDENTU ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTridentDirectDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) return;

        if (trident.hasMetadata("anaitemy_hydro_trident")) {
            event.setCancelled(true);
        }
    }

    // ==================== GHOST ARROW PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        HydroTrojzabManager manager = plugin.getHydroTrojzabManager();
        int ghostSlot = manager.getGhostArrowSlot(player);

        if (ghostSlot != -1 && event.getClickedInventory() == player.getInventory()
                && event.getSlot() == ghostSlot) {
            event.setCancelled(true);
            manager.restoreGhostArrow(player);
            return;
        }

        if (manager.isGhostArrow(event.getCurrentItem()) || manager.isGhostArrow(event.getCursor())) {
            event.setCancelled(true);
            manager.restoreGhostArrow(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        HydroTrojzabManager manager = plugin.getHydroTrojzabManager();
        if (manager.isGhostArrow(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            manager.restoreGhostArrow(event.getPlayer());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getHydroTrojzabManager().cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getHydroTrojzabManager().cleanupPlayer(event.getEntity());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        plugin.getHydroTrojzabManager().restoreGhostArrow(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        plugin.getHydroTrojzabManager().restoreGhostArrow(event.getPlayer());
    }
}
