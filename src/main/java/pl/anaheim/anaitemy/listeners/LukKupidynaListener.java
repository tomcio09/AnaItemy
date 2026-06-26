package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.LukKupidynaItem;
import pl.anaheim.anaitemy.managers.OslepienieManager;

import java.util.concurrent.ThreadLocalRandom;

public class LukKupidynaListener implements Listener {

    private final AnaItemy plugin;

    public LukKupidynaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // ✅ Sprawdź czy strzała pochodzi z łuku kupidyna
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (!LukKupidynaItem.isLukKupidyna(bow)) {
            // Sprawdź offhand
            bow = shooter.getInventory().getItemInOffHand();
            if (!LukKupidynaItem.isLukKupidyna(bow)) return;
        }

        OslepienieManager manager = plugin.getOslepienieManager();

        // ✅ Sprawdź cooldown
        if (manager.isLukOnCooldown(shooter)) return;

        // ✅ 25% szans na oślepienie
        double chance = plugin.getItemsConfig().getLukKupidynaBlindChance();
        if (ThreadLocalRandom.current().nextDouble() * 100.0 > chance) return;

        // ✅ Oślepiaj! (NIE anulujemy damage - normalny damage łuku przechodzi)
        manager.applyBlindness(shooter, victim, false);
    }
}
