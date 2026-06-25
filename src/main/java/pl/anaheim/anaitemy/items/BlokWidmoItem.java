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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlokWidmoItem {

    public static final String ITEM_NAME_STRIPPED = "Blok widmo";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fDnia Dziecka 2025&7!",
                "",
                " &8» &7Po postawieniu na ziemi,",
                " &8» &7uwalnia &eenergię&7, która obniża",
                " &8» &fmaksymalny limit &7serc pobliskim",
                " &8» &7graczom na określony czas..."
        );

        ItemStack item = new ItemStack(Material.STRUCTURE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&c&lBlok widmo")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            Component lineComp = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line)
                    .decoration(TextDecoration.ITALIC, false);
            loreComponents.add(lineComp);
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(0);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBlokWidmo(ItemStack item) {
        if (item == null || item.getType() != Material.STRUCTURE_BLOCK) return false;
        if (!item.hasItemMeta()) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
