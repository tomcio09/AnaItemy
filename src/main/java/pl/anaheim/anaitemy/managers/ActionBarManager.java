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

public class ActionBarManager {

    private final AnaItemy plugin;

    private final Map<UUID, Map<String, String>> pendingActionBars = new ConcurrentHashMap<>();
    private final Set<UUID> inCombat = ConcurrentHashMap.newKeySet();

    // ✅ Licznik tickow do alternowania barow podczas combatu
    private int globalTick = 0;

    private BukkitTask tickTask;

    public ActionBarManager(AnaItemy plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    private void startTickTask() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                globalTick++;
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
                        inCombat.add(playerId);

                        // ✅ Podczas combatu: co 1 sekunde (20 tickow) pokaz nasze bary
                        // przez pol sekundy, potem antylogout wraca
                        // globalTick co 10 tickow = 0.5s
                        // Pokaz nasze bary co drugie wywolanie (co 1s na 0.5s)
                        Map<String, String> bars = entry.getValue();
                        if (bars != null && !bars.isEmpty()) {
                            // Co 2 cykle (co 1s) pokaz nasze bary na 0.5s
                            if (globalTick % 2 == 0) {
                                List<Map.Entry<String, String>> sortedBars = new ArrayList<>(bars.entrySet());
                                sortedBars.sort(Comparator.comparingInt(bar -> getSourcePriority(bar.getKey())));

                                StringBuilder combined = new StringBuilder();
                                boolean first = true;
                                for (Map.Entry<String, String> bar : sortedBars) {
                                    String barText = bar.getValue();
                                    if (barText == null || barText.isEmpty()) continue;
                                    if (!first) combined.append(" &8| ");
                                    combined.append(barText);
                                    first = false;
                                }

                                if (!combined.isEmpty()) {
                                    player.sendActionBar(LegacyComponentSerializer.legacyAmpersand()
                                            .deserialize(combined.toString()));
                                }
                            }
                            // W innym cyklu — antylogout sam wysle swoj action bar
                        }

                        continue;
                    }

                    // Gracz wyszedl z combatu
                    if (inCombat.contains(playerId)) {
                        inCombat.remove(playerId);
                    }

                    // Normalne wyswietlanie (bez combatu)
                    Map<String, String> bars = entry.getValue();
                    if (bars.isEmpty()) continue;

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
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private int getSourcePriority(String source) {
        if (source == null) return Integer.MAX_VALUE;

        return switch (source.toLowerCase(Locale.ROOT)) {
            case "elytra" -> 0;
            case "hydroklatka" -> 1;
            default -> 100;
        };
    }

    public void setActionBar(Player player, String source, String message) {
        pendingActionBars.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>())
                .put(source, message);
    }

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
    }

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();
        pendingActionBars.clear();
        inCombat.clear();
    }
}
