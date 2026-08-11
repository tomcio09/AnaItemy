// src/main/java/pl/anaheim/anaitemy/items/ZlamaneSerceItem.java
package pl.anaheim.anaitemy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZlamaneSerceItem {

    public static final int CUSTOM_MODEL_DATA = 56463534;
    
    // ✅ NOWE: Klucz PDC do identyfikacji itemu (niezawodny sposób)
    private static final String PDC_KEY = "zlamane_serce";
    private static NamespacedKey pdcKey = null;

    public static void init(org.bukkit.plugin.Plugin plugin) {
        pdcKey = new NamespacedKey(plugin, PDC_KEY);
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.PURPLE_DYE);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&5&lZłamane serce")
                .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        for (String line : Arrays.asList(
                " &8» &7Jest to przedmiot z:",
                " &8» &feventu walentynkowego (2026)",
                "",
                " &8» &7Uderz nim przeciwnika, aby",
                " &8» &7złamać jego &dserduszko&7!")) {
            lore.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        
        // ✅ NOWE: Dodaj PDC tag
        if (pdcKey != null) {
            meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        }
        
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isZlamaneSerce(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.PURPLE_DYE) return false;
        if (!item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // ✅ Metoda 1: Sprawdź PDC (najpewniejsza)
        if (pdcKey != null && meta.getPersistentDataContainer().has(pdcKey, PersistentDataType.BYTE)) {
            return true;
        }
        
        // ✅ Metoda 2: Sprawdź CustomModelData (fallback dla starych itemów)
        if (meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA) {
            return true;
        }
        
        return false;
    }
}
