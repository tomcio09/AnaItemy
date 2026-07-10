package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class KamienKowalskiItem {
    public static final String ITEM_NAME_STRIPPED = "Kamień kowalski";
    public static final int CUSTOM_MODEL_DATA = 10;

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&cKamień kowalski").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(
                " &8» &7Powoduje, że przedmiot po ulepszeniu",
                " &8» &7u &fKowala &7nie cofa swojego poziomu w",
                " &8» &7przypadku &eniepowodzenia&7!"))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isKamienKowalski(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_DYE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
