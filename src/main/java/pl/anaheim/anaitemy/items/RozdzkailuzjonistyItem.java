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

public class RozdzkailuzjonistyItem {

    public static final String ITEM_NAME_STRIPPED = "Różdżka Iluzjonisty";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fwydarzenia cyrkowego 2025&7!",
                "",
                " &8» &7LPM: przywołuje &dSzczęki Evokera",
                " &8» &7atakujące przeciwników na wprost.",
                "",
                " &8» &7PPM: &dznikasz &7na &e4s &7i tworzysz",
                " &8» &dklona &7biegnącego do przodu."
        );

        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&5&lRóżdżka Iluzjonisty")
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
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRozdzkaIluzjonisty(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_HOE) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 1) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
