package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarchewkowyMieczItem {

    public static final String ITEM_NAME_STRIPPED = "Marchewkowy miecz";
    public static final int CUSTOM_MODEL_DATA = 1;
    private static final NamespacedKey MARCHEWKOWY_KEY = new NamespacedKey(AnaItemy.getInstance(), "marchewkowy_miecz");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Dzięki niemu możesz &bzamrozić &7przeciwnika",
                " &8» &7po &cuderzeniu &7na &asekundę!",
                "",
                " &8» &7Przedmiot z &e&lEVENTU WIELKANOCNEGO!",
                " &8» &7Zdobyty: &f09-04-2023, 08:30:08"
        );

        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&6Marchewkowy miecz")
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
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        meta.getPersistentDataContainer().set(MARCHEWKOWY_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMarchewkowyMiecz(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;

        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(MARCHEWKOWY_KEY, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return false;

        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
