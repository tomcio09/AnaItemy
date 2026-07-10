package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AnarchicznySetItem {

    // ==================== SET 2 ====================
    public static ItemStack createHelm2() {
        return createArmor(Material.NETHERITE_HELMET, "&4Anarchiczny Helm II", 1, 6, 6);
    }
    public static ItemStack createKlata2() {
        return createArmor(Material.NETHERITE_CHESTPLATE, "&4Anarchiczna Klata II", 1, 6, 6);
    }
    public static ItemStack createSpodnie2() {
        return createArmor(Material.NETHERITE_LEGGINGS, "&4Anarchiczne Spodnie II", 1, 6, 6);
    }
    public static ItemStack createButy2() {
        return createArmor(Material.NETHERITE_BOOTS, "&4Anarchiczne Buty II", 1, 6, 6);
    }

    // ==================== SET 1 ====================
    public static ItemStack createHelm1() {
        return createArmor(Material.NETHERITE_HELMET, "&4Anarchiczny Helm", 1, 5, 5);
    }
    public static ItemStack createKlata1() {
        return createArmor(Material.NETHERITE_CHESTPLATE, "&4Anarchiczna Klata", 1, 5, 5);
    }
    public static ItemStack createSpodnie1() {
        return createArmor(Material.NETHERITE_LEGGINGS, "&4Anarchiczne Spodnie", 1, 5, 5);
    }
    public static ItemStack createButy1() {
        return createArmor(Material.NETHERITE_BOOTS, "&4Anarchiczne Buty", 1, 5, 5);
    }

    // ==================== NARZĘDZIA ====================
    public static ItemStack createKilof() {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(col("&4Anarchiczny Kilof"));
        meta.setCustomModelData(1);
        meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
        meta.addEnchant(Enchantment.UNBREAKING, 5, true);
        meta.addEnchant(Enchantment.FORTUNE, 5, true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createMiecz() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(col("&4Anarchiczny Miecz"));
        meta.setCustomModelData(1);
        meta.addEnchant(Enchantment.SHARPNESS, 6, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createLuk() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(col("&4Anarchiczny Luk"));
        meta.setCustomModelData(1);
        meta.addEnchant(Enchantment.PUNCH, 3, true);
        meta.addEnchant(Enchantment.POWER, 6, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addEnchant(Enchantment.FLAME, 1, true);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== HELPER ====================
    private static ItemStack createArmor(Material material, String name, int cmd, int protection, int unbreaking) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(col(name));
        meta.setCustomModelData(cmd);
        meta.addEnchant(Enchantment.PROTECTION, protection, true);
        meta.addEnchant(Enchantment.UNBREAKING, unbreaking, true);
        item.setItemMeta(meta);
        return item;
    }

    private static Component col(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text).decoration(TextDecoration.ITALIC, false);
    }
}
