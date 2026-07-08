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

public class PiernikItem {
    public static final String ITEM_NAME_STRIPPED = "Piernik";
    public static final int CUSTOM_MODEL_DATA = 346521;

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.COOKIE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&6&lPiernik").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(" &8» &7Jest to przedmiot z:", " &8» &feventu świątecznego (2024)", "", " &8» &7Po zjedzeniu &epiernika &7otrzymujesz", " &8» &7efekt &aszybkości kopania 10&7!"))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPiernik(ItemStack item) {
        if (item == null || item.getType() != Material.COOKIE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
