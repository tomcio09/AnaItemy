package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ✅ Centralny manager action barów.
 * 
 * Gdy combat action bar jest aktywny, nasze action bary są całkowicie wstrzymane.
 */
public class ActionBarManager {

    private final AnaItemy plugin;

    // Gracze którzy aktualnie są w walce (ich action bar z combat pluginu ma priorytet)
    private final Map<UUID, Long> combatActionBarActive = new ConcurrentHashMap<>();

    // Nasze aktywne action bary (gracz -> źródło -> tekst)
    private final Map<UUID, Map<String, String>> pendingActionBars = new ConcurrentHashMap<>();

    // Czas opóźnienia po walce (w ms)
    private long resumeDelayMs;

    private BukkitTask tickTask;

    public ActionBarManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.resumeDelayMs = plugin.getItemsConfig().getActionBarResumeDelay() * 50L;
        startTickTask();
    }

    /**
     * ✅ Task który co 1 sekundę aktualizuje action bary (nie co 0.5s - mniej migania).
     */
    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // Cleanup wygasłych combat tagów
                combatActionBarActive.entrySet().removeIf(entry ->
                        now - entry.getValue() > resumeDelayMs);

                // Wyświetl nasze action bary dla graczy nie w walce
                for (Map.Entry<UUID, Map<String, String>> entry : pendingActionBars.entrySet()) {
                    UUID playerId = entry.getKey();

                    // Jeśli gracz jest w walce - NIE WYSYŁAJ (całkowicie wstrzymane)
                    if (isCombatActive(playerId)) {
                        continue;
                    }

                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        pendingActionBars.remove(playerId);
                        continue;
                    }

                    Map<String, String> bars = entry.getValue();
                    if (!bars.isEmpty()) {
                        String firstBar = bars.values().iterator().next();
                        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(firstBar));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // ✅ ZMIENIONO: Co 20 ticków (1 sekunda) zamiast 10
    }

    /**
     * ✅ Oznacz że combat plugin wysłał action bar do gracza.
     */
    public void markCombatActive(Player player) {
        combatActionBarActive.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Sprawdza czy combat action bar jest aktywny.
     */
    public boolean isCombatActive(UUID playerId) {
        Long lastCombat = combatActionBarActive.get(playerId);
        if (lastCombat == null) return false;
        return System.currentTimeMillis() - lastCombat <= resumeDelayMs;
    }

    public boolean isCombatActive(Player player) {
        return isCombatActive(player.getUniqueId());
    }

    /**
     * ✅ Rejestruje nasz action bar do wyświetlenia.
     */
    public void setActionBar(Player player, String source, String message) {
        pendingActionBars.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>())
                .put(source, message);
    }

    /**
     * ✅ Usuwa nasz action bar ze źródła.
     */
    public void removeActionBar(Player player, String source) {
        Map<String, String> bars = pendingActionBars.get(player.getUniqueId());
        if (bars != null) {
            bars.remove(source);
            if (bars.isEmpty()) {
                pendingActionBars.remove(player.getUniqueId());
                // ✅ TYLKO jeśli nie ma combat bara - wyczyść
                if (!isCombatActive(player)) {
                    player.sendActionBar(Component.empty());
                }
            }
        }
    }

    public boolean hasActionBar(Player player, String source) {
        Map<String, String> bars = pendingActionBars.get(player.getUniqueId());
        return bars != null && bars.containsKey(source);
    }

    public void clearAll(Player player) {
        pendingActionBars.remove(player.getUniqueId());
        combatActionBarActive.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    public void reload() {
        this.resumeDelayMs = plugin.getItemsConfig().getActionBarResumeDelay() * 50L;
    }

    public void cleanup() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        pendingActionBars.clear();
        combatActionBarActive.clear();
    }
}
