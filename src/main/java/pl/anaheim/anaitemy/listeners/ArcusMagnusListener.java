package pl.anaheim.anaitemy.listeners;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.ArcusMagnusItem;
import pl.anaheim.anaitemy.managers.ArcusMagnusManager;

public class ArcusMagnusListener implements Listener {

    private final AnaItemy plugin;

    public ArcusMagnusListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== GHOST ARROW ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!ArcusMagnusItem.isArcusMagnus(item)) return;

        plugin.getArcusMagnusManager().prepareGhostArrow(player);
    }

    // ==================== STRZAŁ ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getBow() == null) return;
        if (!ArcusMagnusItem.isArcusMagnus(event.getBow())) return;

        ArcusMagnusManager manager = plugin.getArcusMagnusManager();

        // Przywróć ghost arrow
        manager.restoreGhostArrow(player);

        // Anuluj vanilla strzałę
        event.setCancelled(true);
        if (event.getProjectile() != null) event.getProjectile().remove();

        if (manager.isInBlockedRegion(player.getLocation())) return;

        // Wystrzal custom strzałę
        manager.fireArrow(player, event.getForce());
    }

    // ==================== TRAFIENIE ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ArcusMagnusManager manager = plugin.getArcusMagnusManager();

        if (!manager.isArcusArrow(arrow)) return;

        // ✅ Anuluj vanilla damage
        event.setCancelled(true);

        Player shooter = manager.getArrowShooter(arrow);
        if (shooter == null || !shooter.isOnline()) return;
        if (shooter.equals(victim)) return;

        // Usuń strzałę
        arrow.remove();

        // Obsłuż hit combo
        manager.handleHit(shooter, victim);
    }

    // ==================== GHOST ARROW PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ArcusMagnusManager manager = plugin.getArcusMagnusManager();
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
        ArcusMagnusManager manager = plugin.getArcusMagnusManager();
        if (manager.isGhostArrow(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            manager.restoreGhostArrow(event.getPlayer());
        }
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getArcusMagnusManager().cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getArcusMagnusManager().cleanupPlayer(event.getEntity());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        plugin.getArcusMagnusManager().restoreGhostArrow(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        plugin.getArcusMagnusManager().restoreGhostArrow(event.getPlayer());
    }
}
