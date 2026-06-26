package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LukKupidynaItem {

    public static final String ITEM_NAME_STRIPPED = "Łuk kupidyna";
    public static final int CUSTOM_MODEL_DATA = 1;

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &5&lSpecjalny przedmiot!",
                " &8» &7Masz szansę, że trafiony przez",
                " &8» &7Ciebie gracz zostanie &doślepiony&7!"
        );

        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&4Łuk kupidyna")
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

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.ARROW_DAMAGE, 6, true);
        meta.addEnchant(Enchantment.ARROW_FIRE, 1, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);
        meta.addEnchant(Enchantment.ARROW_INFINITE, 1, true);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isLukKupidyna(ItemStack item) {
        if (item == null || item.getType() != Material.BOW) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
