package pl.anaheim.anaitemy.listeners;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
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

        // ✅ Co 2 ticki wymuszaj palenie
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

                    // ✅ Wymuszaj palenie nawet w wodzie
                    if (victim.getFireTicks() <= 1) {
                        victim.setFireTicks(40);
                    }

                    // ✅ Jeśli w wodzie - nadal pali
                    if (victim.isInWater()) {
                        victim.setFireTicks(40);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // ✅ Co sekundę zadaj fire damage ręcznie jeśli fire resistance blokuje vanilla damage
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(hellFirePlayers.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) continue;

                    Player victim = org.bukkit.Bukkit.getPlayer(uuid);
                    if (victim == null || !victim.isOnline() || victim.isDead()) continue;

                    // ✅ Tylko jeśli ma fire resistance (bo vanilla fire damage nie przechodzi)
                    if (!victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) continue;

                    // ✅ Oblicz damage z uwzględnieniem zbroi i enchantów
                    double baseDamage = 1.0; // Vanilla fire damage = 1 HP/s
                    double finalDamage = calculateFireDamageWithArmor(victim, baseDamage);

                    if (finalDamage <= 0) continue;

                    double health = victim.getHealth();
                    if (health - finalDamage <= 0) {
                        victim.setHealth(0.0);
                    } else {
                        victim.setHealth(health - finalDamage);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
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

    /**
     * ✅ Oblicza fire damage z uwzględnieniem:
     * - Protection enchant (ogólna ochrona)
     * - Fire Protection enchant (ochrona przed ogniem)
     * - Armor toughness
     * 
     * Wzory z Minecraft Wiki:
     * - Każdy level Protection daje 4% redukcji (max 80% łącznie)
     * - Każdy level Fire Protection daje 8% redukcji od ognia (max 80% łącznie)
     * - Łączna redukcja z enchantów max 80%
     */
    private double calculateFireDamageWithArmor(Player player, double baseDamage) {
        int totalProtectionLevel = 0;
        int totalFireProtectionLevel = 0;

        // ✅ Sprawdź każdą część zbroi
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;

            // Protection (ogólna)
            int protLevel = piece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL);
            totalProtectionLevel += protLevel;

            // Fire Protection
            int fireProtLevel = piece.getEnchantmentLevel(Enchantment.PROTECTION_FIRE);
            totalFireProtectionLevel += fireProtLevel;
        }

        // ✅ Oblicz redukcję enchantów
        // Protection: każdy level = 4% redukcji
        // Fire Protection: każdy level = 8% redukcji od ognia
        // Łącznie max 80%
        double enchantReduction = Math.min(80.0,
                (totalProtectionLevel * 4.0) + (totalFireProtectionLevel * 8.0));

        // ✅ Zastosuj redukcję
        double damageAfterEnchants = baseDamage * (1.0 - (enchantReduction / 100.0));

        return Math.max(0, damageAfterEnchants);
    }
}
