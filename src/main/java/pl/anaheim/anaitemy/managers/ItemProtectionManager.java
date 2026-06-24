package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ✅ Manager systemu ochrony przed eventówkami.
 * 
 * Każdy item ma swoją osobną ochronę - gracz może mieć jednocześnie:
 * - Ochronę przed różdżką (4s)
 * - Ochronę przed wędką (4s)
 * - Ochronę przed elytrą (4s)
 */
public class ItemProtectionManager {

    private final AnaItemy plugin;

    // Mapa: UUID gracza -> (ID itemu -> czas wygaśnięcia ochrony w ms)
    private final Map<UUID, Map<String, Long>> protections = new ConcurrentHashMap<>();

    public ItemProtectionManager(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * Sprawdza czy gracz jest chroniony przed danym itemem.
     * 
     * @param player Gracz
     * @param itemId ID itemu (np. "rozdzka-iluzjonisty", "wedka-nielota")
     * @return true jeśli gracz jest chroniony
     */
    public boolean isProtected(Player player, String itemId) {
        if (!plugin.getItemsConfig().isItemProtectionEnabled()) {
            return false;
        }

        if (!plugin.getItemsConfig().doesItemRespectProtection(itemId)) {
            return false;
        }

        Map<String, Long> playerProtections = protections.get(player.getUniqueId());
        if (playerProtections == null) return false;

        Long expirationTime = playerProtections.get(itemId);
        if (expirationTime == null) return false;

        return System.currentTimeMillis() < expirationTime;
    }

    /**
     * Zwraca ile sekund pozostało do końca ochrony.
     * 
     * @param player Gracz
     * @param itemId ID itemu
     * @return Sekundy (zaokrąglone w górę), lub 0 jeśli brak ochrony
     */
    public int getRemainingSeconds(Player player, String itemId) {
        Map<String, Long> playerProtections = protections.get(player.getUniqueId());
        if (playerProtections == null) return 0;

        Long expirationTime = playerProtections.get(itemId);
        if (expirationTime == null) return 0;

        long remaining = expirationTime - System.currentTimeMillis();
        if (remaining <= 0) return 0;

        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Nakłada ochronę na gracza przed danym itemem.
     * 
     * @param player Gracz
     * @param itemId ID itemu
     */
    public void applyProtection(Player player, String itemId) {
        if (!plugin.getItemsConfig().isItemProtectionEnabled()) {
            return;
        }

        if (!plugin.getItemsConfig().doesItemRespectProtection(itemId)) {
            return;
        }

        int durationSeconds = plugin.getItemsConfig().getItemProtectionDuration();
        long expirationTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        protections.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(itemId, expirationTime);
    }

    /**
     * Pokazuje atakującemu wiadomość o nieudanym ataku (gracz chroniony).
     * 
     * @param attacker Atakujący
     * @param itemId ID itemu
     * @param secondsLeft Sekundy do końca ochrony
     */
    public void notifyAttacker(Player attacker, String itemId, int secondsLeft) {
        if (!plugin.getItemsConfig().shouldNotifyAttacker(itemId)) {
            return;
        }

        String titleText = plugin.getItemsConfig().getProtectionTitle(itemId);
        String subtitleText = plugin.getItemsConfig().getProtectionSubtitle(itemId);

        // ✅ Jeśli < 1s → wyświetl "0s"
        String secondsDisplay = secondsLeft < 1 ? "0s" : secondsLeft + "s";
        subtitleText = subtitleText.replace("{seconds_left}", secondsDisplay);

        attacker.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(titleText),
                LegacyComponentSerializer.legacyAmpersand().deserialize(subtitleText),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(250)
                )
        ));
    }

    /**
     * Usuwa ochronę gracza (np. przy wylogowaniu).
     */
    public void removeProtection(Player player) {
        protections.remove(player.getUniqueId());
    }

    /**
     * Cleanup przy wyłączeniu pluginu.
     */
    public void cleanup() {
        protections.clear();
    }
}
