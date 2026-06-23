package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WedkaNielotaItem;
import pl.anaheim.anaitemy.managers.WedkaNielotaManager;

public class WedkaNielotaListener implements Listener {

    private final AnaItemy plugin;
    private static final String WEDKA_HOOK_META = "wedka_nielota_hook";

    public WedkaNielotaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== RZUCENIE WĘDKI - OZNACZ HACZYK ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onFishingStart(PlayerFishEvent event) {
        Player fisher = event.getPlayer();

        ItemStack mainHand = fisher.getInventory().getItemInMainHand();
        ItemStack offHand = fisher.getInventory().getItemInOffHand();

        boolean isWedka = WedkaNielotaItem.isWedkaNielota(mainHand)
                || WedkaNielotaItem.isWedkaNielota(offHand);

        if (!isWedka) return;

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // Sprawdź cooldown przy rzucaniu
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (manager.isOnCooldown(fisher)) {
                event.setCancelled(true);
                manager.sendMessage(fisher,
                        plugin.getItemsConfig().getWedkaNielotaCooldownMessage());
                return;
            }

            // ✅ Oznacz haczyk metadatą - to jest wędka nielota
            if (event.getHook() != null) {
                event.getHook().setMetadata(WEDKA_HOOK_META,
                        new FixedMetadataValue(plugin, fisher.getUniqueId().toString()));
            }
        }
    }

    // ==================== HACZYK TRAFIA W GRACZA ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(event.getHitEntity() instanceof Player victim)) return;

        // ✅ Sprawdź czy to haczyk wędki nielota
        if (!hook.hasMetadata(WEDKA_HOOK_META)) return;

        String fisherUUID = hook.getMetadata(WEDKA_HOOK_META).get(0).asString();
        Player fisher = plugin.getServer().getPlayer(java.util.UUID.fromString(fisherUUID));

        if (fisher == null || !fisher.isOnline()) return;
        if (victim.equals(fisher)) return; // Nie złap sam siebie

        WedkaNielotaManager manager = plugin.getWedkaNielotaManager();

        // Sprawdź cooldown (nie powinien mieć bo sprawdziliśmy przy rzucie, ale dla pewności)
        if (manager.isOnCooldown(fisher)) return;

        // Sprawdź region
        if (plugin.getWorldGuardManager().isInBlockedRegion(
                victim.getLocation(),
                plugin.getItemsConfig().getWedkaNielotaBlockedRegions())) {
            return;
        }

        // ✅ NAŁÓŻ KLĄTWĘ NATYCHMIAST przy trafieniu w gracza
        manager.applyCurse(victim, fisher);

        plugin.getLogger().info("[WedkaDebug] ZŁAPANO GRACZA: " + victim.getName()
                + " przez " + fisher.getName());
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
                event.setCancelled(true);
                manager.markSpaceClicked(player);
            }
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

        manager.resetBugowanie(victim);
    }

    // ==================== VOID/POISON/FALL - BEZ EFEKTU ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageOther(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event instanceof EntityDamageByEntityEvent) return;
    }

    // ==================== ZMIANA SLOTU ====================

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
