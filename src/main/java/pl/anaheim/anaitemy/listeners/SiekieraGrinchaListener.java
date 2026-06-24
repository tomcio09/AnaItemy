package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.SiekieraGrinchaItem;
import pl.anaheim.anaitemy.managers.SiekieraGrinchaManager;

public class SiekieraGrinchaListener implements Listener {

    private final AnaItemy plugin;

    public SiekieraGrinchaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!SiekieraGrinchaItem.isSiekieraGrincha(mainHand)) return;

        SiekieraGrinchaManager manager = plugin.getSiekieraGrinchaManager();

        // ✅ Próbuj aktywować umiejętność (manager sprawdza cooldown, region, protection)
        manager.attack(attacker, victim);

        // ✅ NIE anulujemy eventu - normalny damage z golden_axe przechodzi
        // Umiejętność (piorun + 30% HP) jest DODATKOWA
    }
}
