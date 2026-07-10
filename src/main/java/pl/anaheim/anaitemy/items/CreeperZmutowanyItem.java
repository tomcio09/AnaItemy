package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class CreeperZmutowanyItem {
    public static final String ITEM_NAME_STRIPPED = "Jajko zmutowanego creepera";

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.CREEPER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&a&lJajko zmutowanego creepera").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fspecjalnego wydarzenia marcowego 2025&7!",
                "",
                " &8» &7Prawym kliknięciem w jajko przywołujesz",
                " &8» &fpotężnego &7creepera, który po &cwybuchu",
                " &8» &7zadaje &eogromne &7obrażenia."))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCreeperZmutowany(ItemStack item) {
        if (item == null || item.getType() != Material.CREEPER_SPAWN_EGG) return false;
        if (!item.hasItemMeta()) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
