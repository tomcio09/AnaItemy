package pl.anaheim.anaitemy.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.utils.ItemBuilder;

import java.util.Arrays;
import java.util.List;

public class TotemUlaskawienia {

    public static final String ITEM_NAME_STRIPPED = "Totem Ułaskawienia";

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot z:",
                " &8» &feventu halloween (2023)",
                "",
                " &8» &7Trzymając go po śmierci &dnie",
                " &8» &dstracisz &7swoich przedmiotów"
        );

        return new ItemBuilder(Material.TOTEM_OF_UNDYING)
                .name("&5&lTotem Ułaskawienia")
                .lore(lore)
                .customModelData(1)
                .build();
    }

    /**
     * Sprawdza czy dany ItemStack to Totem Ułaskawienia (po nazwie i custom model data).
     */
    public static boolean isTotemUlaskawienia(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 1) return false;

        // Sprawdź nazwę
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
