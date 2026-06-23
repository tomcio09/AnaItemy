package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

    // ==================== ZŁAPANIE GRACZA ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player fisher = event.getPlayer();
        ItemStack rod = fisher.getInventory().getItemInMainHand();

        if (!WedkaNielotaItem.isWedkaNielota(rod)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // Sprawdź cooldown
        if (manager.isOnCooldown(fisher)) {
            event.setCancelled(true);
            manager.sendMessage(fisher,
                    plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
            return;
        }

        // Złapanie gracza na haczyk
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (event.getCaught() instanceof Player victim) {

                // Sprawdź region
                if (plugin.getWorldGuardManager().isInBlockedRegion(
                        victim.getLocation(),
                        plugin.getItemsConfig().getWedkaNielotaBlockedRegions())) {
                    return;
                }

                // Nałóż klątwę
                manager.applyCurse(victim, fisher);
            }
        }
    }

    // ==================== BLOKADA STARTU ELYTRY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(PlayerToggleGlideEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);

        // ✅ Gracz czeka na odlot (klątwa wygasła lub puszczona)
        if (curse.isWaitingForFlight() && event.isGliding()) {
            // Gracz odleciał - pokaż odpowiednią wiadomość i usuń klątwę
            if (curse.isWasReleased()) {
                manager.showReleasedTitle(player);
            } else {
                manager.showFreedTitle(player);
            }
            manager.forceRemoveCurse(player);
            return;
        }

        // ✅ Klątwa aktywna - blokuj start elytry (ale nie przerywaj istniejącego lotu)
        if (!curse.isWaitingForFlight() && event.isGliding()) {
            event.setCancelled(true);
        }
    }

    // ==================== BUGOWANIE (SPACJA W POWIETRZU) ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);
        if (curse.isWaitingForFlight()) return;

        // Jeśli gracz już leci elytrą - nie robimy nic
        if (player.isGliding()) return;

        // Jeśli gracz na ziemi lub w wodzie - nie robimy nic
        if (player.isOnGround() || player.isInWater()) return;

        // ✅ Gracz w powietrzu i próbuje "bugować" (velocity Y > 0 = kliknął spację)
        double velY = player.getVelocity().getY();
        if (velY > 0.1) {
            manager.handleSpaceClick(player);
        }
    }

    // ==================== UDERZENIE MIECZEM ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(victim);
        if (curse.isWaitingForFlight()) return;

        // ✅ Resetuj bugowanie (gracz normalnie spada przez 3-4 ticki)
        manager.resetBugowanie(victim);
    }

    // ==================== VOID/POISON/FALL - BEZ EFEKTU ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageOther(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event instanceof EntityDamageByEntityEvent) return; // Obsługiwane wyżej

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        // ✅ Void, poison, fall - nic nie robimy (bugowanie dalej działa)
        if (cause == EntityDamageEvent.DamageCause.VOID ||
                cause == EntityDamageEvent.DamageCause.POISON ||
                cause == EntityDamageEvent.DamageCause.FALL) {
            // Celowo puste
        }
    }

    // ==================== ZMIANA SLOTU ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player fisher = event.getPlayer();
        ItemStack previousItem = fisher.getInventory().getItem(event.getPreviousSlot());

        if (previousItem == null) return;
        if (!WedkaNielotaItem.isWedkaNielota(previousItem)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // ✅ Gracz przestał trzymać wędkę - klątwa czeka na odlot
        for (WedkaNielotaManager.CurseData curse : manager.getActiveCurses()) {
            if (!curse.getAttackerId().equals(fisher.getUniqueId())) continue;

            Player victim = org.bukkit.Bukkit.getPlayer(curse.getVictimId());
            if (victim == null) continue;

            manager.removeCurse(victim, true);
        }
    }

    // ==================== WYLOGOWANIE ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // Usuń klątwę jeśli wylogowany gracz ją posiadał
        if (manager.hasCurse(player)) {
            manager.forceRemoveCurse(player);
        }

        // Jeśli wylogował się łowiący - klątwa przechodzi w tryb "waitingForFlight"
        for (WedkaNielotaManager.CurseData curse : manager.getActiveCurses()) {
            if (curse.getAttackerId() == null) continue;
            if (!curse.getAttackerId().equals(player.getUniqueId())) continue;

            Player victim = org.bukkit.Bukkit.getPlayer(curse.getVictimId());
            if (victim == null) continue;

            manager.removeCurse(victim, true);
        }
    }
}
