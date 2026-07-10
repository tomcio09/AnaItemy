package pl.anaheim.anaitemy.items;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class OlafItem {

    public static final String ITEM_NAME_STRIPPED = "Olaf";
    public static final int CUSTOM_MODEL_DATA = 155902;
    private static final String TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjg2ZTQ2OGYyNDk0ZDgxYjhhZDRkNmVhYTI0MmYyNWI4NmNlNWE0YjgxN2UwMDc1OWViNWNkNWQxOGFhZDU4ZSJ9fX0=";
    private static final UUID PROFILE_UUID = UUID.fromString("E1E2E3E4-F5F6-A7A8-B9B0-C1C2C3C4C5C6");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Przedmiot z &fświątecznego wydarzenia 2025&7!",
                "",
                " &8» &7Po trafieniu przeciwnika przywoła",
                " &8» &bbałwana Olafa&7, który &czasłoni mu widok&7!"
        );

        ItemStack item = new ItemStack(Material.EGG);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&b&lOlaf").decoration(TextDecoration.ITALIC, false));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isOlaf(ItemStack item) {
        if (item == null || item.getType() != Material.EGG) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        if (item.getItemMeta().getCustomModelData() != CUSTOM_MODEL_DATA) return false;
        if (item.getItemMeta().displayName() == null) return false;
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(ITEM_NAME_STRIPPED);
    }

    public static String getSnowmanTexture() { return TEXTURE; }
    public static UUID getProfileUUID() { return PROFILE_UUID; }
}
