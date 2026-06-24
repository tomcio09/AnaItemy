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
 * Combat plugin ma najwyższy priorytet - nasze bary są całkowicie wstrzymane podczas walki.
 */
public class ActionBarManager {

    private final AnaItemy plugin;

    // Gracze w walce (timestamp ostatniego wykrycia combatu)
    private final Map<UUID, Long> combatActiveUntil = new ConcurrentHashMap<>();

    // Gracze u których już wyczyściliśmy nasz action bar (żeby nie spamować empty)
    private final Set<UUID> combatSuppressed = ConcurrentHashMap.newKeySet();

    // Nasze aktywne action bary (gracz -> źródło -> tekst)
    private final Map<UUID, Map<String, String>> pendingActionBars = new ConcurrentHashMap<>();

    private long resumeDelayMs;
    private BukkitTask tickTask;

    public ActionBarManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.resumeDelayMs = plugin.getItemsConfig().getActionBarResumeDelay() * 50L;
        startTickTask();
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // Cleanup wygasłych combat tagów
                combatActiveUntil.entrySet().removeIf(entry -> now >= entry.getValue());

                for (Map.Entry<UUID, Map<String, String>> entry : pendingActionBars.entrySet()) {
                    UUID playerId = entry.getKey();
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        pendingActionBars.remove(playerId);
                        combatSuppressed.remove(playerId);
                        continue;
                    }

                    boolean inCombat = isCombatActive(playerId);

                    if (inCombat) {
                        // ✅ Combat aktywny - wyczyść nasz bar RAZ i wstrzymaj wysyłanie
                        if (!combatSuppressed.contains(playerId)) {
                            player.sendActionBar(Component.empty());
                            combatSuppressed.add(playerId);
                        }
                        continue; // Nie wysyłaj naszych barów
                    }

                    // ✅ Combat nieaktywny - przywróć nasze bary
                    combatSuppressed.remove(playerId);

                    Map<String, String> bars = entry.getValue();
                    if (!bars.isEmpty()) {
                        String firstBar = bars.values().iterator().next();
                        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(firstBar));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // Co 0.5s
    }

    /**
     * ✅ Oznacz że combat jest aktywny dla gracza.
     */
    public void markCombatActive(Player player) {
        combatActiveUntil.put(player.getUniqueId(), System.currentTimeMillis() + resumeDelayMs);
    }

    public boolean isCombatActive(UUID playerId) {
        Long until = combatActiveUntil.get(playerId);
        return until != null && System.currentTimeMillis() < until;
    }

    public boolean isCombatActive(Player player) {
        return isCombatActive(player.getUniqueId());
    }

    /**
     * ✅ Rejestruje nasz action bar.
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
                if (!isCombatActive(player)) {
                    player.sendActionBar(Component.empty());
                }
            }
        }
    }

    public void clearAll(Player player) {
        pendingActionBars.remove(player.getUniqueId());
        combatActiveUntil.remove(player.getUniqueId());
        combatSuppressed.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
    }

    public void reload() {
        this.resumeDelayMs = plugin.getItemsConfig().getActionBarResumeDelay() * 50L;
    }

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();
        pendingActionBars.clear();
        combatActiveUntil.clear();
        combatSuppressed.clear();
    }
}
