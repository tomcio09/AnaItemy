package pl.anaheim.anaitemy.listeners;

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
import org.bukkit.Location;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.RozdzkailuzjonistyItem;
import pl.anaheim.anaitemy.managers.RozdzkailuzjonistyManager;

import java.util.UUID;

public class RozdzkailuzjonistyListener implements Listener {

    private final AnaItemy plugin;

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
        double damage = config.getRozdzkailuzjonistyFangsDamage();

        // ✅ Zadaj damage ofierze
        applyDamage(victim, damage);
        plugin.getItemProtectionManager().applyProtection(victim, "rozdzka-iluzjonisty");
        manager.markFangDamaged(fang, victim.getUniqueId());

        // ✅ Sprawdz graczy 1-2 bloki wyzej nad szczekami (skaczacy gracze)
        Location fangLoc = fang.getLocation();
        for (Player nearby : fangLoc.getWorld().getNearbyPlayers(fangLoc, 1.5, 2.5, 1.5)) {
            if (nearby.equals(victim)) continue;
            if (ownerUUID != null && nearby.getUniqueId().equals(ownerUUID)) continue;
            if (manager.hasFangDamaged(fang, nearby.getUniqueId())) continue;
            if (!manager.canFangDamageInRegion(nearby.getLocation())) continue;

            if (plugin.getItemProtectionManager().isProtected(nearby, "rozdzka-iluzjonisty")) {
                if (attacker != null) {
                    int sl = plugin.getItemProtectionManager().getRemainingSeconds(nearby, "rozdzka-iluzjonisty");
                    plugin.getItemProtectionManager().notifyAttacker(attacker, "rozdzka-iluzjonisty", sl);
                }
                manager.markFangDamaged(fang, nearby.getUniqueId());
                continue;
            }

            applyDamage(nearby, damage);
            plugin.getItemProtectionManager().applyProtection(nearby, "rozdzka-iluzjonisty");
            manager.markFangDamaged(fang, nearby.getUniqueId());
        }

        fang.remove();
        manager.cleanupFang(fang);
    }

    private void applyDamage(Player victim, double damage) {
        double newHealth = victim.getHealth() - damage;
        if (newHealth <= 0) victim.setHealth(0.0);
        else victim.setHealth(newHealth);
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
