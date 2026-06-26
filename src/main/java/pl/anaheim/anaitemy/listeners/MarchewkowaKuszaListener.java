package pl.anaheim.anaitemy.listeners;

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
import pl.anaheim.anaitemy.items.MarchewkowaKuszaItem;
import pl.anaheim.anaitemy.managers.MarchewkowaKuszaManager;

public class MarchewkowaKuszaListener implements Listener {

    private final AnaItemy plugin;

    public MarchewkowaKuszaListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!MarchewkowaKuszaItem.isMarchewkowaKusza(item)) return;
        plugin.getMarchewkowaKuszaManager().prepareGhostArrow(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getBow() == null) return;
        if (!MarchewkowaKuszaItem.isMarchewkowaKusza(event.getBow())) return;

        MarchewkowaKuszaManager manager = plugin.getMarchewkowaKuszaManager();
        manager.restoreGhostArrow(player);

        if (manager.isOnCooldown(player)) {
            event.setCancelled(true);
            if (event.getProjectile() != null) event.getProjectile().remove();
            return;
        }

        // Oznacz strzałę jako kuszową
        if (event.getProjectile() instanceof Arrow arrow) {
            manager.markArrow(arrow, player);
            arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        MarchewkowaKuszaManager manager = plugin.getMarchewkowaKuszaManager();
        if (!manager.isKuszaArrow(arrow)) return;

        // ✅ Anuluj damage - kusza tylko przyciąga
        event.setCancelled(true);

        Player shooter = manager.getArrowShooter(arrow);
        if (shooter == null || !shooter.isOnline()) return;
        if (shooter.equals(victim)) return;

        arrow.remove();

        // Sprawdź czy shooter się nie teleportował
        // (shooterLocation zapisujemy jako aktualną pozycję strzelca w momencie trafienia)
        manager.startPull(shooter, victim, shooter.getLocation());
    }

    // ==================== GHOST ARROW PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MarchewkowaKuszaManager m = plugin.getMarchewkowaKuszaManager();
        int gs = m.getGhostArrowSlot(player);
        if (gs != -1 && event.getClickedInventory() == player.getInventory() && event.getSlot() == gs) {
            event.setCancelled(true); m.restoreGhostArrow(player); return;
        }
        if (m.isGhostArrow(event.getCurrentItem()) || m.isGhostArrow(event.getCursor())) {
            event.setCancelled(true); m.restoreGhostArrow(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        MarchewkowaKuszaManager m = plugin.getMarchewkowaKuszaManager();
        if (m.isGhostArrow(event.getItemDrop().getItemStack())) {
            event.setCancelled(true); m.restoreGhostArrow(event.getPlayer());
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.getMarchewkowaKuszaManager().cleanupPlayer(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { plugin.getMarchewkowaKuszaManager().cleanupPlayer(event.getEntity()); }
    @EventHandler public void onHeld(PlayerItemHeldEvent event) { plugin.getMarchewkowaKuszaManager().restoreGhostArrow(event.getPlayer()); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) { plugin.getMarchewkowaKuszaManager().restoreGhostArrow(event.getPlayer()); }
}
