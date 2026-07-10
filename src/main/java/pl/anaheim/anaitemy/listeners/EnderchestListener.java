package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.RozszerzenieECItem;
import pl.anaheim.anaitemy.managers.EnderchestManager;

import java.util.UUID;

public class EnderchestListener implements Listener {

    private final AnaItemy plugin;

    public EnderchestListener(AnaItemy plugin) { this.plugin = plugin; }

    /**
     * ✅ Przechwytuj otwieranie bloku enderchest.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderchestOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENDER_CHEST) return;

        Player player = event.getPlayer();

        // ✅ Sprawdź czy gracz trzyma rozszerzenie EC
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (RozszerzenieECItem.isRozszerzenieEC(mainHand)) {
            event.setCancelled(true);
            handleExpansion(player, mainHand);
            return;
        }

        // ✅ Przechwytuj otwieranie — otwórz nasze custom EC zamiast vanilla
        event.setCancelled(true);
        plugin.getEnderchestManager().openEnderchest(player);
    }

    /**
     * ✅ PPM z rozszerzeniem EC w powietrze.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExpansionUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!RozszerzenieECItem.isRozszerzenieEC(mainHand)) return;

        event.setCancelled(true);
        handleExpansion(player, mainHand);
    }

    private void handleExpansion(Player player, ItemStack item) {
        EnderchestManager manager = plugin.getEnderchestManager();

        if (!manager.canExpand(player.getUniqueId())) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&cOsiągnąłeś już maksymalną liczbę rozszerzeń!"));
            return;
        }

        boolean success = manager.expand(player);
        if (success) {
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else player.getInventory().setItemInMainHand(null);

            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&aPomyślnie rozszerzyłeś enderchesta!"));
        }
    }

    /**
     * ✅ Zapisz zawartość EC po zamknięciu.
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        String plainTitle = PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());

        EnderchestManager manager = plugin.getEnderchestManager();
        if (!manager.isEnderchestTitle(plainTitle)) return;

        UUID ownerId = manager.getOwnerFromTitle(plainTitle);
        if (ownerId == null) return;

        manager.saveFromInventory(ownerId, event.getInventory());
    }

    /**
     * ✅ Załaduj dane gracza przy logowaniu.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getEnderchestManager().onPlayerJoin(event.getPlayer());
    }

    /**
     * ✅ Zapisz dane gracza przy wylogowaniu.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getEnderchestManager().onPlayerQuit(event.getPlayer());
    }
}
