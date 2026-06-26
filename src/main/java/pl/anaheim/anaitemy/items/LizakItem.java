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

public class LizakItem {

    public static final String ITEM_NAME_STRIPPED = "Lizak";
    public static final int CUSTOM_MODEL_DATA = 1;

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Przedmiot zdobyty w &fdzień dziecka&7!",
                " &8» &7Trzymaj go w ręce aby otrzymywać:",
                " &7 - &fEfekt SIŁY 1"
        );

        ItemStack item = new ItemStack(Material.ALLIUM);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&d&lLizak")
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

    public static boolean isLizak(ItemStack item) {
        if (item == null || item.getType() != Material.ALLIUM) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
