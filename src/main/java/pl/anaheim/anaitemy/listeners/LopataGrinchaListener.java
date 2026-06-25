package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.LopataGrinchaItem;
import pl.anaheim.anaitemy.managers.LopataGrinchaManager;

public class LopataGrinchaListener implements Listener {

    private final AnaItemy plugin;

    public LopataGrinchaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!LopataGrinchaItem.isLopataGrincha(mainHand)) return;

        LopataGrinchaManager manager = plugin.getLopataGrinchaManager();
        manager.attack(attacker, victim);
    }
}
