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
import org.bukkit.inventory.PlayerInventory;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.TotemUlaskawienia;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TotemListener implements Listener {

    private final AnaItemy plugin;

    // Gracze którzy użyli totemu w tej samej sekundzie
    private final Set<UUID> usedTotem = new HashSet<>();

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * BLOKUJEMY vanilla działanie totemu (najwyższy priorytet)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Sprawdź czy to nasz custom totem
        ItemStack item = event.getHand() != null
                ? player.getInventory().getItem(event.getHand())
                : null;

        if (!TotemUlaskawienia.isTotemUlaskawienia(item)) return;

        // ANULUJEMY vanilla efekt totemu (gracz nie zostaje wskrzeszony)
        event.setCancelled(true);

        // Usuwamy totem ręcznie z ręki
        player.getInventory().setItem(event.getHand(), null);

        // Zapamiętujemy że gracz użył totemu
        usedTotem.add(player.getUniqueId());
    }

    /**
     * Po śmierci aktywujemy keep inventory dla graczy z totemem
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

        // Heal po respawnie (2 ticki opóźnienia)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            player.setHealth(maxHealth);
        }, 2L);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
