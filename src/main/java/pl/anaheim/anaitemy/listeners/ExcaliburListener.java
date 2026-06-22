package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.Excalibur;

public class ExcaliburListener implements Listener {

    private final AnaItemy plugin;

    public ExcaliburListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * Liczy zabójstwa TYLKO GRACZY (nie mobów).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

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

        // Zaktualizuj kills (edytuje tylko konkretne linie)
        ItemStack updatedItem = Excalibur.updateKills(itemInHand, newKills, maxKills);

        // Ustaw zaktualizowany item w ręce
        killer.getInventory().setItemInMainHand(updatedItem);
    }
}
