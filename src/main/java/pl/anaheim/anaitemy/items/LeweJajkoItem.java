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

public class LeweJajkoItem {
    public static final String ITEM_NAME_STRIPPED = "Lewe jajko";
    private static final NamespacedKey LEWE_KEY = new NamespacedKey(AnaItemy.getInstance(), "lewe_jajko");

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.EGG);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&eLewe jajko").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(" &8» &7Jest to przedmiot z &fwydarzenia", " &8» &fwielkanocnego &7z &f2024 roku", "", " &8» &7Dzięki niemu możesz wyrzucić przeciwnika", " &8» &7w powietrze!"))
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(LEWE_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isLeweJajko(ItemStack item) {
        if (item == null || item.getType() != Material.EGG) return false;
        if (!item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer().get(LEWE_KEY, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }
}
