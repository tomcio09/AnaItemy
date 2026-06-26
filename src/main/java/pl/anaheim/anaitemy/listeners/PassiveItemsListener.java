package pl.anaheim.anaitemy.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.LizakItem;
import pl.anaheim.anaitemy.items.RozaKupidynaItem;

public class PassiveItemsListener implements Listener {

    private final AnaItemy plugin;

    public PassiveItemsListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj stawianie róży i lizaka na ziemi.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        if (RozaKupidynaItem.isRozaKupidyna(event.getItem())
                || LizakItem.isLizak(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPassiveItemsManager().cleanupPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getPassiveItemsManager().cleanupPlayer(event.getEntity());
    }
}
