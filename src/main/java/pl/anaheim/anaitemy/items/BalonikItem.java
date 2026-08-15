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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BalonikItem {

    public static final String ITEM_NAME_STRIPPED = "Balonik z helem";
    private static final String ITEM_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTFiZTQ0ZTg0ZjAxMmY0M2ZhODExNzI3ZDJkNzQ2YTEwYjc1ZGQ5MjQzNzZkZDgwZmJjYjE3NzY4M2QzNTNjZSJ9fX0=";
    private static final UUID PROFILE_UUID = UUID.fromString("B2B3B4B5-C6C7-D8D9-E0E1-F2F3F4F5F6F7");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Jest to przedmiot zdobyty podczas",
                " &8» &fDnia dziecka 2026&7!",
                "",
                " &8» &7Po aktywacji unosi się, niszcząc",
                " &8» &7wszystkie bloki znajdujące się nad nim"
        );

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&3&lBalonik z helem")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.setCustomModelData(811);
        // ✅ 1.21.4 - UNBREAKING zamiast DURABILITY
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PlayerProfile profile = Bukkit.createProfile(PROFILE_UUID, "Balonik");
        profile.setProperty(new ProfileProperty("textures", ITEM_TEXTURE));
        meta.setPlayerProfile(profile);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBalonik(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!item.hasItemMeta()) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
