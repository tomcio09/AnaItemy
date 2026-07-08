package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.SplesnialaKanapkaItem;

public class SplesnialaKanapkaListener implements Listener {

    private final AnaItemy plugin;

    public SplesnialaKanapkaListener(AnaItemy plugin) { this.plugin = plugin; }

    // ✅ Blokuj jedzenie
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (SplesnialaKanapkaItem.isSplesnialaKanapka(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!SplesnialaKanapkaItem.isSplesnialaKanapka(mainHand)) return;

        int duration = plugin.getItemsConfig().getSplesnialaKanapkaGlowingDuration() * 20;

        // ✅ Glowing na 20 sekund
        victim.setGlowing(true);

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isOnline()) victim.setGlowing(false);
        }, duration);

        // Zużyj
        if (mainHand.getAmount() > 1) mainHand.setAmount(mainHand.getAmount() - 1);
        else attacker.getInventory().setItemInMainHand(null);
    }
}
