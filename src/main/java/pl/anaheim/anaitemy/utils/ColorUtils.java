package pl.anaheim.anaitemy.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ColorUtils {

    /**
     * Konwertuje string z kodami & na Component BEZ kursywy.
     */
    public static Component colorize(String text) {
        Component base = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        // Usuń domyślną kursywę itemów
        return base.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /**
     * Konwertuje string z kodami & na zwykły string (stripped).
     */
    public static String strip(String text) {
        return text.replace("&0", "").replace("&1", "").replace("&2", "")
                .replace("&3", "").replace("&4", "").replace("&5", "")
                .replace("&6", "").replace("&7", "").replace("&8", "")
                .replace("&9", "").replace("&a", "").replace("&b", "")
                .replace("&c", "").replace("&d", "").replace("&e", "")
                .replace("&f", "").replace("&l", "").replace("&o", "")
                .replace("&n", "").replace("&m", "").replace("&k", "")
                .replace("&r", "");
    }
}
