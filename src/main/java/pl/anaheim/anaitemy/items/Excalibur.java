package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Excalibur {

    public static final String ITEM_NAME_STRIPPED = "Excalibur";

    private static final NamespacedKey KILLS_KEY =
            new NamespacedKey(AnaItemy.getInstance(), "excalibur_kills");

    // ✅ 1.21.4 - NamespacedKey dla AttributeModifier
    private static final NamespacedKey ATTACK_SPEED_KEY =
            new NamespacedKey(AnaItemy.getInstance(), "excalibur_attack_speed");
    private static final NamespacedKey ATTACK_DAMAGE_KEY =
            new NamespacedKey(AnaItemy.getInstance(), "excalibur_attack_damage");

    public static final double BASE_DAMAGE = 11.5;
    public static final double MAX_DAMAGE = 12.0;
    public static final int BAR_LENGTH = 20;

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
        lore.add("");
        lore.add(" &8» &7Jest to przedmiot zdobyty z");
        lore.add(" &8» &fwydarzenia wakacyjnego 2024&7!");
        lore.add("");
        lore.add(" &8» &7Aktualnie zabójstw: &f" + kills);
        lore.add(" &8» " + progressBar + " &r &e" + percentStr + "%");
        lore.add("");
        lore.add(" &8» &7Zapełnienie paska zapewnia");
        lore.add(" &8» &7Ci &f12 punktów obrażeń&7, co");
        lore.add(" &8» &7jest równoznaczne z &eostrością 7&7!");
        lore.add("");
        lore.add(" &8» &7Obrażenia od ataku: &f" + damageStr);

        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&e&lExcalibur")
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

        meta.setCustomModelData(5);

        meta.addEnchant(Enchantment.SHARPNESS, 6, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);

        // ✅ 1.21.4 - AttributeModifier z NamespacedKey + EquipmentSlotGroup
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(
                        ATTACK_SPEED_KEY,
                        -2.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                ));

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(
                        ATTACK_DAMAGE_KEY,
                        attackDamage - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                ));

        meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, kills);

        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS
        );

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack updateKills(ItemStack item, int kills, int maxKills) {
        if (!isExcalibur(item)) return item;
        if (kills > maxKills) kills = maxKills;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, kills);

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        double percent = maxKills > 0 ? (double) kills / maxKills * 100.0 : 0;
        double attackDamage = calculateDamage(kills, maxKills);
        String progressBar = buildProgressBar(kills, maxKills);
        String percentStr = String.format(Locale.US, "%.0f", percent);
        String damageStr = String.format(Locale.US, "%.2f", attackDamage);

        for (int i = 0; i < lore.size(); i++) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(lore.get(i));

            if (plain.contains("Aktualnie zabójstw:")) {
                lore.set(i, colorize(" &8» &7Aktualnie zabójstw: &f" + kills));
            } else if (plain.contains("%") && plain.contains("»")) {
                lore.set(i, colorize(" &8» " + progressBar + " &r &e" + percentStr + "%"));
            } else if (plain.contains("Obrażenia od ataku:")) {
                lore.set(i, colorize(" &8» &7Obrażenia od ataku: &f" + damageStr));
            }
        }

        meta.lore(lore);

        // ✅ 1.21.4 - usuń i dodaj z nowym API
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(
                        ATTACK_DAMAGE_KEY,
                        attackDamage - 1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
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

    public static String buildProgressBar(int kills, int maxKills) {
        if (maxKills <= 0) {
            return "&f&m" + " ".repeat(BAR_LENGTH);
        }

        int filled = (int) Math.round((double) kills / maxKills * BAR_LENGTH);
        if (filled > BAR_LENGTH) filled = BAR_LENGTH;

        String green = filled > 0 ? "&a&m" + " ".repeat(filled) : "";
        String white = (BAR_LENGTH - filled) > 0
                ? "&f&m" + " ".repeat(BAR_LENGTH - filled)
                : "";

        return green + white;
    }

    public static boolean isExcalibur(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != 5) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(item.getItemMeta().displayName());

        return plainName.equals(ITEM_NAME_STRIPPED);
    }

    public static int getKillsFromItem(ItemStack item) {
        if (!isExcalibur(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer kills = meta.getPersistentDataContainer()
                .get(KILLS_KEY, PersistentDataType.INTEGER);
        return kills != null ? kills : 0;
    }

    private static Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
