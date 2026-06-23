package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WedkaNielotaItem;
import pl.anaheim.anaitemy.managers.WedkaNielotaManager;

public class WedkaNielotaListener implements Listener {

    private final AnaItemy plugin;

    public WedkaNielotaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== DEBUG - WSZYSTKIE STANY WĘDKI ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player fisher = event.getPlayer();

        ItemStack mainHand = fisher.getInventory().getItemInMainHand();
        ItemStack offHand = fisher.getInventory().getItemInOffHand();

        boolean isWedka = WedkaNielotaItem.isWedkaNielota(mainHand)
                || WedkaNielotaItem.isWedkaNielota(offHand);

        if (!isWedka) return;

        // ✅ DEBUG - wypisz wszystkie stany do konsoli
        plugin.getLogger().info("[WedkaDebug] State: " + event.getState()
                + " | Caught: " + (event.getCaught() != null ? event.getCaught().getType() : "null")
                + " | Fisher: " + fisher.getName());

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (manager.isOnCooldown(fisher)) {
            event.setCancelled(true);
            manager.sendMessage(fisher,
                    plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (!(event.getCaught() instanceof Player victim)) return;
            if (victim.equals(fisher)) return;

            if (plugin.getWorldGuardManager().isInBlockedRegion(
                    victim.getLocation(),
                    plugin.getItemsConfig().getWedkaNielotaBlockedRegions())) {
                return;
            }

            manager.applyCurse(victim, fisher);
        }
    }

    // Reszta listenerów bez zmian...

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);

        if (curse.isWaitingForFlight() && event.isGliding()) {
            if (curse.isWasReleased()) {
                manager.showReleasedTitle(player);
            } else {
                manager.showFreedTitle(player);
            }
            manager.forceRemoveCurse(player);
            return;
        }

        if (!curse.isWaitingForFlight() && event.isGliding()) {
            ItemStack chestplate = player.getInventory().getChestplate();
            boolean hasElytra = chestplate != null &&
                    chestplate.getType() == org.bukkit.Material.ELYTRA;

            if (hasElytra && !player.isOnGround()) {
                event.setCancelled(true);
                manager.markSpaceClicked(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(victim);
        if (curse.isWaitingForFlight()) return;

        manager.resetBugowanie(victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageOther(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event instanceof EntityDamageByEntityEvent) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player fisher = event.getPlayer();
        ItemStack previousItem = fisher.getInventory().getItem(event.getPreviousSlot());

        if (previousItem == null) return;
        if (!WedkaNielotaItem.isWedkaNielota(previousItem)) return;

        if (WedkaNielotaItem.isWedkaNielota(fisher.getInventory().getItemInOffHand())) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        for (WedkaNielotaManager.CurseData curse : manager.getActiveCurses()) {
            if (curse.getAttackerId() == null) continue;
            if (!curse.getAttackerId().equals(fisher.getUniqueId())) continue;

            Player victim = org.bukkit.Bukkit.getPlayer(curse.getVictimId());
            if (victim == null) continue;

            manager.removeCurse(victim, true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (manager.hasCurse(player)) {
            manager.forceRemoveCurse(player);
        }

        for (WedkaNielotaManager.CurseData curse : manager.getActiveCurses()) {
            if (curse.getAttackerId() == null) continue;
            if (!curse.getAttackerId().equals(player.getUniqueId())) continue;

            Player victim = org.bukkit.Bukkit.getPlayer(curse.getVictimId());
            if (victim == null) continue;

            manager.removeCurse(victim, true);
        }
    }
}
