package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.Excalibur;

import java.util.HashMap;
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
            // Zbyt szybko zabił tego samego gracza - nie licz
            killer.sendMessage(plugin.getItemsConfig().getExcaliburMessageTooFast()
                    .replace("{victim}", victim.getName())
                    .replace("{time}", String.valueOf(getRemainingCooldown(killerUUID, victimUUID))));
            return;
        }

        // ✅ Zlicz zabójstwo
        int newKills = currentKills + 1;

        // Zaktualizuj kills (edytuje tylko konkretne linie)
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
        if (!killCooldowns.containsKey(killer)) {
            return true; // Pierwszy raz zabija tego gracza
        }

        Map<UUID, Long> victimMap = killCooldowns.get(killer);
        if (!victimMap.containsKey(victim)) {
            return true; // Pierwszy raz zabija tego gracza
        }

        long lastKillTime = victimMap.get(victim);
        long timePassed = System.currentTimeMillis() - lastKillTime;

        return timePassed >= KILL_COOLDOWN_MS; // True jeśli minęło >= 10 minut
    }

    /**
     * Oznacza zabójstwo (zapisuje timestamp).
     */
    private void markKill(UUID killer, UUID victim) {
        killCooldowns.computeIfAbsent(killer, k -> new ConcurrentHashMap<>())
                .put(victim, System.currentTimeMillis());
    }

    /**
     * Zwraca pozostały czas cooldownu w sekundach.
     */
    private int getRemainingCooldown(UUID killer, UUID victim) {
        if (!killCooldowns.containsKey(killer)) return 0;
        
        Map<UUID, Long> victimMap = killCooldowns.get(killer);
        if (!victimMap.containsKey(victim)) return 0;

        long lastKillTime = victimMap.get(victim);
        long timePassed = System.currentTimeMillis() - lastKillTime;
        long remaining = KILL_COOLDOWN_MS - timePassed;

        return (int) Math.max(0, remaining / 1000); // Sekundy
    }

    /**
     * ✅ Cleanup task - usuwa stare cooldowny co 5 minut (żeby nie zapychać pamięci).
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                int cleaned = 0;

                for (UUID killer : killCooldowns.keySet()) {
                    Map<UUID, Long> victimMap = killCooldowns.get(killer);
                    
                    // Usuń wygasłe cooldowny
                    victimMap.entrySet().removeIf(entry -> 
                            now - entry.getValue() >= KILL_COOLDOWN_MS);
                    
                    cleaned += victimMap.size();

                    // Jeśli mapa jest pusta - usuń gracza
                    if (victimMap.isEmpty()) {
                        killCooldowns.remove(killer);
                    }
                }

                if (cleaned > 0) {
                    plugin.getLogger().info("[Excalibur] Cleanup: usunieto " + cleaned + " starych cooldownow");
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Co 5 minut (6000 ticków)
    }
}
