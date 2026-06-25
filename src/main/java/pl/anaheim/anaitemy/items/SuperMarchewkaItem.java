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

public class SuperMarchewkaItem {

    public static final String ITEM_NAME_STRIPPED = "Super Nieskończona Marchewka";
    public static final int CUSTOM_MODEL_DATA = 2026;

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fwielkanocnego wydarzenia 2026&7!",
                "",
                " &8» &7Po użyciu &fpowiększa cię do &6ogromnej wielkości&7,",
                " &8» &7nakłada &cSpowolnienie II &7i &aOdporność III",
                " &8» &7oraz &czwiększa &dobrażenia krytyczne&7!"
        );

        ItemStack item = new ItemStack(Material.GOLDEN_CARROT);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&6&lSuper Nieskończona Marchewka")
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
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSuperMarchewka(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_CARROT) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
