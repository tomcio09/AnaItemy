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
 * Wszystkie action bary pluginu przechodzą przez ten manager.
 * Podczas combatu - nie wysyłamy niczego (Antylogout ma pełną kontrolę).
 * Gdy jest kilka naszych barów (elytra + hydroklatka) - łączymy je w jeden.
 *
 * ✅ Kolejność jest stała:
 * 1. elytra
 * 2. hydroklatka
 * 3. reszta
 */
public class ActionBarManager {

    private final AnaItemy plugin;

    // Nasze aktywne action bary (gracz -> źródło -> tekst)
    private final Map<UUID, Map<String, String>> pendingActionBars = new ConcurrentHashMap<>();

    // Gracze którzy są w combacie
    private final Set<UUID> inCombat = ConcurrentHashMap.newKeySet();

    private BukkitTask tickTask;

    public ActionBarManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    /**
     * ✅ Co sekundę sprawdzaj combat i wysyłaj nasze action bary.
     * Jeśli jest kilka barów - łącz je w jeden z separatorem " &8| ".
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
                        // ✅ GRACZ W COMBATIE - Nie wysyłamy NICZEGO
                        inCombat.add(playerId);
                        continue;
                    }

                    // ✅ GRACZ WYSZEDŁ Z COMBATU
                    if (inCombat.contains(playerId)) {
                        inCombat.remove(playerId);
                    }

                    Map<String, String> bars = entry.getValue();
                    if (bars.isEmpty()) continue;

                    // ✅ STAŁA KOLEJNOŚĆ: elytra zawsze po lewej, hydroklatka po prawej
                    List<Map.Entry<String, String>> sortedBars = new ArrayList<>(bars.entrySet());
                    sortedBars.sort(Comparator.comparingInt(bar -> getSourcePriority(bar.getKey())));

                    StringBuilder combined = new StringBuilder();
                    boolean first = true;

                    for (Map.Entry<String, String> bar : sortedBars) {
                        String barText = bar.getValue();
                        if (barText == null || barText.isEmpty()) continue;

                        if (!first) {
                            combined.append(" &8| ");
                        }
                        combined.append(barText);
                        first = false;
                    }

                    if (combined.isEmpty()) continue;

                    player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(combined.toString()));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // ✅ Co 10 ticków (0.5s)
    }

    private int getSourcePriority(String source) {
        if (source == null) return Integer.MAX_VALUE;

        return switch (source.toLowerCase(Locale.ROOT)) {
            case "elytra" -> 0;
            case "hydroklatka" -> 1;
            default -> 100;
        };
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
