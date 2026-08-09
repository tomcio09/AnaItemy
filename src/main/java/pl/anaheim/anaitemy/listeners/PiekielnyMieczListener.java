package pl.anaheim.anaitemy.listeners;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiekielnyMieczItem;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PiekielnyMieczListener implements Listener {

    private final AnaItemy plugin;
    private final Map<UUID, Long> hellFirePlayers = new ConcurrentHashMap<>();

    public PiekielnyMieczListener(AnaItemy plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(hellFirePlayers.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) {
                        hellFirePlayers.remove(uuid);
                        continue;
                    }

                    Player victim = org.bukkit.Bukkit.getPlayer(uuid);
                    if (victim == null || !victim.isOnline() || victim.isDead()) {
                        hellFirePlayers.remove(uuid);
                        continue;
                    }

                    double damage = calculateFireDamageWithArmor(victim, 1.0);
                    if (damage <= 0) continue;

                    victim.setFireTicks(25);

                    double health = victim.getHealth();
                    if (health - damage <= 0) {
                        victim.setHealth(0.0);
                    } else {
                        victim.setHealth(health - damage);
                        victim.damage(0.001);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!PiekielnyMieczItem.isPiekielnyMiecz(mainHand)) return;

        int fireDuration = plugin.getItemsConfig().getPiekielnyMieczFireDuration();
        long fireEnd = System.currentTimeMillis() + (fireDuration * 1000L);
        hellFirePlayers.put(victim.getUniqueId(), fireEnd);

        victim.setFireTicks(fireDuration * 20);
    }

    private double calculateFireDamageWithArmor(Player player, double baseDamage) {
        int totalProtectionLevel = 0;
        int totalFireProtectionLevel = 0;

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;
            // ✅ 1.21.4 - nowe nazwy enchantów
            totalProtectionLevel += piece.getEnchantmentLevel(Enchantment.PROTECTION);
            totalFireProtectionLevel += piece.getEnchantmentLevel(Enchantment.FIRE_PROTECTION);
        }

        double enchantReduction = Math.min(80.0,
                (totalProtectionLevel * 4.0) + (totalFireProtectionLevel * 8.0));

        return Math.max(0, baseDamage * (1.0 - (enchantReduction / 100.0)));
    }

    public boolean isHellFired(Player player) {
        return hellFirePlayers.containsKey(player.getUniqueId());
    }
}
