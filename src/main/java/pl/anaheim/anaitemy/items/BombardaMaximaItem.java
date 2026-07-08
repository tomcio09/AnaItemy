package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.anaheim.anaitemy.AnaItemy;
import java.util.*;

public class BombardaMaximaItem {
    public static final String ITEM_NAME_STRIPPED = "Bombarda maxima";
    public static final int CUSTOM_MODEL_DATA = 1;
    private static final NamespacedKey BOMBARDA_KEY = new NamespacedKey(AnaItemy.getInstance(), "bombarda_maxima");

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&5&lBombarda maxima").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(" &8» &7Po wystrzeleniu wybucha w okolicy", " &8» &7i niszczy każdy możliwy blok oprócz", " &8» &fskały macierzystej&7!"))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(BOMBARDA_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBombardaMaxima(ItemStack item) {
        if (item == null || item.getType() != Material.FIRE_CHARGE) return false;
        if (!item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(BOMBARDA_KEY, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
