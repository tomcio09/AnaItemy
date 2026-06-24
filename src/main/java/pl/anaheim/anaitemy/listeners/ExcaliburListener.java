package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.Excalibur;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExcaliburListener implements Listener {

    private final AnaItemy plugin;

    // ✅ Cooldown map: killer UUID -> (victim UUID -> timestamp ostatniego zabójstwa)
    private final Map<UUID, Map<UUID, Long>> killCooldowns = new ConcurrentHashMap<>();

    // ✅ 10 minut cooldown (w milisekundach)
    private static final long KILL_COOLDOWN_MS = 10 * 60 * 1000;

    public ExcaliburListener(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Liczy zabójstwa TYLKO GRACZY (nie mobów) z anty-farmem.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Sprawdź czy zabójcą jest gracz
        if (killer == null) return;

        // Sprawdź czy gracz trzyma Excalibur w głównej ręce
        ItemStack itemInHand = killer.getInventory().getItemInMainHand();
        if (!Excalibur.isExcalibur(itemInHand)) return;

        int maxKills = plugin.getConfig().getInt("excalibur.max-kills", 100);
        int currentKills = Excalibur.getKillsFromItem(itemInHand);

        // Jeśli już na limicie - nic nie rób
        if (currentKills >= maxKills) return;

        // ✅ ANTY-FARM: Sprawdź czy można zliczyć zabójstwo
        UUID killerUUID = killer.getUniqueId();
        UUID victimUUID = victim.getUniqueId();

        if (!canCountKill(killerUUID, victimUUID)) {
            // ✅ Brak wiadomości - zajmuje się tym inny plugin
            return;
        }

        // ✅ Zlicz zabójstwo
        int newKills = currentKills + 1;

        // Zaktualizuj kills
        ItemStack updatedItem = Excalibur.updateKills(itemInHand, newKills, maxKills);

        // Ustaw zaktualizowany item w ręce
        killer.getInventory().setItemInMainHand(updatedItem);

        // ✅ Oznacz zabójstwo (cooldown 10 minut)
        markKill(killerUUID, victimUUID);

        // ✅ DEBUG LOG
        plugin.getLogger().info("[Excalibur] " + killer.getName() +
                " zabil " + victim.getName() + " (" + newKills + "/" + maxKills + " kills)");
    }

    /**
     * Sprawdza czy można zliczyć zabójstwo (czy minęło 10 minut od ostatniego).
     */
    private boolean canCountKill(UUID killer, UUID victim) {
        if (!killCooldowns.containsKey(killer)) return true;

        Map<UUID, Long> victimMap = killCooldowns.get(killer);
        if (!victimMap.containsKey(victim)) return true;

        long lastKillTime = victimMap.get(victim);
        long timePassed = System.currentTimeMillis() - lastKillTime;

        return timePassed >= KILL_COOLDOWN_MS;
    }

    /**
     * Oznacza zabójstwo (zapisuje timestamp).
     */
    private void markKill(UUID killer, UUID victim) {
        killCooldowns.computeIfAbsent(killer, k -> new ConcurrentHashMap<>())
                .put(victim, System.currentTimeMillis());
    }

    /**
     * Cleanup task - usuwa stare cooldowny co 5 minut.
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (UUID killer : new java.util.HashSet<>(killCooldowns.keySet())) {
                    Map<UUID, Long> victimMap = killCooldowns.get(killer);
                    if (victimMap == null) continue;

                    victimMap.entrySet().removeIf(entry ->
                            now - entry.getValue() >= KILL_COOLDOWN_MS);

                    if (victimMap.isEmpty()) {
                        killCooldowns.remove(killer);
                    }
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }
}
