package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.BoskiToporItem;
import pl.anaheim.anaitemy.managers.BoskiToporManager;

import java.time.Duration;

public class BoskiToporListener implements Listener {

    private final AnaItemy plugin;

    public BoskiToporListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== AKTYWACJA PPM ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!BoskiToporItem.isBoskiTopor(item)) return;

        // ✅ NIE anulujemy eventu - topór ma działać normalnie (niszczenie bloków)
        // Anulujemy tylko jeśli PPM w powietrze (nie na blok)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Pozwól na normalne użycie na bloku (np. odkorowanie drewna)
            // Aktywacja tylko w powietrze
            return;
        }

        BoskiToporManager manager = plugin.getBoskiToporManager();

        if (manager.isOnCooldown(player)) {
            long remaining = manager.getCooldownRemaining(player);
            String subtitle = plugin.getItemsConfig().getBoskiToporCooldownSubtitle()
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

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO,
                    SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }

        if (manager.isInBlockedRegion(player.getLocation())) {
            return;
        }

        manager.activate(player);
    }

    // ==================== NIEŚMIERTELNOŚĆ ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        BoskiToporManager manager = plugin.getBoskiToporManager();

        if (manager.isInvincible(player)) {
            event.setCancelled(true);
        }
    }

    // ==================== WYLOGOWANIE ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.isGlowing()) {
            player.setGlowing(false);
        }
    }
}
