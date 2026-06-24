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
 * Priorytety:
 * 1. Combat plugin (najwyższy) - nasze action bary znikają
 * 2. Nasze action bary (normalny) - Hydro Klatka, Wzmocniona Elytra
 * 
 * Gdy combat action bar jest aktywny, nasze action bary są wstrzymane.
 * Po zakończeniu walki, nasze action bary wracają po configurowalnym opóźnieniu.
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
        this.resumeDelayMs = plugin.getItemsConfig().getActionBarResumeDelay() * 50L; // ticki -> ms
        startTickTask();
    }

    /**
     * ✅ Task który co tick aktualizuje action bary.
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

                    // Jeśli gracz jest w walce - nie wyświetlaj naszych
                    if (isCombatActive(playerId)) continue;

                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        pendingActionBars.remove(playerId);
                        continue;
                    }

                    // Wyświetl pierwszy aktywny action bar (priorytet: kolejność dodania)
                    Map<String, String> bars = entry.getValue();
                    if (!bars.isEmpty()) {
                        String firstBar = bars.values().iterator().next();
                        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(firstBar));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Co 10 ticków (0.5s)
    }

    /**
     * ✅ Oznacz że combat plugin wysłał action bar do gracza.
     * Nasze action bary zostaną wstrzymane.
     */
    public void markCombatActive(Player player) {
        combatActionBarActive.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Sprawdza czy combat action bar jest aktywny (lub czekamy na resume delay).
     */
    public boolean isCombatActive(UUID playerId) {
        Long lastCombat = combatActionBarActive.get(playerId);
        if (lastCombat == null) return false;
        return System.currentTimeMillis() - lastCombat <= resumeDelayMs;
    }

    /**
     * Sprawdza czy combat action bar jest aktywny dla gracza.
     */
    public boolean isCombatActive(Player player) {
        return isCombatActive(player.getUniqueId());
    }

    /**
     * ✅ Rejestruje nasz action bar do wyświetlenia.
     * Źródło identyfikuje który system go wysyła (np. "hydroklatka", "elytra").
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
                // Wyczyść action bar
                player.sendActionBar(Component.empty());
            }
        }
    }

    /**
     * ✅ Sprawdza czy gracz ma aktywny action bar z danego źródła.
     */
    public boolean hasActionBar(Player player, String source) {
        Map<String, String> bars = pendingActionBars.get(player.getUniqueId());
        return bars != null && bars.containsKey(source);
    }

    /**
     * ✅ Czyści wszystkie action bary gracza.
     */
    public void clearAll(Player player) {
        pendingActionBars.remove(player.getUniqueId());
        combatActionBarActive.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    /**
     * Przeładuj config.
     */
    public void reload() {
        this.resumeDelayMs = plugin.getItemsConfig().getActionBarResumeDelay() * 50L;
    }

    /**
     * Cleanup.
     */
    public void cleanup() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        pendingActionBars.clear();
        combatActionBarActive.clear();
    }
}
