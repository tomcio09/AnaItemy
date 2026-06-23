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
import java.util.UUID;

public class SakiewkaDropu {

    public static final String ITEM_NAME_STRIPPED = "Sakiewka dropu";
    private static final NamespacedKey UUID_KEY = new NamespacedKey(AnaItemy.getInstance(), "sakiewka_uuid");

    /**
     * Tworzy nową unikalną Sakiewkę Dropu.
     */
    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                "",
                " &8» &7Posiadając ten przedmiot w",
                " &8» &7ekwipunku po &czabiciu &7dowolnego",
                " &8» &fgracza &7jego przedmioty trafiają",
                " &8» &7do twojej &asakiewki&7!",
                "",
                " &8» &eKliknij PRAWYM, aby otworzyć sakiewkę!"
        );

        ItemStack item = new ItemStack(Material.RABBIT_FOOT);
        ItemMeta meta = item.getItemMeta();

        // Nazwa
        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&a&lSakiewka dropu")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        // Lore
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            Component lineComp = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line)
                    .decoration(TextDecoration.ITALIC, false);
            loreComponents.add(lineComp);
        }
        meta.lore(loreComponents);

        // Custom model data
        meta.setCustomModelData(1);

        // Unbreakable (bez glintu)
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // ✅ UNIKALNY UUID - każda sakiewka jest inna
        String uniqueId = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(UUID_KEY, PersistentDataType.STRING, uniqueId);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Sprawdza czy ItemStack to Sakiewka Dropu.
     */
    public static boolean isSakiewka(ItemStack item) {
        if (item == null || item.getType() != Material.RABBIT_FOOT) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 1) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }

    /**
     * Pobiera UUID sakiewki.
     */
    public static String getUUID(ItemStack item) {
        if (!isSakiewka(item)) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(UUID_KEY, PersistentDataType.STRING);
    }

    /**
     * ✅ REGENERUJE UUID sakiewki (tworzy nową unikalną sakiewkę z tymi samymi itemami w środku).
     */
    public static ItemStack regenerateUUID(ItemStack oldSakiewka) {
        if (!isSakiewka(oldSakiewka)) return oldSakiewka;

        // Pobierz zapisane itemy ze starej sakiewki
        List<ItemStack> savedItems = pl.anaheim.anaitemy.utils.SakiewkaData.loadItems(oldSakiewka);

        // Stwórz nową sakiewkę z nowym UUID
        ItemStack newSakiewka = create();

        // Przenieś itemy do nowej sakiewki
        pl.anaheim.anaitemy.utils.SakiewkaData.saveItems(newSakiewka, savedItems);

        return newSakiewka;
    }

    /**
     * Sprawdza czy dwie sakiewki są identyczne (ten sam UUID).
     */
    public static boolean isSameSakiewka(ItemStack item1, ItemStack item2) {
        if (!isSakiewka(item1) || !isSakiewka(item2)) return false;
        String uuid1 = getUUID(item1);
        String uuid2 = getUUID(item2);
        return uuid1 != null && uuid1.equals(uuid2);
    }
}
