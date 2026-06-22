package pl.anaheim.anaitemy.listeners;

import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.config.ItemsConfig;
import pl.anaheim.anaitemy.items.RozdzkailuzjonistyItem;
import pl.anaheim.anaitemy.managers.RozdzkailuzjonistyManager;

public class RozdzkailuzjonistyListener implements Listener {

    private final AnaItemy plugin;

    public RozdzkailuzjonistyListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    // ==================== BLOKOWANIE UŻYWANIA MOTYKI ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!RozdzkailuzjonistyItem.isRozdzkaIluzjonisty(item)) return;

        Player player = event.getPlayer();
        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        Action action = event.getAction();

        // LPM (LEFT CLICK) - Szczęki Evokera
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            manager.activateFangs(player);
            return;
        }

        // PPM (RIGHT CLICK) - Zniknięcie
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            manager.activateVanish(player);
            return;
        }
    }

    // ==================== FANGS DAMAGE ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onFangDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof EvokerFangs fang)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        // Sprawdź czy ta szczęka już zadała damage temu graczowi
        if (manager.hasFangDamaged(fang, victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Sprawdź czy lokalizacja jest w zablokowanym regionie
        if (!manager.canFangDamageInRegion(victim.getLocation())) {
            event.setCancelled(true);
            fang.remove();
            manager.cleanupFang(fang);
            return;
        }

        // Ustaw custom damage
        ItemsConfig config = plugin.getItemsConfig();
        double damage = config.getRozdzkailuzjonistyFangsDamage();
        event.setDamage(damage);

        // Oznacz gracza jako trafionego
        manager.markFangDamaged(fang, victim.getUniqueId());

        // Usuń szczękę po trafieniu
        fang.remove();
        manager.cleanupFang(fang);
    }

    // ==================== VANISH - ZAKOŃCZENIE PRZY ATAKU ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        // Jeśli gracz jest w zniknięciu i atakuje - zakończ zniknięcie
        if (manager.isVanished(attacker)) {
            manager.endVanish(attacker, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        // Jeśli gracz jest w zniknięciu i interactuje z entity - zakończ zniknięcie
        if (manager.isVanished(player)) {
            manager.endVanish(player, true);
        }
    }
}
