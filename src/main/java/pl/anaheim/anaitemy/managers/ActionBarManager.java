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
 * ✅ Uproszczony manager action barów.
 * Podczas combatu - kompletnie czyszczamy nasze bary i NIE wysyłamy nic.
 * Po combatu - wznowienie naszych barów.
 */
public class ActionBarManager {

    private final AnaItemy plugin;

    // Nasze aktywne action bary (gracz -> źródło -> tekst)
    private final Map<UUID, Map<String, String>> pendingActionBars = new ConcurrentHashMap<>();

    // Gracze którzy byli w combatie - nie pokazujemy naszych barów
    private final Set<UUID> inCombat = ConcurrentHashMap.newKeySet();

    private BukkitTask tickTask;

    public ActionBarManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * ✅ Co sekundę sprawdzaj combat i wysyłaj nasze action bary TYLKO gdy nie ma combatu.
     */
    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                CombatIntegrationManager combat = plugin.getCombatIntegrationManager();

                for (Map.Entry<UUID, Map<String, String>> entry : new ArrayList<>(pendingActionBars.entrySet())) {
                    UUID playerId = entry.getKey();
                    Player player = Bukkit.getPlayer(playerId);

                    if (player == null || !player.isOnline()) {
                        pendingActionBars.remove(playerId);
                        inCombat.remove(playerId);
                        continue;
                    }

                    boolean playerInCombat = combat.isInCombat(player);

                    if (playerInCombat) {
                        // ✅ GRACZ W COMBATIE - Nie wysyłamy NICZEGO (Antylogout ma pełną kontrolę)
                        inCombat.add(playerId);
                        continue;
                    }

                    // ✅ GRACZ NIE W COMBATIE - Wysyłaj nasze action bary
                    if (inCombat.contains(playerId)) {
                        // Dopiero co wyszedł z combatu - wyczyść action bar
                        player.sendActionBar(Component.empty());
                        inCombat.remove(playerId);
                    }

                    Map<String, String> bars = entry.getValue();
                    if (!bars.isEmpty()) {
                        String firstBar = bars.values().iterator().next();
                        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(firstBar));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Co 1 sekundę
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
                // Wyczyść tylko jeśli nie ma combatu
                if (!plugin.getCombatIntegrationManager().isInCombat(player)) {
                    player.sendActionBar(Component.empty());
                }
            }
        }
    }

    public void clearAll(Player player) {
        pendingActionBars.remove(player.getUniqueId());
        inCombat.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    public void reload() {
        // Nic do przeładowania
    }

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();
        pendingActionBars.clear();
        inCombat.clear();
    }
}
