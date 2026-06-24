package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WzmocnianaElytra;

public class WzmocnianaElytraListener implements Listener {

    private final AnaItemy plugin;

    public WzmocnianaElytraListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokuj fall damage gdy elytra ma 100%.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        // Sprawdź czy gracz ma wzmocnianą elytrę z 100%
        org.bukkit.inventory.ItemStack chestplate = player.getInventory().getChestplate();
        if (!WzmocnianaElytra.isWzmocnianaElytra(chestplate)) return;

        double charge = WzmocnianaElytra.getCharge(chestplate);
        if (charge < 100.0) return;

        // ✅ Blokuj damage i wywal piorun
        event.setCancelled(true);
        plugin.getWzmocnianaElytraManager().triggerLightningStrike(player);
    }

    /**
     * ✅ Shift click - resetuj ładowanie.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onShiftClick(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        Player player = event.getPlayer();
        if (!player.isGliding()) return;

        plugin.getWzmocnianaElytraManager().onShiftClick(player);
    }
}
