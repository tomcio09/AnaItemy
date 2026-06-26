package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KroliczyMieczItem {

    public static final String ITEM_NAME_STRIPPED = "Króliczy miecz";
    public static final int CUSTOM_MODEL_DATA = 2;
    private static final NamespacedKey KROLICZY_KEY = new NamespacedKey(AnaItemy.getInstance(), "kroliczy_miecz");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot z &fwydarzenia",
                " &8» &fwielkanocnego &7z &f2024 roku",
                "",
                " &8» &7Po uderzeniu przeciwnika blokuje",
                " &8» &7możliwość skakania na &f4 sekundy&7!"
        );

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&3&lKróliczy miecz")
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

        // ✅ Enchanty WIDOCZNE (bez hide enchants)
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.DAMAGE_ALL, 6, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);

        // ✅ Marker NBT żeby odróżnić od Excalibura (też netherite sword)
        meta.getPersistentDataContainer().set(KROLICZY_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isKroliczyMiecz(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        // ✅ Sprawdź marker NBT (żeby nie mylić z Excaliburem)
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(KROLICZY_KEY, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
