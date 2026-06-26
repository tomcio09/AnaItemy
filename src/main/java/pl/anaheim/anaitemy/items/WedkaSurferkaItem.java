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

public class WedkaSurferkaItem {

    public static final String ITEM_NAME_STRIPPED = "Wędka surferka";
    public static final int CUSTOM_MODEL_DATA = 1;
    private static final NamespacedKey SURFERKA_KEY = new NamespacedKey(AnaItemy.getInstance(), "wedka_surferka");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot z:",
                " &8» &feventu wakacyjnego (2023)",
                "",
                " &8» &7Ta &aniezwykła &7wędka zapewni ci",
                " &8» &7zdumiewającą zdolność przyciągania się",
                " &8» &7do &bbloków &7i &dgraczy&7, tworząc zupełnie",
                " &8» &7nowe możliwości eksploracji walki!"
        );

        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&b&lWędka surferka")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(SURFERKA_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isWedkaSurferka(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta()) return false;

        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(SURFERKA_KEY, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return false;

        if (item.getItemMeta().displayName() == null) return false;
        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
