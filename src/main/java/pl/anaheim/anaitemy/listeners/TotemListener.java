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
import org.bukkit.event.entity.EntityDamageEvent;
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

    // ✅ API — inne pluginy mogą sprawdzić czy gracz jest chroniony totemem
    // Używane np. przez plugin na kostiumy żeby nie zabierać kostiumu przy śmierci
    private final Set<UUID> keepInventoryDeaths = new HashSet<>();

    public TotemListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokujemy vanilla resurrect dla customowego totemu.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (hasTotemInHand(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * ✅ Przechwytujemy KAŻDY damage który sprowadza gracza do 0 HP.
     * Jeśli gracz ma totem — ratujemy go ZANIM umrze.
     * To działa nawet z setHealth(0) bo EntityDamageEvent jest wywoływany
     * przed faktyczną śmiercią.
     *
     * Priorytet LOWEST = uruchamiamy się PRZED wszystkim innym.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Sprawdź czy po tym damage gracz by umarł
        double healthAfter = player.getHealth() - event.getFinalDamage();
        if (healthAfter > 0) return;

        // Gracz by umarł — sprawdź totem
        if (!hasTotemInHand(player)) return;

        // ✅ Oznacz gracza jako chronionego PRZED śmiercią
        totemProtectedPlayers.add(player.getUniqueId());
    }

    /**
     * ✅ Główna obsługa śmierci z totemem.
     * Priorytet LOWEST = uruchamia się PIERWSZY.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // ✅ Sprawdź czy gracz ma totem (mógł go stracić między damage a death)
        // Lub czy został oznaczony w onDamage
        boolean hasTotem = hasTotemInHand(player) || totemProtectedPlayers.contains(player.getUniqueId());

        if (!hasTotem) return;

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
        keepInventoryDeaths.add(playerId);

        // ✅ KLUCZOWE — ustaw ZANIM cokolwiek innego przetworzy event
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
                keepInventoryDeaths.remove(playerId);
                return;
            }

            player.spigot().respawn();

            Bukkit.getScheduler().runTask(plugin, () -> {
                totemProtectedPlayers.remove(playerId);
                keepInventoryDeaths.remove(playerId);

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

    /**
     * ✅ Sprawdza czy gracz jest chroniony przez totem (używane przez SakiewkaListener).
     */
    public boolean isTotemProtected(UUID playerUUID) {
        return totemProtectedPlayers.contains(playerUUID);
    }

    /**
     * ✅ Ręczne usunięcie ochrony.
     */
    public void removeProtection(UUID playerUUID) {
        totemProtectedPlayers.remove(playerUUID);
    }

    /**
     * ✅ API: Sprawdza czy dana śmierć gracza jest chroniona przez Totem Ułaskawienia
     * i keepInventory jest aktywne.
     *
     * UŻYCIE W INNYCH PLUGINACH:
     *
     * Inne pluginy (np. plugin na kostiumy, plugin na serca) mogą sprawdzić
     * czy gracz umarł z totemem i nie powinien tracić itemów/efektów:
     *
     * Przykład użycia w pluginie na kostiumy:
     * -----------------------------------------------
     * // W listenerze PlayerDeathEvent:
     * @EventHandler(priority = EventPriority.MONITOR)
     * public void onDeath(PlayerDeathEvent event) {
     *     Player player = event.getEntity();
     *
     *     // Sprawdź czy AnaItemy jest załadowany
     *     Plugin anaItemy = Bukkit.getPluginManager().getPlugin("AnaItemy");
     *     if (anaItemy != null && anaItemy.isEnabled()) {
     *         try {
     *             // Pobierz TotemListener z AnaItemy
     *             Object totemListener = anaItemy.getClass().getMethod("getTotemListener").invoke(anaItemy);
     *
     *             // Sprawdź czy śmierć jest chroniona totemem
     *             boolean isKeepInv = (boolean) totemListener.getClass()
     *                     .getMethod("isKeepInventoryDeath", java.util.UUID.class)
     *                     .invoke(totemListener, player.getUniqueId());
     *
     *             if (isKeepInv) {
     *                 // Gracz umarł z totemem — NIE zabieraj kostiumu!
     *                 // NIE zabieraj serc!
     *                 // event.setKeepInventory(true) już jest ustawione przez AnaItemy
     *                 return;
     *             }
     *         } catch (Exception ignored) {
     *             // AnaItemy nie ma tej metody lub błąd — kontynuuj normalnie
     *         }
     *     }
     *
     *     // Normalna logika pluginu — zabierz kostium / serce / etc.
     *     handleNormalDeath(player);
     * }
     * -----------------------------------------------
     *
     * Alternatywnie, prostsze podejście bez refleksji:
     * -----------------------------------------------
     * @EventHandler(priority = EventPriority.MONITOR)
     * public void onDeath(PlayerDeathEvent event) {
     *     // Jeśli keepInventory jest true — ktoś (np. totem) chronił gracza
     *     if (event.getKeepInventory()) {
     *         // Nie zabieraj kostiumu / serc
     *         return;
     *     }
     *
     *     // Normalna śmierć
     *     handleNormalDeath(event.getEntity());
     * }
     * -----------------------------------------------
     *
     * @param playerUUID UUID gracza
     * @return true jeśli gracz właśnie umarł z totemem i ma keepInventory
     */
    public boolean isKeepInventoryDeath(UUID playerUUID) {
        return keepInventoryDeaths.contains(playerUUID);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text)
                .decoration(TextDecoration.ITALIC, false);
    }
}
