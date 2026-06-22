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

    // Długość paska postępu - ile spacji dla pełnego paska
    public static final int BAR_LENGTH = 30;

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

        // Pierwsza linia pusta
        lore.add("");

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

        // Attack speed modifier (-2.0 = nieco szybsze bicie)
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED,
                new AttributeModifier(
                        UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3"),
                        "generic.attack_speed",
                        -2.0,
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

    /**
     * EDYTUJE TYLKO KONKRETNE LINIE w istniejącym itemie (nie nadpisuje całego).
     * Używane przy komendzie /itemyeventowe kills <liczba>
     */
    public static ItemStack updateKills(ItemStack item, int kills, int maxKills) {
        if (!isExcalibur(item)) return item;
        if (kills > maxKills) kills = maxKills;

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        double percent = maxKills > 0 ? (double) kills / maxKills * 100.0 : 0;
        double attackDamage = calculateDamage(kills, maxKills);
        String progressBar = buildProgressBar(kills, maxKills);
        String percentStr = String.format(Locale.US, "%.0f", percent);
        String damageStr = String.format(Locale.US, "%.2f", attackDamage);

        // Edytuj tylko konkretne linie
        for (int i = 0; i < lore.size(); i++) {
            String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));

            // Linia z liczbą zabójstw
            if (plain.contains("Aktualnie zabójstw:")) {
                lore.set(i, colorize(" &8» &7Aktualnie zabójstw: &f" + kills));
            }
            // Linia z paskiem postępu (oparta o %)
            else if (plain.contains("%") && plain.contains("»")) {
                lore.set(i, colorize(" &8» " + progressBar + " &a" + percentStr + "%"));
            }
            // Linia z obrażeniami
            else if (plain.contains("Obrażenia od ataku:")) {
                lore.set(i, colorize(" &8» &7Obrażenia od ataku: &f" + damageStr));
            }
        }

        meta.lore(lore);

        // Zaktualizuj attack damage atrybut
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE,
                new AttributeModifier(
                        UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF"),
                        "generic.attack_damage",
                        attackDamage - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlot.HAND
                ));

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
     * Buduje pasek postępu używając STRIKETHROUGH (&m) dla ciągłej linii.
     * To tworzy wizualnie ciągłą linię bez przerw.
     */
    public static String buildProgressBar(int kills, int maxKills) {
        if (maxKills <= 0) {
            // Pełny biały pasek ze strikethrough
            return "&f&m" + " ".repeat(BAR_LENGTH);
        }

        int filled = (int) Math.round((double) kills / maxKills * BAR_LENGTH);
        if (filled > BAR_LENGTH) filled = BAR_LENGTH;

        // Część zielona (wypełniona)
        String green = filled > 0 ? "&a&m" + " ".repeat(filled) : "";

        // Część biała (pusta)
        String white = (BAR_LENGTH - filled) > 0
                ? "&f&m" + " ".repeat(BAR_LENGTH - filled)
                : "";

        return green + white;
    }

    /**
     * Sprawdza czy ItemStack to Excalibur.
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

    private static Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
