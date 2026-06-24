package pl.anaheim.anaitemy.api;

import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;

/**
 * ✅ API dla innych pluginów do sprawdzania efektu Bloku Widmo.
 * 
 * Przykład użycia w pluginie na serca:
 * 
 * <pre>
 * import pl.anaheim.anaitemy.api.BlokWidmoAPI;
 * 
 * // Przed dodaniem serc graczowi:
 * if (BlokWidmoAPI.isAffected(player)) {
 *     player.sendMessage("§cNie możesz dodać serc podczas działania Bloku Widmo!");
 *     player.sendMessage("§7Efekt wygasa za: §e" + BlokWidmoAPI.getFormattedTimeLeft(player));
 *     return;
 * }
 * 
 * // Przed zmianą max health:
 * if (BlokWidmoAPI.isAvailable() && BlokWidmoAPI.isAffected(player)) {
 *     // Zablokuj zmianę max health
 *     event.setCancelled(true);
 *     return;
 * }
 * 
 * // Przy śmierci gracza - NIE MUSISZ nic robić!
 * // AnaItemy automatycznie zdejmuje efekt PRZED innymi pluginami (priority LOWEST).
 * // Plugin na serca zobaczy prawdziwy max health gracza.
 * </pre>
 * 
 * === WAŻNE: KOMPATYBILNOŚĆ Z PLUGINEM NA SERCA ===
 * 
 * Blok Widmo działa na zasadzie AttributeModifier na GENERIC_MAX_HEALTH.
 * Przy śmierci gracza, AnaItemy AUTOMATYCZNIE zdejmuje modifier
 * z priorytetem LOWEST (czyli PRZED pluginem na serca).
 * 
 * Dzięki temu:
 * 1. Gracz umiera z efektem bloku widmo
 * 2. AnaItemy zdejmuje modifier (max health wraca do normy)
 * 3. Plugin na serca przetwarza śmierć i widzi prawdziwy max health
 * 4. Plugin na serca zabiera serce normalnie
 * 
 * === WAŻNE: KOMPATYBILNOŚĆ Z KOSTIUMAMI ===
 * 
 * Jeśli kostium zabiera podwójną ilość serc:
 * - Blok widmo NIE ingeruje w tę mechanikę
 * - Blok widmo TYLKO obniża max health o stałą wartość
 * - Przy śmierci modifier jest zdejmowany ZANIM kostium/serca zadziałają
 * - Nie ma konfliktu
 */
public class BlokWidmoAPI {

    /**
     * ✅ Sprawdza czy plugin AnaItemy jest dostępny.
     * @return true jeśli plugin jest załadowany
     */
    public static boolean isAvailable() {
        try {
            return AnaItemy.getInstance() != null
                    && AnaItemy.getInstance().getBlokWidmoManager() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ✅ Sprawdza czy gracz jest zainfekowany efektem Bloku Widmo.
     * @param player Gracz do sprawdzenia
     * @return true jeśli gracz ma obniżony limit serc
     */
    public static boolean isAffected(Player player) {
        if (!isAvailable()) return false;
        return AnaItemy.getInstance().getBlokWidmoManager().isAffected(player);
    }

    /**
     * ✅ Zwraca ile sekund pozostało do końca efektu.
     * @param player Gracz
     * @return Sekundy do końca efektu, lub 0 jeśli brak efektu
     */
    public static int getRemainingSeconds(Player player) {
        if (!isAvailable()) return 0;
        return AnaItemy.getInstance().getBlokWidmoManager().getRemainingSeconds(player);
    }

    /**
     * ✅ Zwraca ile HP (nie serc, lecz HP) zostało zabrane z max health.
     * 20 HP = 10 serduszek
     * @param player Gracz
     * @return Ilość zabranego HP, lub 0 jeśli brak efektu
     */
    public static double getReducedHealth(Player player) {
        if (!isAvailable()) return 0;
        return AnaItemy.getInstance().getBlokWidmoManager().getReducedHealth(player);
    }

    /**
     * ✅ Sformatowany czas pozostały (np. "1m20s", "45s").
     * @param player Gracz
     * @return Sformatowany czas
     */
    public static String getFormattedTimeLeft(Player player) {
        int seconds = getRemainingSeconds(player);
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m" + String.format("%02d", secs) + "s";
    }
}
