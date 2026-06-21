package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.Excalibur;

public class ExcaliburListener implements Listener {

    private final AnaItemy plugin;

    public ExcaliburListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        // Sprawdź czy zabójcą jest gracz
        if (killer == null) return;

        // Sprawdź czy gracz trzyma Excalibur w głównej ręce
        ItemStack itemInHand = killer.getInventory().getItemInMainHand();
        if (!Excalibur.isExcalibur(itemInHand)) return;

        int maxKills = plugin.getConfig().getInt("excalibur.max-kills", 100);
        int currentKills = Excalibur.getKillsFromItem(itemInHand);

        // Jeśli już na limicie - nic nie rób
        if (currentKills >= maxKills) return;

        int newKills = currentKills + 1;

        // Zbuduj nowy item z zaktualizowanymi kills
        ItemStack updatedItem = Excalibur.buildItem(newKills, maxKills);

        // Ustaw zaktualizowany item w ręce
        killer.getInventory().setItemInMainHand(updatedItem);
    }
}
