package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiekielnaTarczaItem;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PiekielnaTarczaListener implements Listener {

    private final AnaItemy plugin;
    private final Map<UUID, Long> reflectCooldowns = new ConcurrentHashMap<>();

    public PiekielnaTarczaListener(AnaItemy plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player defender)) return;

        if (!defender.isBlocking()) return;

        ItemStack offHand = defender.getInventory().getItemInOffHand();
        ItemStack mainHand = defender.getInventory().getItemInMainHand();

        boolean hasPiekielnaTarcza = PiekielnaTarczaItem.isPiekielnaTarcza(offHand)
                || PiekielnaTarczaItem.isPiekielnaTarcza(mainHand);

        if (!hasPiekielnaTarcza) return;

        ItemStack attackerItem = attacker.getInventory().getItemInMainHand();
        if (attackerItem.getType().name().endsWith("_AXE")) return;

        Long cooldownEnd = reflectCooldowns.get(defender.getUniqueId());
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) return;

        if (ThreadLocalRandom.current().nextDouble() > 0.25) return;

        double baseDamage = 1.0;

        // ✅ 1.21.4 - nowa nazwa enchanta
        int sharpness = attackerItem.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            baseDamage += 0.5 * sharpness + 0.5;
        }

        baseDamage += getWeaponDamage(attackerItem);

        double armorReduction = calculateArmorReduction(attacker);
        double finalDamage = baseDamage * (1.0 - armorReduction);
        finalDamage = Math.max(0.5, finalDamage);

        double health = attacker.getHealth();
        if (health - finalDamage <= 0) {
            attacker.setHealth(0.0);
        } else {
            attacker.setHealth(health - finalDamage);
        }

        attacker.damage(0.001);

        reflectCooldowns.put(defender.getUniqueId(), System.currentTimeMillis() + 5000);

        String attackerSub = plugin.getItemsConfig().getPiekielnaTarczaAttackerSubtitle()
                .replace("{shield_handler}", defender.getName());
        attacker.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(attackerSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));

        String defenderSub = plugin.getItemsConfig().getPiekielnaTarczaDefenderSubtitle()
                .replace("{attacker_bez_tarczy}", attacker.getName());
        defender.showTitle(Title.title(Component.empty(),
                LegacyComponentSerializer.legacyAmpersand().deserialize(defenderSub),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))));
    }

    private double getWeaponDamage(ItemStack item) {
        return switch (item.getType()) {
            case NETHERITE_SWORD -> 8.0;
            case DIAMOND_SWORD -> 7.0;
            case IRON_SWORD -> 6.0;
            case STONE_SWORD -> 5.0;
            case GOLDEN_SWORD -> 4.0;
            case WOODEN_SWORD -> 4.0;
            case NETHERITE_AXE -> 10.0;
            case DIAMOND_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case STONE_AXE -> 9.0;
            case GOLDEN_AXE -> 7.0;
            case WOODEN_AXE -> 7.0;
            default -> 1.0;
        };
    }

    private double calculateArmorReduction(Player player) {
        int totalProtection = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            // ✅ 1.21.4 - nowa nazwa enchanta
            totalProtection += piece.getEnchantmentLevel(Enchantment.PROTECTION);
        }
        double enchantReduction = Math.min(80.0, totalProtection * 4.0);

        double armorPoints = 0;
        // ✅ 1.21.4 - nowa nazwa atrybutu
        if (player.getAttribute(Attribute.ARMOR) != null) {
            armorPoints = player.getAttribute(Attribute.ARMOR).getValue();
        }
        double armorReduction = Math.min(0.8, armorPoints * 0.04);

        return Math.min(0.9, armorReduction + (enchantReduction / 100.0));
    }
}
