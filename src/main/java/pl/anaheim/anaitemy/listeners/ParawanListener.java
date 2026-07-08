package pl.anaheim.anaitemy.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.ParawanItem;

public class ParawanListener implements Listener {

    private final AnaItemy plugin;

    public ParawanListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!ParawanItem.isParawan(item)) return;

        event.setCancelled(true);

        Location center = player.getLocation();

        for (Player target : center.getWorld().getNearbyPlayers(center, 5.0)) {
            if (target.equals(player)) continue;

            Vector knockback = target.getLocation().toVector().subtract(center.toVector());
            knockback.setY(0);
            if (knockback.lengthSquared() < 0.0001) knockback = new Vector(1, 0, 0);
            knockback.normalize().multiply(1.8).setY(0.4);

            target.setVelocity(target.getVelocity().add(knockback));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 1, false, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
        }

        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);
    }
}
