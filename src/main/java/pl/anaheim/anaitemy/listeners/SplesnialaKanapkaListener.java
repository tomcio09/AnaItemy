package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.SplesnialaKanapkaItem;

public class SplesnialaKanapkaListener implements Listener {

    private final AnaItemy plugin;

    public SplesnialaKanapkaListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!SplesnialaKanapkaItem.isSplesnialaKanapka(mainHand)) return;

        // ✅ 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "splesniale-kanapka")) {
            int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "splesniale-kanapka");
            plugin.getItemProtectionManager().notifyAttacker(attacker, "splesniale-kanapka", sl);
            return;
        }

        int glowingDuration = plugin.getItemsConfig().getSplesnialaKanapkaGlowingDuration() * 20;
        victim.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, glowingDuration, 0, false, true, true));

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "splesniale-kanapka");

        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }
    }
}
