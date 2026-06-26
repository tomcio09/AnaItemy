package pl.anaheim.anaitemy.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.WedkaSurferkaItem;
import pl.anaheim.anaitemy.managers.WedkaSurferkaManager;

import java.time.Duration;

public class WedkaSurferkaListener implements Listener {

    private final AnaItemy plugin;

    public WedkaSurferkaListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (!WedkaSurferkaItem.isWedkaSurferka(mainHand)
                && !WedkaSurferkaItem.isWedkaSurferka(offHand)) return;

        WedkaSurferkaManager manager = plugin.getWedkaSurferkaManager();

        if (event.getState() == PlayerFishEvent.State.REEL_IN
                || event.getState() == PlayerFishEvent.State.IN_GROUND) {

            if (manager.isOnCooldown(player)) {
                long remaining = manager.getCooldownRemaining(player);
                String subtitle = plugin.getItemsConfig().getWedkaSurferkaCooldownSubtitle()
                        .replace("{seconds_left}", remaining + "s");
                player.showTitle(Title.title(
                        Component.empty(),
                        LegacyComponentSerializer.legacyAmpersand().deserialize(subtitle),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(200))
                ));
                return;
            }

            FishHook hook = event.getHook();
            if (hook != null) {
                manager.launchTowards(player, hook.getLocation());
            }
        }
    }
}
