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
import java.util.*;

public class CiepleMlekoItem {
    public static final String ITEM_NAME_STRIPPED = "Ciepłe mleko";
    public static final int CUSTOM_MODEL_DATA = 43634712;

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.BOWL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&1&lCiepłe mleko").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(" &8» &7Jest to przedmiot z:", " &8» &feventu świątecznego (2024)", "", " &8» &7Po wypiciu usuwa wszystkie", " &8» &cnegatywne efekty&7! Również posiada", " &8» &7mechanikę stackowania"))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCiepleMleko(ItemStack item) {
        if (item == null || item.getType() != Material.BOWL) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
