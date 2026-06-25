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

public class RogJednorozcaItem {

    public static final String ITEM_NAME_STRIPPED = "Róg Jednorożca";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                "",
                " &8» &7Jest to przedmiot z:",
                " &8» &feventu walentynkowego (2026)",
                "",
                " &8» &7Po użyciu przywołujesz &djednorożca&7,",
                " &8» &7którym możesz jeździć! Niszczyć bloki",
                " &8» &7na swojej drodze i &cogłuszać graczy&7,",
                " &8» &7w których wjedziesz!"
        );

        ItemStack item = new ItemStack(Material.GOAT_HORN);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&d&lRóg Jednorożca")
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

        meta.setCustomModelData(1);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRogJednorozca(ItemStack item) {
        if (item == null || item.getType() != Material.GOAT_HORN) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 1) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
