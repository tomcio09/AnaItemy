package pl.anaheim.anaitemy.utils;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ✅ Helper do obliczania redukcji damage od eventówek na podstawie lore zbroi.
 *
 * System działa tak:
 * - Każda część zbroi może mieć w lore linię zawierającą słowo "eventówki"
 * - W tej linii musi być liczba w formacie {X}% na kolorze &6, &f lub &e
 * - Jeśli w tej samej linii jest druga liczba na kolorze &a - ignorujemy ją
 * - Sumujemy procenty ze wszystkich 4 części zbroi
 * - Damage od eventówek jest redukowany o tę sumę
 *
 * Przykład lore: ' &8» &7Redukcja od &6eventówki&7: &e6% &a(+2%)'
 * → wyciągamy 6%, ignorujemy 2%
 */
public class ArmorReductionHelper {

    // ✅ Pasuje do kolorów &6, &f, &e a po nich liczby z %
    // Ignoruje liczby po &a (drugi bonus)
    private static final Pattern PERCENT_PATTERN = Pattern.compile(
            "§[6fe](\\d+(?:\\.\\d+)?)%"
    );

    // ✅ Pasuje do &a{liczba}% - te ignorujemy
    private static final Pattern IGNORE_PATTERN = Pattern.compile(
            "§a(\\d+(?:\\.\\d+)?)%"
    );

    /**
     * Oblicza łączną redukcję damage od eventówek dla gracza.
     * Sprawdza lore wszystkich 4 części zbroi.
     *
     * @param player Gracz którego zbroja jest sprawdzana
     * @return Redukcja w procentach (0.0 - 100.0), np. 12.5 = 12.5% mniej damage
     */
    public static double getTotalEventReduction(Player player) {
        if (player == null) return 0.0;

        double total = 0.0;

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            total += getReductionFromItem(piece);
        }

        return Math.min(total, 100.0);
    }

    /**
     * Oblicza redukcję z jednej części zbroi.
     */
    private static double getReductionFromItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0.0;
        if (!item.hasItemMeta()) return 0.0;

        var meta = item.getItemMeta();
        if (meta == null || meta.lore() == null) return 0.0;

        for (var loreLine : meta.lore()) {
            if (loreLine == null) continue;

            // ✅ Serializuj do plain z kolorami (§ zamiast &)
            String serialized = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(loreLine);

            // ✅ Sprawdź czy linia zawiera słowo "eventówki" (bez kolorów)
            String plain = PlainTextComponentSerializer.plainText().serialize(loreLine);
            if (!plain.toLowerCase().contains("eventówki") &&
                !plain.toLowerCase().contains("eventowki")) {
                continue;
            }

            // ✅ Znajdź wszystkie liczby po &a (do zignorowania)
            java.util.Set<String> toIgnore = new java.util.HashSet<>();
            Matcher ignoreMatcher = IGNORE_PATTERN.matcher(serialized);
            while (ignoreMatcher.find()) {
                toIgnore.add(ignoreMatcher.group(1));
            }

            // ✅ Znajdź liczby po &6, &f, &e
            Matcher matcher = PERCENT_PATTERN.matcher(serialized);
            while (matcher.find()) {
                String numStr = matcher.group(1);

                // ✅ Pomiń jeśli ta sama liczba jest też po &a
                if (toIgnore.contains(numStr)) continue;

                try {
                    double value = Double.parseDouble(numStr);
                    return value; // Jeden bonus na część zbroi
                } catch (NumberFormatException ignored) {}
            }
        }

        return 0.0;
    }

    /**
     * Aplikuje redukcję zbroi do damage.
     *
     * @param originalDamage Oryginalny damage
     * @param reductionPercent Redukcja w procentach (0-100)
     * @return Zredukowany damage
     */
    public static double applyReduction(double originalDamage, double reductionPercent) {
        if (reductionPercent <= 0) return originalDamage;
        if (reductionPercent >= 100) return 0.0;
        return originalDamage * (1.0 - reductionPercent / 100.0);
    }

    /**
     * Oblicza i aplikuje redukcję zbroi do damage w jednym kroku.
     *
     * @param originalDamage Oryginalny damage
     * @param victim Gracz który dostaje damage
     * @return Zredukowany damage
     */
    public static double applyArmorReduction(double originalDamage, Player victim) {
        double reduction = getTotalEventReduction(victim);
        return applyReduction(originalDamage, reduction);
    }
}
