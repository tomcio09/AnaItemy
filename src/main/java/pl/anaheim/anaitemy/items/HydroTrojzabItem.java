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

public class HydroTrojzabItem {

    public static final String ITEM_NAME_STRIPPED = "Hydro Trójząb";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                "",
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fwydarzenia wakacyjnego 2025&7!",
                "",
                " &8» &7Po rzuceniu trójzębem w miejsce",
                " &8» &7uderzenia &buderza piorun&7, który",
                " &8» &7zadaje obrażenia i odpycha",
                " &8» &7pobliskich przeciwników!",
                "",
                " &8» &7Klikając &fSHIFT &7możesz się",
                " &8» &7wystrzelić w dowolnym miejscu!"
        );

        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&3&lHydro Trójząb")
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

        // ✅ Założyłem CMD = 1 dla łuku.
        // Jeśli resourcepack ma inne override, zmień w items.yml
        meta.setCustomModelData(1);

        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHydroTrojzab(ItemStack item) {
        if (item == null || item.getType() != Material.BOW) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 1) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
