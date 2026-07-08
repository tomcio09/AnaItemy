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

public class PrzeterminowanyTrunekItem {
    public static final String ITEM_NAME_STRIPPED = "Przeterminowany trunek";
    public static final int CUSTOM_MODEL_DATA = 4325325;

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&2&lPrzeterminowany trunek").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(" &8» &7Jest to przedmiot zdobyty podczas", " &8» &fwydarzenia pirackiego 2025&7!", "", " &8» &7Po rzuceniu na ziemię, uwalnia &emgiełkę&7, która", " &8» &7nakłada &atruciznę &7i &aspowolnienie", " &8» &7na pobliskich graczy przez &ckilkadziesiąt &7sekund."))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPrzeterminowanyTrunek(ItemStack item) {
        if (item == null || item.getType() != Material.HONEY_BOTTLE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
