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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.SuperMarchewkaItem;
import pl.anaheim.anaitemy.managers.HydroKlatkaManager;
import pl.anaheim.anaitemy.managers.SuperMarchewkaManager;

import java.time.Duration;

public class SuperMarchewkaListener implements Listener {

    private final AnaItemy plugin;

    public SuperMarchewkaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== BLOKADA JEDZENIA ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (SuperMarchewkaItem.isSuperMarchewka(event.getItem())) {
            event.setCancelled(true);
        }
    }

    // ==================== UŻYCIE PPM ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!SuperMarchewkaItem.isSuperMarchewka(item)) return;

        event.setCancelled(true);

        SuperMarchewkaManager manager = plugin.getSuperMarchewkaManager();

        // Cooldown
        if (manager.isOnCooldown(player)) {
            long remaining = manager.getCooldownRemaining(player);
            String subtitle = plugin.getItemsConfig().getSuperMarchewkaCooldownSubtitle()
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

        // Blocked region
        if (manager.isInBlockedRegion(player.getLocation())) {
            return;
        }

        // Sprawdź czy gracz jest w hydroklatce
        HydroKlatkaManager hydroManager = plugin.getHydroKlatkaManager();
        boolean inKlatka = hydroManager.getKlatkaForPlayer(player) != null;

        manager.activate(player, inKlatka);
    }

    // ==================== ŚMIERĆ ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        plugin.getSuperMarchewkaManager().cleanupPlayer(event.getEntity());
    }

    // ==================== WYLOGOWANIE ====================

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSuperMarchewkaManager().cleanupPlayer(event.getPlayer());
    }
}
