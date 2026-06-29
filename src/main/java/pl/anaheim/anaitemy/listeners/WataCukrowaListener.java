package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WataCukrowaItem;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WataCukrowaListener implements Listener {

    private final AnaItemy plugin;

    public WataCukrowaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!WataCukrowaItem.isWataCukrowa(item)) return;

        event.setCancelled(true);

        // ✅ Znajdź części zbroi które gracz ma założone
        List<ArmorSlot> wornArmor = new ArrayList<>();

        if (player.getInventory().getHelmet() != null
                && !player.getInventory().getHelmet().getType().isAir()) {
            wornArmor.add(new ArmorSlot("Hełm", 0));
        }
        if (player.getInventory().getChestplate() != null
                && !player.getInventory().getChestplate().getType().isAir()) {
            // Sprawdź czy to elytra
            String name = player.getInventory().getChestplate().getType().name();
            if (name.equals("ELYTRA")) {
                wornArmor.add(new ArmorSlot("Elytra", 1));
            } else {
                wornArmor.add(new ArmorSlot("Klata", 1));
            }
        }
        if (player.getInventory().getLeggings() != null
                && !player.getInventory().getLeggings().getType().isAir()) {
            wornArmor.add(new ArmorSlot("Spodnie", 2));
        }
        if (player.getInventory().getBoots() != null
                && !player.getInventory().getBoots().getType().isAir()) {
            wornArmor.add(new ArmorSlot("Buty", 3));
        }

        // ✅ Jeśli gracz nie ma żadnej zbroi
        if (wornArmor.isEmpty()) {
            player.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&cBłąd!"),
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&7Nie masz założonej żadnej zbroi!"),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
            ));
            return;
        }

        // ✅ Losuj część zbroi
        ArmorSlot chosen = wornArmor.get(ThreadLocalRandom.current().nextInt(wornArmor.size()));

        // ✅ Zużyj watę
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // ✅ Dźwięk bell
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,
                SoundCategory.PLAYERS, 1.0f, 1.0f);

        // ✅ Title: rozpoczęto naprawę
        player.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize("&dWata cukrowa"),
                LegacyComponentSerializer.legacyAmpersand()
                        .deserialize("&7Rozpoczęto naprawę: &f" + chosen.name),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(250))
        ));

        // ✅ Po 1.5 sekundy — napraw i pokaż wynik
        final String partName = chosen.name;
        final int slotIndex = chosen.slotIndex;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // ✅ Napraw część zbroi
            ItemStack armorPiece = getArmorBySlot(player, slotIndex);
            if (armorPiece != null && armorPiece.hasItemMeta()) {
                ItemMeta meta = armorPiece.getItemMeta();
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(0);
                    armorPiece.setItemMeta(meta);
                }
            }

            // ✅ Dźwięk naprawy
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE,
                    SoundCategory.PLAYERS, 1.0f, 1.5f);

            // ✅ Title: naprawiono
            player.showTitle(Title.title(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&aNaprawiono!"),
                    LegacyComponentSerializer.legacyAmpersand()
                            .deserialize("&7Element: &f" + partName),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2000), Duration.ofMillis(250))
            ));
        }, 30L); // 1.5 sekundy
    }

    private ItemStack getArmorBySlot(Player player, int slotIndex) {
        return switch (slotIndex) {
            case 0 -> player.getInventory().getHelmet();
            case 1 -> player.getInventory().getChestplate();
            case 2 -> player.getInventory().getLeggings();
            case 3 -> player.getInventory().getBoots();
            default -> null;
        };
    }

    private static class ArmorSlot {
        final String name;
        final int slotIndex; // 0=helmet, 1=chestplate, 2=leggings, 3=boots

        ArmorSlot(String name, int slotIndex) {
            this.name = name;
            this.slotIndex = slotIndex;
        }
    }
}
