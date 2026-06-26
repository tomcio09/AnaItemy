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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KukurydzaItem {

    public static final String ITEM_NAME_STRIPPED = "Rozgotowana kukurydza";
    public static final int CUSTOM_MODEL_DATA = 3;
    private static final NamespacedKey KUKURYDZA_KEY = new NamespacedKey(AnaItemy.getInstance(), "kukurydza_item");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot zdobyty z",
                " &8» &fwydarzenia wakacyjnego 2024&7!",
                "",
                " &8» &7Wystrzeliwuje magiczny pocisk,",
                " &8» &7który niszczy zbroję oraz zadaje",
                " &8» &7obrażenia w miejscu wybuchu."
        );

        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&a&lRozgotowana kukurydza")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);

        meta.getPersistentDataContainer().set(KUKURYDZA_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isKukurydza(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;

        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(KUKURYDZA_KEY, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return false;

        if (item.getItemMeta().displayName() == null) return false;
        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
