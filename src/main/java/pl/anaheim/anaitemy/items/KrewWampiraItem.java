package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KrewWampiraItem {

    public static final String ITEM_NAME_STRIPPED = "Krew wampira";
    public static final int CUSTOM_MODEL_DATA = 1;
    public static final int MAX_STACK = 8;

    public static ItemStack create() {
        return create(1);
    }

    public static ItemStack create(int amount) {
        if (amount < 1) amount = 1;
        if (amount > MAX_STACK) amount = MAX_STACK;

        ItemStack item = new ItemStack(Material.BEETROOT_SOUP, amount);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&c&lKrew wampira")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot z:",
                " &8» &feventu halloween (2023)",
                "",
                " &8» &7Po kliknięciu uleczy Cię",
                " &8» &7do &cpełnego HP&7!"
        );

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(CUSTOM_MODEL_DATA);
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
}
