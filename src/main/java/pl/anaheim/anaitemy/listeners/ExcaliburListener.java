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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExcaliburListener implements Listener {

    private final AnaItemy plugin;
    private final Map<UUID, Map<UUID, Long>> killCooldowns = new ConcurrentHashMap<>();
    private static final long KILL_COOLDOWN_MS = 10 * 60 * 1000;

    public ExcaliburListener(AnaItemy plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;

        ItemStack itemInHand = killer.getInventory().getItemInMainHand();
        if (!Excalibur.isExcalibur(itemInHand)) return;

        int maxKills = plugin.getConfig().getInt("excalibur.max-kills", 100);
        int currentKills = Excalibur.getKillsFromItem(itemInHand);

        if (currentKills >= maxKills) return;

        UUID killerUUID = killer.getUniqueId();
        UUID victimUUID = victim.getUniqueId();

        if (!canCountKill(killerUUID, victimUUID)) return;

        int newKills = currentKills + 1;
        ItemStack updatedItem = Excalibur.updateKills(itemInHand, newKills, maxKills);
        killer.getInventory().setItemInMainHand(updatedItem);
        markKill(killerUUID, victimUUID);
    }

    private boolean canCountKill(UUID killer, UUID victim) {
        if (!killCooldowns.containsKey(killer)) return true;
        Map<UUID, Long> victimMap = killCooldowns.get(killer);
        if (!victimMap.containsKey(victim)) return true;
        long lastKillTime = victimMap.get(victim);
        long timePassed = System.currentTimeMillis() - lastKillTime;
        return timePassed >= KILL_COOLDOWN_MS;
    }

    private void markKill(UUID killer, UUID victim) {
        killCooldowns.computeIfAbsent(killer, k -> new ConcurrentHashMap<>())
                .put(victim, System.currentTimeMillis());
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (UUID killer : new java.util.HashSet<>(killCooldowns.keySet())) {
                    Map<UUID, Long> victimMap = killCooldowns.get(killer);
                    if (victimMap == null) continue;
                    victimMap.entrySet().removeIf(entry -> now - entry.getValue() >= KILL_COOLDOWN_MS);
                    if (victimMap.isEmpty()) killCooldowns.remove(killer);
                }
            }
        }.runTaskTimer(plugin, 6000L, 6000L);
    }
}
