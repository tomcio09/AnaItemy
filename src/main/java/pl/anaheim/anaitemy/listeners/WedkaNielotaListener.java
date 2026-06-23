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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WedkaNielotaItem;
import pl.anaheim.anaitemy.managers.WedkaNielotaManager;

public class WedkaNielotaListener implements Listener {

    private final AnaItemy plugin;

    // Klucz metadata dla hooka wędki nielota
    private static final String HOOK_META_KEY = "wedka_nielota_hook";

    public WedkaNielotaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== RZUCENIE WĘDKI - OZNACZ HOOK ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player fisher = event.getPlayer();

        // Sprawdź obie ręce
        ItemStack mainHand = fisher.getInventory().getItemInMainHand();
        ItemStack offHand = fisher.getInventory().getItemInOffHand();

        if (!WedkaNielotaItem.isWedkaNielota(mainHand) &&
                !WedkaNielotaItem.isWedkaNielota(offHand)) return;

        // Sprawdź cooldown przy próbie rzutu
        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (manager.isOnCooldown(fisher)) {
            event.setCancelled(true);
            manager.sendMessage(fisher,
                    plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
        }
    }

    // ==================== ZŁAPANIE GRACZA ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player fisher = event.getPlayer();

        // Sprawdź obie ręce
        ItemStack mainHand = fisher.getInventory().getItemInMainHand();
        ItemStack offHand = fisher.getInventory().getItemInOffHand();

        boolean isWedka = WedkaNielotaItem.isWedkaNielota(mainHand)
                || WedkaNielotaItem.isWedkaNielota(offHand);

        if (!isWedka) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        PlayerFishEvent.State state = event.getState();

        // ✅ CAUGHT_ENTITY = haczyk trafił w gracza
        if (state == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (!(event.getCaught() instanceof Player victim)) return;
            if (victim.equals(fisher)) return;

            // Sprawdź cooldown
            if (manager.isOnCooldown(fisher)) {
                event.setCancelled(true);
                manager.sendMessage(fisher,
                        plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
                return;
            }

            // Sprawdź region
            if (plugin.getWorldGuardManager().isInBlockedRegion(
                    victim.getLocation(),
                    plugin.getItemsConfig().getWedkaNielotaBlockedRegions())) {
                return;
            }

            // ✅ Nałóż klątwę NATYCHMIAST przy złapaniu
            manager.applyCurse(victim, fisher);
            return;
        }

        // ✅ IN_GROUND / FAILED_ATTEMPT / FISHING - ignoruj (nie nakładaj klątwy)
    }

    // ==================== BLOKADA STARTU ELYTRY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(player)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(player);

        // ✅ Klątwa wygasła / puszczona - pozwól odlecieć + wiadomość
        if (curse.isWaitingForFlight() && event.isGliding()) {
            if (curse.isWasReleased()) {
                manager.showReleasedTitle(player);
            } else {
                manager.showFreedTitle(player);
            }
            manager.forceRemoveCurse(player);
            return;
        }

        // ✅ Klątwa aktywna + gracz próbuje włączyć glide + ma elytrę + jest w powietrzu
        if (!curse.isWaitingForFlight() && event.isGliding()) {
            ItemStack chestplate = player.getInventory().getChestplate();
            boolean hasElytra = chestplate != null &&
                    chestplate.getType() == org.bukkit.Material.ELYTRA;

            if (hasElytra && !player.isOnGround()) {
                // ✅ Zablokuj glide
                event.setCancelled(true);
                // ✅ Oznacz że gracz kliknął spację (do bugowania)
                manager.markSpaceClicked(player);
            }
        }
    }

    // ==================== BUGOWANIE - LIMIT PRĘDKOŚCI OPADANIA ====================

    // ✅ Nie używamy PlayerMoveEvent do wykrywania spacji
    // Logika działa w WedkaNielotaManager.startBugowanieTask() co tick

    // ==================== UDERZENIE MIECZEM ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        WedkaNielotaManager.CurseData curse = manager.getCurse(victim);
        if (curse.isWaitingForFlight()) return;

        // ✅ Resetuj bugowanie na 3-4 ticki (gracz normalnie spada przez krótką chwilę)
        manager.resetBugowanie(victim);
    }

    // ==================== VOID/POISON/FALL - BEZ EFEKTU ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageOther(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event instanceof EntityDamageByEntityEvent) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();
        if (!manager.hasCurse(victim)) return;

        // Void, poison, fall - nic nie robimy z bugowaniem
    }

    // ==================== ZMIANA SLOTU ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player fisher = event.getPlayer();
        ItemStack previousItem = fisher.getInventory().getItem(event.getPreviousSlot());

        if (previousItem == null) return;
        if (!WedkaNielotaItem.isWedkaNielota(previousItem)) return;

        // Sprawdź czy nadal trzyma wędkę w offhandzie
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
