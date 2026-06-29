package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KrewWampiraItem {

    public static final String ITEM_NAME_STRIPPED = "Krew wampira";
    public static final int CUSTOM_MODEL_DATA = 1;
    public static final int MAX_STACK = 8;

    private static final NamespacedKey COUNT_KEY = new NamespacedKey(AnaItemy.getInstance(), "krew_wampira_count");

    public static ItemStack create() {
        return create(1);
    }

    public static ItemStack create(int count) {
        if (count < 1) count = 1;
        if (count > MAX_STACK) count = MAX_STACK;

        ItemStack item = new ItemStack(Material.BEETROOT_SOUP);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&c&lKrew wampira")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.getPersistentDataContainer().set(COUNT_KEY, PersistentDataType.INTEGER, count);

        updateLore(meta, count);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isKrewWampira(ItemStack item) {
        if (item == null || item.getType() != Material.BEETROOT_SOUP) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }

    public static int getCount(ItemStack item) {
        if (!isKrewWampira(item)) return 0;
        Integer count = item.getItemMeta().getPersistentDataContainer()
                .get(COUNT_KEY, PersistentDataType.INTEGER);
        return count != null ? count : 1;
    }

    public static void setCount(ItemStack item, int count) {
        if (!isKrewWampira(item)) return;
        if (count < 1) count = 1;
        if (count > MAX_STACK) count = MAX_STACK;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(COUNT_KEY, PersistentDataType.INTEGER, count);
        updateLore(meta, count);
        item.setItemMeta(meta);
    }

    private static void updateLore(ItemMeta meta, int count) {
        List<String> baseLore = Arrays.asList(
                " &8» &7Jest to przedmiot z:",
                " &8» &feventu halloween (2023)",
                "",
                " &8» &7Po kliknięciu uleczy Cię",
                " &8» &7do &cpełnego HP&7!"
        );

        List<Component> loreComponents = new ArrayList<>();
        for (String line : baseLore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }

        if (count > 1) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("").decoration(TextDecoration.ITALIC, false));
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(" &8» &7Ilość: &f" + count + "&7/&f" + MAX_STACK)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(loreComponents);
    }
}
