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

public class WzmocnianaElytra {

    public static final String ITEM_NAME_STRIPPED = "Wzmocniona elytra";
    private static final NamespacedKey CHARGE_KEY = new NamespacedKey(AnaItemy.getInstance(), "elytra_charge");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                "",
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fWielkanocnego Wydarzenia 2025&7!",
                "",
                " &8» &7Po naładowaniu elytry do &f100% &7zyskujesz",
                " &8» &cpotężną moc&7, która przy uderzeniu w ziemię",
                " &8» &7uwolni się, oddziałując na pobliskich graczy!"
        );

        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&5&lWzmocniana elytra")
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

        meta.setCustomModelData(1);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);

        // ✅ Załaduj na 0% domyślnie
        meta.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.DOUBLE, 0.0);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isWzmocnianaElytra(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 1) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }

    public static double getCharge(ItemStack item) {
        if (!isWzmocnianaElytra(item)) return 0.0;
        Double charge = item.getItemMeta().getPersistentDataContainer()
                .get(CHARGE_KEY, PersistentDataType.DOUBLE);
        return charge != null ? charge : 0.0;
    }

    public static void setCharge(ItemStack item, double charge) {
        if (!isWzmocnianaElytra(item)) return;
        charge = Math.max(0.0, Math.min(100.0, charge)); // 0-100
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.DOUBLE, charge);
        item.setItemMeta(meta);
    }

    public static void resetCharge(ItemStack item) {
        setCharge(item, 0.0);
    }
}
