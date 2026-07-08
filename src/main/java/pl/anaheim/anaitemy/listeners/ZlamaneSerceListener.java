package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.ZlamaneSerceItem;

import java.time.Duration;

public class ZlamaneSerceListener implements Listener {

    private final AnaItemy plugin;

    public ZlamaneSerceListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!ZlamaneSerceItem.isZlamaneSerce(mainHand)) return;

        int duration = plugin.getItemsConfig().getZlamaneSerceSlowFallingDuration() * 20;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0, false, true, true));

        // Subtitle
        String attackerSub = plugin.getItemsConfig().getZlamaneSerceAttackerSubtitle()
                .replace("{victim}", victim.getName());
        attacker.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        victim.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        plugin.getItemsConfig().getZlamaneSerceVictimSubtitle()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        // Zużyj
        if (mainHand.getAmount() > 1) mainHand.setAmount(mainHand.getAmount() - 1);
        else attacker.getInventory().setItemInMainHand(null);
    }
}
