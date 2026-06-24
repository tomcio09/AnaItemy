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

    // ✅ Publiczny set - SakiewkaListener sprawdza czy gracz użył totemu
    private final Set<UUID> totemProtectedPlayers = new HashSet<>();

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokujemy vanilla resurrect dla customowego totemu,
     * bo nasz totem ma zachować ekwipunek po śmierci, a nie ratować życie.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getHand() != null
                ? player.getInventory().getItem(event.getHand())
                : null;

        if (!TotemUlaskawienia.isTotemUlaskawienia(item)) return;

        event.setCancelled(true);
    }

    /**
     * ✅ Uniwersalna obsługa totemu:
     * działa na KAŻDY rodzaj śmierci:
     * - vanilla damage
     * - /kill
     * - setHealth(0)
     * - śmierć z innych pluginów
     * - custom kill z Elytry / Różdżki
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!hasTotemInHand(player)) return;

        ItemsConfig config = plugin.getItemsConfig();
        boolean inBlockedRegion = plugin.getWorldGuardManager().isInBlockedRegion(
                player.getLocation(),
                config.getTotemBlockedRegions()
        );

        // ✅ Zachowujemy poprzednie zachowanie:
        // poza blocked regionem totem jest konsumowany,
        // w blocked regionie nie konsumujemy go.
        if (!inBlockedRegion) {
            consumeOneTotem(player);
        }

        UUID playerId = player.getUniqueId();
        totemProtectedPlayers.add(playerId);

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        String message = config.getTotemDeathMessage()
                .replace("{victim}", player.getName());

        Component msg = color(message);
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                totemProtectedPlayers.remove(playerId);
                return;
            }

            player.spigot().respawn();

            Bukkit.getScheduler().runTask(plugin, () -> {
                totemProtectedPlayers.remove(playerId);

                if (!player.isOnline()) return;

                var maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHealth = maxHealthAttribute != null
                        ? maxHealthAttribute.getValue()
                        : 20.0;

                player.setHealth(maxHealth);
            });
        });
    }

    private boolean hasTotemInHand(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        return TotemUlaskawienia.isTotemUlaskawienia(mainHand)
                || TotemUlaskawienia.isTotemUlaskawienia(offHand);
    }

    private void consumeOneTotem(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (TotemUlaskawienia.isTotemUlaskawienia(offHand)) {
            player.getInventory().setItemInOffHand(null);
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand)) {
            player.getInventory().setItemInMainHand(null);
        }
    }

    /**
     * ✅ Sprawdza czy gracz jest chroniony przez totem (używane przez SakiewkaListener).
     */
    public boolean isTotemProtected(UUID playerUUID) {
        return totemProtectedPlayers.contains(playerUUID);
    }

    /**
     * ✅ Ręczne usunięcie ochrony (na wszelki wypadek).
     */
    public void removeProtection(UUID playerUUID) {
        totemProtectedPlayers.remove(playerUUID);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
