package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.KosaItem;
import pl.anaheim.anaitemy.managers.OslepienieManager;

import java.time.Duration;

public class KosaListener implements Listener {

    private final AnaItemy plugin;

    public KosaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * ✅ Blokada używania motyki (oranie ziemi, tworzenie ścieżek).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (KosaItem.isKosa(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (!KosaItem.isKosa(mainHand)) return;

        OslepienieManager manager = plugin.getOslepienieManager();

        if (manager.isKosaOnCooldown(attacker)) {
            long remaining = manager.getKosaCooldownRemaining(attacker);
            String subtitle = plugin.getItemsConfig().getOslepienieCooldownSubtitle()
                    .replace("{seconds_left}", remaining + "s");

            attacker.showTitle(Title.title(
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

        manager.applyBlindness(attacker, victim, true);
    }
}
