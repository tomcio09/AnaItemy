package pl.anaheim.anaitemy.items;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class KostkaRubikaItem {

    public static final String ITEM_NAME_STRIPPED = "Kostka Rubika";
    private static final String TEXTURE_VALUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODUxM2YwZjFkYzA2MzJkYjhmYzFjMjc5MmE2NjI5ZTA5ZDQ3YzdiMTJjNjM4MjRhNzVjOTRiZjRjZDdlODFkYyJ9fX0=";
    // ✅ Stały UUID żeby itemy się stackowały
    private static final UUID PROFILE_UUID = UUID.fromString("A1A2A3A4-B5B6-C7C8-D9D0-E1E2E3E4E5E6");

    public static ItemStack create() {
        List<String> lore = Arrays.asList(
                " &8» &7Kostka Rubika była do zdobycia",
                " &8» &7w &f2026 roku &7podczas &adnia dziecka",
                "",
                " &8» &7Uderz gracza, aby jego ekwipunek",
                " &8» &7został &fprzemieszany!"
        );

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        Component nameComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&e&lKostka Rubika")
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(nameComponent);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        // ✅ Stały profil z tym samym UUID = stackowanie
        PlayerProfile profile = Bukkit.createProfile(PROFILE_UUID, "KostkaRubika");
        profile.setProperty(new ProfileProperty("textures", TEXTURE_VALUE));
        meta.setPlayerProfile(profile);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isKostkaRubika(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return false;
        if (!item.hasItemMeta()) return false;
        if (item.getItemMeta().displayName() == null) return false;

        String plainName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        return plainName.equals(ITEM_NAME_STRIPPED);
    }
}
