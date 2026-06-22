package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class HydroKlatka {

    public static final String ITEM_NAME_STRIPPED = "Wyrzutnia Hydro Klatki";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                "",
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fwydarzenia wakacyjnego 2025&7!",
                "",
                " &8» &7Po użyciu wystrzeliwuje pocisk, który",
                " &8» &7tworzy &3wodną klatkę &7uwięziającą",
                " &8» &7przeciwników w miejscu!"
        );

        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();

        // Nazwa
        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&3&lWyrzutnia Hydro Klatki")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        // Lore
        List<Component> loreComponents = Arrays.asList();
        for (String line : lore) {
            Component lineComp = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line)
                    .decoration(TextDecoration.ITALIC, false);
            loreComponents.add(lineComp);
        }
        meta.lore(loreComponents);

        // Custom model data
        meta.setCustomModelData(2);

        // Glow effect (dodaj enchant i ukryj)
        meta.addEnchant(Enchantment.DURABILITY, 1, true);

        // Niezniszczalny
        meta.setUnbreakable(true);

        // Ukryj flagi
        meta.addItemFlags(
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Sprawdza czy ItemStack to Hydro Klatka.
     */
    public static boolean isHydroKlatka(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 2) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
