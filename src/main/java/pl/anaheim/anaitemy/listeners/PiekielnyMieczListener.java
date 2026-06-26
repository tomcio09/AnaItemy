package pl.anaheim.anaitemy.listeners;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.PiekielnyMieczItem;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PiekielnyMieczListener implements Listener {

    private final AnaItemy plugin;

    // UUID -> czas wygaśnięcia piekielnego ognia
    private final Map<UUID, Long> hellFirePlayers = new ConcurrentHashMap<>();

    // UUID -> zapisany efekt fire resistance (do przywrócenia po wygaśnięciu ognia)
    private final Map<UUID, SavedFireResistance> savedFireResistance = new ConcurrentHashMap<>();

    public PiekielnyMieczListener(AnaItemy plugin) {
        this.plugin = plugin;

        // ✅ Co 2 ticki wymuszaj palenie + zarządzaj fire resistance
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<UUID, Long> entry : new ArrayList<>(hellFirePlayers.entrySet())) {
                    UUID uuid = entry.getKey();

                    Player victim = org.bukkit.Bukkit.getPlayer(uuid);
                    if (victim == null || !victim.isOnline() || victim.isDead()) {
                        restoreFireResistance(uuid);
                        hellFirePlayers.remove(uuid);
                        continue;
                    }

                    if (now >= entry.getValue()) {
                        // ✅ Piekielny ogień wygasł - przywróć fire resistance
                        restoreFireResistance(uuid);
                        hellFirePlayers.remove(uuid);
                        victim.setFireTicks(0); // Zgaś ogień
                        continue;
                    }

                    // ✅ Jeśli gracz dostał fire resistance (np. od mikstury) - zabierz i zapisz
                    if (victim.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                        PotionEffect currentFR = victim.getPotionEffect(PotionEffectType.FIRE_RESISTANCE);
                        if (currentFR != null) {
                            // Zapisz tylko jeśli nie mamy już zapisanego lub nowy jest dłuższy
                            SavedFireResistance saved = savedFireResistance.get(uuid);
                            if (saved == null || currentFR.getDuration() > 10) {
                                savedFireResistance.put(uuid, new SavedFireResistance(
                                        currentFR.getAmplifier(),
                                        currentFR.getDuration(),
                                        now
                                ));
                            }
                        }
                        victim.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                    }

                    // ✅ Wymuszaj palenie
                    if (victim.getFireTicks() <= 1) {
                        victim.setFireTicks(40);
                    }

                    // ✅ W wodzie nadal pali
                    if (victim.isInWater()) {
                        victim.setFireTicks(40);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
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

        // ✅ Jeśli gracz ma fire resistance - zabierz i zapisz
        if (victim.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            PotionEffect currentFR = victim.getPotionEffect(PotionEffectType.FIRE_RESISTANCE);
            if (currentFR != null) {
                savedFireResistance.put(victim.getUniqueId(), new SavedFireResistance(
                        currentFR.getAmplifier(),
                        currentFR.getDuration(),
                        System.currentTimeMillis()
                ));
            }
            victim.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }

        // ✅ Natychmiast podpal
        victim.setFireTicks(fireDuration * 20);
    }

    /**
     * ✅ Przywraca fire resistance po wygaśnięciu piekielnego ognia.
     * Oblicza ile czasu zostało z oryginalnego efektu.
     */
    private void restoreFireResistance(UUID uuid) {
        SavedFireResistance saved = savedFireResistance.remove(uuid);
        if (saved == null) return;

        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        // ✅ Oblicz ile ticków zostało z oryginalnego efektu
        long elapsedMs = System.currentTimeMillis() - saved.savedAt;
        int elapsedTicks = (int) (elapsedMs / 50);
        int remainingTicks = saved.duration - elapsedTicks;

        if (remainingTicks > 20) { // Minimum 1 sekunda
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE,
                    remainingTicks,
                    saved.amplifier,
                    false, true, true
            ));
        }
    }

    /**
     * ✅ Sprawdza czy gracz jest podpalony piekielnym ogniem.
     */
    public boolean isHellFired(Player player) {
        return hellFirePlayers.containsKey(player.getUniqueId());
    }

    // ==================== INNER CLASS ====================

    private static class SavedFireResistance {
        final int amplifier;
        final int duration; // w tickach
        final long savedAt; // czas zapisu w ms

        SavedFireResistance(int amplifier, int duration, long savedAt) {
            this.amplifier = amplifier;
            this.duration = duration;
            this.savedAt = savedAt;
        }
    }
}
