package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.FishHook;
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
            manager.sendMessage(fisher, plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
            return;
        }

        // Złapanie gracza
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

    // ==================== BLOKADA ELYTRY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleGlide(PlayerToggleGlideEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);

        // Jeśli klątwa wygasła i czeka na odlot - pozwól odlecieć i pokaż wiadomość
        if (curse.isWaitingForFlight() && event.isGliding()) {
            manager.showFreedTitle(player);
            manager.removeCurse(player, false);
            return;
        }

        // Jeśli klątwa aktywna - blokuj start elytry
        if (!curse.isWaitingForFlight() && event.isGliding()) {
            event.setCancelled(true);
        }
    }

    // ==================== BUGOWANIE (SPAM SPACJI) ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);
        if (curse.isWaitingForFlight()) return;

        // Jeśli gracz już leci - nie blokuj
        if (player.isGliding()) return;

        // Jeśli gracz w powietrzu (nie na ziemi)
        if (!player.isOnGround() && !player.isInWater()) {
            // Sprawdź czy klika spację (próbuje latać)
            // To będzie obsługiwane przez osobny task w PlayerToggleGlideEvent
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJump(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);
        if (curse.isWaitingForFlight()) return;

        // Jeśli gracz w powietrzu i próbuje bugować
        if (!player.isOnGround() && !player.isGliding() && !player.isInWater()) {
            // Wykryj spację przez velocity Y > 0 (próba skoku w powietrzu)
            double velocityY = player.getVelocity().getY();
            if (velocityY > 0) {
                manager.handleSpaceClick(player);
            }
        }
    }

    // ==================== UDERZENIE MIECZEM ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(victim)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(victim);
        if (curse.isWaitingForFlight()) return;

        // Resetuj bugowanie (gracz normalnie spada przez krótką chwilę)
        manager.resetBugowanie(victim);
    }

    // ==================== VOID/POISON - BEZ EFEKTU ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamageOther(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        // Void, poison, fall - bez resetowania bugowania
        if (cause == EntityDamageEvent.DamageCause.VOID ||
                cause == EntityDamageEvent.DamageCause.POISON ||
                cause == EntityDamageEvent.DamageCause.FALL) {
            // Nic nie rób
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

        // Znajdź wszystkich złapanych graczy przez tego rybaka
        for (WedkaNielotaManager.CurseData curse : manager.getActiveCurses()) {
            if (curse.getAttackerId().equals(fisher.getUniqueId())) {
                Player victim = org.bukkit.Bukkit.getPlayer(curse.getVictimId());
                if (victim != null) {
                    manager.removeCurse(victim, true);
                }
            }
        }
    }

    // ==================== QUIT ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // Usuń klątwę jeśli gracz się wylogował
        if (manager.hasCurse(player)) {
            manager.removeCurse(player, false);
        }
    }
}
