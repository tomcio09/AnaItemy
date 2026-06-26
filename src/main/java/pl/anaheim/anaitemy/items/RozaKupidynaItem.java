package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RozaKupidynaItem {

    public static final String ITEM_NAME_STRIPPED = "Róża kupidyna";
    public static final int CUSTOM_MODEL_DATA = 1;

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Przedmiot zdobyty w &fwalentynki w 2024 roku&7!",
                " &8» &7Trzymaj go w ręce aby otrzymywać:",
                " &7 - &fEfekt odporności 1",
                " &7 - &fEfekt regeneracji 2",
                " &7 - &f+5 dodatkowych serc"
        );

        ItemStack item = new ItemStack(Material.POPPY);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&c&lRóża kupidyna")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRozaKupidyna(ItemStack item) {
        if (item == null || item.getType() != Material.POPPY) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
