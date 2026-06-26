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

public class KoronaAnarchiiItem {

    public static final String ITEM_NAME_STRIPPED = "Korona ANARCHII";
    public static final int CUSTOM_MODEL_DATA = 1;

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &eTen przedmiot jest wyjątkowy!",
                " &8» &7Na serwerze znajduje się tylko jedna",
                " &8» &7taka korona! Która nigdy nie zostanie",
                " &8» &7zniszczona.",
                "",
                " &8» &eZalety przedmiotu:",
                " &8» &fStałe efekty:",
                " &8» &7 - Szybkości II",
                " &8» &7 - Odporności na ogień I",
                " &8» &7 - Siła II",
                " &8» &7 - Odporności III",
                " &8» &7 - Szczęście I"
        );

        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&6Korona ANARCHII")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(line)
                            .decoration(TextDecoration.ITALIC, false)
            );
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.PROTECTION_PROJECTILE, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.WATER_WORKER, 1, true);
        meta.addEnchant(Enchantment.OXYGEN, 3, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isKoronaAnarchii(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_HELMET) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
