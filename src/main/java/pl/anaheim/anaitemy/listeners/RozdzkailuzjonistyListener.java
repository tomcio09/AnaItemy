package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.RozdzkailuzjonistyItem;
import pl.anaheim.anaitemy.managers.RozdzkailuzjonistyManager;
import pl.anaheim.anaitemy.utils.ArmorReductionHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RozdzkailuzjonistyListener implements Listener {

    private final AnaItemy plugin;
    private final Set<String> damagedByWave = new HashSet<>();

    public RozdzkailuzjonistyListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!RozdzkailuzjonistyItem.isRozdzkaIluzjonisty(item)) return;

        Player player = event.getPlayer();
        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            manager.activateFangs(player);
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            manager.activateVanish(player);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFangDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof EvokerFangs fang)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        if (manager.hasFangDamaged(fang, victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!manager.canFangDamageInRegion(victim.getLocation())) {
            event.setCancelled(true);
            fang.remove();
            manager.cleanupFang(fang);
            return;
        }

        UUID ownerUUID = manager.getFangOwner(fang);

        if (ownerUUID != null && ownerUUID.equals(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Player attacker = ownerUUID != null ? plugin.getServer().getPlayer(ownerUUID) : null;

        if (attacker != null) {
            if (plugin.getItemProtectionManager().isProtected(victim, "rozdzka-iluzjonisty")) {
                event.setCancelled(true);
                int secondsLeft = plugin.getItemProtectionManager()
                        .getRemainingSeconds(victim, "rozdzka-iluzjonisty");
                plugin.getItemProtectionManager()
                        .notifyAttacker(attacker, "rozdzka-iluzjonisty", secondsLeft);
                manager.markFangDamaged(fang, victim.getUniqueId());
                fang.remove();
                manager.cleanupFang(fang);
                return;
            }
        }

        event.setCancelled(true);

        ItemsConfig config = plugin.getItemsConfig();
        double baseDamage = config.getRozdzkailuzjonistyFangsDamage();

        // ✅ Zastosuj redukcję zbroi eventówek
        double damage = ArmorReductionHelper.applyArmorReduction(baseDamage, victim);

        applyDamage(victim, damage);
        plugin.getItemProtectionManager().applyProtection(victim, "rozdzka-iluzjonisty");
        manager.markFangDamaged(fang, victim.getUniqueId());

        Location fangLoc = fang.getLocation();
        checkPlayersAbove(fangLoc, fang, ownerUUID, attacker, damage, manager);

        fang.remove();
        manager.cleanupFang(fang);
    }

    private void checkPlayersAbove(Location fangLoc, EvokerFangs fang,
                                    UUID ownerUUID, Player attacker,
                                    double damage, RozdzkailuzjonistyManager manager) {

        for (Player nearby : fangLoc.getWorld().getNearbyPlayers(fangLoc, 1.5, 3.0, 1.5)) {
            if (manager.hasFangDamaged(fang, nearby.getUniqueId())) continue;
            if (ownerUUID != null && nearby.getUniqueId().equals(ownerUUID)) continue;
            if (nearby.getLocation().getY() - fangLoc.getY() < 0.5) continue;

            if (!manager.canFangDamageInRegion(nearby.getLocation())) continue;

            if (plugin.getItemProtectionManager().isProtected(nearby, "rozdzka-iluzjonisty")) {
                if (attacker != null) {
                    int sl = plugin.getItemProtectionManager()
                            .getRemainingSeconds(nearby, "rozdzka-iluzjonisty");
                    plugin.getItemProtectionManager()
                            .notifyAttacker(attacker, "rozdzka-iluzjonisty", sl);
                }
                manager.markFangDamaged(fang, nearby.getUniqueId());
                continue;
            }

            // ✅ Zastosuj redukcję zbroi eventówek dla gracza powyżej
            double nearbyDamage = ArmorReductionHelper.applyArmorReduction(damage, nearby);
            applyDamage(nearby, nearbyDamage);
            plugin.getItemProtectionManager().applyProtection(nearby, "rozdzka-iluzjonisty");
            manager.markFangDamaged(fang, nearby.getUniqueId());
        }
    }

    /**
     * ✅ Zadaje damage najpierw zabierając absorpcję (złote serca),
     * a dopiero potem zwykłe HP.
     */
    private void applyDamage(Player victim, double damage) {
        if (damage <= 0) return;

        // ✅ Sprawdź absorpcję (złote serca z enchanted golden apple etc.)
        AttributeInstance absorptionAttr = victim.getAttribute(Attribute.MAX_ABSORPTION);
        double absorption = victim.getAbsorptionAmount();

        if (absorption > 0) {
            if (damage <= absorption) {
                // ✅ Cały damage idzie z absorpcji
                victim.setAbsorptionAmount(absorption - damage);
                return;
            } else {
                // ✅ Część z absorpcji, reszta z HP
                double remainingDamage = damage - absorption;
                victim.setAbsorptionAmount(0.0);

                double newHealth = victim.getHealth() - remainingDamage;
                if (newHealth <= 0) victim.setHealth(0.0);
                else victim.setHealth(newHealth);
            }
        } else {
            // ✅ Brak absorpcji - cały damage z HP
            double newHealth = victim.getHealth() - damage;
            if (newHealth <= 0) victim.setHealth(0.0);
            else victim.setHealth(newHealth);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        if (manager.isVanished(attacker)) {
            manager.endVanish(attacker, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        if (manager.isVanished(player)) {
            manager.endVanish(player, true);
        }
    }
}
