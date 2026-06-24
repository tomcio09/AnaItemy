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

public class CudownaLatarniaItem {

    public static final String ITEM_NAME_STRIPPED = "Cudowna Latarnia";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                "",
                " &8» &7Przedmiot z &7świątecznego wydarzenia 2025&7!",
                "",
                " &8» &7Po postawieniu otrzymujesz:",
                " &8» &dRegenerację V &8(20s)",
                " &8» &eAbsorpcję VI &8(10s)",
                " &8» &cSiłę II &8(10s)",
                "",
                " &8» &7Fontanna pozostaje aktywna przez &f30 sekund&7.",
                " &8» &7Efekty działają w zasięgu &f30 kratek&7.",
                " &8» &7Zniszczenie fontanny usuwa efekty."
        );

        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&d&lCudowna Latarnia")
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

    public static boolean isCudownaLatarnia(ItemStack item) {
        if (item == null || item.getType() != Material.BEACON) return false;
        if (!item.hasItemMeta()) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
