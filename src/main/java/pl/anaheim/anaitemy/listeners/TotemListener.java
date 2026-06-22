package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TotemListener implements Listener {

    private final AnaItemy plugin;

    // Gracze którzy użyli totemu
    private final Set<UUID> usedTotem = new HashSet<>();

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * BLOKUJEMY vanilla działanie totemu
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Sprawdź czy to nasz custom totem
        ItemStack item = event.getHand() != null
                ? player.getInventory().getItem(event.getHand())
                : null;

        if (!TotemUlaskawienia.isTotemUlaskawienia(item)) return;

        // ANULUJEMY vanilla efekt totemu
        event.setCancelled(true);

        // Usuwamy totem ręcznie z ręki
        player.getInventory().setItem(event.getHand(), null);

        // Zapamiętujemy że gracz użył totemu
        usedTotem.add(player.getUniqueId());
    }

    /**
     * Po śmierci aktywujemy keep inventory i instant respawn
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!usedTotem.contains(player.getUniqueId())) return;

        // Usuń gracza z listy
        usedTotem.remove(player.getUniqueId());

        // KEEP INVENTORY + EXP
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Wiadomość dla wszystkich graczy
        Component msg = color(
                "&cGracz &7" + player.getName() +
                        " &czginął z &eTotemem Ułaskawienia&c!"
        );

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        // INSTANT RESPAWN - bez ekranu śmierci
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.spigot().respawn();
            
            // Heal do max HP po respawnie
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                
                double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(maxHealth);
            });
        });
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
