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
    private final Set<UUID> totemProtectedPlayers = new HashSet<>();

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokujemy vanilla resurrect dla customowego totemu.
     * Nasz totem zachowuje ekwipunek po smierci, a nie ratuje zycie.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean mainTotem = TotemUlaskawienia.isTotemUlaskawienia(mainHand);
        boolean offTotem = TotemUlaskawienia.isTotemUlaskawienia(offHand);

        if (!mainTotem && !offTotem) return;

        // ✅ Anuluj vanilla resurrect — nasz totem dziala przez PlayerDeathEvent
        event.setCancelled(true);
    }

    /**
     * ✅ Uniwersalna obsluga totemu:
     * Priorytet LOWEST = uruchamia sie PIERWSZY, zanim cokolwiek innego.
     * Dziala na KAZDY rodzaj smierci:
     * - vanilla damage
     * - /kill
     * - setHealth(0)
     * - smierc z innych pluginow
     * - custom kill z Elytry / Rozdzki / etc.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!hasTotemInHand(player)) return;

        ItemsConfig config = plugin.getItemsConfig();
        boolean inBlockedRegion = plugin.getWorldGuardManager().isInBlockedRegion(
                player.getLocation(),
                config.getTotemBlockedRegions()
        );

        // Poza blocked regionem totem jest konsumowany
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

                var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
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
            if (offHand.getAmount() > 1) offHand.setAmount(offHand.getAmount() - 1);
            else player.getInventory().setItemInOffHand(null);
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (TotemUlaskawienia.isTotemUlaskawienia(mainHand)) {
            if (mainHand.getAmount() > 1) mainHand.setAmount(mainHand.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);
        }
    }

    public boolean isTotemProtected(UUID playerUUID) {
        return totemProtectedPlayers.contains(playerUUID);
    }

    public void removeProtection(UUID playerUUID) {
        totemProtectedPlayers.remove(playerUUID);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
