package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KostkaRubikaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KostkaRubikaListener implements Listener {

    private final AnaItemy plugin;

    public KostkaRubikaListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!KostkaRubikaItem.isKostkaRubika(mainHand)) return;

        // ✅ 4s protection
        if (plugin.getItemProtectionManager().isProtected(victim, "kostka-rubika")) {
            int sl = plugin.getItemProtectionManager().getRemainingSeconds(victim, "kostka-rubika");
            plugin.getItemProtectionManager().notifyAttacker(attacker, "kostka-rubika", sl);
            return;
        }

        // ✅ Zużyj 1 kostkę
        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            attacker.getInventory().setItemInMainHand(null);
        }

        // ✅ Przemieszaj hotbar ofiary (sloty 0-8)
        List<ItemStack> hotbarItems = new ArrayList<>();
        for (int i = 0; i <= 8; i++) {
            ItemStack item = victim.getInventory().getItem(i);
            hotbarItems.add(item != null ? item.clone() : null);
        }

        Collections.shuffle(hotbarItems);

        for (int i = 0; i <= 8; i++) {
            victim.getInventory().setItem(i, hotbarItems.get(i));
        }

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(victim, "kostka-rubika");

        // ✅ Combat tag
        if (plugin.getCombatIntegrationManager().isEnabled()) {
            plugin.getCombatIntegrationManager().tagPlayer(victim, attacker);
            plugin.getCombatIntegrationManager().tagPlayer(attacker, victim);
        }
    }
}
