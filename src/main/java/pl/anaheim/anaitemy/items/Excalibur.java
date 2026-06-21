package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Excalibur {

    public static final String ITEM_NAME_STRIPPED = "Excalibur";

    // Bazowy damage przy 0 killach (netherite sword + sharpness 6 = 11.5)
    public static final double BASE_DAMAGE = 11.5;
    // Maksymalny damage
    public static final double MAX_DAMAGE = 12.0;
    // Długość paska postępu (znaki) - zmniejszone do 10
    public static final int BAR_LENGTH = 10;
    // Znak paska - cienka linia
    public static final String BAR_CHAR = "▬";

    public static ItemStack create(int maxKills) {
        return buildItem(0, maxKills);
    }

    public static ItemStack buildItem(int kills, int maxKills) {
        if (kills > maxKills) kills = maxKills;

        double percent = maxKills > 0 ? (double) kills / maxKills * 100.0 : 0;
        double attackDamage = calculateDamage(kills, maxKills);
        String progressBar = buildProgressBar(kills, maxKills);
        String percentStr = String.format(Locale.US, "%.0f", percent);
        String damageStr = String.format(Locale.US, "%.2f", attackDamage);

        List<String> lore = new ArrayList<>();
        lore.add(" &8» &7Jest to przedmiot zdobyty z");
        lore.add(" &8» &fwydarzenia wakacyjnego 2024&7!");
        lore.add("");
        lore.add(" &8» &7Aktualnie zabójstw: &f" + kills);
        lore.add(" &8» " + progressBar + " &a" + percentStr + "%");
        lore.add("");
        lore.add(" &8» &7Zapełnienie paska zapewnia");
        lore.add(" &8» &7Ci &f12 punktów obrażeń&7, co");
        lore.add(" &8» &7jest równoznaczne z &eostrością 7&7!");
        lore.add("");
        lore.add(" &8» &7Obrażenia od ataku: &f" + damageStr);

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        // Nazwa
        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&e&lExcalibur")
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
        meta.setCustomModelData(5);

        // Enchanty
        meta.addEnchant(Enchantment.DAMAGE_ALL, 6, true); // Sharpness 6
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.DURABILITY, 3, true);

        // Attack speed modifier (-2.4)
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(
                        UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3"),
                        "generic.attack_speed",
                        -2.4,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlot.HAND
                ));

        // Attack damage modifier
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(
                        UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),
                        "generic.attack_damage",
                        attackDamage - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlot.HAND
                ));

        // Ukryj flagi
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS
        );

        item.setItemMeta(meta);
        return item;
    }

    public static double calculateDamage(int kills, int maxKills) {
        if (maxKills <= 0) return BASE_DAMAGE;
        if (kills >= maxKills) return MAX_DAMAGE;
        double progress = (double) kills / maxKills;
        return BASE_DAMAGE + progress * (MAX_DAMAGE - BASE_DAMAGE);
    }

    /**
     * Buduje pasek postępu jako ciągłą cienką linię.
     * Wypełniona część jest zielona (&a), reszta biała (&f).
     */
    public static String buildProgressBar(int kills, int maxKills) {
        if (maxKills <= 0) {
            return "&f" + BAR_CHAR.repeat(BAR_LENGTH);
        }

        int filled = (int) Math.round((double) kills / maxKills * BAR_LENGTH);
        if (filled > BAR_LENGTH) filled = BAR_LENGTH;

        String green = filled > 0 ? "&a" + BAR_CHAR.repeat(filled) : "";
        String white = (BAR_LENGTH - filled) > 0
                ? "&f" + BAR_CHAR.repeat(BAR_LENGTH - filled)
                : "";

        return green + white;
    }

    /**
     * Sprawdza czy ItemStack to Excalibur po nazwie i custom model data.
     */
    public static boolean isExcalibur(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 5) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }

    /**
     * Odczytuje aktualne kills z lore itemu.
     */
    public static int getKillsFromItem(ItemStack item) {
        if (!isExcalibur(item)) return 0;
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return 0;

        for (Component line : lore) {
            String plain = PlainTextComponentSerializer.plainText().serialize(line);
            // Szukamy linii "» Aktualnie zabójstw: X"
            if (plain.contains("Aktualnie zabójstw:")) {
                String[] parts = plain.split(":");
                if (parts.length >= 2) {
                    try {
                        return Integer.parseInt(parts[parts.length - 1].trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }
}
