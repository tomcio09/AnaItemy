package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
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

        // ✅ Sprawdź obie ręce
        ItemStack mainHand = fisher.getInventory().getItemInMainHand();
        ItemStack offHand = fisher.getInventory().getItemInOffHand();

        boolean isMainHand = WedkaNielotaItem.isWedkaNielota(mainHand);
        boolean isOffHand = WedkaNielotaItem.isWedkaNielota(offHand);

        if (!isMainHand && !isOffHand) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // Sprawdź cooldown
        if (manager.isOnCooldown(fisher)) {
            event.setCancelled(true);
            manager.sendMessage(fisher,
                    plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
            return;
        }

        // ✅ Złapanie gracza na haczyk - TYLKO state CAUGHT_ENTITY
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (!(event.getCaught() instanceof Player victim)) return;

            // Sprawdź region
            if (plugin.getWorldGuardManager().isInBlockedRegion(
                    victim.getLocation(),
                    plugin.getItemsConfig().getWedkaNielotaBlockedRegions())) {
                return;
            }

            // Nałóż klątwę natychmiast przy złapaniu
            manager.applyCurse(victim, fisher);
        }
    }

    // ==================== BLOKADA STARTU ELYTRY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);

        // ✅ Klątwa wygasła / puszczona - czeka na odlot
        if (curse.isWaitingForFlight() && event.isGliding()) {
            if (curse.isWasReleased()) {
                manager.showReleasedTitle(player);
            } else {
                manager.showFreedTitle(player);
            }
            manager.forceRemoveCurse(player);
            return;
        }

        // ✅ Klątwa aktywna - blokuj START elytry
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

        // ✅ Tylko jeśli gracz NIE jest na ziemi i NIE jest w wodzie
        if (player.isOnGround() || player.isInWater()) return;

        // ✅ Bugowanie działa TYLKO gdy gracz jest w powietrzu i klika spację
        // Wykrywamy przez velocity Y > 0 (próba lotu elytrą = spacja w powietrzu)
        // ale NIE przy pierwszym skoku (skaczemy z ziemi - isOnGround był true)
        double velY = player.getVelocity().getY();

        // ✅ Wykryj "drugą spację" - gracz już spada (velY < 0) i klika spację (velY nagle > 0)
        // lub gracz jest w powietrzu i klika spację (velY > 0.1 ale nie jest to skok z ziemi)
        if (velY > 0.05 && !curse.wasJustOnGround()) {
            manager.handleSpaceClick(player);
        }

        // ✅ Aktualizuj flagę "był na ziemi"
        curse.setJustOnGround(false);
    }

    // ==================== ŚLEDZENIE LĄDOWANIA ====================

    /**
     * Gdy gracz ląduje na ziemi - resetujemy flagę aby następny skok był normalny
     * Bugowanie zaczyna działać dopiero przy DRUGIEJ spacji (w powietrzu)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLand(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);
        if (curse == null) return;

        // Gracz wylądował - ustaw flagę
        if (player.isOnGround()) {
            curse.setJustOnGround(true);
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

        // ✅ Resetuj bugowanie na 3-4 ticki
        manager.resetBugowanie(victim);
    }

    // ==================== VOID/POISON/FALL - BEZ EFEKTU ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageOther(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event instanceof EntityDamageByEntityEvent) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        // Void, poison, fall - nic nie robimy
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

        // ✅ Sprawdź czy gracz nadal trzyma wędkę w offhandzie
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

    // ==================== WYLOGOWANIE ====================

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
