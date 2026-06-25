package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.RogJednorozcaItem;
import pl.anaheim.anaitemy.managers.RogJednorozcaManager;

import java.time.Duration;

public class RogJednorozcaListener implements Listener {

    private final AnaItemy plugin;

    public RogJednorozcaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== UŻYCIE ROGU ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!RogJednorozcaItem.isRogJednorozca(item)) return;

        event.setCancelled(true);

        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();

        if (manager.isOnCooldown(player)) {
            long remaining = manager.getCooldownRemaining(player);
            String subtitle = plugin.getItemsConfig().getRogJednorozcaCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");

            player.showTitle(Title.title(
                    Component.empty(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                    Title.Times.times(
                            Duration.ofMillis(200),
                            Duration.ofMillis(2000),
                            Duration.ofMillis(200)
                    )
            ));
            return;
        }

        if (manager.isInBlockedRegion(player.getLocation())) {
            return;
        }

        if (manager.hasActiveUnicorn(player)) {
            return;
        }

        manager.summonUnicorn(player);
    }

    // ==================== BLOKADA GUI KONIA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory() instanceof HorseInventory)) return;

        if (player.getVehicle() instanceof Horse horse) {
            RogJednorozcaManager manager = plugin.getRogJednorozcaManager();
            if (manager.isUnicornHorse(horse)) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== ZEJŚCIE Z KONIA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        if (!(event.getVehicle() instanceof Horse horse)) return;

        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();
        if (manager.isUnicornHorse(horse)) {
            // Koń znika gdy gracz zejdzie
            manager.forceRemove(player);
        }
    }

    // ==================== ŚMIERĆ KONIA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHorseDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;

        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();
        RogJednorozcaManager.ActiveUnicorn unicorn = manager.getUnicornByHorse(horse);

        if (unicorn == null) return;

        // Nie dropuj niczego z konia
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    // ==================== OGŁUSZONY GRACZ - BLOKADA RUCHU ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStunnedMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();

        if (!manager.isStunned(player)) return;

        // Pozwól na obrót głowy, zablokuj ruch
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStunnedTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();

        if (!manager.isStunned(player)) return;

        // Pozwól na teleport pluginowy (nasz stun system), zablokuj perły itd
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    // ==================== WYLOGOWANIE ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();

        manager.forceRemove(player);
        manager.removeStun(player);
    }

    // ==================== ŚMIERĆ GRACZA ====================

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        RogJednorozcaManager manager = plugin.getRogJednorozcaManager();

        manager.forceRemove(player);
        manager.removeStun(player);
    }
}
