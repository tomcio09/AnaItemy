package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;

public class TotemListener implements Listener {

    private final AnaItemy plugin;

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerInventory inventory = player.getInventory();

        // Sprawdź main hand i offhand
        ItemStack mainHand = inventory.getItemInMainHand();
        ItemStack offHand = inventory.getItemInOffHand();

        boolean hasTotem = TotemUlaskawienia.isTotemUlaskawienia(mainHand)
                || TotemUlaskawienia.isTotemUlaskawienia(offHand);

        if (!hasTotem) return;

        // Aktywuj mechanikę totemu
        // Zachowaj ekwipunek i exp (jak keep inventory)
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Usuń totem z ręki (zniknie)
        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand)) {
            inventory.setItemInMainHand(null);
        } else {
            inventory.setItemInOffHand(null);
        }

        // Wyślij wiadomość do wszystkich
        String victimName = player.getName();
        Component deathMessage = color(
                "&cGracz &7" + victimName + " &czginął z &eTotemem Ułaskawienia&c!"
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(deathMessage);
        }

        // Po śmierci ulecz gracza do max HP
        // Używamy schedulera bo podczas eventu śmierci gracz jeszcze ginie
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                double maxHealth = player.getAttribute(
                        org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH
                ).getValue();
                player.setHealth(maxHealth);
            }
        }, 1L);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
