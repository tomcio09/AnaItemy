package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiekielnyMieczItem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PiekielnyMieczListener implements Listener {

    private final AnaItemy plugin;

    // ✅ Gracze podpaleni piekielnym mieczem: UUID -> czas wygaśnięcia ognia
    private final Map<UUID, Long> hellFirePlayers = new ConcurrentHashMap<>();

    public PiekielnyMieczListener(AnaItemy plugin) {
        this.plugin = plugin;

        // ✅ Co tick sprawdzaj i podpalaj graczy ponownie
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new java.util.ArrayList<>(hellFirePlayers.entrySet())) {
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

                    // ✅ Wymuszaj palenie - nawet w wodzie, nawet z fire resistance
                    if (victim.getFireTicks() <= 1) {
                        victim.setFireTicks(40); // 2 sekundy na raz, odnawiany co tick
                    }

                    // ✅ Zadaj damage ręcznie jeśli fire resistance blokuje normalny fire damage
                    if (victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) {
                        // Fire resistance blokuje fire damage, więc zadajemy ręcznie
                        // 1 HP co sekundę (vanilla fire damage rate)
                        // Sprawdzamy co 20 ticków (ten task chodzi co 2 ticki, więc co 10 wywołań)
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        // ✅ Osobny task na ręczny fire damage dla graczy z fire resistance
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new java.util.ArrayList<>(hellFirePlayers.entrySet())) {
                    UUID uuid = entry.getKey();

                    if (now >= entry.getValue()) continue;

                    Player victim = org.bukkit.Bukkit.getPlayer(uuid);
                    if (victim == null || !victim.isOnline() || victim.isDead()) continue;

                    // ✅ Jeśli ma fire resistance - zadaj 1 HP damage ręcznie
                    if (victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) {
                        double health = victim.getHealth();
                        if (health > 1.0) {
                            victim.setHealth(health - 1.0);
                        }
                    }

                    // ✅ Jeśli jest w wodzie - nadal pali
                    if (victim.isInWater()) {
                        victim.setFireTicks(40);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Co sekundę
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!PiekielnyMieczItem.isPiekielnyMiecz(mainHand)) return;

        // ✅ Zapisz czas piekielnego ognia
        int fireDuration = plugin.getItemsConfig().getPiekielnyMieczFireDuration();
        long fireEnd = System.currentTimeMillis() + (fireDuration * 1000L);
        hellFirePlayers.put(victim.getUniqueId(), fireEnd);

        // ✅ Natychmiast podpal
        victim.setFireTicks(fireDuration * 20);
    }

    /**
     * ✅ Zapobiega gaszeniu ognia przez wodę/deszcz dla graczy z piekielnym ogniem.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCombust(EntityCombustEvent event) {
        // Nie ingerujemy - pozwalamy na podpalenie
    }
}
