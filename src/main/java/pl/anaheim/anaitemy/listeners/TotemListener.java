package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TotemListener implements Listener {

    private final AnaItemy plugin;
    private final Set<UUID> usedTotem = new HashSet<>();

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getHand() != null
                ? player.getInventory().getItem(event.getHand())
                : null;

        if (!TotemUlaskawienia.isTotemUlaskawienia(item)) return;

        event.setCancelled(true);
        player.getInventory().setItem(event.getHand(), null);
        usedTotem.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!usedTotem.contains(player.getUniqueId())) return;
        usedTotem.remove(player.getUniqueId());

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Wiadomość z configu
        ItemsConfig config = plugin.getItemsConfig();
        String message = config.getTotemDeathMessage()
                .replace("{victim}", player.getName());

        Component msg = color(message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        // Heal po respawnie
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.spigot().respawn();

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
