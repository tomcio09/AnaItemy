package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class RozszerzenieECItem {
    public static final String ITEM_NAME_STRIPPED = "Rozszerzenie enderchesta";
    public static final int CUSTOM_MODEL_DATA = 1;

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.FLOWER_BANNER_PATTERN);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&5&lRozszerzenie enderchesta").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(
                " &8» &7Pozwala rozszerzyć enderchesta o",
                " &8» &ddodatkowe 9 slotów&7!",
                "",
                " &8» &cUwaga! &7Maksymalnie możesz",
                " &8» &frozszerzyć enderchesta &7tylko o",
                " &8» &7trzy dodatkowe linie!"))
            lore.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRozszerzenieEC(ItemStack item) {
        if (item == null || item.getType() != Material.FLOWER_BANNER_PATTERN) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())
                .equals(ITEM_NAME_STRIPPED);
    }
}
