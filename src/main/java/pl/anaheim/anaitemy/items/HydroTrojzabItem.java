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

public class HydroTrojzabItem {

    public static final String ITEM_NAME_STRIPPED = "Hydro Trojzab";
    public static final int CUSTOM_MODEL_DATA = 104141741;
    private static final NamespacedKey TROJZAB_KEY = new NamespacedKey(AnaItemy.getInstance(), "hydro_trojzab");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8\u00bb &7Jest to przedmiot zdobyty podczas",
                " &8\u00bb &fwydarzenia wakacyjnego 2025&7!",
                "",
                " &8\u00bb &7Po rzuceniu trojzebem w miejsce",
                " &8\u00bb &7uderzenia &buderza piorun&7, ktory",
                " &8\u00bb &7zadaje obrazenia i odpycha",
                " &8\u00bb &7pobliskich przeciwnikow!",
                "",
                " &8\u00bb &7Klikajac &fSHIFT &7mozesz sie",
                " &8\u00bb &7wystrzelic w dowolnym miejscu!"
        );

        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&3&lHydro Trojzab")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // ✅ NBT marker do odróżnienia od innych łuków
        meta.getPersistentDataContainer().set(TROJZAB_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHydroTrojzab(ItemStack item) {
        if (item == null || item.getType() != Material.BOW) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();

        // ✅ Sprawdź NBT marker
        Byte marker = meta.getPersistentDataContainer().get(TROJZAB_KEY, PersistentDataType.BYTE);
        if (marker != null && marker == (byte) 1) return true;

        // Fallback: sprawdź CMD + nazwę
        if (!meta.hasCustomModelData()) return false;
        if (meta.getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (meta.displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
